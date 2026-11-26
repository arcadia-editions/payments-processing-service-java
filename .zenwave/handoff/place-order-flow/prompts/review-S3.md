You are the reviewer of slice S3 of the board C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/work/integration/docs/tasks.md. Work in C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/work/review-S3, a read-only checkout at dbd108e.
Follow the skill zenwave-review-slice: C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.claude/skills/zenwave-review-slice/SKILL.md.
Range: 56e69bb..dbd108e. Tasks of the slice were implemented by: claude-sonnet (T-16, T-17). Judge the code, not their reports.
Run the full suite once: export JAVA_HOME=~/.sdkman/candidates/java/current; mvn -q verify -Dmaven.test.failure.ignore=true.
Known and expected at this point: nothing fails. The failed authorization (S4) and capture-failed (S5) branches throw on purpose, untested yet.
Previous review: C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/handoff/place-order-flow/reports/review-S2.md (PASS, notes only).
Docker: ports 5432/9092/8081/2181 are fixed and shared with another project's AFK run, which grabs them often. Before the suite, check docker ps and wait until they have been free for 30 s in a row (poll every 10 s). If the suite fails with "Bind for 0.0.0.0:<port> failed", that is the other project: wait again and rerun once; say so. Never stop containers you did not start.
Standards to check: AGENTS.md, the project rules of C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/docs/zenwave-sdd-overlay.md, the conventions of the code next to each change.
This is a demo: report as findings only what is wrong or would break later slices, not style preferences.
Change nothing. Write your report, in the shape of the skill, to C:/Users/ivangsa/workspace/arcadia-editions/payments-processing-service-java/.zenwave/handoff/place-order-flow/reports/review-S3.md, and end your final message with it.
