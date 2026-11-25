# 0003. The AsyncAPI generator uses the SpringKafka templates

Status: accepted (2026-11-25)

`pom.xml` sets `<asyncapi.templates>SpringKafka</asyncapi.templates>` and passes it as `<templates>` to both
`AsyncAPIGenerator` executions (provider `asyncapi.yml` and client `asyncapi-client.yml`), instead of the zenwave
default `SpringCloudStream`. The generated producers and `@KafkaListener` consumers use Spring Kafka directly, with no
Spring Cloud Stream binder or `spring.cloud.stream.bindings.*`.

ZenWave does not generate the Kafka configuration from the AsyncAPI files: it lives in `application.yml` under
`app.kafka.topics.<channel>` (see https://www.zenwave360.io/zenwave-sdk/plugins/asyncapi-generator/):

- `topic`: required for every consumed channel (no default: without it no Spring context starts); the producers
  default to the channel name, so set the real topic name from `asyncapi.yml` too.
- consumers only, optional: `groupId`, `containerFactory`, `enabled` (default true), `dead-letter-queue-error-map`.
- producers only, optional: `timeout` (default 10).
- bootstrap servers, Avro serializers and the schema registry stay in `spring.kafka.*`.

Consequence: T-07 writes these properties before any Spring context test can pass.
