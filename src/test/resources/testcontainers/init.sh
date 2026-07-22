#!/bin/bash

set -e

## CONFIGURATION ##
VERBOSE=true
AWS_PROFILE="default"
AWS_REGION="us-east-1"
LOCALSTACK_ENDPOINT="http://localhost:4566"

## DEFINITIONS ##
MOCK_SEQUENCE_PARAMETER_NAME="MapIoConnectorMockSequence"
MOCK_SEQUENCE_PARAMETER_VALUE='[
  { "sequenceName": "OK_READ_THEN_PAID",
    "steps": [
      { "afterSeconds": 0,  "status": "ACCEPTED" },
      { "afterSeconds": 30, "status": "PROCESSED", "readStatus": "UNREAD", "paymentStatus": "NOT_PAID" },
      { "afterSeconds": 60, "status": "PROCESSED", "readStatus": "READ", "paymentStatus": "NOT_PAID" },
      { "afterSeconds": 90, "status": "PROCESSED", "readStatus": "READ", "paymentStatus": "PAID" }
    ] },
  { "sequenceName": "OK_READ",
    "steps": [
      { "afterSeconds": 0,  "status": "ACCEPTED" },
      { "afterSeconds": 0,  "status": "PROCESSED" },
      { "afterSeconds": 60, "readStatus": "READ" }
    ] },
  { "sequenceName": "FAILED",
    "steps": [
      { "afterSeconds": 0,  "status": "ACCEPTED" },
      { "afterSeconds": 20, "status": "FAILED" }
    ] }
]'

SENDER_NOT_ALLOWED_PARAMETER_NAME="MapIoConnectorMockSenderNotAllowed"
SENDER_NOT_ALLOWED_PARAMETER_VALUE='[]'

REAL_TAX_IDS_WHITELIST_PARAMETER_NAME="MapIoConnectorMockRealTaxIdsWhitelist"
REAL_TAX_IDS_WHITELIST_PARAMETER_VALUE='[
  "RSSMRA80A01H501U",
  "VRDLGI85M20F205X"
]'

## LOGGING FUNCTIONS ##
log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

silent() {
  if [ "$VERBOSE" = false ]; then
    "$@" > /dev/null 2>&1
  else
    "$@"
  fi
}

## FUNCTIONS ##
create_ssm_parameter() {
  local parameter_name=$1
  local parameter_value=$2

  log "Creating SSM parameter: $parameter_name"
  if ! silent aws ssm get-parameter \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --endpoint-url "$LOCALSTACK_ENDPOINT" \
    --name "$parameter_name" ; then
    if ! aws ssm put-parameter \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --endpoint-url "$LOCALSTACK_ENDPOINT" \
      --name "$parameter_name" \
      --type String \
      --value "$parameter_value" ; then
      log "Failed to create SSM parameter: $parameter_name"
      return 1
    else
      log "SSM parameter created: $parameter_name"
    fi
  else
    log "SSM parameter already exists: $parameter_name"
  fi
}

initialize_ssm() {
  log "Initializing SSM Parameter Store"
  local return_code=0

  create_ssm_parameter "$MOCK_SEQUENCE_PARAMETER_NAME" "$MOCK_SEQUENCE_PARAMETER_VALUE" || return_code=1
  create_ssm_parameter "$SENDER_NOT_ALLOWED_PARAMETER_NAME" "$SENDER_NOT_ALLOWED_PARAMETER_VALUE" || return_code=1
  create_ssm_parameter "$REAL_TAX_IDS_WHITELIST_PARAMETER_NAME" "$REAL_TAX_IDS_WHITELIST_PARAMETER_VALUE" || return_code=1

  return $return_code
}

main() {
  initialize_ssm || { log "Failed to initialize SSM Parameter Store"; exit 1; }
  log "Initialization completed successfully"
}

main
echo "Initialization terminated"
