# Smart Converge X engineering rules

1. Read `docs/广告平台设计/README.md`, `status/CURRENT.md`, accepted ADRs and the task-specific contract before changing code.
2. Treat `docs/广告平台设计/adr/0002-技术栈与仓库结构.md` as the current technology decision. Create an ADR before changing a cross-cutting decision.
3. Never commit credentials, tokens, certificates, production URLs, personal data or captured payloads. Use local `.env` files and environment variables.
4. Implement one vertically testable slice at a time. Every business change includes migration, contract, authorization, audit/metrics where relevant, tests and documentation updates.
5. An API must enforce subject ownership on the server. UI route/menu permissions are not authorization.
6. Do not implement advertising business features until the relevant open product/finance/compliance decision is accepted.
7. Before handoff run the affected build, tests, contract validation and migration validation; record limitations honestly.
