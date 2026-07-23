"use strict";

const SENSITIVE_HEADERS = new Set([
  'ocp-apim-subscription-key',
  'authorization',
  'cookie',
  'set-cookie',
  'x-api-key',
  'x-functions-key'
]);

const REDACTED = '***';

const FISCAL_CODE_REGEX = /\b[A-Z]{6}\d{2}[A-Z]\d{2}[A-Z]\d{3}[A-Z]\b/gi;

function maskFiscalCode(cf) {
  return cf.slice(0, 6) + '*'.repeat(cf.length - 8) + cf.slice(-2);
}

/**
 * Sostituisce ogni codice fiscale presente nella stringa con la forma mascherata.
 */
function maskFiscalCodes(str) {
  return str == null ? str : String(str).replace(FISCAL_CODE_REGEX, maskFiscalCode);
}

function redactHeaders(headers) {
  const out = {};
  for (const [k, v] of Object.entries(headers || {})) {
    out[k] = SENSITIVE_HEADERS.has(k.toLowerCase()) ? REDACTED : v;
  }
  return out;
}

function requestSummary(event) {
  const ctx = (event && event.requestContext) || {};
  const http = ctx.http || {};
  const headers = (event && event.headers) || {};
  return {
    msg: 'request received',
    method: http.method || (event && event.httpMethod),
    rawPath: maskFiscalCodes((event && (event.rawPath || event.path)) || undefined),
    requestId: ctx.requestId || headers['x-amzn-trace-id'],
    headers: redactHeaders(headers)
  };
}

module.exports = { redactHeaders, requestSummary, maskFiscalCodes, SENSITIVE_HEADERS };
