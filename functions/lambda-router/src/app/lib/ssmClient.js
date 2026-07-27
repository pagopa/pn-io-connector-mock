"use strict";

const { SSMClient } = require('@aws-sdk/client-ssm');

// Init condizionale (stesso pattern di dynamoDbClient in downloadAttachments):
// in test/locale punta a LocalStack via AWS_SSM_ENDPOINT, in cloud usa il ruolo IAM.
const ssmClient = process.env.AWS_SSM_ENDPOINT ? new SSMClient({
  region: process.env.AWS_REGION,
  endpoint: process.env.AWS_SSM_ENDPOINT,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'test',
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'test'
  }
}) : new SSMClient({});

module.exports = ssmClient;
