# Check: place-order-flow (payments side)

checkout: .zenwave/work/check, afk/place-order-flow at 6eb4908 (read-only; build run on a `git archive` copy in the scratchpad)
board: .zenwave/work/check/docs/tasks.md (baseline/place-order-flow-5)
mode: zenwave-tasks-from-code-and-models, check (steps 2 to 4). Full suite not run (S5 review ran it green at this commit).

## Verdict

**No gaps.** Every path of PlaceOrderFlow that crosses PaymentsProcessing is implemented and tested, except the
known, out-of-scope items below.

## Step 2: the models hold

- Lint `--strict`: OK, exit 0, for payments-processing-api, orders-checkout-api, catalog-inventory-api,
  fulfillment-shipping-api and notifications-consumer-api `domain-model.zdl`. No `zdl.data-lineage` warnings.
- Staleness: the packaged service-all 0.2.0 (`zenwave-scripts.zw` @import = pom `payments-processing-api.version` =
  0.2.0) matches the working copy: `domain-model.zdl`, `openapi.yml`, `asyncapi.yml`, `asyncapi-client.yml` all the same.
- ZFL/ZDL identities: all 5 `@zdl(...)` paths resolve. Every command in `systems` and every event, response and
  `@compensates` target in the flow is in its ZDL (authorizePayment, reauthorizePayment, retryPayment, capturePayment,
  voidPayment, getPaymentStatus; the 9 Payment* events; NoPaymentResult / PaymentStartedResult; reserveStock,
  startOrderCheckout, scheduleFulfillment).
- Approved inputs untouched: `place-order-flow.zfl` has no diff since baseline/place-order-flow-5 and the model repos
  are clean; `git diff baseline/place-order-flow-5..HEAD` touches nothing in `src/test/resources` or `zenwave-scripts.zw`.

## Step 3: paths (payments side)

| Path | Slice | Code | Flow test |
|---|---|---|---|
| OrderCreated > authorizePayment[authorized] > ... > FulfillmentScheduled > capturePayment > PaymentCaptured > end completed | S1 | done | PlaceOrderFlowCompletedIntegrationTest |
| ReservationExpired > expireReservation > call getPaymentStatus[NoPayment / PaymentStarted] | S2 | done | PlaceOrderFlowReservationTimerIntegrationTest |
| authorizePayment[declined] > PaymentDeclined > ... > end orderCancelled | S3 | done | PlaceOrderFlowCancelledIntegrationTest#declined |
| authorizePayment[failed] > retryPayment[retried] > reauthorizePayment ... > retryPayment[retry-exhausted] > end orderCancelled | S4 | done | PlaceOrderFlowRetryIntegrationTest, #retriesExhausted |
| FulfillmentScheduled > capturePayment[PaymentCaptureFailed] > voidPayment[voided / void-failed] > end orderCancelled | S5 | done | #captureFailed, #voidFailed |
| FulfillmentFailed > voidPayment | S6 | KNOWN | - |
| PaymentAuthorized (late) > confirmOrder > OrderConfirmationRejected > voidPayment | S7 | KNOWN | - |
| StartOrderCheckout > StockUnavailable > end stockGone; notificationFailed | - | no payments step | - |

## Step 4: commands against the model

- authorizePayment (ZFL 114-122, ZDL 149-158): creation only (existing Payment, any state: no-op, no event);
  AUTHORIZATION_REQUESTED, attemptCount 1, committed before the processor call (`NOT_SUPPORTED`); AUTHORIZED / DECLINED /
  FAILED each persist and emit one event; 30 s timeout in StandInPaymentProcessor becomes FAILED (processor_timeout).
- reauthorizePayment (ZFL 138-146, ZDL 160-168): no-op when no Payment, not FAILED or attemptCount differs;
  attemptCount + 1, commits before the call; same outcomes as authorize.
- retryPayment (ZFL 130-136, ZDL 170-178): same guards; never increments; 5 s / 15 s backoff from failedAt with the
  Clock; RETRY_EXHAUSTED at 3 with PaymentRetryExhausted (reason, exhaustedAt); otherwise PaymentRetried, stays FAILED.
- capturePayment (ZFL 196-200, ZDL 180-183): no-op on CAPTURED / CAPTURE_FAILED / VOIDED; other states throw (T-08);
  CAPTURED + capturedAt / CAPTURE_FAILED + failureReason (failedAt not stored, C-21); commits before sending (T-34).
- voidPayment (ZFL 245-252, ZDL 185-194): no-op unless AUTHORIZED or CAPTURE_FAILED; VOIDED + voidedAt or VOID_FAILED
  + voidFailedAt; failureReason holds VoidPaymentInput.reason (ADR 0002, A-03).
- getPaymentStatus (ZFL 293-297, ZDL 196-198, 110-133): read only, NO_PAYMENT with noPayment, or STARTED with started
  for any existing Payment; controller POST /status wired.
- Listeners: OrderCreated > authorizePayment, FulfillmentScheduled > capturePayment, own channels payment-failed >
  retryPayment, payment-retried > reauthorizePayment, payment-capture-failed > voidPayment (both messages of each own
  channel). Every generated `I*ConsumerService` has an implementation. Topics in application.yml match the AsyncAPI
  addresses of payments and of the producers (orders-checkout, fulfillment-shipping).
- Event mappers: every field of the 9 events is mapped (no MapStruct warning on EventsMapper or the consumer mappers).
- Project checks: `mvn -DskipTests clean test-compile` BUILD SUCCESS. Unmapped-target warnings only on the generated
  PaymentsProcessingServiceMapper (input to Payment: status, timestamps and the like are set by the service; not
  runtime-breaking). No `TODO` and no `UnsupportedOperationException` in src/main (the only hits are the generated
  in-memory test base InMemoryJpaRepository).

## KNOWN (out of scope for this loop, not gaps)

- T-27, T-28 (S6, FulfillmentFailed > voidPayment; ZDL 191, ZFL 246) and T-29, T-30 (S7, OrderConfirmationRejected >
  voidPayment; ZDL 190, ZFL 236-246): blocked by P-01 (decided A, next loop): `asyncapi-client.yml` 0.2.0 has no
  fulfillment-failed-event-v1 / order-confirmation-rejected-event-v1, so no consumer interface is generated.
- Transactional outbox (next loop): events are sent after the commit (or inside the transaction for voidPayment), so
  a failed send is never re-sent.
- Steps of the other bounded contexts (confirmOrder, releaseStock, expireReservation, cancelOrder, scheduleFulfillment,
  notifications...) belong to their own repositories.

## Notes (no action for the flow)

- `check-board.sh docs/tasks.md`: "line 255: a slice heading is `## S<n> <name>`" for the `## Next loop` section the
  orchestrator added, and warnings that T-32 and T-33 (fix tasks) have no `model:` lines. These are about how the board
  is formatted, not gaps in the flow.
