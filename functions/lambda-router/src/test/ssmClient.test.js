"use strict";

const { expect } = require('chai');
const { SSMClient } = require('@aws-sdk/client-ssm');

const MODULE_PATH = '../app/lib/ssmClient';

function freshRequire() {
  delete require.cache[require.resolve(MODULE_PATH)];
  return require(MODULE_PATH);
}

describe('ssmClient', () => {
  const original = process.env.AWS_SSM_ENDPOINT;

  afterEach(() => {
    if (original === undefined) {
      delete process.env.AWS_SSM_ENDPOINT;
    } else {
      process.env.AWS_SSM_ENDPOINT = original;
    }
    freshRequire();
  });

  it('builds a client with explicit endpoint when AWS_SSM_ENDPOINT is set (LocalStack)', () => {
    process.env.AWS_SSM_ENDPOINT = 'http://localhost:4566';
    process.env.AWS_REGION = 'us-east-1';
    const client = freshRequire();
    expect(client).to.be.instanceOf(SSMClient);
  });

  it('builds a default client when AWS_SSM_ENDPOINT is not set (cloud / IAM role)', () => {
    delete process.env.AWS_SSM_ENDPOINT;
    const client = freshRequire();
    expect(client).to.be.instanceOf(SSMClient);
  });
});
