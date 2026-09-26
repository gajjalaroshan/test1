# Application guide

## Components

Packages follow **web → service → domain** (see [docs/architecture/PACKAGE_STRUCTURE.md](docs/architecture/PACKAGE_STRUCTURE.md)).

| Area | Classes |
|------|---------|
| **web.controller** | `TrafficController`, `LogAnalysisController` |
| **web.dto** | `AnalyzeRequest`, `AnalyzeResponse`, `TrafficRequest`, `RuleFlagSummary`, `AgentReports`, `LogFileEntry` |
| **service.simulator** | `TrafficSimulator` |
| **service.logging** | `GlobalApiExceptionHandler`, `StructuredApiErrorLogger`, `RequestTraceFilter`, `TraceMdc`, `ApiRequestContext` |
| **service.analysis** | `LogFileParser`, `LogFileAnalyzer`, `LogDirectoryService`, `TraceContextResolver` |
| **service.agent** | `DualAgentOrchestrator`, `RemediationPlannerAgent`, `ExecutiveReportAgent` |
| **config.logging** | `LineBasedRollingFileAppender`, `LineCountTriggeringPolicy` |
| **domain.analysis** | `LogEvent`, `ErrorBreakdown`, `LogAnalysisResult` |

| Layer | Responsibility |
|-------|----------------|
| Java | Totals, splits, rule flags (`javaAnalysis`, `ruleFlags`) |
| Remediation Planner | Technical runbook + evidence from Java prompt |
| Executive Report | Business impact from **Java prompt only** (no remediation text, no stack/log dumps) |

Response fields: `agents.remediationPlanMarkdown`, `agents.executiveReportMarkdown`.

## REST surface

| Method | Path |
|--------|------|
| POST | `/api/v1/traffic/simulate-batch` |
| POST | `/api/v1/traffic/demo-flagging` |
| POST | `/api/v1/traffic/simulate-single-trace-triple-error` |
| GET | `/api/v1/logs/files` |
| POST | `/api/v1/logs/analyze` |

**Traffic:** `simulate-batch` catches each scenario and calls `StructuredApiErrorLogger.logFailure` so one HTTP request can emit several `API_FAILURE` lines. `demo-flagging` uses `logFailureWithMdc` with a single synthetic `traceId`/`spanId` across the burst loop plus the VIP failure.

**Analyze:** Omit paths → active file from `LogDirectoryService`. `logFilePaths` reads files in roll order (higher `.N` first, then active). UI loads `/api/v1/logs/files` and sends checked paths as `logFilePaths`.

## Log format and hygiene

`StructuredApiErrorLogger` writes one ERROR line per failure to logger `API_ERROR`:

- Message prefix `API_FAILURE`; `customerId=` / `orderId=` only when known (not blank, not `unknown`).
- Always includes `amountInr`, `path`, `exception`, `message`.
- `RequestTraceFilter` puts `traceId` / `spanId` in MDC (visible in the log pattern).

`GlobalApiExceptionHandler` calls `logFailure` only when `request.getRequestURI()` starts with `/api/v1/`. Static assets and non-API 404s are not written as `API_FAILURE`.

Example:

```
2026-09-25T10:00:01.000Z ERROR [http-nio-8080-exec-1 traceId=abc spanId=def] API_ERROR - API_FAILURE customerId=cust-1 orderId=ord-1 amountInr=50000.0 path=/api/v1/traffic/simulate-batch exception=java.lang.NullPointerException message=...
```

Following lines are stack traces; `LogFileParser` attaches them to the preceding ERROR event.

## Analysis semantics

1. Parse lines → `LogEvent` list (`explicitCustomerId` true only when the message contained `customerId=`).
2. `TraceContextResolver.resolve` — propagate `customerId`/`orderId` to other events on the same `traceId` for grouping only.
3. **Explicit counting** — `errorBreakdown.byCustomerId`, `byCustomer` keys, and customer burst detection use `explicitCustomerId` only.
4. **Trace slices** — for each explicit customer event, `byTraceId` includes all `API_FAILURE` errors on that trace (e.g. triple-error demo: total errors 3, explicit customer count 1, trace slice count 3).

`flaggedCustomers`: one record per flagged `customerId` with `windows[]` of burst windows that exceeded the distinct-stack threshold.

## Thresholds

`logsentinel.analysis` in `application.yml` (overridable via env in compose). Customer burst: `distinct.size() > customerDistinctStackThreshold` (default 3 → **4** signatures).

## Log rotation

`logback-spring.xml` uses `LineBasedRollingFileAppender` and `LineCountTriggeringPolicy`. Env `LOG_MAX_LINES_PER_FILE` / `LOG_MAX_ROLLED_FILES` map to `logsentinel.log-rotation.max-lines-per-file` and `max-index`.

## LLM

- Profile `ollama`, `gemini`, or `openai` via `LLM_PROVIDER` (see `.env.example`).
- `LLM_MAX_TOKENS=8192` for completion length on all providers.
- `useLlm: false` on analyze skips agents.
- `LlmRuntimeConfig` gates agent calls when the provider API key is missing.

## Troubleshooting

| Issue | Action |
|-------|--------|
| 404 on analyze | Run traffic first; check `LOG_FILE_PATH` and compose volume `./logs:/app/logs` |
| Agent 429 | Retry later, `GEMINI_MODEL=gemini-2.5-flash`, lower `MAX_TOOL_CALLS`, or Java-only analyze |
| Empty customer flags | Need 4 distinct stack signatures within 3s with explicit `customerId` on those lines |
| Empty order flags | `amountInr` must be **>** 150000 on the ERROR line |
| Multi-file empty | Ensure checkboxes include files with content; paths must exist |

See [docs/operations/gemini-free-tier.md](docs/operations/gemini-free-tier.md) for quota tips.
