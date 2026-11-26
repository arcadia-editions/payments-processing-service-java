You are the reviewer of slice S0 of the board C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/work/integration/docs/tasks.md. Work in C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/work/review-S0, a read-only checkout at c2db21d.
Follow the skill zenwave-review-slice: C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.claude/skills/zenwave-review-slice/SKILL.md.
Range: 71f0868..c2db21d. Tasks of the slice were implemented by: grok grok-4.7 (T-01, T-03, T-04, T-07), claude-opus (T-06); T-02, T-05, T-08 were HITL decisions (ADRs 0001-0003). Judge the code, not their reports.
Run the full suite once: export JAVA_HOME=~/.sdkman/candidates/java/current; mvn -q verify -Dmaven.test.failure.ignore=true (about 4 min, needs Docker).
Known and expected at this point: PaymentsProcessingApiIntegrationTest.testGetPaymentStatus_201 fails (fixed by T-14, slice S2). Anything else failing is a finding.
Docker: ports 5432/9092/8081/2181 are fixed and shared with another project's AFK run. Before the suite, check docker ps; if they are busy, wait (poll every 30 s) until free, then run. Never stop containers you did not start.
Standards to check: AGENTS.md, the project rules of C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/docs/zenwave-sdd-overlay.md, the conventions of the code next to each change.
This is a demo: report as findings only what is wrong or would break later slices, not style preferences.
Change nothing. Write your report, in the shape of the skill, to C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/handoff/place-order-flow/reports/review-S0.md, and end your final message with it.
