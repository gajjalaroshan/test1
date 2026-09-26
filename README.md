# Log Sentinel — file-based log triage POC

Spring Boot 3.5 + Spring AI **dual-agent** pipeline over a **rolling log file**. Business code does not call `log.error`; failures are recorded by a **global API exception handler** with structured `API_FAILURE` lines.

## Architecture

| API | Purpose |
|-----|---------|
| `POST /api/v1/traffic/simulate` | Single failing request → exception → log file |
| `POST /api/v1/traffic/simulate-batch` | Multiple scenarios for one customer (burst demo) |
| `POST /api/v1/logs/analyze` | Parse log file, group by `customerId` / `orderId`, apply rules, optional **2 LLM agents** |

**Java rules (deterministic)**

- Flag **order** when `amountInr` **> 150,000** (1.5L INR) on an ERROR event.
- Flag **customer** when **more than 3 distinct stack signatures** occur within **3 seconds** (not total error count — 28 repeats of the same 3 failure types still count as 3 signatures).

**Agents** (Java owns all counts/splits; agents own action)

1. **Remediation Planner** — on-call runbook: solutions per exception type, playbooks for flagged customer/order ids.
2. **Executive Report** — severity, impact summary, and **Solutions by issue type** for leadership + team leads.

## Prerequisites

- Docker Desktop (or Docker Engine + Compose v2)
- Optional: a local Ollama model, `GEMINI_API_KEY`, or `OPENAI_API_KEY` in `.env` for LLM steps (`useLlm: true`)

Builds and tests run in Docker (Java 21). Host JDK can be older.

## Quick start

```powershell
copy .env.example .env
# edit .env — set GEMINI_API_KEY or OPENAI_API_KEY if you want agents
docker compose up --build -d
```

Open [http://localhost:8080](http://localhost:8080) for buttons, or use curl:

```powershell
# Recommended: one call that triggers BOTH rules (customer burst + high-value order)
curl -s -X POST http://localhost:8080/api/v1/traffic/demo-flagging

# Analyze — rule flags at top level; agent markdown in agents.*Markdown (multi-line strings)
curl -s -X POST http://localhost:8080/api/v1/logs/analyze -H "Content-Type: application/json" -d "{\"useLlm\":true}"

# Java-only: see ruleFlags.flaggedCustomerIds and ruleFlags.flaggedOrderIds
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

On Windows without jq, use http://localhost:8080 — the UI renders agent reports with real line breaks (not escaped inside one JSON line).

Default log path: `logs/log-sentinel-app.log` (inside the container working directory).

## Configuration

| Variable | Default |
|----------|---------|
| `LOG_FILE_PATH` | `logs/log-sentinel-app.log` |
| `HIGH_VALUE_AMOUNT_INR` | `150000` |
| `CUSTOMER_BURST_WINDOW_MS` | `3000` |
| `CUSTOMER_DISTINCT_STACK_THRESHOLD` | `3` |
| `LLM_PROVIDER` | `ollama` |

For local Ollama, start Ollama, pull the configured model (for example, `ollama pull gemma:2b`), and keep `LLM_PROVIDER=ollama`. When running through Docker Compose, the default URL is `http://host.docker.internal:11434`; set `OLLAMA_BASE_URL` and `OLLAMA_MODEL` in `.env` when needed.

See [APPLICATION_GUIDE.md](APPLICATION_GUIDE.md) and [DEMO_SCRIPT.md](DEMO_SCRIPT.md) for presenter flow.

**Docs:** [docs/README.md](docs/README.md) — [architecture](docs/architecture/ARCHITECTURE.md) · [flows](docs/architecture/FLOW.md) · [packages](docs/architecture/PACKAGE_STRUCTURE.md)
