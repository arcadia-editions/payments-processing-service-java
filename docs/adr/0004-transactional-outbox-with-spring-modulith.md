# 0004. Transactional outbox with Spring Modulith in the AsyncAPI generator

Status: accepted, 2026-11-26. Implementation pending (HITL: it changes the generator config and regenerates code).

## Context

The generated event producer sends to Kafka inside the service method, before the transaction commits. A failed
commit leaves an event for state that doesn't exist; a crash after the commit and before the send loses the event.
Every command that saves and emits (`withEvents` in the ZDL) has this window.

## Decision

Turn on the ZenWave AsyncAPI generator's transactional outbox with Spring Modulith
([docs](https://www.zenwave360.io/zenwave-sdk/plugins/asyncapi-generator/),
[post](https://www.zenwave360.io/posts/TransactionalOutBoxWithAsyncAPIAndSpringModulith/)): the generated producer
publishes a `Message<?>` through `ApplicationEventPublisher`; Spring Modulith stores it in its event publication
registry in the same transaction and externalizes it to Kafka after commit.

1. `pom.xml`, both `zenwave-sdk-maven-plugin` executions of `AsyncAPIGenerator` (`generate-asyncapi`,
   `generate-asyncapi-client`), in `<configOptions>`:
   ```xml
   <transactionalOutbox>modulith</transactionalOutbox>
   ```
2. Dependencies (Spring Boot 4.1: Spring Modulith 2.1.x, Jackson 3):
   - `org.springframework.modulith:spring-modulith-bom` 2.1.x in `<dependencyManagement>`;
   - `org.springframework.modulith:spring-modulith-starter-jpa` (publication registry in PostgreSQL);
   - `io.zenwave360.sdk:spring-modulith-events-scs` 1.3.x (externalizes `Message<?>` events through Spring Cloud
     Stream, routed by the generated destination header);
   - `tools.jackson.dataformat:jackson-dataformat-avro` (Avro payloads in the registry).
3. A `@Configuration` in `config` with `@EnableSpringCloudStreamEventExternalization`.
4. `application.yml`:
   ```yaml
   spring.modulith.events:
     jdbc.schema-initialization.enabled: true   # event_publication table
     republish-outstanding-events-on-restart: true
   ```
5. Regenerate (person), check the generated-once code still compiles (producer names, the test `InMemory`
   producer), and add a test: no event on Kafka when the transaction rolls back, the event arrives after commit.

**SpringKafka templates (ADR 0003).** With `transactionalOutbox=modulith` the generator uses the shared
`shared/producer/outbox/modulith/Producer.java` template for SpringKafka too: it routes with the Spring Cloud Stream
header `spring.cloud.stream.sendto.destination`, not with `KafkaTemplate`. So this repository also needs the Spring
Cloud Stream Kafka binder (and `spring.cloud.stream.bindings` destinations for the produced channels) for the
externalizer, while consumers stay on SpringKafka. If that mix is not wanted, the alternative is an SDK template for a
SpringKafka outbox (`kafka/producer/outbox/modulith`), a separate change.

## Consequences

- Delivery becomes at-least-once and after commit. Consumers already tolerate redelivery (the ZFL redelivery rules).
- One more table (`event_publication`) per service database; docker-compose needs nothing new.
- Tests that read events from the in-memory producer keep working; only the Kafka integration tests see the delay.
