# Gate place-order-flow, round 2 (baseline-6)

2026-11-26. Reviewed by claude on behalf of the person (delegated HITL; demo project: "it should run, do not
overcomplicate": simplest option, recorded here). Scope: the delta since baseline-5 only (round 1 = S0..S5, done,
archived in `docs/tasks/place-order-flow-5.md`). Round 2 = S6, S7: T-27, T-28, T-30.

## Delta

- ZFL: unchanged since `baseline/place-order-flow-5` (architecture repo: only `zenwave-architecture.yml` manifest
  commits since; an unrelated uncommitted manifest change is not part of the flow).
- ZDL: content unchanged 0.2.0 -> 0.2.2 (version stamp only). voidPayment already had the three `@asyncapi`
  triggers (`domain-model.zdl:185-194`); 0.2.0 projected only the own one.
- `asyncapi-client.yml` 0.2.1/0.2.2: new channels `fulfillment-failed-event-v1`
  (`fulfillment-shipping.fulfillment-failed.event.avro.v1`) and `order-confirmation-rejected-event-v1`
  (`orders-checkout.order-confirmation-rejected.event.avro.v1`), receive ops `onFulfillmentFailedEventV1`,
  `onOrderConfirmationRejectedEventV1`, retry/DLQ bindings like the other consumed channels.
- `asyncapi.yml` 0.2.2: self-consumed channels carry only the event (Reauthorize/Retry/VoidPaymentInputCommand
  messages dropped); receive ops `doPaymentRetried`, `doPaymentFailed`, `doPaymentCaptureFailed` (consumers already
  adapted in 09980bd).
- Packaged copy (`service-all-0.2.2.jar`) = working copy of `../payments-processing-api` except the version stamps
  (the local clone is behind origin by the release commits).

## Item 4: grill (frontier empty)

| # | Question | Answer (source) |
|---|---|---|
| 1 | What maps to VoidPaymentInput? | orderId + reason of each event; other fields (customerId, fulfillmentId, failedAt, rejectedAt) have no target (ZDL 141-144) |
| 2 | Where does the reason go? | Payment.failureReason (ADR 0002, A-03): carrier_unavailable (C-04), STOCK_NOT_HELD (C-05), ORDER_CANCELLED (C-06) |
| 3 | Redelivery / duplicate triggers? | voidPayment no-op unless AUTHORIZED or CAPTURE_FAILED (T-24, done): covers redelivery and a second trigger |
| 4 | Can two triggers race on one order? | No: OrderConfirmationRejected means never confirmed, so no FulfillmentScheduled / FulfillmentFailed / PaymentCaptureFailed follows |
| 5 | Is the payment AUTHORIZED when OrderConfirmationRejected arrives? | Yes: it is caused by PaymentAuthorized, sent after the AUTHORIZED commit (T-09) |
| 6 | Void refused? | PaymentVoidFailed, left to ops (ZFL 237-252); stand-in refuses on pm_void_fail (ADR 0002) |
| 7 | Topics? | consumers need app.kafka.topics.<channel>.topic (ADR 0003): two new entries, in T-27 |
| 8 | Error topics / DLQ bindings of the new channels? | as the existing consumed channels: not wired for the demo (no change) |

Nothing new for the models: no proposal.

## Item 5: projections

Read: `asyncapi-client.yml` 0.2.2 (two new channels and receive ops, addresses match the producers' `asyncapi.yml`
in `../fulfillment-shipping-api`, `../orders-checkout-api`), `asyncapi.yml` 0.2.2 (event-only self-consumed
channels), generated `IFulfillmentFailedEventV1ConsumerService`, `IOrderConfirmationRejectedEventV1ConsumerService`,
the Avro `FulfillmentFailed` (fulfillmentId, orderId, customerId, reason, failedAt) and `OrderConfirmationRejected`
(orderId, customerId, reason ORDER_CANCELLED | STOCK_NOT_HELD, rejectedAt). State machine unchanged
(`from: [AUTHORIZED, CAPTURE_FAILED] to: [VOIDED, VOID_FAILED]`).

## Item 13: test data

Chains exist in the `data.sql` header and as JSON fixtures: C-02 (`2.json`, AUTHORIZED), C-04 (`4.json`, VOIDED after
FulfillmentFailed, carrier_unavailable), C-05 (`5.json`, late AUTHORIZED, STOCK_NOT_HELD), C-06 (`6.json`, late
AUTHORIZED, ORDER_CANCELLED). Coverage matrix already lists them (voidPayment from AUTHORIZED -> C-02, C-05, C-06).
No data change.

## Decisions (simplest option)

- D-1 T-29 merged into T-27: the Spring context needs both consumer beans and both topics, so the S6 flow test (T-28)
  could not pass before the S7 consumer, and a task cannot be blocked by a later slice. T-27 does both consumers and
  lists every Spring context IT in `tests:` (gate item 12). T-29 is not reused.
- D-2 flow tests T-28, T-30 use a fresh orderId per test (OrderCreated authorized by the stand-in, then the trigger),
  as T-26 does; C-04 / C-05 / C-06 are the result shapes. Not ORD-005 / ORD-006 of `data.sql`: the ITs share one
  database per JVM.
- D-3 consumer unit tests (T-27) use the in-memory fixtures 2, 4, 5, 6.json.

## Gate run (zenwave-gate-check, items 1-20)

| # | Item | Result |
|---|---|---|
| 1 | lint | ✅ `domain-model.zdl: OK` (--strict) |
| 2 | ZFL/ZDL agree | ✅ 5 `@zdl` paths resolve; voidPayment, FulfillmentFailed, OrderConfirmationRejected, PaymentVoided/VoidFailed exist; `@compensates` name commands |
| 3 | no open proposals | ✅ P-01 decided (A) |
| 4 | grill | 👤 done by claude on behalf of the person (above), frontier empty |
| 5 | projections | 👤 done by claude on behalf of the person (above) |
| 6 | generated up to date | ✅ packaged 0.2.2 = working copy (version stamps only); regeneration in a throwaway clone: only blank lines in 2 enums and the always-rewritten generated `PaymentsProcessingApiIntegrationTest` (fixed in round 1, kept) |
| 7 | builds | ✅ `mvn -q -DskipTests clean compile` |
| 8 | test data loads | ✅ no ScriptStatementFailedException / json error |
| 9 | covers the board | ✅ T-27 fixtures 2, 4, 5, 6.json; T-28/T-30 result shapes C-04, C-05, C-06 |
| 10 | overlay test data row | ✅ |
| 11 | suite duration | ✅ 245 s (`mvn verify -Dmaven.test.failure.ignore=true`), overlay updated |
| 12 | known failing | ✅ the 17 Failsafe ITs (context load, missing consumer beans) in T-27 `tests:` and the overlay; Surefire 52/52 |
| 13 | test data approved | 👤 done by claude on behalf of the person (above) |
| 14 | board DAG | ✅ `check-board.sh`: board OK, 33 tasks, 8 slices (2 warnings on done T-32/T-33, history) |
| 15 | model lines | ✅ every `model:` file exists, ranges within the file |
| 16 | tracer bullet | ✅ S1 (done in round 1); round 2 adds no new success path |
| 17 | slices approved | 👤 by claude on behalf of the person: S6, S7 vertical (consumer + flow test) |
| 18 | AFK setup | ✅ `.zenwave/routing.md` committed; grok, codex on PATH (codex disabled) |
| 19 | AGENTS.md, skills | ✅ overlay linked; skills in .claude, .agents, .grok |
| 20 | clean tree | ✅ this repo after commit; ../payments-processing-api clean (behind origin by the 0.2.2 release commits); ../arcadia-editions-architecture has the unrelated uncommitted `zenwave-architecture.yml` (not the ZFL, left as is) |
