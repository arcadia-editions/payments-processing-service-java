# 0002. A stand-in payment processor, driven by paymentMethodId

Status: accepted (2026-11-25)

The ZDL names a payment processor but no API, and this is a demo. The PaymentProcessor port (T-06) gets one
adapter: an in-process stand-in, the same contract as the modulith's `StandInPaymentProvider`. `paymentMethodId`
triggers the outcome: `pm_decline` declines (`card_declined`), `pm_fail_once` fails the first attempt and
`pm_fail_always` every attempt (`processor_unavailable`; a call over 30 s is `processor_timeout`), `pm_capture_fail`
refuses the capture (`capture_rejected`), `pm_void_fail` refuses the void; any other value succeeds.
`providerReference` is `STANDIN-<orderId>` (`STANDIN-CAPFAIL-<orderId>` for `pm_capture_fail`). On void,
`failureReason` keeps `VoidPaymentInput.reason`. capturePayment with no Payment or an unexpected state throws (T-08).
