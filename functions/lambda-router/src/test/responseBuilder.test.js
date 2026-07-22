"use strict";

const { expect } = require('chai');
const { passthrough, error } = require('../app/lib/responseBuilder');

describe('responseBuilder', () => {

  describe('passthrough', () => {
    it('keeps non hop-by-hop headers and preserves status/body/flag', () => {
      const out = passthrough({
        statusCode: 200,
        headers: { 'Content-Type': 'application/json', 'X-Custom': 'y' },
        body: 'Ym9keQ==',
        isBase64Encoded: true
      });
      expect(out.statusCode).to.equal(200);
      expect(out.headers['Content-Type']).to.equal('application/json');
      expect(out.headers['X-Custom']).to.equal('y');
      expect(out.body).to.equal('Ym9keQ==');
      expect(out.isBase64Encoded).to.equal(true);
    });

    it('strips hop-by-hop response headers (case-insensitive)', () => {
      const out = passthrough({
        statusCode: 200,
        headers: { 'Transfer-Encoding': 'chunked', 'Content-Length': '10', 'Connection': 'keep-alive', 'X-Keep': '1' }
      });
      expect(out.headers).to.not.have.property('Transfer-Encoding');
      expect(out.headers).to.not.have.property('Content-Length');
      expect(out.headers).to.not.have.property('Connection');
      expect(out.headers['X-Keep']).to.equal('1');
    });

    it('defaults headers to {} and body to empty string when missing', () => {
      const out = passthrough({ statusCode: 204 });
      expect(out.headers).to.deep.equal({});
      expect(out.body).to.equal('');
      expect(out.isBase64Encoded).to.equal(false);
    });
  });

  describe('error', () => {
    it('builds a problem+json body with the given status/title/detail', () => {
      const out = error(502, 'Bad Gateway', 'upstream down');
      expect(out.statusCode).to.equal(502);
      expect(out.headers['Content-Type']).to.equal('application/problem+json');
      expect(out.isBase64Encoded).to.equal(false);
      expect(JSON.parse(out.body)).to.deep.equal({ status: 502, title: 'Bad Gateway', detail: 'upstream down' });
    });
  });
});
