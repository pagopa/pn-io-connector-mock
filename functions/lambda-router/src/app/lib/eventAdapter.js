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

function decodeBody(event) {
  if (event.body == null) {
    return null;
  }
  return event.isBase64Encoded
    ? Buffer.from(event.body, 'base64').toString('utf8')
    : event.body;
}

function fromAlb(event) {
  const method = event.httpMethod;
  if (!method) {
    throw new Error('Invalid ALB event: missing httpMethod');
  }
  return {
    method,
    path: stripPrefix(event.path || '/'),
    pathParameters: {},
    headers: Object.assign({}, event.headers),
    query: event.queryStringParameters || null,
    rawBody: decodeBody(event),
    isBase64Encoded: Boolean(event.isBase64Encoded)
  };
}

function fromFunctionUrl(event) {
  const http = (event.requestContext && event.requestContext.http) || {};
  const method = http.method;
  if (!method) {
    throw new Error('Invalid event: missing requestContext.http.method');
  }

  const headers = Object.assign({}, event.headers);
  if (Array.isArray(event.cookies) && event.cookies.length && headers.cookie == null && headers.Cookie == null) {
    headers.cookie = event.cookies.join('; ');
  }

  return {
    method,
    path: stripPrefix(event.rawPath || http.path || '/'),
    pathParameters: {},
    headers,
    query: event.queryStringParameters || null,
    rawBody: decodeBody(event),
    isBase64Encoded: Boolean(event.isBase64Encoded)
  };
}

function toRequest(event) {
  if (!event || typeof event !== 'object') {
    throw new Error('Invalid event: not an object');
  }
  const ctx = event.requestContext || {};
  return ctx.elb ? fromAlb(event) : fromFunctionUrl(event);
}

module.exports = { toRequest };
