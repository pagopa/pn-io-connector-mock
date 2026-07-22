"use strict";

const { expect } = require('chai');
const { redactHeaders, requestSummary, maskFiscalCodes } = require('../app/lib/logSanitizer');

describe('logSanitizer', () => {

  describe('redactHeaders', () => {
    it('masks sensitive headers case-insensitively and preserves the others', () => {
      const out = redactHeaders({
        'Ocp-Apim-Subscription-Key': 'super-secret',
        'AUTHORIZATION': 'Bearer xyz',
        'Cookie': 'a=1',
        'X-Api-Key': 'k',
        'Content-Type': 'application/json',
        'X-Keep': '1'
      });
      expect(out['Ocp-Apim-Subscription-Key']).to.equal('***');
      expect(out['AUTHORIZATION']).to.equal('***');
      expect(out['Cookie']).to.equal('***');
      expect(out['X-Api-Key']).to.equal('***');
      expect(out['Content-Type']).to.equal('application/json');
      expect(out['X-Keep']).to.equal('1');
    });

    it('returns an empty object for null/undefined headers', () => {
      expect(redactHeaders(null)).to.deep.equal({});
      expect(redactHeaders(undefined)).to.deep.equal({});
    });
  });

  describe('maskFiscalCodes', () => {
    it('masks a fiscal code keeping first 6 and last 2 chars', () => {
      expect(maskFiscalCodes('RSSMRA80A01H501U')).to.equal('RSSMRA********1U');
    });

    it('masks the fiscal code inside a getMessage path, leaving the id intact', () => {
      expect(maskFiscalCodes('/api/v1/messages/RSSMRA80A01H501U/MOCK-OK_READ-1750579200000-a1b2c3'))
        .to.equal('/api/v1/messages/RSSMRA********1U/MOCK-OK_READ-1750579200000-a1b2c3');
    });

    it('is case-insensitive on the fiscal code pattern', () => {
      expect(maskFiscalCodes('rssmra80a01h501u')).to.equal('rssmra********1u');
    });

    it('leaves strings without a fiscal code unchanged and tolerates null', () => {
      expect(maskFiscalCodes('/api/v1/messages')).to.equal('/api/v1/messages');
      expect(maskFiscalCodes(null)).to.equal(null);
    });
  });

  describe('requestSummary', () => {
    it('summarizes method, rawPath, requestId and redacts headers, without the body', () => {
      const summary = requestSummary({
        rawPath: '/api/v1/messages',
        headers: { 'ocp-apim-subscription-key': 'secret', 'content-type': 'application/json' },
        body: '{"fiscal_code":"RSSMRA80A01H501T"}',
        requestContext: { requestId: 'req-123', http: { method: 'POST' } }
      });
      expect(summary.msg).to.equal('request received');
      expect(summary.method).to.equal('POST');
      expect(summary.rawPath).to.equal('/api/v1/messages');
      expect(summary.requestId).to.equal('req-123');
      expect(summary.headers['ocp-apim-subscription-key']).to.equal('***');
      expect(summary.headers['content-type']).to.equal('application/json');
      expect(summary).to.not.have.property('body');
    });

    it('masks the fiscal code in the getMessage rawPath', () => {
      const summary = requestSummary({
        rawPath: '/api/v1/messages/RSSMRA80A01H501U/MOCK-OK_READ-1750579200000-a1b2c3',
        headers: {},
        requestContext: { requestId: 'req-9', http: { method: 'GET' } }
      });
      expect(summary.rawPath).to.equal('/api/v1/messages/RSSMRA********1U/MOCK-OK_READ-1750579200000-a1b2c3');
    });

    it('tolerates a missing requestContext / http', () => {
      const summary = requestSummary({ rawPath: '/api/v1/profiles' });
      expect(summary.method).to.equal(undefined);
      expect(summary.rawPath).to.equal('/api/v1/profiles');
      expect(summary.requestId).to.equal(undefined);
      expect(summary.headers).to.deep.equal({});
    });
  });
});
