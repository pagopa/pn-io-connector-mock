"use strict";

const https = require('https');
const http = require('http');
const { URL } = require('url');

const MOCK_BASE_URL = process.env.MOCK_BASE_URL || 'http://localhost:8080/io-connector-mock'; // DR4
const IO_REAL_BASE_URL = process.env.IO_REAL_BASE_URL || 'https://api.io.pagopa.it/api/v1';

const FORWARD_TIMEOUT_MS = parseInt(process.env.FORWARD_TIMEOUT_MS || '10000', 10);

const STRIP_REQUEST_HEADERS = new Set(['host', 'content-length', 'connection']);

function baseUrlFor(lane) {
  return (lane === 'MOCK' ? MOCK_BASE_URL : IO_REAL_BASE_URL).replace(/\/+$/, '');
}

function buildQueryString(query) {
  if (!query) {
    return '';
  }
  const pairs = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  return pairs.length ? `?${pairs.join('&')}` : '';
}

function sanitizeRequestHeaders(headers) {
  const out = {};
  for (const [k, v] of Object.entries(headers || {})) {
    if (!STRIP_REQUEST_HEADERS.has(k.toLowerCase())) {
      out[k] = v;
    }
  }
  const hasTrace = Object.keys(out).some((k) => k.toLowerCase() === 'x-amzn-trace-id');
  if (!hasTrace && process.env._X_AMZN_TRACE_ID) {
    out['X-Amzn-Trace-Id'] = process.env._X_AMZN_TRACE_ID;
  }
  return out;
}

async function forward(req, lane) {
  const targetUrl = baseUrlFor(lane) + req.path + buildQueryString(req.query);
  const parsed = new URL(targetUrl);
  const transport = parsed.protocol === 'https:' ? https : http;

  const options = {
    method: req.method,
    hostname: parsed.hostname,
    port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
    path: parsed.pathname + parsed.search,
    headers: sanitizeRequestHeaders(req.headers),
    timeout: FORWARD_TIMEOUT_MS
  };

  return new Promise((resolve, reject) => {
    const upstream = transport.request(options, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const buffer = Buffer.concat(chunks);
        resolve({
          statusCode: res.statusCode,
          statusDescription: res.statusMessage ? `${res.statusCode} ${res.statusMessage}` : String(res.statusCode),
          headers: res.headers,
          body: buffer.toString('base64'),
          isBase64Encoded: true
        });
      });
    });

    upstream.on('timeout', () => {
      const err = new Error(`Upstream ${lane} lane timed out after ${FORWARD_TIMEOUT_MS}ms`);
      err.timeout = true;
      upstream.destroy(err);
    });
    upstream.on('error', reject);
    if (req.rawBody) {
      upstream.write(req.rawBody);
    }
    upstream.end();
  });
}

module.exports = { forward };
