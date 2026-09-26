# Log Sentinel — flow diagrams

End-to-end paths through the POC. Pair with [ARCHITECTURE.md](ARCHITECTURE.md) and [PACKAGE_STRUCTURE.md](PACKAGE_STRUCTURE.md).

## 1. Traffic → log file

Failures are recorded by the global handler or by controllers that catch and call `StructuredApiErrorLogger` directly.

```mermaid
sequenceDiagram
    participant C as Client
    participant API as TrafficController
    participant TS as TrafficSimulator
    participant GEH as GlobalApiExceptionHandler
    participant SAL as StructuredApiErrorLogger
    participant LB as LineBasedRollingFileAppender
    participant F as log file

    C->>API: POST /api/v1/traffic/simulate-batch | demo-flagging | simulate-single-trace-triple-error
    API->>API: bind customerId, orderId, amountInr (batch)
    loop each scenario
        API->>TS: simulate(...)
        TS-->>API: throws
        API->>SAL: logFailure (or logFailureWithMdc)
    end
    Note over API,SAL: Uncaught errors on /api/v1/ also go GEH → SAL
    SAL->>LB: ERROR API_FAILURE (optional customerId/orderId)
    LB->>F: append line + stack; roll at max lines
    API-->>C: 200 summary JSON
```

**demo-flagging:** one shared `traceId`/`spanId` across burst + VIP via `logFailureWithMdc`.

## 2. List and analyze log files

```mermaid
sequenceDiagram
    participant C as Client
    participant LA as LogAnalysisController
    participant LDS as LogDirectoryService
    participant A as LogFileAnalyzer
    participant P as LogFileParser
    participant TCR as TraceContextResolver
    participant F as log file(s)
    participant O as DualAgentOrchestrator
    participant RP as RemediationPlannerAgent
    participant ER as ExecutiveReportAgent

    C->>LA: GET /api/v1/logs/files
    LA->>LDS: listLogFiles()
    LA-->>C: LogFileEntry[]

    C->>LA: POST /api/v1/logs/analyze { useLlm, logFilePaths? }
    LA->>A: analyze(path | paths)
    A->>F: read lines (rolled order)
    A->>P: parse(lines)
    P-->>A: LogEvent[]
    A->>TCR: resolve(events)
    TCR-->>A: trace-propagated identities
    A->>A: API_FAILURE filter, breakdown, flags
    A-->>LA: LogAnalysisResult

    alt useLlm true and API key set
        LA->>O: run(javaResult)
        O->>RP: plan(javaResult)
        RP-->>O: remediation markdown
        O->>ER: report(javaResult.toAgentPrompt())
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
        FILE[(Rotated log files)]
    end

    subgraph compute [Compute Java]
        PARSE[Parse + TraceContextResolver]
        SPLIT[ErrorBreakdown explicit counts]
        FLAGS[Rule flags + windows]
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
    SPLIT --> R2
    FLAGS --> R2
    PARSE --> R2
```

Agent 2 does **not** consume Agent 1 output; both read the Java analysis payload.

## 4. Demo happy path (presenter)

```mermaid
flowchart TD
    A[docker compose up] --> B[POST /api/v1/traffic/demo-flagging]
    B --> C[Log file gains burst + high-value errors]
    C --> D{Audience}
    D -->|Engineers| E[POST /api/v1/logs/analyze useLlm false]
    E --> F[errorBreakdown + ruleFlags + windows]
    D -->|Full story| G[POST /api/v1/logs/analyze useLlm true]
    G --> H[Remediation plan with evidence]
    G --> I[Executive report business brief]
```

## 5. What each stage produces

| Stage | Output |
|-------|--------|
| Traffic | `API_FAILURE` lines; `traceId` in MDC; rotation at line limit |
| Java analyze | `javaAnalysis.errorBreakdown`, `byCustomer` / `byOrder`, `ruleFlags.flaggedCustomers[].windows` |
| Remediation Planner | `agents.remediationPlanMarkdown` |
| Executive Report | `agents.executiveReportMarkdown` |

## API quick reference

| Method | Path | Effect |
|--------|------|--------|
| POST | `/api/v1/traffic/simulate-batch` | Many failures in one request → log |
| POST | `/api/v1/traffic/demo-flagging` | Canned burst + VIP → log |
| POST | `/api/v1/traffic/simulate-single-trace-triple-error` | Three failures, one trace, counting demo |
| GET | `/api/v1/logs/files` | List active + rolled files |
| POST | `/api/v1/logs/analyze` | Read file(s), analyze, optional LLM |
