# pn-io-connector-mock

Stub stateless del backend AppIO per ambienti di test QA, che espone `getProfileByPOST`, `submitMessage` e `getMessage` con risposte deterministiche che evolvono nel tempo secondo sequenze temporizzate (es. `PROCESSED -> READ -> PAID`).

## Elementi

- `docs/openapi/pn-io-connector-mock-internal.yaml` — contratto OpenAPI (3 endpoint)
- `functions/lambda-router/` — Lambda function per il routing su mock o AppIO reale
- `src/main/java/it/pagopa/pn/ioconnectormock/` — Microservizio mock

## Architettura

Nessun database né cache di stato: un marker `@io:<sequenceName>` nel subject e l'`ioMessageId` autodescrittivo permettono a `getMessage` di ricalcolare lo snapshot corrente.

