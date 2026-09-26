# Gemini free tier and Log Sentinel

## Why you see **429 Quota exceeded**

Each click **Analyze** is not one API call. The agent may run:

1. One call to start analysis
2. Up to **`MAX_TOOL_CALLS`** extra round-trips if the model invokes tools

On the **free tier**, limits are **per model** (for example **5 requests/minute** and a **daily** cap on `gemini-3.8-flash`). Running three scenarios in a row, or using multiple tool calls, exhausts quota quickly.

## Recommended `.env` for demos

```env
LLM_PROVIDER=gemini
GEMINI_API_KEY=your-key
GEMINI_MODEL=gemini-2.5-flash
MAX_TOOL_CALLS=1
RATE_LIMIT_RETRIES=2
GEMINI_INCLUDE_THOUGHTS=false
```

- **`gemini-2.5-flash`** uses a **separate quota bucket** from `gemini-3.8-flash`.
- **`MAX_TOOL_CALLS=1`** keeps most analyses to **1–2** API calls.
- Wait **about 60 seconds** between live demos on the free tier.

If you must use **Gemini 3.8 Flash** for tools, set:

```env
GEMINI_MODEL=gemini-3.8-flash
GEMINI_INCLUDE_THOUGHTS=true
```

## After changing `.env`

```powershell
docker compose up -d
```

Compose reads **`.env`**, not `.env.example`. If `GEMINI_MODEL` is still `gemini-3.8-flash` in `.env`, the app will keep hitting that model’s limits even when defaults in the repo change.

## Monitoring

- [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Usage dashboard](https://ai.dev/rate-limit)

## OpenAI alternative

If OpenAI billing is active:

```env
LLM_PROVIDER=openai
OPENAI_API_KEY=your-key
```
