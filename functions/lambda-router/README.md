# lambda-router

Lambda **router a due corsie** per i test QA di `pn-io-connector`. In ambiente di test diventa
l'unico entry point verso cui `io-connector` punta la propria `io-base-url` (WI7): per ogni
richiesta decide se instradare verso il **microservizio mock** (`pn-io-connector-mock`) o verso
il **backend IO reale**, in pass-through trasparente (cambia solo il base path).

Vedi l'analisi completa in `../../docs/analisi/` (WI6) e le convenzioni di stile in
`../../docs/analisi/WI6-riferimento-downloadAttachments.md`.

## Regole di routing

| Endpoint | Corsia MOCK se... | Altrimenti |
|---|---|---|
| `POST /profiles` | `fiscal_code` **∉** `MapIoConnectorMockRealTaxIdsWhitelist` (CF non in whitelist) | IO reale (CF in whitelist) |
| `POST /messages` | `content.subject` matcha `@io:<sequenceName>` | IO reale |
| `GET /messages/{fiscal_code}/{id}` | `id` inizia per `MOCK-` | IO reale |

## Struttura

```
index.js                     entry point (exports.handler)
src/app/eventHandler.js       orchestrazione: adapter -> router -> forwarder
src/app/lib/eventAdapter.js   evento ALB target / Function URL v2 -> Request canonica
src/app/lib/router.js         regole di routing -> RouteDecision           (doppia corsia)
src/app/lib/routingSetClient.js  whitelist CF (->reale) da SSM (letta a ogni richiesta)
src/app/lib/ssmClient.js      client SSM (init condizionale LocalStack)
src/app/lib/forwarder.js      pass-through http/https + re-basepath + trace id (DR4)
src/app/lib/responseBuilder.js  risposte API GW proxy
```

## Comandi

```bash
npm install
npm test          # mocha + nyc
npm run coverage  # report lcov
npm run build     # npm prune --production + zip function.zip
npm run local-dev # invocazione locale (richiede SSM/LocalStack + mock in ascolto)
```

## Deploy (CloudFormation)

Le risorse AWS della Lambda (Function, ruolo IAM, SG, log group) sono definite **dentro**
`scripts/aws/cfn/microservice.yml`, insieme al microservizio ECS. Vengono deployate dalla
pipeline standard, riusano i parametri VPC passati al microservizio e la managed policy SSM
già definita. Non esiste un template standalone.

**Esposizione**: la lambda è un **target dell'ALB interno** (`TargetGroup` di tipo `lambda` +
`ListenerRule` su `/api/v1/*`, invocazione da `elasticloadbalancing.amazonaws.com`), stesso
pattern di `pn-lambda-reverse-proxy` in `pn-infra`. **Nessuna Function URL, nessuna auth lato
chiamante**: io-connector la chiama in-VPC su `http://<ALB>:8080/api/v1` (output
`RouterInvokeBaseUrl`). `MOCK_BASE_URL` è derivato dallo stesso ALB
(`http://<ALB>:8080/io-connector-mock`).

Il codice segue la convenzione pipeline PN: `functions/lambda-router/` viene impacchettato in
`<base>/functions_zip/lambda-router.zip` e referenziato via i parametri `MicroserviceBucketName`
/ `MicroserviceBucketBaseKey` (valorizzati dalla pipeline). Il `microservice-dev-cfg.json` non
richiede parametri della Lambda: usano tutti i default del template.

## Configurazione (env var)

| Env var | Default | Nota |
|---|---|---|
| `IO_REAL_BASE_URL` | `https://api.io.pagopa.it/api/v1` | corsia reale |
| `MOCK_BASE_URL` | `http://localhost:8080/io-connector-mock` | corsia mock (in cloud: `http://<ALB>:8080/io-connector-mock`) |
| `PN_IOCONNECTORMOCK_REALTAXIDSWHITELIST_PARAMETERNAME` | `MapIoConnectorMockRealTaxIdsWhitelist` | whitelist CF verso IO reale (SSM) |
| `FORWARD_TIMEOUT_MS` | `10000` | timeout richiesta in uscita; alla scadenza → 504 (§3.6) |
| `PATH_PREFIX` | `/api/v1` | prefisso di esposizione rimosso dal `rawPath` (WI6/WI7); `''` disabilita |
| `AWS_SSM_ENDPOINT` | *(vuoto in cloud)* | endpoint SSM per test/locale (LocalStack) |
| `AWS_REGION` | — | region client AWS |

Il logging usa `console.log`/`console.error` (stile house). `@aws-sdk/client-ssm` è
devDependency: in Lambda è fornito dal runtime, escluso dallo zip via `npm prune --production`.
