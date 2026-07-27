"use strict";

const { GetParameterCommand } = require('@aws-sdk/client-ssm');
const ssmClient = require('./ssmClient');

const PARAMETER_NAME = process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME || 'MapIoConnectorMockRealTaxIdsWhitelist';
const CACHE_TTL_MS = parseInt(process.env.ROUTING_CACHE_TTL_MS || '300000', 10); // DR3: default PT5M

// Cache in-memory nel container warm (nessuna persistenza). Invalidazione per sola scadenza TTL.
let cache = { values: null, loadedAtEpochMs: 0 };

async function load() {
  const result = await ssmClient.send(new GetParameterCommand({ Name: PARAMETER_NAME }));
  const raw = result && result.Parameter ? result.Parameter.Value : null;
  let list;
  try {
    list = raw ? JSON.parse(raw) : [];
  } catch (e) {
    throw new Error(`Routing-set parameter '${PARAMETER_NAME}' is not valid JSON: ${raw}`);
  }
  if (!Array.isArray(list)) {
    throw new Error(`Routing-set parameter '${PARAMETER_NAME}' must be a JSON array of strings`);
  }
  cache = { values: new Set(list), loadedAtEpochMs: Date.now() };
  console.log(JSON.stringify({ msg: 'routing-set loaded', parameter: PARAMETER_NAME, size: cache.values.size }));
  return cache.values;
}

async function contains(fiscalCode) {
  if (!fiscalCode) {
    return false;
  }
  const expired = (Date.now() - cache.loadedAtEpochMs) >= CACHE_TTL_MS;
  const values = (!cache.values || expired) ? await load() : cache.values;
  return values.has(fiscalCode);
}

// Solo per i test: azzera la cache in-memory.
function _resetCache() {
  cache = { values: null, loadedAtEpochMs: 0 };
}

module.exports = { contains, _resetCache };
