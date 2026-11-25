-- Scenario test data. Regenerate with $zenwave-test-scenarios (place-order-flow).
-- DDL derived from entities + modulith data.sql, not exported (Docker busy, 2026-11-25); validated in step 7.
-- Models: payments-processing service-all 0.2.0. Only aggregate: Payment (table payment, sequence payment_seq, increment 50).
-- Fixed test "now" (fixed Clock, still to be added by a board task): 2026-10-14T10:00:00Z.
-- JSON mirrors: src/test/resources/data/jpa/Payment/<k>.json, k = row position, id = 1 + (k-1)*50.
-- Business ids shared with orders-checkout (C-01..C-10), catalog-inventory (C-11..C-14) and fulfillment-shipping (C-15..C-17) fixtures: same chain id = same ORD-0nn. C-18..C-21 are payments only.
--
-- SCENARIO MATRIX: chain | business ids | Payment state (upstream state implied) | used by
-- C-01 | ORD-001, CUST-001, PM-001, 49.99 | CAPTURED a1 (order CONFIRMED, flow completed) | capturePayment redelivery no-op; getPaymentStatus STARTED; repository tests findById(1)
-- C-02 | ORD-002, PM-002, 99.97 | AUTHORIZED a1 (order CREATED, stock RESERVED, confirmation pending) | authorizePayment redelivery no-op (creation only); voidPayment from AUTHORIZED (OrderConfirmationRejected, P-01)
-- C-03 | ORD-003, 29.99 | DECLINED a1 (stock RELEASED PAYMENT_DECLINED, order cancel pending) | outcome declined; reauthorize/retry/void no-op on DECLINED
-- C-04 | ORD-004, 29.99 | VOIDED a1, was AUTHORIZED (order CONFIRMED, fulfillment failed, stock RELEASED PAYMENT_VOIDED) | void after FulfillmentFailed (result state, P-01); voidPayment / capturePayment redelivery no-op on VOIDED
-- C-05 | ORD-005, 29.99 | AUTHORIZED a1, late (stock RELEASED RESERVATION_EXPIRED, order CREATED) | voidPayment from AUTHORIZED on OrderConfirmationRejected STOCK_NOT_HELD (P-01)
-- C-06 | ORD-006, 29.99 | AUTHORIZED a1, late (order CANCELLED RESERVATION_EXPIRED) | voidPayment from AUTHORIZED on OrderConfirmationRejected ORDER_CANCELLED (P-01); outcome cancelled by expiry
-- C-07 | ORD-007, 29.99 | DECLINED a1 (order CANCELLED PAYMENT_DECLINED) | outcome cancelled by decline
-- C-08 | ORD-008, 29.99 | RETRY_EXHAUSTED a3 (order CANCELLED PAYMENT_RETRY_EXHAUSTED) | outcome cancelled by retries exhausted; retryPayment redelivery no-op
-- C-09 | ORD-009, 29.99 | VOIDED a1, was CAPTURE_FAILED (order CANCELLED PAYMENT_VOIDED) | outcome cancelled after capture failure; voidPayment redelivery no-op
-- C-10 | ORD-010, 29.99 | VOID_FAILED a1 (order CANCELLED PAYMENT_VOID_FAILED) | outcome void refused; voidPayment redelivery no-op on VOID_FAILED
-- C-11 | ORD-011 | no Payment row (catalog: reservation past deadline, no order fixture) | getPaymentStatus NO_PAYMENT; retryPayment / reauthorizePayment / voidPayment no-op when no Payment (P-12 A)
-- C-12 | ORD-012, SKU-003x1, 39.99 | AUTHORIZED a1 (stock CONFIRMED, fulfillment pending); off-chain (no order fixture) | capturePayment from AUTHORIZED (CAPTURED / CAPTURE_FAILED stub-driven)
-- C-13, C-14 | SKU-004, SKU-005 | no Payment (stock unavailable: no order, no payment) | -
-- C-15 | ORD-015, SKU-002x2 + SKU-003x1, 99.97 | AUTHORIZED a1 (fulfillment FUL-015 SCHEDULED, capture pending); off-chain | capturePayment from AUTHORIZED (multiplicity on the fulfillment side)
-- C-16 | ORD-016, 29.99 | CAPTURE_FAILED a1 (fulfillment FUL-016 SCHEDULED, capture failed, cancel in progress); off-chain | voidPayment from CAPTURE_FAILED (PaymentCaptureFailed); capturePayment redelivery no-op
-- C-17 | ORD-017, 29.99 | CAPTURED a1 (fulfillment FUL-017 READY_TO_SHIP, paymentCapturedAt 09:55:47); off-chain | capturePayment redelivery no-op on CAPTURED; getPaymentStatus STARTED
-- C-18 | ORD-018, 29.99 | AUTHORIZATION_REQUESTED a1 (call in flight); off-chain | getPaymentStatus STARTED (status AUTHORIZATION_REQUESTED); voidPayment no-op; capturePayment not from this state
-- C-19 | ORD-019, 29.99 | FAILED a1; off-chain | retryPayment below limit -> PaymentRetried (backoff 5 s); reauthorizePayment from FAILED (input attemptCount 1 -> a2); stale input (attemptCount 2) no-op
-- C-20 | ORD-020, 29.99 | FAILED a2; off-chain | retryPayment below limit -> PaymentRetried (backoff 15 s); reauthorizePayment a2 -> a3
-- C-21 | ORD-021, 29.99 | FAILED a3 (processor timeout); off-chain | retryPayment at the limit -> RETRY_EXHAUSTED; reauthorizePayment stale input no-op
--
-- COVERAGE (need -> chain, or why no row)
-- Lifecycle: authorizePayment (creation) -> no row needed (new ORD-0nn, e.g. ORD-099); reauthorizePayment from FAILED -> C-19, C-20;
--   retryPayment from FAILED -> C-19, C-20, C-21; capturePayment from AUTHORIZED -> C-12, C-15 (and C-02); voidPayment from AUTHORIZED -> C-02, C-05, C-06,
--   from CAPTURE_FAILED -> C-16
-- Branches: authorize / reauthorize AUTHORIZED | DECLINED | FAILED (incl. 30 s timeout) -> stub-driven (processor decides), start rows as above;
--   retry PaymentRetried -> C-19/C-20, PaymentRetryExhausted -> C-21; capture CAPTURED | CAPTURE_FAILED -> stub-driven from C-12;
--   void PaymentVoided | PaymentVoidFailed -> stub-driven from C-02/C-16
-- Outcomes (result states): completed C-01; declined C-03/C-07; retries exhausted C-08; voided after fulfillment failure C-04 / capture failure C-09;
--   void refused C-10; late authorization (expiry) C-05/C-06; stockGone C-13/C-14 no row; notificationFailed -> notifications context
-- Multiplicity: one Payment per order (orderId unique) -> no one-to-many
-- Time: no deadline field on Payment; retry backoff 5 s / 15 s and the 30 s processor timeout are timers in the service (board task), start rows C-19/C-20/C-21;
--   the 10-minute reservation timer is catalog-inventory's, it reads getPaymentStatus (C-11 NO_PAYMENT, C-18 STARTED)
-- Redelivery: authorizePayment on existing -> C-02 (any state); reauthorize/retry stale or wrong state -> C-03, C-08, C-21; capture on CAPTURED C-01/C-17,
--   CAPTURE_FAILED C-16, VOIDED C-04; void on VOIDED C-09, VOID_FAILED C-10, AUTHORIZATION_REQUESTED C-18; no Payment -> C-11 (ORD-011)
-- Read side (POST /api/payments/status): NO_PAYMENT -> ORD-011; STARTED -> any row (AUTHORIZATION_REQUESTED C-18, AUTHORIZED C-02, CAPTURED C-01)
--
-- ASSUMPTIONS: assumption | value used | open question
-- A-01 status stored as the enum name (entity Enumerated(STRING)); the modulith stores the enum id | 'CAPTURED', ... | validated in step 7
-- A-02 providerReference format, set when authorized and kept | STANDIN-ORD-0nn | processor stand-in contract (HITL)
-- A-03 failureReason strings follow the modulith stand-in reasons; on void it holds VoidPaymentInput.reason (C-04/C-10 carrier_unavailable) or keeps the capture reason (C-09) | card_declined, processor_unavailable, processor_timeout, capture_rejected, carrier_unavailable | reason codes not in the model (HITL)
-- A-04 attemptCount counts authorization attempts (authorize = 1, each reauthorize +1); limit 3 | a1..a3 | ZDL comments (P-11 A); limit config name not in the model
-- A-05 failedAt = time of the last failed attempt; declinedAt / authorizedAt / capturedAt / voidedAt / voidFailedAt set by the transition that reaches them; voiding keeps authorizedAt | as listed | -
-- A-06 amount = orders' Order.totalAmount (C-01 49.99, C-02 99.97, else SKU-002 29.99); ORD-012 SKU-003 39.99; currency EUR | as listed | -
-- A-07 customer/payment-method ids and emails follow orders: CUST-0nn, PM-0nn, customer0nnexample.com (ORD-001: customerexample.com) | as listed | -
-- A-08 late authorizations C-05/C-06 authorized 30 s after the timer released the stock (catalog 09:50:05 / 09:50:06) | 09:50:35 / 09:50:36 | -
-- A-09 off-chain rows C-12, C-15..C-21 have no order fixture: payments never reads orders | - | -
-- Timestamps: ORD-001 keeps 2026-09-26 (authorized 13:03:00 before confirmation 13:05:00, captured 13:06:30); others 2026-10-14, each before the
--   orders / catalog timestamps it causes (e.g. C-03 declined 09:51:02, stock released 09:51:03); C-15..C-17 authorized 09:54:45..47, 30 s before fulfillment scheduled them (09:55:15..17), C-17 captured 09:55:46; FAILED C-19..C-21 failed 09:59:50..52

-- Payments Processing: payment (one per order)
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-001', 'CUST-001', 'PM-001', 'customer@example.com', 49.99, 'EUR', 'CAPTURED', 'STANDIN-ORD-001', 1, '2026-09-26T13:03:00Z', NULL, NULL, '2026-09-26T13:06:30Z', NULL, NULL, NULL); -- C-01
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-002', 'CUST-002', 'PM-002', 'customer002@example.com', 99.97, 'EUR', 'AUTHORIZED', 'STANDIN-ORD-002', 1, '2026-10-14T09:50:32Z', NULL, NULL, NULL, NULL, NULL, NULL); -- C-02
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-003', 'CUST-003', 'PM-003', 'customer003@example.com', 29.99, 'EUR', 'DECLINED', NULL, 1, NULL, '2026-10-14T09:51:02Z', NULL, NULL, NULL, NULL, 'card_declined'); -- C-03
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-004', 'CUST-004', 'PM-004', 'customer004@example.com', 29.99, 'EUR', 'VOIDED', 'STANDIN-ORD-004', 1, '2026-10-14T09:50:24Z', NULL, NULL, NULL, '2026-10-14T09:51:33Z', NULL, 'carrier_unavailable'); -- C-04
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-005', 'CUST-005', 'PM-005', 'customer005@example.com', 29.99, 'EUR', 'AUTHORIZED', 'STANDIN-ORD-005', 1, '2026-10-14T09:50:35Z', NULL, NULL, NULL, NULL, NULL, NULL); -- C-05
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-006', 'CUST-006', 'PM-006', 'customer006@example.com', 29.99, 'EUR', 'AUTHORIZED', 'STANDIN-ORD-006', 1, '2026-10-14T09:50:36Z', NULL, NULL, NULL, NULL, NULL, NULL); -- C-06
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-007', 'CUST-007', 'PM-007', 'customer007@example.com', 29.99, 'EUR', 'DECLINED', NULL, 1, NULL, '2026-10-14T09:51:05Z', NULL, NULL, NULL, NULL, 'card_declined'); -- C-07
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-008', 'CUST-008', 'PM-008', 'customer008@example.com', 29.99, 'EUR', 'RETRY_EXHAUSTED', NULL, 3, NULL, NULL, '2026-10-14T09:51:26Z', NULL, NULL, NULL, 'processor_unavailable'); -- C-08
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-009', 'CUST-009', 'PM-009', 'customer009@example.com', 29.99, 'EUR', 'VOIDED', 'STANDIN-ORD-009', 1, '2026-10-14T09:50:29Z', NULL, NULL, NULL, '2026-10-14T09:51:37Z', NULL, 'capture_rejected'); -- C-09
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-010', 'CUST-010', 'PM-010', 'customer010@example.com', 29.99, 'EUR', 'VOID_FAILED', 'STANDIN-ORD-010', 1, '2026-10-14T09:50:30Z', NULL, NULL, NULL, NULL, '2026-10-14T09:51:38Z', 'carrier_unavailable'); -- C-10
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-012', 'CUST-012', 'PM-012', 'customer012@example.com', 39.99, 'EUR', 'AUTHORIZED', 'STANDIN-ORD-012', 1, '2026-10-14T09:50:32Z', NULL, NULL, NULL, NULL, NULL, NULL); -- C-12
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-015', 'CUST-015', 'PM-015', 'customer015@example.com', 99.97, 'EUR', 'AUTHORIZED', 'STANDIN-ORD-015', 1, '2026-10-14T09:54:45Z', NULL, NULL, NULL, NULL, NULL, NULL); -- C-15
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-016', 'CUST-016', 'PM-016', 'customer016@example.com', 29.99, 'EUR', 'CAPTURE_FAILED', 'STANDIN-ORD-016', 1, '2026-10-14T09:54:46Z', NULL, NULL, NULL, NULL, NULL, 'capture_rejected'); -- C-16
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-017', 'CUST-017', 'PM-017', 'customer017@example.com', 29.99, 'EUR', 'CAPTURED', 'STANDIN-ORD-017', 1, '2026-10-14T09:54:47Z', NULL, NULL, '2026-10-14T09:55:46Z', NULL, NULL, NULL); -- C-17
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-018', 'CUST-018', 'PM-018', 'customer018@example.com', 29.99, 'EUR', 'AUTHORIZATION_REQUESTED', NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL); -- C-18
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-019', 'CUST-019', 'PM-019', 'customer019@example.com', 29.99, 'EUR', 'FAILED', NULL, 1, NULL, NULL, '2026-10-14T09:59:50Z', NULL, NULL, NULL, 'processor_unavailable'); -- C-19
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-020', 'CUST-020', 'PM-020', 'customer020@example.com', 29.99, 'EUR', 'FAILED', NULL, 2, NULL, NULL, '2026-10-14T09:59:51Z', NULL, NULL, NULL, 'processor_unavailable'); -- C-20
INSERT INTO payment (id, version, order_id, customer_id, payment_method_id, customer_email, amount, currency, status, provider_reference, attempt_count, authorized_at, declined_at, failed_at, captured_at, voided_at, void_failed_at, failure_reason)
VALUES (nextval('payment_seq'), 0, 'ORD-021', 'CUST-021', 'PM-021', 'customer021@example.com', 29.99, 'EUR', 'FAILED', NULL, 3, NULL, NULL, '2026-10-14T09:59:52Z', NULL, NULL, NULL, 'processor_timeout'); -- C-21
