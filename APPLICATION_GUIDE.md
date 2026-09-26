# Application guide

## Components

Packages follow **web → service → domain** (see [docs/architecture/PACKAGE_STRUCTURE.md](docs/architecture/PACKAGE_STRUCTURE.md)).

- **web.controller** — traffic and analyze REST endpoints.
- **web.dto** — `AnalyzeRequest`, `AnalyzeResponse`, traffic bodies, `RuleFlagSummary`, `AgentReports`.
- **service.traffic** — `TrafficSimulator`.
- **service.logging** — `GlobalApiExceptionHandler`, `StructuredApiErrorLogger`.
- **service.analysis** — `LogFileParser`, `LogFileAnalyzer`.
- **domain.analysis** — `LogEvent`, `ErrorBreakdown`, `LogAnalysisResult`.
- **service.agent** — `DualAgentOrchestrator`, remediation + executive agents.
- **ErrorBreakdown** — Java exposes `totalApiFailureErrors`, `errorsByExceptionType`, splits per `customerId` / `orderId` on `javaAnalysis.errorBreakdown`.

| Layer | Responsibility |
|-------|----------------|
| Java | Totals, splits, rule flags (`errorBreakdown`, `ruleFlags`) |
| Agent 1 Remediation Planner | Technical runbook: fixes per exception type, steps for flagged ids |
| Agent 2 Executive Report | Severity, impact narrative, **Solutions by issue type** (exec-friendly), next-hour actions |

API field: `agents.remediationPlanMarkdown`, `agents.executiveReportMarkdown`.

## Log format

Example line:

```
2026-09-25T10:00:01.000Z ERROR [http-nio-8080-exec-1] API_ERROR - API_FAILURE customerId=cust-1 orderId=ord-1 amountInr=50000.0 path=/api/v1/traffic/simulate exception=java.lang.NullPointerException message=...
```

Following lines are standard stack traces; the parser attaches them to the preceding ERROR event.

## Thresholds

Configured under `logsentinel.analysis` in `application.yml`. Customer burst uses **strict** `distinct.size() > threshold` (default 3 → needs **4** different stack signatures in the window).

## LLM

- Profile `ollama`, `gemini`, or `openai` via `LLM_PROVIDER`.
- Set `useLlm: false` on analyze to skip agents when quota is limited.
- `LlmRuntimeConfig` gates agent calls when API key is missing.

## Troubleshooting

| Issue | Action |
|-------|--------|
| 404 on analyze | Run traffic first; check `LOG_FILE_PATH` and compose volume `./logs:/app/logs` |
| Agent 429 | Retry later, switch model (`GEMINI_MODEL=gemini-2.5-flash`), or Java-only analyze |
| Empty flags | Ensure batch used 4 scenarios within 3s; high-value needs `amountInr > 150000` |
