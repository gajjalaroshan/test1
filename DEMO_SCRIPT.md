# Log Sentinel — demo script

## Setup (30s)

1. `copy .env.example .env` — set `GEMINI_API_KEY` or `OPENAI_API_KEY` + `LLM_PROVIDER=openai`; confirm `LLM_MAX_TOKENS=8192` if you change defaults.
2. `docker compose up --build -d` (Java 21 build + tests in image).
3. Open http://localhost:8080

## Story

"We simulate API failures without sprinkling log statements in business code. A global handler writes structured `API_FAILURE` lines to a **line-rotated** log file. Analyze reads one or more rolled files, applies deterministic rules, then two LLM agents — a dev runbook and a product-owner brief — both grounded in the same Java analysis."

## Steps

1. **All-in-one rules** — *AllExceptionTrigger* → `POST /api/v1/traffic/demo-flagging`. Burst (`cust-burst-1`, 4 stack types in 3s) + VIP order (`ord-vip-1`, ₹2L).
2. **Optional pieces** — *StandardExceptionsTrigger* (batch only), *AmountExceptionTrigger* (high value), *SingleTraceTripleError* (one trace, 3 errors, explicit customer on first line only).
3. **Log files** — section 2 loads `GET /api/v1/logs/files`; leave all checkboxes checked for multi-file analyze after rotation.
4. **Analyze (Java only)** — show `ruleFlags.flaggedCustomerIds`, `flaggedCustomers[].windows`, `errorBreakdown.byCustomerId` vs `byCustomer.*.byTraceId`.
5. **Analyze (Java + 2 agents)** — Remediation Planner (evidence + playbooks), Executive Report (business language, no stacks). On 429, use Java-only.

## Talking points

- **Silent failures**: `TrafficSimulator` only throws; logging is centralized and **only under `/api/v1/`**.
- **Explicit vs trace**: customer **totals** ignore lines without `customerId=`; trace grouping still shows co-located errors.
- **Deterministic rules** before LLM — thresholds in `application.yml` / env.
- **Two agents, one Java truth**: orchestrator does not feed Agent 1 markdown into Agent 2.
- **Rotation at 5000 lines** — mention `LOG_MAX_LINES_PER_FILE` when demos run long.
