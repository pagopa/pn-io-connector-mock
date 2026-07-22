"use strict";

const { expect } = require('chai');
const proxyquire = require('proxyquire').noCallThru();

process.env.MOCK_BASE_URL = 'http://mock.internal:8080/io-connector-mock';
process.env.IO_REAL_BASE_URL = 'https://api.io.pagopa.it/api/v1';

function makeHttpMock({ statusCode = 200, headers = {}, body = '', onError = null, onTimeout = false, capture = {} }) {
  return {
    request: (options, callback) => {
      capture.options = options;
      const handlers = {};
      const req = {
        _body: '',
        write(chunk) { this._body += chunk; capture.body = this._body; },
        end() {
          if (onError) {
            return; // error emesso tramite req.on('error')
          }
          if (onTimeout) {
            if (handlers.timeout) setImmediate(() => handlers.timeout());
            return; // nessuna risposta: simula la corsia che non risponde
          }
          const res = {
            statusCode,
            headers,
            on: (event, handler) => {
              if (event === 'data' && body) handler(Buffer.from(body));
              if (event === 'end') handler();
              return res;
            }
          };
          callback(res);
        },
        destroy(err) {
          if (handlers.error) setImmediate(() => handlers.error(err));
        },
        on: (event, handler) => {
          handlers[event] = handler;
          if (event === 'error' && onError) {
            setImmediate(() => handler(onError));
          }
          return req;
        }
      };
      return req;
    }
  };
}

function makeForwarder(httpMock, httpsMock) {
  return proxyquire('../app/lib/forwarder', {
    http: httpMock || makeHttpMock({}),
    https: httpsMock || makeHttpMock({})
  });
}

describe('forwarder', () => {

  it('builds the MOCK target URL with re-basepath and preserved path', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ statusCode: 201, body: 'OK', capture }));
    await forwarder.forward(
      { method: 'POST', path: '/messages', headers: {}, query: null, rawBody: '{}' },
      'MOCK'
    );
    expect(capture.options.hostname).to.equal('mock.internal');
    expect(capture.options.port).to.equal('8080');
    expect(capture.options.path).to.equal('/io-connector-mock/messages');
    expect(capture.options.method).to.equal('POST');
  });

  it('appends the query string to the target path', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'GET', path: '/messages', headers: {}, query: { a: '1', b: 'x y' }, rawBody: null },
      'MOCK'
    );
    expect(capture.options.path).to.equal('/io-connector-mock/messages?a=1&b=x%20y');
  });

  it('preserves status and body (base64) from the upstream response', async () => {
    const forwarder = makeForwarder(makeHttpMock({ statusCode: 200, body: 'hello-bytes' }));
    const resp = await forwarder.forward(
      { method: 'GET', path: '/messages/cf/MOCK-x', headers: {}, query: null, rawBody: null },
      'MOCK'
    );
    expect(resp.statusCode).to.equal(200);
    expect(resp.isBase64Encoded).to.equal(true);
    expect(Buffer.from(resp.body, 'base64').toString()).to.equal('hello-bytes');
  });

  it('strips hop-by-hop request headers (Host, Content-Length)', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'POST', path: '/messages', headers: { Host: 'x', 'Content-Length': '5', 'X-Keep': '1' }, query: null, rawBody: '{}' },
      'MOCK'
    );
    expect(capture.options.headers).to.not.have.property('Host');
    expect(capture.options.headers).to.not.have.property('Content-Length');
    expect(capture.options.headers['X-Keep']).to.equal('1');
  });

  it('forwards the request body to the upstream', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'POST', path: '/messages', headers: {}, query: null, rawBody: '{"a":1}' },
      'MOCK'
    );
    expect(capture.body).to.equal('{"a":1}');
  });

  it('sets the outbound request timeout (default 10s)', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null },
      'MOCK'
    );
    expect(capture.options.timeout).to.equal(10000);
  });

  it('rejects with a timeout-marked error when the upstream times out', async () => {
    const forwarder = makeForwarder(makeHttpMock({ onTimeout: true }));
    try {
      await forwarder.forward({ method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null }, 'MOCK');
      expect.fail('should have rejected');
    } catch (err) {
      expect(err.timeout).to.equal(true);
      expect(err.message).to.include('timed out');
    }
  });

  it('rejects when the upstream transport errors (lane unreachable)', async () => {
    const forwarder = makeForwarder(makeHttpMock({ onError: new Error('ECONNREFUSED') }));
    try {
      await forwarder.forward({ method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null }, 'MOCK');
      expect.fail('should have rejected');
    } catch (err) {
      expect(err.message).to.include('ECONNREFUSED');
    }
  });

  it('uses https transport and port 443 for the REAL lane', async () => {
    const capture = {};
    const forwarder = makeForwarder(
      makeHttpMock({}),                       // http (non usato)
      makeHttpMock({ statusCode: 200, capture }) // https
    );
    await forwarder.forward(
      { method: 'POST', path: '/messages', headers: {}, query: null, rawBody: '{}' },
      'REAL'
    );
    expect(capture.options.hostname).to.equal('api.io.pagopa.it');
    expect(capture.options.port).to.equal(443);
    expect(capture.options.path).to.equal('/api/v1/messages');
  });

  it('skips null/undefined query values and yields no "?" when empty', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'GET', path: '/messages', headers: {}, query: { a: '1', b: null, c: undefined }, rawBody: null },
      'MOCK'
    );
    expect(capture.options.path).to.equal('/io-connector-mock/messages?a=1');

    const capture2 = {};
    const forwarder2 = makeForwarder(makeHttpMock({ capture: capture2 }));
    await forwarder2.forward(
      { method: 'GET', path: '/messages', headers: {}, query: {}, rawBody: null },
      'MOCK'
    );
    expect(capture2.options.path).to.equal('/io-connector-mock/messages');
  });

  it('defaults to port 80 for an http base URL without an explicit port', async () => {
    const savedMock = process.env.MOCK_BASE_URL;
    process.env.MOCK_BASE_URL = 'http://mock.internal/io-connector-mock';
    try {
      const capture = {};
      const forwarder = makeForwarder(makeHttpMock({ capture }));
      await forwarder.forward(
        { method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null },
        'MOCK'
      );
      expect(capture.options.port).to.equal(80);
    } finally {
      process.env.MOCK_BASE_URL = savedMock;
    }
  });

  it('tolerates undefined request headers', async () => {
    const capture = {};
    const forwarder = makeForwarder(makeHttpMock({ capture }));
    await forwarder.forward(
      { method: 'GET', path: '/messages', query: null, rawBody: null },
      'MOCK'
    );
    expect(capture.options.headers).to.deep.equal({});
  });

  it('propagates the Lambda X-Amzn-Trace-Id to the upstream when the caller did not', async () => {
    const saved = process.env._X_AMZN_TRACE_ID;
    process.env._X_AMZN_TRACE_ID = 'Root=1-abc;Parent=xyz';
    try {
      const capture = {};
      const forwarder = makeForwarder(makeHttpMock({ capture }));
      await forwarder.forward(
        { method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null },
        'MOCK'
      );
      expect(capture.options.headers['X-Amzn-Trace-Id']).to.equal('Root=1-abc;Parent=xyz');
    } finally {
      if (saved === undefined) delete process.env._X_AMZN_TRACE_ID; else process.env._X_AMZN_TRACE_ID = saved;
    }
  });

  it('does not override a trace id already provided by the caller', async () => {
    const saved = process.env._X_AMZN_TRACE_ID;
    process.env._X_AMZN_TRACE_ID = 'Root=1-lambda';
    try {
      const capture = {};
      const forwarder = makeForwarder(makeHttpMock({ capture }));
      await forwarder.forward(
        { method: 'GET', path: '/messages', headers: { 'x-amzn-trace-id': 'Root=1-caller' }, query: null, rawBody: null },
        'MOCK'
      );
      expect(capture.options.headers['x-amzn-trace-id']).to.equal('Root=1-caller');
      expect(capture.options.headers).to.not.have.property('X-Amzn-Trace-Id');
    } finally {
      if (saved === undefined) delete process.env._X_AMZN_TRACE_ID; else process.env._X_AMZN_TRACE_ID = saved;
    }
  });

  it('falls back to default base URLs when env vars are not set', async () => {
    const savedMock = process.env.MOCK_BASE_URL;
    const savedReal = process.env.IO_REAL_BASE_URL;
    delete process.env.MOCK_BASE_URL;
    delete process.env.IO_REAL_BASE_URL;
    try {
      const capture = {};
      const forwarder = makeForwarder(makeHttpMock({ capture }));
      await forwarder.forward(
        { method: 'GET', path: '/messages', headers: {}, query: null, rawBody: null },
        'MOCK'
      );
      expect(capture.options.hostname).to.equal('localhost');
      expect(capture.options.port).to.equal('8080');
      expect(capture.options.path).to.equal('/io-connector-mock/messages');
    } finally {
      process.env.MOCK_BASE_URL = savedMock;
      process.env.IO_REAL_BASE_URL = savedReal;
    }
  });
});
