"use strict";

const STRIP_RESPONSE_HEADERS = new Set([
  'transfer-encoding',
  'connection',
  'keep-alive',
  'content-length'
]);

function passthrough({ statusCode, headers, body, isBase64Encoded }) {
  const outHeaders = {};
  for (const [k, v] of Object.entries(headers || {})) {
    if (!STRIP_RESPONSE_HEADERS.has(k.toLowerCase())) {
      outHeaders[k] = v;
    }
  }
  return {
    statusCode,
    headers: outHeaders,
    body: body || '',
    isBase64Encoded: Boolean(isBase64Encoded)
  };
}

function error(statusCode, title, detail) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/problem+json' },
    body: JSON.stringify({ status: statusCode, title, detail }),
    isBase64Encoded: false
  };
}

module.exports = { passthrough, error };
