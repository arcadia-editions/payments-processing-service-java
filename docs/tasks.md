# PlaceOrderFlow: tasks

flow: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl
baseline: baseline/place-order-flow-5
approved: 2026-11-25 by the person (tasks and slices reviewed)
branch: afk/place-order-flow

<!--
Scope: the PaymentsProcessing bounded context (this repository) only. The steps of the other contexts (orders,
inventory, fulfillment, notifications) are boards of their own repositories; here they are Kafka messages in and out,
and the HTTP read getPaymentStatus that catalog-inventory's reservation timer calls.
Model paths: ZFL = ../arcadia-editions-architecture/business-flows/place-order-flow.zfl,
ZDL = ../payments-processing-api/domain-model.zdl.
Test data chains C-01..C-22: header of src/test/resources/data.sql (assumptions A-01..A-09 there). Data.sql written
from the entities and the modulith data.sql, then checked against the exported DDL.
Approved models: payments-processing-api service-all 0.2.0 (tag release/service-all/v0.2.0); ZFL at
arcadia-editions-architecture tag baseline/place-order-flow-<n>.
Open proposal: P-01 (asyncapi-client.yml 0.2.0 has no FulfillmentFailed / OrderConfirmationRejected channel) blocks
T-27..T-30.
Baseline failures confirmed by mvn verify 2026-11-25 (overlay, full-suite command). Data.sql matches the exported DDL.
AsyncAPI templates: pom.xml uses SpringKafka (8adfd42), whose consumers need app.kafka.topics.<channel>.topic
properties: until T-07 adds them no Spring context starts, so every repository and API IT fails at context load.
-->

## S0 Baseline
review: [x] claude-sonnet PASS (reports/review-S0.md)

- [x] T-01 mechanical AFK Generated tests that should pass do: updateTest without Instant.now(), API ITs with a datasource (@EnableDockerCompose commented in BaseWebTestClientTest), testGetPaymentStatus_400 sends a valid body
      done: grok grok-4.7
      test data: data/jpa/Payment/1.json, data.sql (C-01, id 1)
      tests: PaymentsProcessingServiceTest, PaymentsProcessingApiControllerTest, PaymentRepositoryIntegrationTest#updateTest, PaymentsProcessingApiIntegrationTest#testGetPaymentStatus_400
      blocked by: T-07
- [x] T-02 bounded HITL ArchitectureTest fails: core.application uses the generated producers in asyncapi.provider (outside the hexagon, as zenwave-scripts.zw sets it). Decide: allow the dependency in the rule, or put an outbound port in core.outbound; record it as an ADR. Decided 2026-11-25 by the person: allow it in the rule (EventProducerSDK layer), docs/adr/0001-core-application-uses-generated-event-producers.md
      tests: ArchitectureTest
- [x] T-03 mechanical AFK Inject a java.time.Clock (system clock in main, fixed 2026-10-14T10:00:00Z in tests) for every timestamp the service sets and every event time (authorizedAt, declinedAt, failedAt, retriedAt, exhaustedAt, capturedAt, voidedAt)
      done: grok grok-4.7
      tests: PaymentsProcessingServiceTest
      blocked by: T-01
- [x] T-04 mechanical AFK PaymentRepository.findByOrderId (JPA and in-memory twin): every command correlates by orderId (unique)
      done: grok grok-4.7
      model: ../payments-processing-api/domain-model.zdl:56-57, ../payments-processing-api/domain-model.zdl:135-144
      test data: data.sql (C-01, C-11 none)
      tests: PaymentRepositoryIntegrationTest
- [x] T-05 bounded HITL Decide the payment processor integration: provider or a stand-in (the modulith uses one, driven by paymentMethodId triggers), how authorize / capture / void answer, providerReference format, failureReason codes, and which Payment field keeps VoidPaymentInput.reason; record it as an ADR. The ZDL names the processor but no API (fixtures assume A-02, A-03). Decided 2026-11-25 for the demo: the modulith's stand-in, docs/adr/0002-stand-in-payment-processor.md
      model: ../payments-processing-api/domain-model.zdl:149-163, ../payments-processing-api/domain-model.zdl:64-73, ../payments-processing-api/domain-model.zdl:141-144
      test data: data.sql (C-02, C-03, C-12, C-21)
- [x] T-06 advanced AFK Outbound port PaymentProcessor (authorize, capture, void) in core.outbound with the adapter T-05 chose and an in-memory stub for tests that answers each outcome by paymentMethodId or orderId, including a call that exceeds the 30-second timeout
      done: claude-opus opus
      model: ../payments-processing-api/domain-model.zdl:149-163
      test data: stub-driven (no row): the stub decides AUTHORIZED / DECLINED / FAILED, CAPTURED / CAPTURE_FAILED, VOIDED / VOID_FAILED
      tests: PaymentProcessorStubTest
      blocked by: T-05
- [x] T-07 mechanical AFK Kafka topics in application.yml (SpringKafka templates: app.kafka.topics.<channel>.topic, topic names from asyncapi.yml / asyncapi-client.yml); required for consumers (no default), producers default to the channel name; see docs/adr/0003-springkafka-templates-for-asyncapi-generator.md for the produced channels (payment-authorized, -declined, -failed, -retried, -retry-exhausted, -captured, -capture-failed, -voided, -void-failed) and the consumed order-created-event-v1, fulfillment-scheduled-event-v1 and the own payment-failed, payment-retried, payment-capture-failed channels, replacing the archetype's on-customer-event
      done: grok grok-4.7
      model: ../payments-processing-api/domain-model.zdl:155, ../payments-processing-api/domain-model.zdl:165, ../payments-processing-api/domain-model.zdl:176, ../payments-processing-api/domain-model.zdl:181, ../payments-processing-api/domain-model.zdl:192, ../payments-processing-api/domain-model.zdl:201-283
      test data: data.sql (C-02 for a produced event, C-12 for a consumed one)
      tests: PaymentsProcessingApiIntegrationTest, PaymentRepositoryIntegrationTest
- [x] T-08 bounded HITL Decide what capturePayment does when there is no Payment for the orderId, or it is in a state the ZDL lists neither as from: nor as redelivery no-op (AUTHORIZATION_REQUESTED, DECLINED, FAILED, RETRY_EXHAUSTED, VOID_FAILED): no-op, error to the DLQ, or else; the generated code throws. Decided 2026-11-25 for the demo: it throws (as generated), no special handling
      model: ../payments-processing-api/domain-model.zdl:180-183
      test data: data.sql (C-15, C-03, C-11 none)

## S1 completed (tracer bullet)
path: OrderCreated > authorizePayment[authorized] > PaymentAuthorized > confirmOrder > ... > FulfillmentScheduled
      > capturePayment > PaymentCaptured > sendOrderConfirmation > end completed
review: [x] claude-opus PASS (reports/review-S1.md; every worker implemented part of S1)

- [x] T-09 advanced AFK authorizePayment authorized: creation only (no-op, no event, when a Payment for orderId exists in any state); save AUTHORIZATION_REQUESTED with attemptCount 1 and commit before calling the processor; on AUTHORIZED set providerReference + authorizedAt, persist, emit only PaymentAuthorized (not every alternative, as generated)
      done: claude-opus opus
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:114-121, ../payments-processing-api/domain-model.zdl:149-158, ../payments-processing-api/domain-model.zdl:89-96, ../payments-processing-api/domain-model.zdl:201-209
      test data: data.sql (C-02 redelivery; a new orderId such as ORD-099 for creation)
      tests: PaymentsProcessingServiceTest#authorizePaymentTest
      blocked by: T-01, T-03, T-04, T-06
- [x] T-10 mechanical AFK OrderCreatedConsumerService: map OrderCreated (orderId, customerId, customerEmail, paymentMethodId, amount, currency) to AuthorizePaymentInput and call authorizePayment
      done: grok grok-4.7
      model: ../payments-processing-api/domain-model.zdl:89-96, ../payments-processing-api/domain-model.zdl:155
      test data: data.sql (C-02)
      tests: OrderCreatedConsumerServiceTest
      blocked by: T-07, T-09
- [x] T-11 bounded AFK capturePayment captured: load by orderId, from AUTHORIZED call the processor capture, CAPTURED + capturedAt, persist, emit only PaymentCaptured (with customerEmail and providerReference); no-op, no event, when CAPTURED, CAPTURE_FAILED or VOIDED; other states per T-08
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:196-199, ../payments-processing-api/domain-model.zdl:180-183, ../payments-processing-api/domain-model.zdl:135-138, ../payments-processing-api/domain-model.zdl:247-256
      test data: data.sql (C-12, C-20 from AUTHORIZED, C-01, C-22, C-21, C-04 redelivery)
      tests: PaymentsProcessingServiceTest#capturePaymentTest
      blocked by: T-01, T-03, T-04, T-06, T-08
- [x] T-12 mechanical AFK FulfillmentScheduledConsumerService: map FulfillmentScheduled.orderId to CapturePaymentInput and call capturePayment
      done: grok grok-4.7
      model: ../payments-processing-api/domain-model.zdl:135-138, ../payments-processing-api/domain-model.zdl:181
      test data: data.sql (C-12)
      tests: FulfillmentScheduledConsumerServiceTest
      blocked by: T-07, T-11
- [x] T-31 bounded AFK Kafka Avro wiring for the SpringKafka runtime: value serializer / deserializer for Avro (Confluent KafkaAvroSerializer / KafkaAvroDeserializer, simplest), schema.registry.url, specific.avro.reader true, listener ack-mode manual (the generated listeners take an Acknowledgment), the serializer dependency in pom.xml; ADR 0003 says these live in spring.kafka.*
      done: claude-sonnet sonnet
      fixes: .zenwave/handoff/place-order-flow/reports/review-S0.md (model axis, T-07)
      model: ../payments-processing-api/domain-model.zdl:201-283
      tests: a Kafka round-trip IT (produce PaymentAuthorized, consume it back as Avro), e.g. KafkaAvroWiringIntegrationTest
- [x] T-13 bounded AFK Flow test completed (payments side): an OrderCreated message publishes PaymentAuthorized on payments-processing.payment-authorized.event.avro.v1; a FulfillmentScheduled message for that order publishes PaymentCaptured
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:114-121, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:196-199, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-342
      test data: data.sql (C-01, C-12)
      tests: PlaceOrderFlowCompletedTest
      blocked by: T-10, T-12, T-31
- [x] T-32 mechanical AFK Rename the flow test PlaceOrderFlowCompletedTest to PlaceOrderFlowCompletedIntegrationTest (Spring + Docker tests run with Failsafe: overlay project rules), so plain mvn test needs no Docker and the suite starts compose once per JVM
      done: grok grok-4.7
      fixes: .zenwave/handoff/place-order-flow/reports/review-S1.md (standards axis)
      tests: PlaceOrderFlowCompletedIntegrationTest

## S2 reservation timer read (getPaymentStatus)
path: ReservationExpired > expireReservation > call getPaymentStatus[NoPaymentResult | PaymentStartedResult]
      > (catalog-inventory releases on NoPaymentResult) > end orderCancelled
review: [x] claude-opus PASS (reports/review-S2.md)

- [x] T-14 bounded AFK getPaymentStatus: find by orderId; none: outcome NO_PAYMENT with noPayment.orderId; any existing Payment: outcome STARTED with started.orderId and started.status; read only. Fix the generated testGetPaymentStatus_201, which expects noPayment and started both set
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:293-297, ../payments-processing-api/domain-model.zdl:196-198, ../payments-processing-api/domain-model.zdl:110-133
      test data: data.sql (C-11 NO_PAYMENT, C-15 STARTED in flight, C-01 STARTED captured)
      tests: PaymentsProcessingServiceTest#getPaymentStatusTest, PaymentsProcessingApiControllerTest#getPaymentStatusTest, PaymentsProcessingApiIntegrationTest#testGetPaymentStatus_201
      blocked by: T-01, T-04
- [x] T-15 bounded AFK Flow test reservation timer read (payments side): POST /api/payments/status answers NO_PAYMENT for ORD-011 and STARTED (AUTHORIZATION_REQUESTED) for ORD-015, as catalog-inventory's timer reads it
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:268-297
      test data: data.sql (C-11, C-15)
      tests: PlaceOrderFlowReservationTimerIntegrationTest
      blocked by: T-14

## S3 orderCancelled (payment declined)
path: OrderCreated > authorizePayment[declined] > PaymentDeclined > releaseStock > StockReleased > cancelOrder
      > OrderCancelled > sendOrderCancelledNotification > end orderCancelled
review: [x] claude-opus PASS (reports/review-S3.md; SHOULD FIX -> T-33)

- [x] T-16 bounded AFK authorizePayment declined: the processor declines, DECLINED + declinedAt + failureReason, persist, emit only PaymentDeclined (reason, declinedAt); no retry
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:107-121, ../payments-processing-api/domain-model.zdl:157-158, ../payments-processing-api/domain-model.zdl:211-219
      test data: stub-driven from a new orderId; result shape C-03, C-07
      tests: PaymentsProcessingServiceTest#authorizePaymentDeclinedTest
      blocked by: T-09
- [x] T-17 bounded AFK Flow test orderCancelled, declined (payments side): an OrderCreated the stub declines publishes PaymentDeclined and nothing else
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:114-121, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-07 result shape)
      tests: PlaceOrderFlowCancelledIntegrationTest#declined
      blocked by: T-10, T-16

## S4 orderCancelled (retries exhausted) and the retry loop
path: OrderCreated > authorizePayment[failed] > PaymentFailed > retryPayment[retried] > PaymentRetried
      > reauthorizePayment[authorized | declined | failed] > ... > retryPayment[retry-exhausted]
      > PaymentRetryExhausted > releaseStock > ... > end orderCancelled
review: [ ]

- [x] T-18 advanced AFK authorizePayment failed: a processor error or a call over 30 seconds becomes FAILED + failedAt + failureReason, persist, emit only PaymentFailed with attemptCount
      done: claude-opus opus
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:107-121, ../payments-processing-api/domain-model.zdl:149-158, ../payments-processing-api/domain-model.zdl:221-228
      test data: stub-driven from a new orderId; result shape C-16
      tests: PaymentsProcessingServiceTest#authorizePaymentFailedTest
      blocked by: T-09
- [x] T-19 advanced AFK retryPayment: no-op, no event, when there is no Payment, it is not FAILED, or input.attemptCount != attemptCount; never increments attemptCount; below 3 attempts emit PaymentRetried (attemptCount, retriedAt) after the backoff (5 s before attempt 2, 15 s before attempt 3, with the Clock), status stays FAILED; at 3 RETRY_EXHAUSTED, emit PaymentRetryExhausted (reason, exhaustedAt)
      done: claude-opus opus
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:124-136, ../payments-processing-api/domain-model.zdl:170-178, ../payments-processing-api/domain-model.zdl:98-102, ../payments-processing-api/domain-model.zdl:230-245
      test data: data.sql (C-16 a1, C-17 a2, C-18 a3, C-11 none, C-03 and C-08 wrong state)
      tests: PaymentsProcessingServiceTest#retryPaymentTest
      blocked by: T-01, T-03, T-04
- [x] T-20 bounded AFK reauthorizePayment: no-op, no event, when there is no Payment, it is not FAILED, or input.attemptCount != attemptCount; save AUTHORIZATION_REQUESTED with attemptCount + 1 and commit before the processor call; then the outcomes of authorizePayment (T-09, T-16, T-18)
      done: claude-sonnet sonnet
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:138-146, ../payments-processing-api/domain-model.zdl:160-168, ../payments-processing-api/domain-model.zdl:104-108
      test data: data.sql (C-16 a1 to a2, C-17 a2 to a3, C-16 with stale attemptCount 2, C-11 none, C-08 wrong state)
      tests: PaymentsProcessingServiceTest#reauthorizePaymentTest
      blocked by: T-16, T-18
- [ ] T-21 mechanical AFK PaymentFailedConsumerService and PaymentRetriedConsumerService: map both messages of each own channel (RetryPaymentInput / PaymentFailed, ReauthorizePaymentInput / PaymentRetried: orderId, attemptCount) and call retryPayment / reauthorizePayment
      model: ../payments-processing-api/domain-model.zdl:165, ../payments-processing-api/domain-model.zdl:176, ../payments-processing-api/domain-model.zdl:98-108
      test data: data.sql (C-16)
      tests: PaymentFailedConsumerServiceTest, PaymentRetriedConsumerServiceTest
      blocked by: T-07, T-19, T-20
- [ ] T-33 bounded AFK Flow tests prove "nothing else": in PlaceOrderFlowCancelledIntegrationTest collect every record whose payload carries the orderId, of any type and in both loops (a shared helper per orderId, also usable by PlaceOrderFlowCompletedIntegrationTest), and assert exactly one PaymentDeclined; later flow tests (T-22, T-26) reuse it
      fixes: .zenwave/handoff/place-order-flow/reports/review-S3.md (model axis, T-17)
      tests: PlaceOrderFlowCancelledIntegrationTest
- [ ] T-22 bounded AFK Flow test retry loop (payments side): an OrderCreated that fails once then succeeds publishes PaymentFailed, PaymentRetried, PaymentAuthorized (attemptCount 2); one that always fails ends in PaymentRetryExhausted after 3 attempts
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:114-146, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-08 result shape, C-16..C-18)
      tests: PlaceOrderFlowCancelledIntegrationTest#retriesExhausted, PlaceOrderFlowRetryIntegrationTest
      blocked by: T-10, T-21, T-33

## S5 orderCancelled (capture failed: void)
path: FulfillmentScheduled > capturePayment[PaymentCaptureFailed] > voidPayment[voided | void-failed]
      > PaymentVoided | PaymentVoidFailed > releaseStock > ... > end orderCancelled
review: [ ]

- [ ] T-23 bounded AFK capturePayment capture failed: the processor refuses the capture, CAPTURE_FAILED + failureReason, persist, emit only PaymentCaptureFailed (reason, failedAt)
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:190-199, ../payments-processing-api/domain-model.zdl:180-183, ../payments-processing-api/domain-model.zdl:258-264
      test data: data.sql (C-12, stub-driven); result shape C-21
      tests: PaymentsProcessingServiceTest#capturePaymentFailedTest
      blocked by: T-11
- [ ] T-24 bounded AFK voidPayment: no-op, no event, when there is no Payment or it is not AUTHORIZED or CAPTURE_FAILED; call the processor void; VOIDED + voidedAt, emit PaymentVoided; refused: VOID_FAILED + voidFailedAt, emit PaymentVoidFailed (failedAt = voidFailedAt); reason per T-05
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-252, ../payments-processing-api/domain-model.zdl:186-194, ../payments-processing-api/domain-model.zdl:141-144, ../payments-processing-api/domain-model.zdl:266-283
      test data: data.sql (C-21 from CAPTURE_FAILED, C-02 from AUTHORIZED, C-15 / C-09 / C-10 / C-11 no-op)
      tests: PaymentsProcessingServiceTest#voidPaymentTest
      blocked by: T-03, T-04, T-05, T-06
- [ ] T-25 mechanical AFK PaymentCaptureFailedConsumerService: map both messages of the own channel (VoidPaymentInput / PaymentCaptureFailed: orderId, reason) to VoidPaymentInput and call voidPayment
      model: ../payments-processing-api/domain-model.zdl:192, ../payments-processing-api/domain-model.zdl:141-144
      test data: data.sql (C-21)
      tests: PaymentCaptureFailedConsumerServiceTest
      blocked by: T-07, T-24
- [ ] T-26 bounded AFK Flow test orderCancelled, capture failed (payments side): a FulfillmentScheduled the stub refuses to capture publishes PaymentCaptureFailed, then PaymentVoided; with a refused void, PaymentVoidFailed; redelivered, nothing
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:196-199, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:246-252, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-09, C-10 result shapes, C-12, C-21)
      tests: PlaceOrderFlowCancelledIntegrationTest#captureFailed
      blocked by: T-12, T-23, T-25

## S6 orderCancelled (fulfillment failed: void)
path: FulfillmentFailed > voidPayment[voided | void-failed] > PaymentVoided | PaymentVoidFailed > releaseStock > ... > end orderCancelled
review: [ ]

- [!] T-27 mechanical AFK FulfillmentFailed consumer: map orderId and reason to VoidPaymentInput and call voidPayment (no consumer interface is generated: fulfillment-failed-event-v1 is missing from asyncapi-client.yml 0.2.0)
      model: ../payments-processing-api/domain-model.zdl:191, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:246
      test data: data.sql (C-02 shape AUTHORIZED; C-04 result)
      tests: FulfillmentFailedConsumerServiceTest
      blocked by: T-24
      blocked: P-01
- [!] T-28 bounded AFK Flow test orderCancelled, fulfillment failed (payments side): a FulfillmentFailed message voids an AUTHORIZED payment and publishes PaymentVoided
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-252, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-04)
      tests: PlaceOrderFlowCancelledIntegrationTest#fulfillmentFailed
      blocked by: T-27
      blocked: P-01

## S7 late authorization rejected
path: ... > PaymentAuthorized (after expiry) > confirmOrder[stock-not-held | order-cancelled] > OrderConfirmationRejected
      > voidPayment > PaymentVoided > releaseStock (no-op) > end orderCancelled
review: [ ]

- [!] T-29 mechanical AFK OrderConfirmationRejected consumer: map orderId and reason to VoidPaymentInput and call voidPayment (no consumer interface is generated: order-confirmation-rejected-event-v1 is missing from asyncapi-client.yml 0.2.0)
      model: ../payments-processing-api/domain-model.zdl:190, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-246
      test data: data.sql (C-05, C-06)
      tests: OrderConfirmationRejectedConsumerServiceTest
      blocked by: T-24
      blocked: P-01
- [!] T-30 bounded AFK Flow test late authorization (payments side): OrderConfirmationRejected for ORD-005 (STOCK_NOT_HELD) and ORD-006 (ORDER_CANCELLED) voids the late AUTHORIZED payment and publishes PaymentVoided
      model: ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:236-252, ../arcadia-editions-architecture/business-flows/place-order-flow.zfl:340-344
      test data: data.sql (C-05, C-06)
      tests: PlaceOrderFlowLateAuthorizationIntegrationTest
      blocked by: T-29
      blocked: P-01
