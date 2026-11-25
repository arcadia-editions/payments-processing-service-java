# Payments Processing Service (Java): ZenWave SDD overlay

What every zenwave-sdd skill and agent needs to know about this project: where the models are, its shape, its
commands, and where its test data is. `AGENTS.md` points here.

The placeholders marked `<after generation: ...>` and `<after test data: ...>` are filled in by the skill that
produces the fact (`zenwave-test-data` / `zenwave-test-scenarios` the test data row; `zenwave-gate-check` suggests the
full-suite duration).

## Where things are

Paths are relative to this repository's root. The models live in sibling repositories that must be cloned next to
this one: `../arcadia-editions-architecture` (flows) and `../payments-processing-api` (this context's ZDL and contracts).
The flow also references the other contexts' ZDLs: `../orders-checkout-api`, `../catalog-inventory-api`,
`../fulfillment-shipping-api`, `../notifications-consumer-api`.

| What | Path |
|---|---|
| Business flows (ZFL) | `../arcadia-editions-architecture/business-flows/*.zfl` (today: `place-order-flow.zfl`) |
| Domain models (ZDL), one per bounded context | `../payments-processing-api/domain-model.zdl` (this service); other contexts: `../<context>-api/domain-model.zdl` |
| API contracts | `../payments-processing-api/openapi.yml`, `../payments-processing-api/asyncapi.yml` (published events and own command channels), `../payments-processing-api/asyncapi-client.yml` (consumed events), Avro schemas in `../payments-processing-api/avro/` |
| Glossary and ADRs | `GLOSSARY.md`, `docs/adr/` |
| Domain summary | `docs/domain.md` |
| Board of the flow in progress | `docs/tasks.md` (archived boards in `docs/tasks/`) |
| Model gap proposals | `docs/proposals/` |
| Test data and scenario matrix | `src/test/resources/data.sql` (header: scenario and coverage matrices, chains `C-01`..`C-22`, assumptions, the fixed test "now" `2026-10-14T10:00:00Z` read by tests through a fixed `Clock`, not yet in the code; DDL checked against the export) and `src/test/resources/data/jpa/Payment/<n>.json` |
| Generation script | `zenwave-scripts.zw` |

**`zenwave-scripts.zw` and `pom.xml` read the models from a released package** (`@import(...)` and `classpath:`):

- artifact and version: `com.arcadiaeditions.payments.payment-processing.payments-processing:service-all:0.2.0` (the
  `@import` of `zenwave-scripts.zw` and `pom.xml` `payments-processing-api.version` must match). No other context's
  package is needed: every Avro schema here (own and consumed) has primitive fields only, so there are no
  `avroCompilerProperties.imports`. Repository: `https://maven.pkg.github.com/arcadia-editions/arcadia-editions-architecture`
  (server id `github` in `~/.m2/settings.xml`). Folder inside the jar:
  `com/arcadiaeditions/payments/payment-processing/payments-processing/` (`payment-processing`, singular: from the
  owner id `payments.payment-processing.payments-processing`).
- regenerate: `ZW_FILE=zenwave-scripts.zw node ../api-product-workflows/scripts/zenwave/generate-from-zdl.mjs . .`
  (`jbang zw` does not read `.zw` files; the script turns the `@import` into `-d`). Never `--force`.
- extract the models when their repository is not cloned:

  ```bash
  P=com/arcadiaeditions/payments/payment-processing/payments-processing; V=$(grep -o 'service-all:[^")]*' zenwave-scripts.zw | head -1 | cut -d: -f2)
  JAR=~/.m2/repository/$P/service-all/$V/service-all-$V.jar
  mkdir -p target/models && unzip -o -q -j "$JAR" "$P/*.zdl" "$P/*.yml" -d target/models
  ```

- staleness check (prints nothing when the packaged copy matches the working copy):
  `for f in domain-model.zdl openapi.yml asyncapi.yml asyncapi-client.yml; do diff -q --strip-trailing-cr "../payments-processing-api/$f" "target/models/$f"; done`
- release a model change: in `payments-processing-api`, run `artifact-release.yml` (workflow_dispatch) for each changed
  artifact (`domain-model`, `openapi`, `asyncapi-all`; it stamps the file versions and updates the architecture
  manifest), then `service-all`; bump `payments-processing-api.version` and the `@import`; regenerate.
- residual coupling to `main` (not pinned by the package): the generated `asyncapi-client.yml` `$ref`s the producers'
  `asyncapi.yml` on `main` (`fulfillment-shipping-api`, `orders-checkout-api`); `domain-model.zdl` `apis { asyncapi client ... }`
  points at `main` of the same two; both AsyncAPI contracts `$ref` `api-contract-commons` on `refs/heads/main`
  (servers, traits, error topics, message headers).
- known contract gap: `asyncapi-client.yml` 0.2.0 lacks `order-confirmation-rejected-event-v1` and
  `fulfillment-failed-event-v1`, which voidPayment consumes: `docs/proposals/P-01.md` (open).

**The ZFL's `@zdl(...)` paths** (`../../<context>-api/domain-model.zdl`, relative to `business-flows/`) resolve to
the sibling clones above; no mapping needed. This service owns `payments-processing-api/domain-model.zdl` only.

## Shape

- Deployment: **microservices** (one bounded context per repository; Kafka and AsyncAPI with Avro payloads, Schema
  Registry; ZDL consumers use `@asyncapi`). Mostly event-driven: the only REST operation is `getPaymentStatus`
  (`POST /status`, read by the reservation timer).
- Code already in place: yes (`zenwave-tasks-from-code-and-models`). Generated with ZenWave SDK: yes (commits
  `32b883f`, `8017788`); service methods and event listeners are still the generated `TODO`s; stub AsyncAPI consumer
  adapters in `adapters.events` throw `UnsupportedOperationException`.
- Stack: Java 25, Spring Boot 4.1.2, Spring Cloud Stream (Kafka), MapStruct, ArchUnit, Maven (no wrapper: `mvn`).
- Persistence: `jpa` (PostgreSQL; Testcontainers via docker-compose in tests).
- Organization prefix and packages: `com.arcadiaeditions` (base package `com.arcadiaeditions.payments.processing`),
  `CleanHexagonalProjectLayout`: `core.domain`, `core.application`, `core.inbound`, `core.outbound`,
  `adapters.web`, `adapters.events`, `infrastructure` (JPA), `config`; generated AsyncAPI SDK outside the hexagon in
  `asyncapi.provider` (`asyncapi.yml`) and `asyncapi.client` (`asyncapi-client.yml`), Avro models in `events.avro`.

## Commands

```bash
# JDK and tools: Java 25 and Maven on PATH (SDKMAN), node, Docker
export JAVA_HOME=~/.sdkman/candidates/java/current
# regenerate after a model change (the person runs it; never the orchestrator or its workers). Never --force.
ZW_FILE=zenwave-scripts.zw node ../api-product-workflows/scripts/zenwave/generate-from-zdl.mjs . .
# then: mvn -DskipTests clean test-compile (runs the OpenAPI / AsyncAPI Maven generators) and review git diff.
# compile, and collect MapStruct warnings
mkdir -p target && mvn -q -DskipTests clean compile 2>&1 | tee target/compile.log
# focused tests of a task (the implementer runs only these)
mvn -q test -Dtest='<TestClass>'
# full suite with integration tests and containers (once per slice review)
# about 4 minutes (222 s, 2026-11-25, committed pom); needs Docker (Testcontainers starts the docker-compose
# PostgreSQL / Kafka / Schema Registry on fixed ports 5432, 9092, 8081, 2181).
# Surefire failures stop the build before the integration tests: add -Dmaven.test.failure.ignore=true to see both.
# known failing at the baseline (2026-11-25), each fixed by a board task (docs/tasks.md tests:):
#   PaymentRepositoryIntegrationTest.updateTest: asserts Instant.now() -> T-01
#   PaymentsProcessingApiIntegrationTest.testGetPaymentStatus_400: no datasource (@EnableDockerCompose commented out), valid body -> T-01
#   PaymentsProcessingApiIntegrationTest.testGetPaymentStatus_201: no datasource -> T-01; expects noPayment and started both set -> T-14
# pom.xml uses the SpringKafka templates (8adfd42): every Spring context test (PaymentRepositoryIntegrationTest, all of
# PaymentsProcessingApiIntegrationTest) also fails at context load until the app.kafka.topics.<channel>.topic properties exist (T-07).
mvn -q verify -Dmaven.test.failure.ignore=true
# lint the models
bash .zenwave/bin/lint-zdl.sh --strict ../payments-processing-api/domain-model.zdl
# export the Hibernate DDL (JPA); -t names a concrete test (without it, the abstract base is picked and nothing is written)
bash .claude/skills/zenwave-test-data/scripts/export-ddl.sh -t PaymentRepositoryIntegrationTest
```

The orchestrator gives the workers the focused command (with the task's `tests:`) and the reviewer the full one.

## Project rules

- Naming conventions (ids, channels, topics): `.claude/skills/zenwave-grill-models/references/zenwave-languages/asyncapi-naming-conventions.md`;
  shared AsyncAPI traits, bindings and error topics in `../api-contract-commons/`.
- Tests: `*Test` run with Surefire (`mvn test`), `*IT` / `*IntegrationTest` with Failsafe (`mvn verify`). Prefer
  in-memory repositories and vertical tests over Mockito; adapters to external resources are tested with
  Testcontainers.
- Generated at build time into `target/` (never edit): OpenAPI interfaces and `*DTO`s, the AsyncAPI SDK.
- Format with `mvn spotless:apply`.
- Decisions already taken that the models don't show: none recorded yet (`docs/adr/`); the ZDL comments cite the
  decided proposals of the models' side (P-01 D, P-11 A, P-12 A). For a reference implementation of the same flow,
  see `../arcadia-editions-modulith-service-java/docs/zenwave-sdd-overlay.md` (Project rules) and its ADRs.
