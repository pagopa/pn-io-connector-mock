/**
 * Script per invocare la Lambda router in locale con un evento di test.
 * Prerequisiti:
 *   1. SSM raggiungibile (es. LocalStack su http://localhost:4566)
 *   2. Il microservizio mock in ascolto su MOCK_BASE_URL (o IO reale raggiungibile)
 *   3. npm install
 *
 * Uso:
 *   node local-dev.js [method] [path]
 *
 * Il path va indicato COMPLETO di prefisso /api/v1, come lo invia pn-io-connector
 * (base url <lambdaUrl>/api/v1); l'eventAdapter lo strippa.
 *
 * Esempi:
 *   node local-dev.js POST /api/v1/messages
 *   node local-dev.js GET  /api/v1/messages/RSSMRA80A01H501T/MOCK-OK_READ_THEN_PAID-1750579200000-a1b2c3
 */

require('dotenv').config({ path: `${__dirname}/localdev.env` });

const { SSMClient, PutParameterCommand } = require('@aws-sdk/client-ssm');
const { handler } = require('./index');

const method = process.argv[2] || 'POST';
const path = process.argv[3] || '/api/v1/messages';

// Whitelist (SSM) = CF instradati verso l'IO reale; il CF usato nelle richieste NON e' in
// whitelist, cosi' POST /profiles dimostra la corsia MOCK (default).
// WHITELISTED_FISCAL_CODE e' allineato all'init.sh dei test (MapIoConnectorMockRealTaxIdsWhitelist).
const MOCK_FISCAL_CODE = 'MRORSS80A01H501K';        // NON whitelistato -> corsia MOCK
const WHITELISTED_FISCAL_CODE = 'RSSMRA80A01H501U'; // whitelistato -> corsia REAL

const bodyByPath = {
  '/api/v1/profiles': { fiscal_code: MOCK_FISCAL_CODE },
  '/api/v1/messages': {
    fiscal_code: MOCK_FISCAL_CODE,
    content: { subject: 'Test @io:OK_READ_THEN_PAID', markdown: 'corpo del messaggio di test' }
  }
};

// Evento Lambda Function URL (payload format 2.0).
const event = {
  version: '2.0',
  routeKey: '$default',
  rawPath: path,
  rawQueryString: '',
  headers: { 'content-type': 'application/json', 'ocp-apim-subscription-key': 'local-dev-key' },
  queryStringParameters: null,
  body: method === 'POST' ? JSON.stringify(bodyByPath[path] || {}) : null,
  isBase64Encoded: false,
  requestContext: { http: { method, path } }
};

async function seedWhitelist() {
  const ssm = new SSMClient({
    region: process.env.AWS_REGION,
    endpoint: process.env.AWS_SSM_ENDPOINT,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || 'test',
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || 'test'
    }
  });
  const name = process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME || 'MapIoConnectorMockRealTaxIdsWhitelist';
  await ssm.send(new PutParameterCommand({
    Name: name,
    Type: 'String',
    Overwrite: true,
    Value: JSON.stringify([WHITELISTED_FISCAL_CODE])
  }));
  console.log(`SSM whitelist seeded: ${name} = ["${WHITELISTED_FISCAL_CODE}"] (CF -> IO reale; gli altri -> mock)`);
}

async function main() {
  await seedWhitelist();
  console.log('\nInvoking Lambda with event:', JSON.stringify(event, null, 2));
  const result = await handler(event);
  console.log('\nResponse:');
  console.log(JSON.stringify(result, null, 2));
}

main().catch((err) => {
  console.error('\nUnhandled error:', err);
  process.exit(1);
});
