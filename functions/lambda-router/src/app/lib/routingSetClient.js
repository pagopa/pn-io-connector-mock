"use strict";

const { GetParameterCommand } = require('@aws-sdk/client-ssm');
const ssmClient = require('./ssmClient');

const PARAMETER_NAME = process.env.PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME || 'MapIoConnectorMockRealTaxIdsWhitelist';

async function load() {
  const result = await ssmClient.send(new GetParameterCommand({ Name: PARAMETER_NAME }));
  const raw = result && result.Parameter ? result.Parameter.Value : null;
  let list;
  try {
    list = raw ? JSON.parse(raw) : [];
  } catch (e) {
    throw new Error(`Routing-set parameter '${PARAMETER_NAME}' is not valid JSON`);
  }
  if (!Array.isArray(list)) {
    throw new Error(`Routing-set parameter '${PARAMETER_NAME}' must be a JSON array of strings`);
  }
  const values = new Set(list);
  console.log(JSON.stringify({ msg: 'routing-set loaded', parameter: PARAMETER_NAME, size: values.size }));
  return values;
}

// Ogni verifica legge direttamente da ParameterStore
async function contains(fiscalCode) {
  if (!fiscalCode) {
    return false;
  }
  const values = await load();
  return values.has(fiscalCode);
}

module.exports = { contains };
