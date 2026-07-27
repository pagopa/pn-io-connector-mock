"use strict";

const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();

const FUNCTION_URL_EVENT = require('./fixtures/functionurl-event.json');

function makeHandler({ route, forward } = {}) {
  return proxyquire('../app/eventHandler', {
    './lib/router': { route: route || (async () => ({ endpoint: 'messages', lane: 'MOCK', matchedCriterion: 'x' })) },
    './lib/forwarder': { forward: forward || (async () => ({ statusCode: 201, headers: {}, body: '', isBase64Encoded: true })) }
  });
}

function clone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

describe('eventHandler', () => {

  it('never logs the subscription key in clear text', async () => {
    const handler = makeHandler();
    const logged = [];
    const original = console.log;
    console.log = (...args) => logged.push(args.join(' '));
    try {
      await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    } finally {
      console.log = original;
    }
    const all = logged.join('\n');
    expect(all).to.not.contain('test-subscription-key');
    expect(all).to.contain('***');
  });

  it('returns the forwarded response on the happy path', async () => {
    const handler = makeHandler({
      forward: async () => ({ statusCode: 201, headers: { 'X-Test': '1' }, body: 'Ym9keQ==', isBase64Encoded: true })
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(201);
    expect(result.headers['X-Test']).to.equal('1');
    expect(result.isBase64Encoded).to.equal(true);
  });

  it('returns 400 when the event cannot be adapted', async () => {
    const handler = makeHandler();
    const result = await handler.handleEvent({ rawPath: '/messages' }); // manca requestContext.http.method
    expect(result.statusCode).to.equal(400);
    expect(JSON.parse(result.body).status).to.equal(400);
  });

  it('returns 400 when the router signals a malformed body', async () => {
    const handler = makeHandler({
      route: async () => { const e = new Error('Malformed JSON request body'); e.statusCode = 400; throw e; }
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(400);
  });

  it('returns 404 when the router signals an unhandled route', async () => {
    const handler = makeHandler({
      route: async () => { const e = new Error('Unhandled route'); e.statusCode = 404; throw e; }
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(404);
  });

  it('returns 500 when the routing-set lookup (SSM) fails', async () => {
    const handler = makeHandler({
      route: async () => { throw new Error('SSM unavailable'); }
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(500);
  });

  it('returns 502 when forwarding to the chosen lane fails', async () => {
    const handler = makeHandler({
      forward: async () => { throw new Error('ECONNREFUSED'); }
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(502);
    expect(JSON.parse(result.body).status).to.equal(502);
  });

  it('returns 504 when forwarding times out', async () => {
    const handler = makeHandler({
      forward: async () => { const e = new Error('Upstream MOCK lane timed out after 10000ms'); e.timeout = true; throw e; }
    });
    const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT));
    expect(result.statusCode).to.equal(504);
    expect(JSON.parse(result.body).status).to.equal(504);
  });

  // Integrazione adapter + router reali: verifica end-to-end che il prefisso /api/v1 in
  // ingresso venga strippato e che il forwarder riceva il path "nudo" con la corsia corretta.
  describe('adapter + router integration (path prefix)', () => {
    function makeRealHandler(captureForward) {
      return proxyquire('../app/eventHandler', {
        './lib/forwarder': {
          forward: async (req, lane) => {
            captureForward.req = req;
            captureForward.lane = lane;
            return { statusCode: 200, headers: {}, body: '', isBase64Encoded: true };
          }
        }
      });
    }

    it('strips /api/v1 and routes POST /messages with a marker to the MOCK lane', async () => {
      const capture = {};
      const handler = makeRealHandler(capture);
      const result = await handler.handleEvent(clone(FUNCTION_URL_EVENT)); // rawPath /api/v1/messages, subject con @io:
      expect(result.statusCode).to.equal(200);
      expect(capture.req.path).to.equal('/messages');
      expect(capture.lane).to.equal('MOCK');
    });

    it('strips /api/v1 and routes GET getMessage with MOCK- id to the MOCK lane', async () => {
      const capture = {};
      const handler = makeRealHandler(capture);
      const event = {
        version: '2.0',
        rawPath: '/api/v1/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3',
        headers: {},
        requestContext: { http: { method: 'GET' } }
      };
      const result = await handler.handleEvent(event);
      expect(result.statusCode).to.equal(200);
      expect(capture.req.path).to.equal('/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3');
      expect(capture.lane).to.equal('MOCK');
    });

    it('logs the ioMessageId as an explicit field in the routing decision (getMessage)', async () => {
      const capture = {};
      const handler = makeRealHandler(capture);
      const logged = [];
      const original = console.log;
      console.log = (...args) => logged.push(args.join(' '));
      try {
        await handler.handleEvent({
          version: '2.0',
          rawPath: '/api/v1/messages/RSSMRA80A01H501T/MOCK-OK_READ-1750579200000-a1b2c3',
          headers: {},
          requestContext: { http: { method: 'GET' } }
        });
      } finally {
        console.log = original;
      }
      const decisionLine = logged.map((l) => JSON.parse(l)).find((o) => o.msg === 'routing decision');
      expect(decisionLine.ioMessageId).to.equal('MOCK-OK_READ-1750579200000-a1b2c3');
    });

    it('handles an ALB target event: strips /api/v1, routes to MOCK, uses trace id as requestId', async () => {
      const capture = {};
      const handler = makeRealHandler(capture);
      const logged = [];
      const original = console.log;
      console.log = (...args) => logged.push(args.join(' '));
      try {
        await handler.handleEvent({
          requestContext: { elb: { targetGroupArn: 'arn:aws:elasticloadbalancing:eu-south-1:1:targetgroup/x/abc' } },
          httpMethod: 'POST',
          path: '/api/v1/messages',
          headers: { 'content-type': 'application/json', 'x-amzn-trace-id': 'Root=1-abc' },
          body: JSON.stringify({ fiscal_code: 'MRORSS80A01H501K', content: { subject: 'Avviso @io:OK_READ_THEN_PAID' } }),
          isBase64Encoded: false
        });
      } finally {
        console.log = original;
      }
      expect(capture.req.path).to.equal('/messages');
      expect(capture.lane).to.equal('MOCK');
      const decisionLine = logged.map((l) => JSON.parse(l)).find((o) => o.msg === 'routing decision');
      expect(decisionLine.requestId).to.equal('Root=1-abc');
    });
  });
});
