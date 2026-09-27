# Log Sentinel — file-based log triage POC

Spring Boot 3.5 + Spring AI **dual-agent** pipeline over **line-rotated log files**. Business code does not call `log.error`; failures are recorded by a **global API exception handler** with structured `API_FAILURE` lines (only for paths under `/api/v1/`).

## Architecture

| API | Purpose |
|-----|---------|
| `POST /api/v1/traffic/simulate-batch` | Loop scenarios in one HTTP request → multiple `API_FAILURE` lines (same request `traceId` when caught in-controller) |
| `POST /api/v1/traffic/demo-flagging` | Canned burst + high-value VIP order (shared trace via MDC) |
| `POST /api/v1/traffic/simulate-single-trace-triple-error` | One request, three failures on one `traceId` (only first line has explicit `customerId`) |
| `GET /api/v1/logs/files` | List active + rolled files (`path`, `name`, `size`, `lastModified`) |
| `POST /api/v1/logs/analyze` | Parse one or more files (`logFilePath` or `logFilePaths`), rules, optional **2 LLM agents** |

**Log rotation** — `LineBasedRollingFileAppender` + `LineCountTriggeringPolicy`: roll after **5000 physical newlines** in the active file (stack lines count). Rolled names: `log-sentinel-app.log.1` … `.N` (plain text). Env: `LOG_MAX_LINES_PER_FILE`, `LOG_MAX_ROLLED_FILES` → `logsentinel.log-rotation.*` in `application.yml`.

**Java rules (deterministic)**

- Flag **order** when `amountInr` **> 150,000** (1.5L INR) on an ERROR `API_FAILURE`.
- Flag **customer** when **more than 3 distinct stack signatures** occur within **3 seconds** (strict `> 3` → needs **4** signatures). `flaggedCustomers` has **one entry per `customerId`** with `windows[]` (`distinctStacksInWindow`, `windowStart`, `windowEnd`).
- **Customer totals** (`errorBreakdown.byCustomerId`, burst rules) count only lines with **explicit** `customerId=` in the log message. **`TraceContextResolver`** still copies `customerId`/`orderId` onto co-traced lines for grouping and trace slices (`byCustomer.*.byTraceId`).

**Agents** (Java owns counts/splits; agents own narrative)

1. **Remediation Planner** — dev lead / on-call runbook from `LogAnalysisResult.toRemediationPrompt()` (exceptions, traces, stacks, flags).
2. **Executive Report** — product-owner brief from `LogAnalysisResult.toExecutivePrompt()` (failure volumes, flagged customers/orders, amounts — no traces/stacks/exception types). Does not receive Agent 1 output.

## Prerequisites

- Docker Desktop (or Docker Engine + Compose v2)
- Optional: local Ollama, `GEMINI_API_KEY`, or `OPENAI_API_KEY` in `.env` for LLM steps (`useLlm: true`)

Build and test in Docker (**Java 21**, `maven:3.9.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`). Host JDK can be older.

## Quick start

```powershell
copy .env.example .env
# edit .env — API keys, LOG_*, LLM_* (see .env.example)
docker compose up --build -d
```

Open [http://localhost:8080](http://localhost:8080) — traffic buttons, **log-file checkboxes** (multi-file analyze), agent report panels.

```powershell
curl -s -X POST http://localhost:8080/api/v1/traffic/demo-flagging

curl -s http://localhost:8080/api/v1/logs/files

curl -s -X POST http://localhost:8080/api/v1/logs/analyze -H "Content-Type: application/json" -d "{\"useLlm\":true}"

curl -s -X POST http://localhost:8080/api/v1/logs/analyze -H "Content-Type: application/json" -d "{\"useLlm\":false}"
```

**Expected IDs after `demo-flagging`:** `ruleFlags.flaggedCustomerIds` → `["cust-burst-1"]`, `ruleFlags.flaggedOrderIds` → `["ord-vip-1"]`.

**Readable agent text (Git Bash / WSL with jq):**

```bash
curl -s -X POST http://localhost:8080/api/v1/logs/analyze -H "Content-Type: application/json" -d '{"useLlm":true}' \
  | jq -r '.agents.remediationPlanMarkdown'

curl -s -X POST http://localhost:8080/api/v1/logs/analyze -H "Content-Type: application/json" -d '{"useLlm":true}' \
  | jq -r '.agents.executiveReportMarkdown'
```

Default log path: `logs/log-sentinel-app.log` (container: `/app/logs` via compose volume).

## Configuration

| Variable | Default |
|----------|---------|
| `LOG_FILE_PATH` | `logs/log-sentinel-app.log` |
| `LOG_MAX_LINES_PER_FILE` | `5000` |
| `LOG_MAX_ROLLED_FILES` | `3` |
| `HIGH_VALUE_AMOUNT_INR` | `150000` |
| `CUSTOMER_BURST_WINDOW_MS` | `3000` |
| `CUSTOMER_DISTINCT_STACK_THRESHOLD` | `3` |
| `LLM_PROVIDER` | `ollama` |
| `LLM_MAX_TOKENS` | `8192` |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | see `.env.example` |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | see `.env.example` |
| `OPENAI_API_KEY` / `OPENAI_MODEL` | see `.env.example` |
| `MAX_TOOL_CALLS` / `RATE_LIMIT_RETRIES` | see `.env.example` |

Full template: [`.env.example`](.env.example).

See [APPLICATION_GUIDE.md](APPLICATION_GUIDE.md) and [DEMO_SCRIPT.md](DEMO_SCRIPT.md) for operations and presenter flow.

**Docs:** [docs/README.md](docs/README.md) — [architecture](docs/architecture/ARCHITECTURE.md) · [flows](docs/architecture/FLOW.md) · [packages](docs/architecture/PACKAGE_STRUCTURE.md)
