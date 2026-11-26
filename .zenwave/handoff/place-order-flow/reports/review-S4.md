VERDICT: PASS
SLICE: S4   RANGE: dbd108e..54eae21
SUITE: `export JAVA_HOME=~/.sdkman/candidates/java/current; mvn -q verify -Dmaven.test.failure.ignore=true`, run once in the review-S4 checkout at 54eae21.
- Ports 5432/9092/8081/2181 were free 4 polls in a row (07:45:07-07:45:39). The other project's containers were up, but on 15432/29092/18081/22181.
- Ran 07:45:52-07:59:42 and exited 0. No "Bind for" error, so no rerun.
- Surefire 47/47 passed:
  - ArchitectureTest 2
  - PaymentsProcessingApiControllerTest 1
  - PaymentsProcessingServiceTest 20
  - PaymentProcessorStubTest 11
  - OrderCreatedConsumerServiceTest 2
  - FulfillmentScheduledConsumerServiceTest 2
  - PaymentFailedConsumerServiceTest 4 (new)
  - PaymentRetriedConsumerServiceTest 5 (new)
- Failsafe 15/15 passed:
  - PaymentsProcessingApiIntegrationTest 2
  - PlaceOrderFlowReservationTimerIntegrationTest 2
  - PaymentRepositoryIntegrationTest 6
  - KafkaAvroWiringIntegrationTest 1
  - PlaceOrderFlowCompletedIntegrationTest 1
  - PlaceOrderFlowCancelledIntegrationTest 2: declined 9.2 s, retriesExhausted 30.4 s. That is the real 5 s + 15 s backoff plus the 5 s quiet window.
  - PlaceOrderFlowRetryIntegrationTest 1: 14.0 s.
- No flaky test. `mvn -q spotless:check` is clean, and the tree was clean afterwards.
BASELINE: untouched.
- `git diff --stat dbd108e..54eae21 -- '*.zfl' '*.zdl' '*openapi*.yml' '*asyncapi*.yml' 'src/test/resources/data*'` lists nothing.
- docs/tasks.md and docs/proposals/P-01.md change only in the orchestrator's commits: the `chore(board)` commits and 9ecb0d6 (P-01 decided A).
- The worker commits touch only code and tests: 5caf2a6, 6d62722, 73c3ced, 8501982, 4ad06f0, 04e1f64.

PREVIOUS REVIEW (review-S3.md, PASS)
- resolved  SHOULD FIX T-17 "nothing else" (now T-33):
  - FlowEvents.java:45-51 keeps every record whose payload carries the orderId, of any type, in both await and quiet.
  - PlaceOrderFlowCancelledIntegrationTest.java:48 asserts `types()` is exactly [PaymentDeclined], so a second event or a duplicate fails it.
  - PlaceOrderFlowCompletedIntegrationTest uses the same helper and asserts exactly [Authorized] and then [Authorized, Captured].
- resolved  standards NOTE (inline poll loops duplicated between flow tests): the loops are now the shared FlowEvents helper.
- not resolved (NOTE, carried)  The C-07 reason `card_declined` is still not asserted in the declined flow test. The unit test covers it.
- not resolved (NOTE, carried)  PaymentsProcessingServiceImpl.java:60-62 `Clock clock()`, the test-only accessor.

MODEL AXIS
- NOTE  T-22 PlaceOrderFlowRetryIntegrationTest.java:52-56 and PlaceOrderFlowCancelledIntegrationTest.java:67-82. The flow tests prove less than the board line names:
  - failsOnceThenAuthorized checks the event types and the final Payment.attemptCount 2. It does not check that PaymentFailed and PaymentRetried carry attemptCount 1.
  - retriesExhausted does not check the C-08 result shape: PaymentRetryExhausted attemptCount 3, reason `processor_unavailable`, Payment RETRY_EXHAUSTED.
  - These are covered at unit level: retryPaymentExhaustedTest and reauthorizePaymentFailedAgainTest. Not a gap for later slices.
- NOTE  T-19/T-20 PaymentsProcessingServiceTest.java:317, :290, :379, and PaymentFailedConsumerServiceTest.java:77-79. `before` and `after`/`saved` are the same object, because PaymentRepositoryInMemory stores and returns references. So `assertEquals(before.getX(), after.getX())` is always true. Example: the "failedAt unchanged" assertion at :317.
  - The no-op and no-mutation claims still hold. Each test also asserts fixed values: status FAILED, attemptCount == input, no captured event, no sleep. Any non-no-op path emits an event.
  - The same pattern is at :426 (S1). Copy the fields before the call when this pattern is reused in T-24 voidPayment no-op tests.
- NOTE  T-19 PaymentsProcessingServiceImpl.java:198-205. After retryPayment sends PaymentRetried, the Payment is still FAILED with the same attemptCount.
  - So a redelivered PaymentFailed (for example, the ack is lost after the send) sends a second PaymentRetried.
  - This is what ZDL:170-178 prescribes ("no-op unless FAILED and input.attemptCount == attemptCount").
  - reauthorizePayment's guard drops the duplicate: the status is no longer FAILED, or the attemptCount is stale. No second processor call. Not a code finding.
- Checked, no finding: T-18 PaymentsProcessingServiceImpl.java:125-131, against ZFL:114-121 and ZDL:149-158, 221-228.
  - FAILED + failedAt (Clock) + failureReason are set, saved, and only PaymentFailed is sent.
  - EventsMapper maps reason from failureReason. attemptCount and failedAt map by name.
  - A processor error and the 30 s timeout become FAILED inside StandInPaymentProcessor (`processor_unavailable` / `processor_timeout`, ADR 0002).
  - authorizePaymentFailedTest covers pm_fail_always, pm_fail_once and pm_timeout. It asserts fixed values and exactly one event each.
- Checked, no finding: T-19 retryPayment :174-206, against ZDL:170-178 and ZFL:124-136.
  - It is a no-op with no event when the Payment is missing, not FAILED, or the attemptCount is stale. It never increments attemptCount.
  - At attemptCount >= 3 it goes to RETRY_EXHAUSTED and sends PaymentRetryExhausted (reason from failureReason, exhaustedAt from the Clock).
  - Otherwise it waits failedAt + 5 s (attempt 1) or + 15 s (attempt 2), minus the time already gone, measured with the Clock. Then it sends PaymentRetried (attemptCount = the failed attempt, retriedAt from the Clock).
  - The method is NOT_SUPPORTED, so the wait holds no transaction.
  - Counting from failedAt bounds the listener's total wait per poll batch at about 15 s, well under max.poll.interval.
  - The generated EventsMapperImpl maps the extra `retriedAt` / `exhaustedAt` parameters, which I checked in target.
  - The unit test checks the exact waits: 0 s for C-16 and 6 s for C-17.
- Checked, no finding: T-20 reauthorizePayment :140-162, against ZDL:160-168 and ZFL:138-146.
  - The guards match the model. It saves AUTHORIZATION_REQUESTED with attemptCount + 1 and commits before the processor call.
  - It shares the outcome switch with authorizePayment (callProcessorAndSaveOutcome), and the processor gets the incremented attemptCount, so pm_fail_once succeeds on attempt 2.
- Checked, no finding: T-21 PaymentFailedConsumerService and PaymentRetriedConsumerService.
  - Both messages of each channel map orderId and attemptCount through MapStruct, then call retryPayment / reauthorizePayment (ZDL:165, 176, 98-108).
  - The tests use both message types and fixed C-16 values.
- Checked, no finding: T-22 end to end over real Kafka.
  - OrderCreated(pm_fail_always) produces Failed a1, Retried a1, Failed a2, Retried a2, Failed a3, RetryExhausted, and nothing else, after a measured ~20 s backoff.
  - pm_fail_once produces Failed, Retried, Authorized, with the Payment at attemptCount 2.
  - Outside this repo: releaseStock through end orderCancelled (ZFL:340-344).
- Nothing out of scope: no new field, endpoint, event, status or topic.

STANDARDS AXIS
- NOTE  PaymentsProcessingServiceImpl.java:57-66 adds a test hook with a public setter on the singleton bean, `setSleeper(Consumer<Duration>)`.
  - T-21 (8501982) widened it from package-private to public, so the adapters.events tests can reach it.
  - The convention next to it is the injected Clock: the in-memory config passes TestClock.FIXED.
  - A sleeper passed through the constructor the same way would avoid a public mutator in production code. Nothing breaks today.
  - Minor layout drift: the field is declared after the constructor (:58), and MAX_ATTEMPTS / RETRY_BACKOFF sit between methods (:164-167).
- NOTE  PaymentsProcessingServiceImpl.java:60-62 `Clock clock()`, carried from S0-S3.
- Conventions otherwise followed:
  - AFK worker commits are unsigned, with AI-Agent / AI-Model / AI-Orchestrator trailers. The merge commits have none, as in earlier slices.
  - Code is only in generated-once files: service impl, EventsMapper, the adapters.events consumers with a `<X>ConsumerMapper` (the OrderCreated / FulfillmentScheduled pattern), and tests.
  - The flow tests are `*IntegrationTest` and run with Failsafe.
  - Tests use in-memory repositories and the stand-in processor, not Mockito.
  - No bare `Instant.now()`.
  - Spotless is clean.

PROPOSALS: none.
NOT CHECKED:
- The other contexts' side of the S4 path: releaseStock, cancelOrder and sendOrderCancelledNotification after PaymentRetryExhausted.
- pm_timeout end to end over Kafka: it is only covered by the unit test, with a 500 ms timeout.
- Head-of-line blocking of payment-failed-event-v1 under load. It is bounded at about 15 s per batch by reading the code, not measured.
- Whether the board's `done:` model names match the person's records.
