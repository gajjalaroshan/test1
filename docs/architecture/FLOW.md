# Log Sentinel — flow diagrams

End-to-end paths through the POC. Pair with [ARCHITECTURE.md](ARCHITECTURE.md) and [PACKAGE_STRUCTURE.md](PACKAGE_STRUCTURE.md).

## 1. Traffic → log file (API 1)

Failures are recorded by the global handler, not by simulator code.

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Traffic API
    participant TS as TrafficSimulator
    participant GEH as GlobalApiExceptionHandler
    participant SAL as StructuredApiErrorLogger
    participant LB as Logback FILE appender
    participant F as log-sentinel-app.log

    C->>API: POST /traffic/simulate or simulate-batch or demo-flagging
    API->>API: Set customerId, orderId, amountInr on request
    API->>TS: simulate(scenario, ...)
    TS-->>API: throws Throwable
    API->>GEH: exception propagates
    GEH->>SAL: logFailure(request, ex)
    SAL->>LB: ERROR API_FAILURE customerId=... orderId=...
    LB->>F: append line + stack trace
    GEH-->>C: HTTP 500 (single simulate) or 200 batch/demo summary
```

**Batch / demo:** controller may catch exceptions and call `logFailure` directly so multiple failures are logged in one HTTP call.

## 2. Analyze log file (API 2)

```mermaid
sequenceDiagram
    participant C as Client
    participant LA as LogAnalysisController
    participant A as LogFileAnalyzer
    participant P as LogFileParser
    participant F as log-sentinel-app.log
    participant O as DualAgentOrchestrator
    participant RP as RemediationPlannerAgent
    participant ER as ExecutiveReportAgent

    C->>LA: POST /logs/analyze { useLlm }
    LA->>A: analyze(logFilePath)
    A->>F: read all lines
    A->>P: parse(lines)
    P-->>A: LogEvent list
    A->>A: filter API_FAILURE, group, ErrorBreakdown, rule flags
    A-->>LA: LogAnalysisResult

    alt useLlm true and API key set
        LA->>O: run(javaResult)
        O->>RP: plan(toAgentPrompt())
        RP-->>O: remediation markdown
        O->>ER: report(remediation, toAgentPrompt())
        ER-->>O: executive markdown
        O-->>LA: pipeline result
        LA-->>C: javaAnalysis, ruleFlags, agents
    else Java only or no key
        LA-->>C: javaAnalysis, ruleFlags, agents null
    end
```

## 3. Data flow (logical)

```mermaid
flowchart LR
    subgraph ingest [Ingest]
        T[Traffic APIs]
        E[Exceptions]
        L[API_FAILURE lines]
    end

    subgraph store [Store]
        FILE[(Log file)]
    end

    subgraph compute [Compute Java]
        PARSE[Parse + filter]
        SPLIT[ErrorBreakdown]
        FLAGS[Rule flags]
    end

    subgraph llm [LLM optional]
        R1[Remediation Planner]
        R2[Executive Report]
    end

    T --> E --> L --> FILE
    FILE --> PARSE --> SPLIT
    PARSE --> FLAGS
    SPLIT --> R1
    FLAGS --> R1
    PARSE --> R1
    R1 --> R2
    SPLIT --> R2
    FLAGS --> R2
```

## 4. Demo happy path (presenter)

```mermaid
flowchart TD
    A[docker compose up] --> B[POST /traffic/demo-flagging]
    B --> C[Log file gains burst + high-value errors]
    C --> D{Audience}
    D -->|Engineers| E[POST /logs/analyze useLlm false]
    E --> F[Show errorBreakdown + ruleFlags]
    D -->|Full story| G[POST /logs/analyze useLlm true]
    G --> H[Remediation plan per exception type]
    G --> I[Executive report + solutions by type]
```

## 5. What each stage produces

| Stage | Output |
|-------|--------|
| Traffic | Lines in `logs/log-sentinel-app.log` with `customerId`, `orderId`, `amountInr`, `exception=` |
| Java analyze | `javaAnalysis.errorBreakdown`, `byCustomer`, `byOrder`, `ruleFlags` |
| Remediation Planner | `agents.remediationPlanMarkdown` — technical runbook |
| Executive Report | `agents.executiveReportMarkdown` — severity, impact, solutions by issue type |

## API quick reference

| Method | Path | Effect |
|--------|------|--------|
| POST | `/api/v1/traffic/simulate` | One failure → log (+ usually 500) |
| POST | `/api/v1/traffic/simulate-batch` | Many failures, one customer → log |
| POST | `/api/v1/traffic/demo-flagging` | Canned burst + VIP order → log |
| POST | `/api/v1/logs/analyze` | Read file, analyze, optional LLM |
