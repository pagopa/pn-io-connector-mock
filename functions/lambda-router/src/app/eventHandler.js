
"use strict";

const eventAdapter = require('./lib/eventAdapter');
const router = require('./lib/router');
const forwarder = require('./lib/forwarder');
const responseBuilder = require('./lib/responseBuilder');
const logSanitizer = require('./lib/logSanitizer');

exports.handleEvent = async function (event) {

  const requestId = event && event.requestContext && event.requestContext.requestId;
  console.log(JSON.stringify(logSanitizer.requestSummary(event)));

  let req;
  try {
    req = eventAdapter.toRequest(event);
  } catch (err) {
    console.error('Event adapter error:', err);
    return responseBuilder.error(400, 'Bad Request', err.message);
  }

  let decision;
  try {
    decision = await router.route(req);
  } catch (err) {
    if (err.statusCode === 400) {
      return responseBuilder.error(400, 'Bad Request', err.message);
    }
    if (err.statusCode === 404) {
      return responseBuilder.error(404, 'Not Found', err.message);
    }
    console.error('Routing error:', err);
    return responseBuilder.error(500, 'Internal Server Error', 'Error resolving routing set');
  }

  console.log(JSON.stringify({
    msg: 'routing decision',
    requestId,
    endpoint: decision.endpoint,
    lane: decision.lane,
    ioMessageId: decision.ioMessageId,
    criterion: decision.matchedCriterion
  }));

  try {
    const resp = await forwarder.forward(req, decision.lane);
    return responseBuilder.passthrough(resp);
  } catch (err) {
    console.error(`Forward error (lane=${decision.lane}):`, err);
    if (err && err.timeout) {
      return responseBuilder.error(504, 'Gateway Timeout', `Timeout forwarding request to ${decision.lane} lane`);
    }
    return responseBuilder.error(502, 'Bad Gateway', `Error forwarding request to ${decision.lane} lane`);
  }
};
