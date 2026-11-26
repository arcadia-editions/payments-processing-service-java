VERDICT: PASS
SLICE: S5   RANGE: 54eae21..37fbc83
SUITE: `export JAVA_HOME=~/.sdkman/candidates/java/current; mvn -q verify -Dmaven.test.failure.ignore=true`, run once in the review-S5 checkout at 37fbc83.
- Ports 5432/9092/8081/2181 were free 4 polls in a row (08:52:17-08:52:48). The other project's containers were up, but on 15432/29092/18081/22181.
- Ran 08:52:48-09:00:04 and exited 0. No "Bind for" error, so no rerun.
- Surefire 55/55 passed:
  - ArchitectureTest 2, PaymentsProcessingApiControllerTest 1, PaymentProcessorStubTest 11
  - PaymentsProcessingServiceTest 23 (+3: capturePaymentFailedTest, voidPaymentRefusedTest, voidPaymentNoOpTest; voidPaymentTest filled)
  - OrderCreated 2, FulfillmentScheduled 2, PaymentFailed 4, PaymentRetried 5, PaymentCaptureFailedConsumerServiceTest 5 (new)
- Failsafe 17/17 passed:
  - PaymentsProcessingApiIntegrationTest 2, PlaceOrderFlowReservationTimerIntegrationTest 2, PaymentRepositoryIntegrationTest 6, KafkaAvroWiringIntegrationTest 1
  - PlaceOrderFlowCompletedIntegrationTest 1, PlaceOrderFlowRetryIntegrationTest 1
  - PlaceOrderFlowCancelledIntegrationTest 4: declined 8.5 s, retriesExhausted 28.2 s, captureFailed 11.9 s (new), voidFailed 7.4 s (new)
- No flaky test. `mvn -q spotless:check` exits 0. The tree was clean afterwards.
BASELINE: untouched.
- `git diff --stat 54eae21..37fbc83 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` lists nothing.
- docs/tasks.md changes only in the orchestrator's `chore(board)` commits (0e3751f, d19f390, 11f1a04, 9056f9d, 37fbc83).
- The worker commits touch only generated-once code and tests: c05d63f, e08c3af, c96a65d, 2c982b6. Each is unsigned and has the AI-Agent / AI-Model / AI-Orchestrator trailers.

PREVIOUS REVIEW (review-S4.md, PASS, notes only)
- resolved  NOTE (same-instance before/after asserts):
  - voidPaymentNoOpTest (PaymentsProcessingServiceTest.java, voidPaymentNoOpTest) copies voidedAt and voidFailedAt into Optionals before the call.
  - The consumer no-op tests assert fixed fixture values.
- not resolved (NOTE, carried)  PaymentsProcessingServiceImpl.java:57-66: the public `setSleeper` and the `Clock clock()` accessor. Not touched in S5.
- not resolved (NOTE, carried)  The flow tests' C-07 / C-08 result shapes. Not touched in S5.

MODEL AXIS
- SHOULD FIX  T-23/T-25 PaymentsProcessingServiceImpl.java:208-239 (capturePayment). PaymentCaptureFailed is sent and consumed before its own row commits.
  - capturePayment runs in the class-level @Transactional (REQUIRED). It sends PaymentCaptureFailed synchronously (`kafkaTemplate.send(..).get(10s)`) and only then commits CAPTURE_FAILED.
  - This service consumes that event itself: payment-capture-failed-event-v1 -> voidPayment, new in T-25.
  - So voidPayment can read the Payment while it is still AUTHORIZED. That is still voidable, so it calls the processor void, sends PaymentVoided inside its own transaction, then fails at commit on @Version.
  - The listener retry then reads CAPTURE_FAILED and voids again: two processor voids and two PaymentVoided events. ZDL:194 and ZFL:246-252 allow one event per outcome.
  - The window is real. In this run (suite.log 08:59:24) the capture UPDATE flushed at .466, the consumer received the event at .466, and voidPayment's SELECT ran at .469, about 3 ms later. It read the committed row this time.
  - UNCONFIRMED as a reproduced duplicate: no OptimisticLock in the log, and captureFailed passed. A forced delay between the send and the commit in capturePayment would settle it.
  - S4 avoided this for its own-channel loop: authorizePayment, reauthorizePayment and retryPayment are NOT_SUPPORTED and commit before they send. Two fixes would work: make capturePayment commit CAPTURE_FAILED before it sends (as authorizePayment does), or send after commit.
  - S6/S7 voidPayment callers are other contexts' events, so this loop is the only one this affects.
- NOTE  T-23 PaymentsProcessingServiceImpl.java:228. The code and the approved fixture disagree on failedAt for a capture failure.
  - On capture failure the code sets Payment.failedAt = now. PaymentCaptureFailed.failedAt maps from that field by name.
  - The approved data has failed_at NULL on CAPTURE_FAILED (C-21, data.sql:93) and on was-CAPTURE_FAILED (C-09, :77). A-05 ties failedAt to authorization attempts.
  - The modulith reference sets failedAt the same way the code does, and nothing reads failedAt outside FAILED (retryPayment).
  - A person decides which is right: the fixture or the code. Nothing breaks either way.
- NOTE  T-24/T-26 CAPTURE_FAILED -> VOID_FAILED is not exercised anywhere.
  - voidPaymentRefusedTest starts from AUTHORIZED (C-02).
  - The voidFailed flow test publishes PaymentCaptureFailed straight onto an AUTHORIZED payment, because the stand-in drives a capture refusal by providerReference and a void refusal by paymentMethodId, so it cannot do both (documented in the test).
  - The code path does not branch on the source state after the guard, so this is low risk.
- Checked, no finding: T-23 capturePayment refused path against ZFL:196-199, ZDL:180-183 and :258-264.
  - It goes AUTHORIZED -> CAPTURE_FAILED with failureReason from the processor (`capture_rejected`), persists, and sends only PaymentCaptureFailed: orderId, customerId, reason <- failureReason, failedAt. I checked EventsMapperImpl in target.
  - A redelivered FulfillmentScheduled on CAPTURE_FAILED / VOIDED is a no-op. On VOID_FAILED it throws, per T-08.
- Checked, no finding: T-24 voidPayment :241-269 against ZDL:185-194, :141-144, :266-283 and ADR 0002.
  - It is a no-op, with no event and no processor call, for a missing Payment and for any state except AUTHORIZED / CAPTURE_FAILED.
  - When the void succeeds: VOIDED + voidedAt, and PaymentVoided (providerReference, reason, voidedAt).
  - When the void is refused: VOID_FAILED + voidFailedAt, and PaymentVoidFailed with failedAt <- voidFailedAt.
  - failureReason = VoidPaymentInput.reason (ADR 0002, A-03).
  - It reads only input.orderId and input.reason, so it does not depend on which consumer calls it. That is what the FulfillmentFailed / OrderConfirmationRejected consumers of S6/S7 need.
- Checked, no finding: T-25 PaymentCaptureFailedConsumerService.
  - Both channel messages (VoidPaymentInput, PaymentCaptureFailed) map orderId and reason through MapStruct (generated impl checked) and call voidPayment.
  - It is the same pattern as PaymentFailed / PaymentRetried.
  - Tests cover both messages, redelivery after VOIDED, already VOIDED, and an unknown order.
- Checked, no finding: T-26 over real Kafka.
  - With pm_capture_fail, OrderCreated + FulfillmentScheduled produce exactly Authorized, CaptureFailed, Voided. A redelivered FulfillmentScheduled adds nothing. The FlowEvents helper with containsExactlyInAnyOrder would catch a duplicate.
  - With pm_void_fail, a PaymentCaptureFailed produces PaymentVoidFailed.
  - Outside this repo: releaseStock through end orderCancelled (ZFL:340-344).
- Nothing out of scope: no new field, endpoint, event, status or topic.

STANDARDS AXIS
- NOTE  PaymentsProcessingServiceImpl.java:252-253. `ensureCanVoidPayment` runs right after an explicit guard that already returned for every other state, so the call is redundant. It is harmless and matches the capturePayment style.
- NOTE (carried)  PaymentsProcessingServiceImpl.java:57-66 `setSleeper` / `clock()`.
- Conventions otherwise followed:
  - Code is only in generated-once files: service impl, EventsMapper, the adapters.events consumer + `<X>ConsumerMapper`, and tests.
  - Tests use in-memory repositories and the stand-in processor, not Mockito.
  - The flow test is an `*IntegrationTest` (Failsafe).
  - Times come from the Clock, not a bare `Instant.now()`.
  - Spotless is clean. The AFK commits have the trailers.

PROPOSALS: none.
NOT CHECKED:
- The other contexts' side of the S5 path: releaseStock, cancelOrder, cancelFulfillment and sendOrderCancelledNotification after PaymentVoided / PaymentVoidFailed.
- The capture/void race above, under load or with a forced delay. It was only seen by log timing.
- Whether the board's `done:` model names match the person's records.
