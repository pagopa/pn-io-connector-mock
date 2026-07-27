"use strict";

const { expect } = require('chai');
const { toRequest } = require('../app/lib/eventAdapter');

describe('eventAdapter', () => {

  it('maps a Function URL (payload v2) event to the canonical Request', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/messages',
      rawQueryString: 'foo=bar',
      headers: { 'content-type': 'application/json' },
      queryStringParameters: { foo: 'bar' },
      body: '{"a":1}',
      isBase64Encoded: false,
      requestContext: { http: { method: 'POST', path: '/messages' } }
    });
    expect(req.method).to.equal('POST');
    expect(req.path).to.equal('/messages');
    expect(req.headers['content-type']).to.equal('application/json');
    expect(req.query).to.deep.equal({ foo: 'bar' });
    expect(req.rawBody).to.equal('{"a":1}');
  });

  it('takes the relative path straight from rawPath (no stage/base path)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/messages/cf/MOCK-x',
      headers: {},
      requestContext: { http: { method: 'GET', path: '/messages/cf/MOCK-x' } }
    });
    expect(req.path).to.equal('/messages/cf/MOCK-x');
  });

  it('strips the /api/v1 exposure prefix from rawPath (WI6/WI7)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/api/v1/profiles',
      headers: {},
      requestContext: { http: { method: 'POST', path: '/api/v1/profiles' } }
    });
    expect(req.path).to.equal('/profiles');
  });

  it('strips /api/v1 also from nested paths (getMessage)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/api/v1/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3',
      headers: {},
      requestContext: { http: { method: 'GET' } }
    });
    expect(req.path).to.equal('/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3');
  });

  it('maps the bare prefix /api/v1 to "/"', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/api/v1',
      headers: {},
      requestContext: { http: { method: 'GET' } }
    });
    expect(req.path).to.equal('/');
  });

  it('does not strip a prefix that is not on a segment boundary (/api/v1foo)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/api/v1foo/bar',
      headers: {},
      requestContext: { http: { method: 'GET' } }
    });
    expect(req.path).to.equal('/api/v1foo/bar');
  });

  it('leaves an already-bare path unchanged (no prefix present)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/profiles',
      headers: {},
      requestContext: { http: { method: 'POST' } }
    });
    expect(req.path).to.equal('/profiles');
  });

  it('does not strip anything when PATH_PREFIX is disabled (empty)', () => {
    const saved = process.env.PATH_PREFIX;
    process.env.PATH_PREFIX = '';
    delete require.cache[require.resolve('../app/lib/eventAdapter')];
    try {
      const { toRequest: toRequestNoPrefix } = require('../app/lib/eventAdapter');
      const req = toRequestNoPrefix({
        version: '2.0',
        rawPath: '/api/v1/profiles',
        headers: {},
        requestContext: { http: { method: 'POST' } }
      });
      expect(req.path).to.equal('/api/v1/profiles');
    } finally {
      if (saved === undefined) delete process.env.PATH_PREFIX; else process.env.PATH_PREFIX = saved;
      delete require.cache[require.resolve('../app/lib/eventAdapter')];
    }
  });

  it('rebuilds the Cookie header from the separate cookies array (v2)', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/messages',
      headers: {},
      cookies: ['a=1', 'b=2'],
      requestContext: { http: { method: 'GET' } }
    });
    expect(req.headers.cookie).to.equal('a=1; b=2');
  });

  it('decodes a base64-encoded body', () => {
    const req = toRequest({
      version: '2.0',
      rawPath: '/profiles',
      body: Buffer.from('{"fiscal_code":"RSSMRA80A01H501T"}').toString('base64'),
      isBase64Encoded: true,
      requestContext: { http: { method: 'POST' } }
    });
    expect(req.rawBody).to.equal('{"fiscal_code":"RSSMRA80A01H501T"}');
  });

  it('returns rawBody null when the event has no body', () => {
    const req = toRequest({ rawPath: '/messages/cf/id', requestContext: { http: { method: 'GET' } } });
    expect(req.rawBody).to.equal(null);
  });

  it('falls back to "/" when rawPath is absent', () => {
    const req = toRequest({ requestContext: { http: { method: 'GET' } } });
    expect(req.path).to.equal('/');
    expect(req.query).to.equal(null);
  });

  it('throws when requestContext.http.method is missing', () => {
    expect(() => toRequest({ rawPath: '/messages' })).to.throw(/method/);
  });

  it('throws when the event is not an object', () => {
    expect(() => toRequest(null)).to.throw();
  });

  describe('ALB target event', () => {
    function albEvent(overrides) {
      return Object.assign({
        requestContext: { elb: { targetGroupArn: 'arn:aws:elasticloadbalancing:eu-south-1:1:targetgroup/x/abc' } },
        httpMethod: 'POST',
        path: '/api/v1/messages',
        queryStringParameters: {},
        headers: { 'content-type': 'application/json' },
        body: '{"a":1}',
        isBase64Encoded: false
      }, overrides);
    }

    it('maps an ALB event to the canonical Request and strips /api/v1', () => {
      const req = toRequest(albEvent({ queryStringParameters: { foo: 'bar' } }));
      expect(req.method).to.equal('POST');
      expect(req.path).to.equal('/messages');
      expect(req.headers['content-type']).to.equal('application/json');
      expect(req.query).to.deep.equal({ foo: 'bar' });
      expect(req.rawBody).to.equal('{"a":1}');
    });

    it('maps an ALB getMessage path preserving the id', () => {
      const req = toRequest(albEvent({
        httpMethod: 'GET',
        path: '/api/v1/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3',
        body: null
      }));
      expect(req.path).to.equal('/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3');
      expect(req.rawBody).to.equal(null);
    });

    it('decodes a base64 body in an ALB event', () => {
      const req = toRequest(albEvent({
        body: Buffer.from('{"fiscal_code":"RSSMRA80A01H501T"}').toString('base64'),
        isBase64Encoded: true
      }));
      expect(req.rawBody).to.equal('{"fiscal_code":"RSSMRA80A01H501T"}');
      expect(req.isBase64Encoded).to.equal(true);
    });

    it('falls back to "/" and null query when path/query are absent', () => {
      const req = toRequest({ requestContext: { elb: {} }, httpMethod: 'GET' });
      expect(req.path).to.equal('/');
      expect(req.query).to.equal(null);
    });

    it('throws when httpMethod is missing', () => {
      expect(() => toRequest({ requestContext: { elb: {} }, path: '/api/v1/messages' })).to.throw(/httpMethod/);
    });
  });
});
