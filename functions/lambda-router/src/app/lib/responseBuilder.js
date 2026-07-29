"use strict";

const { STATUS_CODES } = require('http');

const STRIP_RESPONSE_HEADERS = new Set([
  'transfer-encoding',
  'connection',
  'keep-alive',
  'content-length'
]);

function statusLine(statusCode) {
  return `${statusCode} ${STATUS_CODES[statusCode] || 'OK'}`;
}

function sanitizeResponseHeaders(headers) {
  const out = {};
  for (const [k, v] of Object.entries(headers || {})) {
    if (STRIP_RESPONSE_HEADERS.has(k.toLowerCase())) {
      continue;
    }
    out[k] = Array.isArray(v) ? v.join(', ') : v;
  }
  return out;
}

function passthrough({ statusCode, headers, body, isBase64Encoded }) {
  return {
    statusCode,
    statusDescription: statusLine(statusCode),
    headers: sanitizeResponseHeaders(headers),
    body: body || '',
    isBase64Encoded: Boolean(isBase64Encoded)
  };
}

function error(statusCode, title, detail) {
  return {
    statusCode,
    statusDescription: statusLine(statusCode),
    headers: { 'Content-Type': 'application/problem+json' },
    body: JSON.stringify({ status: statusCode, title, detail }),
    isBase64Encoded: false
  };
}

module.exports = { passthrough, error };
