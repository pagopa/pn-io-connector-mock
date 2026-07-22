"use strict";

const PATH_PREFIX = (process.env.PATH_PREFIX !== undefined ? process.env.PATH_PREFIX : '/api/v1').replace(/\/+$/, '');

function stripPrefix(path) {
  if (!PATH_PREFIX) {
    return path;
  }
  if (path === PATH_PREFIX) {
    return '/';
  }
  if (path.startsWith(PATH_PREFIX + '/')) {
    return path.slice(PATH_PREFIX.length);
  }
  return path;
}

function toRequest(event) {
  if (!event || typeof event !== 'object') {
    throw new Error('Invalid event: not an object');
  }

  const http = (event.requestContext && event.requestContext.http) || {};
  const method = http.method;
  if (!method) {
    throw new Error('Invalid event: missing requestContext.http.method');
  }

  const path = stripPrefix(event.rawPath || http.path || '/');

  const query = event.queryStringParameters || null;

  const headers = Object.assign({}, event.headers);
  if (Array.isArray(event.cookies) && event.cookies.length && headers.cookie == null && headers.Cookie == null) {
    headers.cookie = event.cookies.join('; ');
  }

  let rawBody = null;
  if (event.body != null) {
    rawBody = event.isBase64Encoded
      ? Buffer.from(event.body, 'base64').toString('utf8')
      : event.body;
  }

  return {
    method,
    path,
    pathParameters: {},
    headers,
    query,
    rawBody,
    isBase64Encoded: Boolean(event.isBase64Encoded)
  };
}

module.exports = { toRequest };
