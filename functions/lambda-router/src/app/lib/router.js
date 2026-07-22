"use strict";

const routingSetClient = require('./routingSetClient');

const SEQUENCE_MARKER = /@io:([A-Za-z0-9_-]+)/;
const MOCK_ID_PREFIX = 'MOCK-';

function badRequest(message) {
  const err = new Error(message);
  err.statusCode = 400;
  return err;
}

function notFound(message) {
  const err = new Error(message);
  err.statusCode = 404;
  return err;
}

function segments(path) {
  return (path || '').split('/').filter(Boolean);
}

function parseBody(req) {
  if (req.parsedBody !== undefined) {
    return req.parsedBody;
  }
  try {
    req.parsedBody = req.rawBody ? JSON.parse(req.rawBody) : {};
  } catch (e) {
    throw badRequest('Malformed JSON request body');
  }
  return req.parsedBody;
}

/**
 * Decide la corsia (MOCK / REAL) in base al metodo e al path relativo.
 * Lancia un errore con `statusCode` per i casi 400 (body non parsabile) e 404 (rotta ignota).
 */
async function route(req) {
  const seg = segments(req.path);

  // POST /profiles  (getProfileByPOST)
  if (req.method === 'POST' && seg.length === 1 && seg[0] === 'profiles') {
    const body = parseBody(req);

    const lane = (await routingSetClient.contains(body.fiscal_code)) ? 'REAL' : 'MOCK';
    return { endpoint: 'profiles', lane, matchedCriterion: 'fiscal_code NOT in MapIoConnectorMockRealTaxIdsWhitelist' };
  }

  // POST /messages  (submitMessageforUserWithFiscalCodeInBody)
  if (req.method === 'POST' && seg.length === 1 && seg[0] === 'messages') {
    const body = parseBody(req);
    const subject = (body && body.content && body.content.subject) || '';
    const lane = SEQUENCE_MARKER.test(subject) ? 'MOCK' : 'REAL';
    return { endpoint: 'messages', lane, matchedCriterion: 'subject match @io:<sequenceName>' };
  }

  // GET /messages/{fiscal_code}/{id}  (getMessage)
  if (req.method === 'GET' && seg.length === 3 && seg[0] === 'messages') {
    const id = seg[2];
    const lane = id.startsWith(MOCK_ID_PREFIX) ? 'MOCK' : 'REAL';

    return { endpoint: 'getMessage', lane, ioMessageId: id, matchedCriterion: 'id prefix MOCK-' };
  }

  // io-connector chiama solo i 3 endpoint sopra (D5): ogni altro path e' inatteso.
  throw notFound(`Unhandled route: ${req.method} ${req.path}`);
}

module.exports = { route };
