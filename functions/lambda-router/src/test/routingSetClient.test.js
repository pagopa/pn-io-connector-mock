"use strict";

const { expect } = require('chai');
const { mockClient } = require('aws-sdk-client-mock');
const { SSMClient, GetParameterCommand } = require('@aws-sdk/client-ssm');

process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME = 'MapIoConnectorMockRealTaxIdsWhitelist';

const routingSetClient = require('../app/lib/routingSetClient');

const ssmMock = mockClient(SSMClient);

function paramValue(list) {
  return { Parameter: { Name: 'MapIoConnectorMockRealTaxIdsWhitelist', Value: JSON.stringify(list) } };
}

describe('routingSetClient', () => {
  beforeEach(() => {
    ssmMock.reset();
  });

  it('returns true when the fiscal code is in the whitelist', async () => {
    ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T', 'VRDLGI85M01H501Z']));
    expect(await routingSetClient.contains('RSSMRA80A01H501T')).to.equal(true);
  });

  it('returns false when the fiscal code is not in the whitelist', async () => {
    ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T']));
    expect(await routingSetClient.contains('AAAAAA00A00A000A')).to.equal(false);
  });

  it('returns false for an empty fiscal code without querying SSM', async () => {
    ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T']));
    expect(await routingSetClient.contains('')).to.equal(false);
    expect(ssmMock.commandCalls(GetParameterCommand)).to.have.length(0);
  });

  it('reads SSM on every check (no cache)', async () => {
    ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T']));
    await routingSetClient.contains('RSSMRA80A01H501T');
    await routingSetClient.contains('RSSMRA80A01H501T');
    expect(ssmMock.commandCalls(GetParameterCommand)).to.have.length(2);
  });

  it('picks up a whitelist change immediately', async () => {
    ssmMock.on(GetParameterCommand).resolves(paramValue([]));
    expect(await routingSetClient.contains('RSSMRA80A01H501T')).to.equal(false);
    ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T']));
    expect(await routingSetClient.contains('RSSMRA80A01H501T')).to.equal(true);
  });

  it('propagates SSM errors to the caller', async () => {
    ssmMock.on(GetParameterCommand).rejects(new Error('SSM unavailable'));
    try {
      await routingSetClient.contains('RSSMRA80A01H501T');
      expect.fail('should have thrown');
    } catch (err) {
      expect(err.message).to.include('SSM unavailable');
    }
  });

  it('throws when the parameter value is not a JSON array', async () => {
    ssmMock.on(GetParameterCommand).resolves({ Parameter: { Value: '{"not":"an-array"}' } });
    try {
      await routingSetClient.contains('RSSMRA80A01H501T');
      expect.fail('should have thrown');
    } catch (err) {
      expect(err.message).to.include('array');
    }
  });

  it('throws when the parameter value is not valid JSON', async () => {
    ssmMock.on(GetParameterCommand).resolves({ Parameter: { Value: 'not-json{' } });
    try {
      await routingSetClient.contains('RSSMRA80A01H501T');
      expect.fail('should have thrown');
    } catch (err) {
      expect(err.message).to.include('not valid JSON');
    }
  });

  it('treats a missing parameter value as an empty routing-set', async () => {
    ssmMock.on(GetParameterCommand).resolves({ Parameter: {} });
    expect(await routingSetClient.contains('RSSMRA80A01H501T')).to.equal(false);
  });

  it('treats a missing Parameter object as an empty routing-set', async () => {
    ssmMock.on(GetParameterCommand).resolves({});
    expect(await routingSetClient.contains('RSSMRA80A01H501T')).to.equal(false);
  });

  it('uses the default parameter name when the env var is unset', async () => {
    const savedName = process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME;
    delete process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME;
    delete require.cache[require.resolve('../app/lib/routingSetClient')];
    try {
      const fresh = require('../app/lib/routingSetClient');
      ssmMock.on(GetParameterCommand).resolves(paramValue(['RSSMRA80A01H501T']));
      expect(await fresh.contains('RSSMRA80A01H501T')).to.equal(true);
      const call = ssmMock.commandCalls(GetParameterCommand).pop();
      expect(call.args[0].input.Name).to.equal('MapIoConnectorMockRealTaxIdsWhitelist');
    } finally {
      if (savedName !== undefined) process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME = savedName;
      delete require.cache[require.resolve('../app/lib/routingSetClient')];
    }
  });
});
