# Deploying Sakha (first phase, $0 cost, no credit card)

Oracle/AWS/GCP/Azure all require a card for identity verification even on their free tiers. Hugging Face Spaces was the original pick for the Python voice service, but as of mid-2026 HF changed its policy: creating a new Docker Space now requires a paid plan, so that's no longer a no-card option. This path instead runs **both** backend pieces on **Render** (two separate free web services — it supports Docker just like HF Spaces did, with no card required), plus **Aiven** for MySQL and **Vercel** for the frontend. Three dashboards instead of four.

The honest tradeoff versus a single VM: two free Render services share one account-wide pool of 750 instance-hours/month, and both sleep after a period of no traffic — the first request after idle will be noticeably slow (the voice service has to reload Whisper/Kokoro/the embedding model from cold, which took real time even locally). Fine for showing this to a handful of people; worth upgrading before wider traffic.

The voice service's models were since switched to lighter, torch-free runtimes (`fastembed`/ONNX for embeddings, `kokoro-onnx` for TTS) specifically so this would actually fit in Render's free 512MB RAM limit — the original torch-based stack likely wouldn't have.

None of this has been run end-to-end in this environment (no Docker, and no accounts on these platforms, available here) — treat it as a correct, carefully-checked starting point, not a guarantee. Expect to debug the first deploy of each piece.

## 1. Database — Aiven

(An earlier draft of this guide pointed at db4free.net — checked it while writing this revision and the domain has expired and now redirects to an unrelated parked site. Aiven is a legitimate, established managed-database company, not a random free-host site, with a tier explicitly marketed as free forever, not a trial — verified directly against their own page: no card, 1GB storage/1GB RAM, single-node. It does pause the service after a period of inactivity, with email warning first.)

1. Sign up at [aiven.io](https://aiven.io) (free, no card).
2. Create a new service → **MySQL** → pick the free plan → choose any region.
3. Once it's provisioned (a few minutes), Aiven's console gives you a ready-made **connection string/URI** for the service - use that directly rather than assembling one by hand. Note the host, port (Aiven doesn't use the default `3306`), username, password, and default database name it shows you.
4. Aiven's managed MySQL enforces TLS. The `mysql-connector-j` driver this project uses defaults to preferring TLS automatically (`sslMode=PREFERRED`), so the plain `DB_URL` below will likely work as-is - but if the connection fails on SSL/cert verification, check Aiven's console for the exact JDBC connection string and any CA certificate it wants you to trust; this wasn't tested end-to-end here.
5. Run the schema against it from your own machine, using the host/port/user Aiven gave you:
   ```
   mysql -h <aiven-host> -P <aiven-port> -u <aiven-username> -p <aiven-database> < rag_implementation/db/schema.sql
   ```
6. **Known catch**: 1GB is enough for a first phase, not for real scale; and Aiven explicitly says this tier isn't for production workloads. Fine to start here, plan to move off it before this matters for real.

## 2. Python voice service — Render

1. Sign up at [render.com](https://render.com) (free web services don't require a card).
2. New → Web Service → connect this GitHub repo → set **root directory** to `embedding_services`. Render auto-detects the `Dockerfile`.
3. Environment variables (Render dashboard → Environment):
   - `JAVA_BACKEND_URL` = the Java backend's Render URL from step 3 below (circular dependency — come back and set this after step 3; this service will just fail to reach the backend until you do, which is fine to leave broken temporarily).
   - `GROQ_API_KEY` is **not actually needed** — it's only imported by a dead, unused file (`services/llm_service.py`), not the live voice path. Skip it.
4. Deploy. Render gives you a free `https://<voice-service-name>.onrender.com` with HTTPS automatically. Your voice WebSocket endpoint is `wss://<voice-service-name>.onrender.com/voice`.
5. **Memory note**: the free Render plan is 512MB. This service loads three ML models at startup (Whisper, Kokoro, the embedding model) — it was switched to torch-free runtimes (`fastembed`, `kokoro-onnx`) specifically so this fits; if it still OOMs, dropping to smaller model variants (e.g. Whisper `tiny` instead of `base`) is the next lever.
6. **Build time note**: the Docker build downloads the Kokoro ONNX model files (~140MB) from GitHub Releases — if Render's build step times out on the free tier, that's the first thing to check.

## 3. Java backend — Render

1. Same account as step 2 — New → Web Service → connect this GitHub repo → set **root directory** to `rag_implementation`. Render auto-detects the `Dockerfile`.
2. Environment variables (Render dashboard → Environment):
   - `DB_URL` = `jdbc:mysql://<aiven-host>:<aiven-port>/<aiven-database>` (from step 1 - Aiven's port is not the default 3306)
   - `DB_USERNAME`, `DB_PASSWORD` = your Aiven credentials
   - `PINECONE_API_KEY`, `PINECONE_INDEX_URL` = from your Pinecone dashboard
   - `GROQ_API_KEY` = from your Groq dashboard
   - `JWT_SECRET` = generate a new one, don't reuse the dev value — `openssl rand -base64 48`
   - `EMBEDDING_SERVICE_URL` = `https://<voice-service-name>.onrender.com` (the Render voice service from step 2, no trailing slash, no `/voice`)
   - `CORS_ALLOWED_ORIGIN` = your Vercel URL from step 4 (circular again — come back after step 4)
3. Deploy. Render gives you a free `https://<app-name>.onrender.com` with HTTPS automatically.
4. **Memory note**: the free Render plan is 512MB, tight for a JVM. The Dockerfile already caps the heap (`-Xmx350m`) to reduce the chance of an OOM kill — if it still crashes on startup, that's the first thing to investigate.
5. **Instance-hours note**: free Render services share one pool of 750 hours/month per account. Running this *and* the voice service continuously for a full month is ~1,460 hours combined — comfortably over. In practice both sleep after 15 minutes idle, so low-traffic usage should stay well under the cap; worth keeping an eye on Render's dashboard once real users show up.

## 4. Frontend — Vercel

1. Push this repo to GitHub if it isn't already.
2. [vercel.com](https://vercel.com) (free, no card) → New Project → import the repo → root directory `rag-ui`.
3. Environment variables:
   - `VITE_API_BASE_URL` = your Java backend's Render URL from step 3
   - `VITE_VOICE_WS_URL` = `wss://<voice-service-name>.onrender.com/voice` (the voice service from step 2, **not** the Java backend)
4. Deploy. Vercel gives a free `https://<your-app>.vercel.app` with automatic HTTPS. HTTPS here isn't optional — the Voice tab's `getUserMedia` call is blocked by browsers on non-HTTPS origins.
5. Go back and set the two circular values: `CORS_ALLOWED_ORIGIN` on the Java backend's Render service and `JAVA_BACKEND_URL` on the voice service's Render service, both to this real Vercel URL/backend URL respectively, then redeploy each.

## Alternative: a single VM later

If you ever get access to a card/VM (your own, a free trial, whatever), `docker-compose.yml` and the three `Dockerfile`s in this repo already support running everything on one host instead of spread across three platforms — simpler ops, no cold starts, one dashboard. Not written up step-by-step here since it's not the path you're on now, but the pieces are already in place.

## Known gaps worth closing before real users show up

- **`GET /users` still returns every user's password hash** to any authenticated caller — flagged earlier in this session, deliberately left as-is per an explicit decision at the time. Worth revisiting now that "first phase rollout" means real accounts, not just test ones.
- **No rate limiting anywhere** — Groq and Pinecone calls are metered; an unrestricted public endpoint can run up a bill or get abused. Worth adding at least basic per-user/per-IP limits before wide release.
- **No automated tests** — every verification in this session was manual (real browser sessions, curl, direct DB checks). Fine for a first phase with one developer; a real safety net before this grows.
- **`db/schema.sql` is a one-time snapshot, not a migration system** — the next schema change needs to either be hand-applied in prod and the file updated to match, or (better, once this matters) replaced with Flyway/Liquibase so changes are versioned and repeatable.
- **Cold starts on both Render free services** mean the first voice/chat interaction after a quiet period will be slow — worth setting expectations with early users, or looking at a paid "always-on" tier once it's worth the cost.
