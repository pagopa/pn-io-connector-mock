"use strict";

const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();

function makeRouter(routingSetContains) {
  return proxyquire('../app/lib/router', {
    './routingSetClient': {
      contains: routingSetContains || (async () => false)
    }
  });
}

function req(overrides) {
  return Object.assign({ method: 'GET', path: '/', headers: {}, rawBody: null }, overrides);
}

describe('router', () => {

  describe('POST /profiles', () => {
    it('routes to REAL when fiscal_code is in the whitelist', async () => {
      const router = makeRouter(async (fc) => fc === 'RSSMRA80A01H501T');
      const decision = await router.route(req({
        method: 'POST', path: '/profiles',
        rawBody: JSON.stringify({ fiscal_code: 'RSSMRA80A01H501T' })
      }));
      expect(decision.endpoint).to.equal('profiles');
      expect(decision.lane).to.equal('REAL');
      expect(decision.matchedCriterion).to.equal('fiscal_code in MapIoConnectorMockRealTaxIdsWhitelist');
    });

    it('routes to MOCK when fiscal_code is not in the whitelist', async () => {
      const router = makeRouter(async () => false);
      const decision = await router.route(req({
        method: 'POST', path: '/profiles',
        rawBody: JSON.stringify({ fiscal_code: 'AAAAAA00A00A000A' })
      }));
      expect(decision.lane).to.equal('MOCK');
      expect(decision.matchedCriterion).to.equal('fiscal_code NOT in MapIoConnectorMockRealTaxIdsWhitelist');
    });

    it('throws 400 when body is not valid JSON', async () => {
      const router = makeRouter();
      try {
        await router.route(req({ method: 'POST', path: '/profiles', rawBody: 'not-json' }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(400);
      }
    });
  });

  describe('POST /messages', () => {
    it('routes to MOCK when subject matches @io:<sequenceName> (marker checked first)', async () => {
      const router = makeRouter(async () => true);
      const decision = await router.route(req({
        method: 'POST', path: '/messages',
        rawBody: JSON.stringify({ fiscal_code: 'RSSMRA80A01H501T', content: { subject: 'Ciao @io:OK_READ_THEN_PAID' } })
      }));
      expect(decision.endpoint).to.equal('messages');
      expect(decision.lane).to.equal('MOCK');
      expect(decision.matchedCriterion).to.equal('subject match @io:<sequenceName>');
    });

    it('routes to REAL when subject has no marker and fiscal_code is whitelisted', async () => {
      const router = makeRouter(async (fc) => fc === 'RSSMRA80A01H501T');
      const decision = await router.route(req({
        method: 'POST', path: '/messages',
        rawBody: JSON.stringify({ fiscal_code: 'RSSMRA80A01H501T', content: { subject: 'Notifica ordinaria' } })
      }));
      expect(decision.lane).to.equal('REAL');
      expect(decision.matchedCriterion).to.equal('no marker; fiscal_code in MapIoConnectorMockRealTaxIdsWhitelist');
    });

    it('throws 400 when subject has no marker and fiscal_code is NOT whitelisted (no leak to REAL)', async () => {
      const router = makeRouter(async () => false);
      try {
        await router.route(req({
          method: 'POST', path: '/messages',
          rawBody: JSON.stringify({ fiscal_code: 'AAAAAA00A00A000A', content: { subject: 'Notifica ordinaria' } })
        }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(400);
      }
    });

    it('throws 400 when content is present but subject is absent (CF not whitelisted)', async () => {
      const router = makeRouter(async () => false);
      try {
        await router.route(req({
          method: 'POST', path: '/messages',
          rawBody: JSON.stringify({ fiscal_code: 'AAAAAA00A00A000A', content: { markdown: 'no subject here' } })
        }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(400);
      }
    });

    it('throws 400 when the request has no body at all (no marker, no whitelisted CF)', async () => {
      const router = makeRouter(async () => false);
      try {
        await router.route(req({
          method: 'POST', path: '/messages', rawBody: null
        }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(400);
      }
    });

    it('reuses an already-parsed body (parsedBody cache)', async () => {
      const router = makeRouter(async () => false);
      const decision = await router.route(req({
        method: 'POST', path: '/messages',
        rawBody: 'IGNORED-should-not-be-parsed',
        parsedBody: { fiscal_code: 'AAAAAA00A00A000A', content: { subject: '@io:OK_READ' } }
      }));
      expect(decision.lane).to.equal('MOCK');
    });

    it('throws 400 when body is not valid JSON', async () => {
      const router = makeRouter();
      try {
        await router.route(req({ method: 'POST', path: '/messages', rawBody: '{bad' }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(400);
      }
    });
  });

  describe('GET /messages/{fiscal_code}/{id}', () => {
    it('routes to MOCK when id starts with MOCK-', async () => {
      const router = makeRouter();
      const decision = await router.route(req({
        method: 'GET', path: '/messages/RSSMRA80A01H501T/MOCK-OK_READ_THEN_PAID-1750579200000-a1b2c3'
      }));
      expect(decision.endpoint).to.equal('getMessage');
      expect(decision.lane).to.equal('MOCK');
      expect(decision.ioMessageId).to.equal('MOCK-OK_READ_THEN_PAID-1750579200000-a1b2c3');
      expect(decision.matchedCriterion).to.equal('id prefix MOCK-');
    });

    it('routes to REAL when id has no MOCK- prefix', async () => {
      const router = makeRouter();
      const decision = await router.route(req({
        method: 'GET', path: '/messages/RSSMRA80A01H501T/01ABCDEF1234567890'
      }));
      expect(decision.lane).to.equal('REAL');
      expect(decision.ioMessageId).to.equal('01ABCDEF1234567890');
      expect(decision.matchedCriterion).to.equal('id without MOCK- prefix');
    });
  });

  describe('unhandled routes', () => {
    it('throws 404 on an unknown path', async () => {
      const router = makeRouter();
      try {
        await router.route(req({ method: 'DELETE', path: '/services/xyz' }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(404);
      }
    });

    it('throws 404 when the path is undefined', async () => {
      const router = makeRouter();
      try {
        await router.route(req({ method: 'GET', path: undefined }));
        expect.fail('should have thrown');
      } catch (err) {
        expect(err.statusCode).to.equal(404);
      }
    });
  });
});
