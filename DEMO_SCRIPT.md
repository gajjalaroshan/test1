# Log Sentinel — demo script

## Setup (30s)

1. `copy .env.example .env` and add `GEMINI_API_KEY` (or `OPENAI_API_KEY` + `LLM_PROVIDER=openai`).
2. `docker compose up --build -d`
3. Open http://localhost:8080

## Story

"We simulate API failures without sprinkling log statements in business code. A global handler writes structured errors to a file. A second API reads that file, groups by customer and order, flags high-value orders and error bursts, then two LLM agents explain the incident."

## Steps

1. **Batch burst** — click *Batch failures* (or `POST /api/v1/traffic/simulate-batch`). Explain: four different exceptions for one customer within seconds.
2. **High value** — click *High-value failure* (₹2L). Explain: order amount rule at 1.5L INR.
3. **Analyze Java only** — click *Analyze (Java only)*. Show `flaggedCustomers` and `flaggedOrders` in JSON.
4. **Analyze with agents** — click *Analyze (Java + 2 agents)*. Walk through Agent 1 (patterns) and Agent 2 (executive brief). If 429 quota, use Java-only path.

## Talking points

- **Silent failures**: `TrafficSimulator` only throws; logging is centralized.
- **Deterministic rules** before LLM — auditable thresholds in `application.yml`.
- **Two agents**: specialist then executive — mirrors production triage + comms.
