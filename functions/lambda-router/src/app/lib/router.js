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

    if (await routingSetClient.contains(body.fiscal_code)) {
      return { endpoint: 'profiles', lane: 'REAL', matchedCriterion: 'fiscal_code in MapIoConnectorMockRealTaxIdsWhitelist' };
    }
    return { endpoint: 'profiles', lane: 'MOCK', matchedCriterion: 'fiscal_code NOT in MapIoConnectorMockRealTaxIdsWhitelist' };
  }

  // POST /messages  (submitMessageforUserWithFiscalCodeInBody)
  if (req.method === 'POST' && seg.length === 1 && seg[0] === 'messages') {
    const body = parseBody(req);
    const subject = (body && body.content && body.content.subject) || '';

    // Prima il marker: se presente, e' un messaggio di test -> MOCK.
    if (SEQUENCE_MARKER.test(subject)) {
      return { endpoint: 'messages', lane: 'MOCK', matchedCriterion: 'subject match @io:<sequenceName>' };
    }

    // Nessun marker: decide la whitelist, come /profiles.
    // un CF di test (non whitelistato) senza marker non deve mai raggiungere l'IO reale -> 400 (fail-closed).
    if (await routingSetClient.contains(body.fiscal_code)) {
      return { endpoint: 'messages', lane: 'REAL', matchedCriterion: 'no marker; fiscal_code in MapIoConnectorMockRealTaxIdsWhitelist' };
    }
    throw badRequest('Test fiscal_code (not whitelisted) without @io:<sequenceName> marker in content.subject');
  }

  // GET /messages/{fiscal_code}/{id}  (getMessage)
  if (req.method === 'GET' && seg.length === 3 && seg[0] === 'messages') {
    const id = seg[2];
    const isMock = id.startsWith(MOCK_ID_PREFIX);

    return {
      endpoint: 'getMessage',
      lane: isMock ? 'MOCK' : 'REAL',
      ioMessageId: id,
      matchedCriterion: isMock ? 'id prefix MOCK-' : 'id without MOCK- prefix'
    };
  }

  // io-connector chiama solo i 3 endpoint sopra (D5): ogni altro path e' inatteso.
  throw notFound(`Unhandled route: ${req.method} ${req.path}`);
}

module.exports = { route };
