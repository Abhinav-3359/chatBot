# RAG Chatbot / Voice Assistant — Architecture Deep Dive

A from-the-ground-up explanation of this project: what it is, how each piece works, why it's built this way, and what's still rough around the edges. Written so you can explain any part of it in an interview without re-reading the code.

## Table of Contents

1. [What This Project Actually Is](#1-what-this-project-actually-is)
2. [System Map](#2-system-map)
3. [Java Backend (`rag_implementation`)](#3-java-backend-rag_implementation)
4. [Python Embedding Microservice](#4-python-embedding-microservice)
5. [Python Voice Service (`embedding_services`)](#5-python-voice-service-embedding_services)
6. [React Frontend (`rag-ui`)](#6-react-frontend-rag-ui)
7. [End-to-End Flows](#7-end-to-end-flows)
8. [Debugging Case Studies (the voice pipeline's hard bugs)](#8-debugging-case-studies)
9. [Known Issues / Tech Debt (consolidated)](#9-known-issues--tech-debt-consolidated)
10. [Likely Interview Questions](#10-likely-interview-questions)

---

## 1. What This Project Actually Is

A **Retrieval-Augmented Generation (RAG) chatbot platform**, multi-tenant in design: a user creates one or more **"chatbots"**, each one a scoped knowledge base. You upload documents into a chatbot; it extracts and chunks the text, embeds the chunks, and stores them in a vector database (Pinecone). You can then ask that chatbot questions — either by typing (REST API + React UI) or by **talking to it** (a real-time voice pipeline: mic → speech-to-text → the same RAG path → text-to-speech → speaker).

Three independent runtimes make this work:

| Runtime | Role |
|---|---|
| **Java Spring Boot** (`rag_implementation`) | The actual RAG brain: auth, chatbot/document management, embedding orchestration, Pinecone queries, prompt assembly, LLM calls (Groq). Everything — text chat and voice — ultimately answers through this one service. |
| **Python FastAPI — embedding microservice** (`embedding_services/app.py`) | A tiny stateless HTTP service wrapping a local sentence-embedding model. The Java backend calls it for every embedding it needs. |
| **Python FastAPI — voice service** (`embedding_services/websocket/voice_socket.py`) | The real-time voice pipeline: WebSocket endpoint that does mic VAD, Whisper transcription, calls the Java backend's voice-query endpoint, then streams TTS audio back. |

A fourth piece, **`rag-ui`**, is a React/Vite frontend for the text-chat side — currently an early scaffold (routing, auth, API layer are wired up; most pages are placeholders).

The project is deliberately split this way because the heavy ML pieces (sentence embeddings, Whisper, TTS) are much easier to run in Python, while the "business logic" (multi-tenant chatbots, auth, documents, orchestrating the RAG steps) is in Java/Spring. The two Python processes are not the same thing — don't conflate the embedding microservice (port 9000, `/embed`) with the voice service (also port 9000 in this setup, but a different route, `/voice`) — in this repo they happen to be **the same FastAPI app** (`app.py` mounts both the embedding routes and the voice WebSocket router), but conceptually they're separate responsibilities.

---

## 2. System Map

```
                         ┌─────────────────────────┐
                         │   React UI (rag-ui)      │
                         │   login / dashboard /     │
                         │   chat (mostly scaffold)  │
                         └────────────┬──────────────┘
                                      │ REST + JWT (axios, localStorage token)
                                      ▼
┌──────────────────────────────────────────────────────────────────┐
│                 Java Spring Boot (port 8080)                      │
│  UserController · ChatBotController · DocumentController ·        │
│  ChatController                                                     │
│                                                                      │
│  AuthService ──JWT──▶ JwtService / JwtAuthenticationFilter          │
│  DocumentService ──Tika extract──▶ chunk ──▶ EmbeddingService ──┐  │
│  RagService ──▶ EmbeddingService (query) ──▶ PineconeIntegration │  │
│                 Service ──▶ GroqClient (LLM)                    │  │
│                                                                   │  │
│  MySQL (users, chatbots, documents, document_chunks)             │  │
└──────────┬────────────────────────────────────────────┬─────────┘  │
           │ POST /embed, /embed_query                   │ Pinecone   │
           ▼                                              │ REST API  │
┌─────────────────────────────┐                           ▼           │
│ Python embedding service     │                 ┌─────────────────┐  │
│ (FastAPI, port 9000)          │                 │ Pinecone (vector │ │
│ SentenceTransformer           │                 │ DB, namespace    │◀┘
│ BAAI/bge-base-en-v1.5         │                 │ "chatbot_<id>")  │
└───────────────┬───────────────┘                 └─────────────────┘
                │ (same FastAPI app, different router)
                ▼
┌───────────────────────────────────────────────────────────────┐
│ Python voice service (FastAPI WebSocket /voice, port 9000)      │
│ VAD → AudioBuffer → faster-whisper → (calls Java /voice-query)  │
│                   → Kokoro TTS → streamed back over WebSocket   │
└───────────────┬───────────────────────────────────────────────┘
                │ WebSocket (binary audio + JSON control)
                ▼
┌───────────────────────────┐
│ voice_test.py (CLI client) │
│ mic capture + speaker       │
│ playback via sounddevice    │
└───────────────────────────┘
```

---

## 3. Java Backend (`rag_implementation`)

Spring Boot, raw JDBC (`JdbcTemplate` + hand-written `RowMapper`s — **not** JPA/Hibernate), MySQL (`rag_v1`), Lombok, Apache Tika for document parsing, `jjwt` for JWT, Pinecone and Groq accessed over plain REST (`RestTemplate`).

### 3.1 Auth

**Register** (`POST /users/register`, public) — `AuthService.register()`: checks `UserRepository.findByEmail`, throws `UserAlreadyExistsException` (→ 409) if taken, otherwise hashes the password with `BCryptPasswordEncoder` and inserts a row. No token is issued on register — you must log in separately afterward.

**Login** (`POST /users/login`, public) — `AuthService.login()`: looks up by email, verifies with `passwordEncoder.matches()`, throws `InvalidCredentialsException` (→ 401) on failure. On success, `JwtService.generateToken(email)` issues a JWT and returns `{id, email, token}`.

**The JWT itself**: HS256, signed with a secret from `jwt.secret` (currently a plaintext value committed in `application.properties` — should be an env var). Claims are minimal: **subject = email**, `issuedAt`, `expiration` (default 24h via `jwt.expiration=86400000`). No roles, no chatbot ownership, nothing else is embedded.

**Request-time validation** — `JwtAuthenticationFilter` (a `OncePerRequestFilter`): reads `Authorization: Bearer <token>`, extracts the email, loads a Spring Security `UserDetails` via `CustomUserDetailsService` (which gives **every single user the same hardcoded authority, `"USER"`** — there's no roles table at all), and if the token is valid for that user, populates `SecurityContextHolder`.

**`SecurityConfig`**: CSRF off, session policy `STATELESS`, only `/users/login` and `/users/register` are `permitAll()` — everything else (all of `/chatbots/**`, `/documents/**`, even `GET /users`) requires a valid JWT, full stop. There is **no ownership check beyond "are you logged in"** — any authenticated user can read or write any other user's chatbots or documents, because nothing ever compares the JWT's email/user id against `ChatBotDO.userId`.

### 3.2 Chatbots — the multi-tenant unit

A **chatbot row** (`ChatBotDO`: `id`, `userId`, `name`, `description`, `pineConeNamespace`) is "one knowledge base / one assistant instance owned by one user" — the standard SaaS multi-tenant shape. CRUD lives in `ChatBotController` (`GET /chatbots`, `GET /chatbots/{id}`, `POST /chatbots/add`, `PUT /chatbots/{id}` — there's no `DELETE`). The `PUT` is really a partial-update-via-PUT: it only overwrites fields that were non-null in the request body.

**Important gotcha**: the DB has a `pinecone_namespace` column you can set through the API, but **nothing ever reads it**. Both ingestion and querying always compute the namespace themselves as the literal string `"chatbot_" + chatbotId`. The column is dead weight right now — worth knowing so you don't assume it's load-bearing.

### 3.3 Document ingestion pipeline

`POST /documents/add/{chatbotId}` (multipart, up to 250MB) → `DocumentService.processDocument()`:

1. **Extract text** with Apache Tika (`new Tika().parseToString(...)`) — generic auto-detection, no OCR, no per-type handling.
2. **Insert a `documents` row immediately** (`chatbot_id`, `file_name`, `file_type`) — note the **raw extracted text is never stored in MySQL**, by design (keeps the relational DB small).
3. **Chunk the text**: naive character-based sliding window — `chunkSize = 500`, `overlap = 100` (step = 400 chars/iteration). This is character count, not tokens, and can split mid-word — there's no sentence/paragraph awareness.
4. **For each chunk**:
   - Call the Python embedding service (`POST http://localhost:9000/embed`, hardcoded URL) to get a vector.
   - Generate a `vectorId` (`UUID.randomUUID()`) in Java.
   - Upsert into Pinecone: namespace `"chatbot_<id>"`, the vector, and metadata `{"text": chunk}` — **this is the only metadata field; no document id, chunk index, or filename travels with it into Pinecone**.
   - Insert a `document_chunks` row: `document_id`, `chunk_index`, `vector_id` — **no chunk text**. This confirms the project's design intent: *Pinecone owns the text (in metadata), MySQL only owns the pointers*, keeping the relational DB from growing with document content.

### 3.4 The RAG query pipeline (text chat *and* voice — same code path)

`POST /chatbots/{id}/chat` (`ChatRequestDTO{message}`) and `POST /chatbots/{id}/voice-query` (`VoiceQueryRequest{text, sessionId}`) both terminate in the exact same call: `RagService.askQuestion(chatbotId, text)`. The voice endpoint is purely an alternate entry DTO — **there is no voice-specific retrieval or prompting logic, and `sessionId` is accepted but never used anywhere** — every call, text or voice, is single-turn/stateless. No conversation history is ever sent to the LLM.

`RagService.askQuestion()` step by step:

1. Embed the question: `POST http://localhost:9000/embed_query` — a **separate endpoint from document embedding** (`/embed` vs `/embed_query`), because the embedding model (BAAI/bge-base-en-v1.5) expects queries to be prefixed differently than passages (see §4).
2. Query Pinecone: `POST {indexUrl}/query` with `{"vector": ..., "topK": 3, "includeMetadata": true, "namespace": "chatbot_<id>"}`. **`topK` is hardcoded to 3, and there is no similarity-score threshold** — whatever the top 3 matches are, they're used, even if they're a poor match (or there are zero matches, in which case the context is just empty).
3. Assemble context: `String.join("\n", chunks)` — the 3 chunk texts joined with a newline. No source labels, no de-duplication, no token-budget capping.
4. Build the prompt (verbatim):
   ```
   You are an AI assistant.

   Use ONLY the context below.
   If the question related to something little in the context then please answer that also.

   Context:
   %s

   Question:
   %s
   ```
   Worth knowing this is **self-contradicting as written** ("use ONLY the context" vs. "answer that also" for things only loosely related) — reads like an unedited early draft, not a deliberate prompting strategy.
5. Call Groq via `GroqClient` (the only `LLMClient` implementation): the **entire prompt is sent as a single `user`-role message** — no separate `system` message, even though Groq's API supports one. Model is `openai/gpt-oss-20b` (from `llm.model`), endpoint `https://api.groq.com/openai/v1/chat/completions`, key from `groq.api.key` (plaintext in `application.properties`).
6. Parse the response (`choices[0].message.content`, token usage, latency) into `ChatResponseDTO{answer, model, promptTokens, completionTokens, totalTokens, latency}`, wrapped in the standard `ApiResponse` envelope.

### 3.5 Cross-cutting plumbing

- **`ApiResponse<T>`**: every endpoint returns `{success, message, data}`. Consistent across the app (one stray `GET /hello` scaffold endpoint from Spring Initializr returns a bare string and should probably just be deleted).
- **`GlobalExceptionHandler`**: `UserAlreadyExistsException`→409, `InvalidCredentialsException`→401, `ChatBotNotFoundException`→404, everything else (including Tika failures, Pinecone HTTP errors, a malformed JWT) → generic 500 "Something went wrong." One real bug here: a **malformed JWT throws uncaught inside the filter itself** (no try/catch around `extractEmail`), so garbage tokens surface as 500s instead of a clean 401 — only genuinely *expired* (but well-formed) tokens correctly produce a 401 via the normal `isTokenValid` path.
- **CORS**: configured twice — once generically via `SecurityConfig`'s `.cors(Customizer.withDefaults())`, and the actual policy in a separate `CorsConfig` bean (allows `http://localhost:5173`, the Vite dev server, with credentials). Works today, but is a structural smell (one source of truth would be better) and would need a manual update for any real deployed frontend origin.

### 3.6 Dead/legacy code to know about (so you don't mistake it for active)

- `services/LLMService.java` — an earlier prototype pointed at a **local Ollama instance** (`gemma:2b`), fully superseded by `GroqClient`, completely unused (even has a commented-out import left in `RagService`).
- `DocumentRepository.getAllDocuments()` — defined, never called (no `GET /documents` endpoint exists).
- `UserNotFoundException` — defined and handled by the global exception handler, but never actually thrown anywhere.
- `llm/LLMResponse.java` and `dto/ChatResponseDTO.java` are structurally identical — `RagService` just copies one into the other.

---

## 4. Python Embedding Microservice

`embedding_services/services/embedding_service.py` loads **`BAAI/bge-base-en-v1.5`** via `sentence-transformers` once at import time, and exposes two functions wired into `app.py`:

- `POST /embed` → `model.encode(text)` — used for **document chunks** during ingestion.
- `POST /embed_query` → `model.encode("query: " + text)` — used for **questions**, with a `"query: "` prefix.

The asymmetry is deliberate, not a bug: BGE-style embedding models are trained with an instruction prefix convention where queries and passages are embedded slightly differently to improve retrieval quality — passages go in raw, queries get prefixed. This is why the Java side calls two different endpoints rather than one.

---

## 5. Python Voice Service (`embedding_services`)

This is the most complex, most heavily-debugged part of the project. The goal: natural spoken conversation, including the ability to **interrupt the AI mid-sentence (barge-in)**.

### 5.0 The three translation steps — and the order they run in

It's easy to assume Whisper, Groq, and Kokoro are three interchangeable "AI parts," but each does exactly one job, in exactly one direction, and they run **strictly in sequence** for a given turn — never in parallel with each other, never out of order:

| # | Model | Job | Input | Output |
|---|---|---|---|---|
| 1 | **Whisper** (`faster-whisper`) | Speech-to-Text (STT) | your recorded audio bytes | a text string, e.g. `"Who is Abhinav?"` |
| 2 | **Groq** (the LLM, via the Java backend) | Answer the question | text question + retrieved context chunks | a text answer |
| 3 | **Kokoro** | Text-to-Speech (TTS) | the text answer | audio bytes you hear |

Groq **never sees audio** — by the time anything reaches it, Whisper has already turned your voice into plain text, and from Groq's point of view a voice question is indistinguishable from someone typing it into the React UI. Kokoro **never sees your question** — it only receives the final answer text, after Groq has already produced it. There is no step where any two of these run at the same time for one turn; the pipeline is a strict relay: audio → text → text → audio.

### 5.1 Pipeline overview

```
mic chunk (20ms, 16kHz int16 PCM)
   │
   ▼
energy-based VAD (voice_socket.py)
   │  is_speech = mean(abs(samples)) >= ENERGY_THRESHOLD (1000)
   ▼
VoiceSession: pre-roll buffer + AudioBuffer + silence counter
   │  (keeps ~200ms *before* speech starts, so the first phoneme isn't lost)
   ▼
silence sustained for MAX_SILENCE_CHUNKS (60 × 20ms = 1.2s)
   │  → utterance considered finished
   ▼
trim trailing silence down to ~300ms before handing off to Whisper
   │  (full 1.2s of silence in the buffer was causing hallucinated transcripts)
   ▼
asyncio.create_task(handle_utterance(...))   ← runs in the BACKGROUND, steps below run IN ORDER:
   │
   ① faster-whisper transcribe (CPU, run via asyncio.to_thread)        [ audio → text ]
   │     vad_filter=True (Silero VAD strips leftover silence/noise)
   │     initial_prompt biases decoding toward known names/terms
   │     ...once this produces a transcript, Whisper's job is DONE for this turn...
   ▼
   ② POST transcript to Java /chatbots/{id}/voice-query (httpx.AsyncClient, async)   [ text → text ]
   │     (same RAG pipeline as the typed-chat endpoint: embed → Pinecone → Groq)
   │     ...Java hands back the answer text...
   ▼
   ③ TTSWorker.speak(answer) → enqueued on a single serial TTS queue   [ text → audio ]
         Kokoro synthesizes ~50ms PCM chunks (via asyncio.to_thread per chunk)
         each chunk streamed to the client as a WebSocket binary frame
         as soon as it's generated — it doesn't wait for the whole answer
         to finish synthesizing before sending the first chunk
```

### 5.2 Why each design choice exists

**Energy-based VAD with pre-roll + trailing trim, not a fixed on/off switch.** A single low-energy chunk mid-word doesn't end the recording — `silence_count` only increments while recording and resets the instant speech resumes, so natural pauses inside a sentence don't truncate it. The two things that *did* need fixing:
- **Pre-roll** (`VoiceSession.pre_roll`, a 10-chunk/~200ms ring buffer always kept while idle): without it, the first ~100-200ms of a word — spoken before its energy crosses the threshold — was silently dropped, mangling short words.
- **Trailing-silence trim** (`TRAILING_PAD_CHUNKS = 15`, ~300ms): the system has to wait out the full 1.2s silence window to *detect* the utterance ended, but sending Whisper all 1.2s of trailing silence made short utterances mostly-silence in the buffer — a well-known Whisper hallucination trigger (this is specifically why "Infoedge" was once transcribed as "excuse me"). Only speech + a small pad is actually sent to Whisper now.

**`vad_filter=True` + `initial_prompt` on Whisper**: `vad_filter` runs faster-whisper's own internal Silero VAD pass to strip any residual silence/noise before transcribing (belt-and-suspenders on top of the trim above). `initial_prompt` biases decoding toward expected vocabulary ("Abhinav Anand, InfoEdge, Naukri, NIT Jamshedpur...") because a base-sized Whisper model tends to mishear uncommon proper nouns as similar-sounding common phrases.

**A background task per utterance, not an inline `await`.** Originally, the main WebSocket loop ran Whisper → RAG → TTS *inline* before looping back to read the next message — which meant it literally could not read an incoming `"interrupt"` control message until the entire previous response had finished playing. Now, the moment an utterance's recording finishes, the VAD session state is reset **immediately** and the slow pipeline (`handle_utterance`) is spawned as an `asyncio.create_task`, so the main loop is free to keep servicing the socket (new audio, interrupt messages, ping/pong) the whole time.

**`asyncio.to_thread` around Whisper and Kokoro.** Both are synchronous, CPU-bound calls. A blocking call inside *any* coroutine freezes the entire single-threaded event loop — not just its own task — so even with the background-task fix above, the event loop would still stall completely for the duration of transcription or TTS synthesis. Wrapping each in `asyncio.to_thread` moves that CPU work onto a worker thread, keeping the loop free to do everything else concurrently.

**Server-side auto barge-in** (`voice_socket.py`, inside the "a new recording just started" branch): if `tts_worker.is_speaking()` is true when new speech is detected, the server interrupts the TTS worker itself — rather than relying solely on the client's independent, differently-thresholded VAD to decide to send an explicit `"interrupt"` message. The client's and server's energy thresholds don't match (client 1200, server 1000), so there's a band of speech loud enough for the server to notice but not loud enough for the client to think it's barge-in; having the server act on its own authoritative VAD closes that gap.

**`TTSWorker`**: one serial `asyncio.Queue` of sentences per connection; `self.interrupted` short-circuits both the queue-draining loop and the per-chunk send loop; `self.speaking` tracks whether a sentence is actively being generated/sent right now (used by the server-side auto barge-in above).

### 5.3 The client (`voice_test.py`) — a CLI test harness, not production UI

Captures mic audio via `sounddevice.InputStream` (callback handed to the asyncio loop via `call_soon_threadsafe`), plays TTS back via `sounddevice.RawOutputStream`. Its own local VAD exists for exactly one purpose: detecting *you* talking over the AI so it can locally clear its buffered playback and tell the server to stop — it is explicitly **not** responsible for deciding when your utterance has ended (that's the server's job).

---

## 6. React Frontend (`rag-ui`)

Vite + React 19 + Tailwind + React Router + React Hook Form + axios. **Current state: early scaffold.** `Login.jsx` is the only fully built page (email/password form, calls `POST /users/login`, stores the JWT in `localStorage` via `AuthContext`). `Dashboard.jsx`, `Register.jsx`, and `Chat.jsx` are all one-line placeholders (`<h1>...</h1>`) — routing, auth guarding (`ProtectedRoute`), and the API layer (`chatApi`, `chatbotApi`, `documentApi`, `authApi` — all thin axios wrappers) are wired up and ready, but the actual chat/document/dashboard UI hasn't been built yet.

`axios.js` attaches the JWT from `localStorage` as `Authorization: Bearer <token>` on every request and redirects to `/` on a 401 response.

---

## 7. End-to-End Flows

### 7.1 Document upload
`rag-ui` (not yet built) or any client → `POST /documents/add/{chatbotId}` → Tika extracts text → chunked (500 chars, 100 overlap) → each chunk embedded via the Python microservice → upserted into Pinecone namespace `chatbot_<id>` with `{"text": chunk}` metadata → a `(document_id, chunk_index, vector_id)` row recorded in MySQL.

### 7.2 Text chat
UI → `POST /chatbots/{id}/chat {message}` → embed the question (`/embed_query`, "query: " prefix) → Pinecone top-3 search in `chatbot_<id>` → join the 3 chunk texts → build the single-message prompt → Groq (`openai/gpt-oss-20b`) → `{answer, tokens, latency}` back to the UI.

### 7.3 Voice conversation
1. You speak → mic → 20ms PCM chunks over WebSocket to `/voice`.
2. Server VAD accumulates the utterance (with pre-roll), detects 1.2s of trailing silence, trims to ~300ms of pad, hands off to a background task.
3. Background task: Whisper transcribes (threaded) → the transcript is POSTed to the **same** `voice-query` endpoint as above (so it goes through the identical RAG pipeline) → the answer text is sent back to the client immediately → the answer is also pushed into the TTS worker's queue.
4. TTS worker synthesizes ~50ms PCM chunks with Kokoro (threaded) and streams each one back over the WebSocket as a binary frame — far faster than real-time, so the client ends up with a large backlog queued for playback.
5. If you start talking again while that backlog is still playing: the server's own VAD notices speech starting and auto-interrupts the TTS worker (stopping any further generation/sending); independently, the client's local VAD (if it crosses its own, higher threshold) sends an explicit `interrupt`, which locally clears its buffered-but-unplayed audio. Either mechanism firing cuts the old response off.

---

## 8. Debugging Case Studies

These were real bugs found and fixed while hardening the voice pipeline — good material for "tell me about a hard bug you debugged."

**1. Short words transcribed as nonsense ("Infoedge" → "excuse me").**
Root cause: two compounding issues. (a) The VAD only started buffering once a chunk's *own* energy crossed the threshold, silently dropping the quiet onset of a word. (b) The buffer kept the full 1.2s of trailing silence needed to *detect* end-of-utterance, so a short word's audio was mostly silence by the time it reached Whisper — a known hallucination trigger. Fixed with a pre-roll ring buffer plus trimming the silence sent to Whisper down to ~300ms, plus enabling Whisper's own Silero VAD filter and an `initial_prompt` biasing known vocabulary.

**2. The whole app disconnecting mid-reply, and barge-in not working.**
Root cause: the **client's** `audio_player()` called `sounddevice`'s blocking `stream.write()` directly inside an `async def`. Since asyncio is single-threaded, that one call froze the *entire* client event loop for as long as the audio took to play — including the loop that needed to answer the server's WebSocket ping (causing a `ping_timeout` disconnect on long replies) and the loop that needed to send an `interrupt` message. Fixed by moving the write to `asyncio.to_thread`.

**3. Interrupt only "took" after several tries, and the old response somehow still finished.**
Root cause: symmetrical to #2, but on the **server**. The main WebSocket loop ran Whisper → RAG → TTS *inline*, awaiting `tts_worker.wait_until_done()` before it would call `websocket.receive()` again — so an incoming `interrupt` message physically could not be read until the current response had already finished. Fixed by resetting VAD state immediately and running the Whisper/RAG/TTS pipeline as a detached `asyncio.create_task`, keeping the receive loop free at all times.

**4. Interrupt still inconsistent even after the above.**
Root cause: `whisper_service.transcribe()` and Kokoro's `generate()` are both synchronous, CPU-bound calls with no internal `await`. Even running inside a separate `asyncio` task, a blocking call freezes the *whole* event loop (asyncio is cooperative, single-threaded — "separate task" doesn't mean "separate thread"). So the server still couldn't read an `interrupt` message while actively transcribing or synthesizing. Fixed by wrapping both in `asyncio.to_thread`.

**5. Still flaky — turned out to be the debug logging itself.**
A per-chunk debug print ran at ~50Hz, and one of the lines called `len(session.buffer.get_audio())`, which does `b"".join(all_chunks)` — **rebuilding the entire recorded utterance from scratch on every single chunk** just to print its length. Real, continuous, unnecessary CPU work on the hot path. Fixed by maintaining a running byte counter on the buffer instead of rejoining, and gating all per-chunk logging behind a flag defaulted to off.

**6. The real final one: "the AI's second answer only starts after the first one finishes playing," even with all of the above fixed.**
Root cause: the client set `is_playing_tts = False` the instant it received the server's `"assistant_done"` message. But the server sends that message as soon as it's finished *sending* all the TTS audio — which, because sending isn't throttled to real-time, happens seconds before the client actually *finishes playing* a long reply (this is also why huge numbers of chunks were seen queued up locally). So the client's own barge-in check (`if is_speech and is_playing_tts`) was looking at a flag that had already gone false while you were still audibly hearing the AI talk — meaning it silently never fired for the back half of any response. Fixed by splitting the concept in two: `response_fully_sent` (server said done) vs. `is_playing_tts` (local queue has actually drained), and only clearing the latter once both are true.

The throughline across #2–#6: **in an asyncio system, "it's in a task/thread-looking construct" is not the same as "it won't block the thing I care about."** Every one of these was some flavor of "something that looks concurrent was actually blocking the one thing that needed to stay responsive."

---

## 9. Known Issues / Tech Debt (consolidated)

- **Secrets committed in plaintext**: MySQL password, Pinecone key, Groq key, JWT secret all live as literal values in `application.properties`. A hardcoded JWT also sits in `embedding_services/services/rag_service.py` and in `rag-ui/src/api/chatApi.js` (leftover from testing). None of these three extra files are currently tracked by git, but there's no root `.gitignore` protecting `.env`, so this is one careless `git add -A` away from being a real leak.
- **No ownership/authorization checks** beyond "is logged in" — any authenticated user can read/write any other user's chatbots or documents.
- **`GET /users` leaks password hashes** — the `User` model's hash field is serialized straight into the API response with no DTO masking.
- **No conversation memory anywhere** — `sessionId` is accepted by the voice endpoint and tracked client-side by `ConversationSession`, but never actually used to give the LLM any chat history. Every question (text or voice) is answered with zero awareness of prior turns.
- **`pinecone_namespace` DB column is dead** — the namespace is always recomputed as `"chatbot_" + id`, ignoring whatever's stored.
- **Fixed `topK=3`, no similarity threshold** on Pinecone search — irrelevant chunks get included just as readily as good ones if nothing better exists.
- **Character-based chunking** (not token-based), can split mid-word.
- **Self-contradicting RAG prompt** text, no system/user role separation sent to Groq.
- **A malformed JWT throws uncaught and surfaces as a 500**, not a 401.
- **Dead code**: `LLMService.java` (Ollama/gemma:2b prototype), `DocumentRepository.getAllDocuments()`, `UserNotFoundException` (never thrown), and on the Python side `demotest.py` / `websocket/demo.py` / `test_kokoro.py` (all unused experiments — `demotest.py` also has a stray API-key-looking string pasted into it, worth double-checking and removing).
- **No streaming LLM/TTS** — the full RAG answer is generated before TTS starts on it; true low-latency streaming (token-by-token → sentence-by-sentence TTS) isn't implemented.
- **No echo cancellation** — talking over the AI through a laptop's built-in speaker/mic can be suppressed by the OS/hardware's own echo suppression before it ever reaches this code (observed directly while debugging — switching to headphones/earbuds was needed to get barge-in to register at all).

---

## 10. Likely Interview Questions

**"Walk me through what happens when a user asks a question."**
Text → embedded via the Python microservice → Pinecone similarity search (top 3, namespace scoped to that chatbot) → the 3 chunks joined into a context block → a single prompt (context + question) sent to Groq → answer returned. Voice is identical from the RAG step onward — the only difference is *how the text arrives* (Whisper transcription instead of a typed message) and *how the answer leaves* (streamed TTS audio instead of just JSON).

**"How do you keep vector storage and relational storage from duplicating data?"**
Pinecone's metadata holds the actual chunk text (it needs it anyway, to return at query time); MySQL only ever stores the pointers — `document_id`, `chunk_index`, `vector_id`. The relational DB doesn't grow with document content.

**"How does barge-in actually work?"**
Two independent triggers, either of which stops the current TTS: (1) the server's own VAD noticing new speech start while `tts_worker.is_speaking()` is true, and (2) the client's local VAD (tuned for this specific purpose, separate from the server's utterance-detection VAD) crossing its own threshold while actively playing audio, which sends an explicit `interrupt` message and locally clears its buffered playback queue immediately rather than waiting on a round trip.

**"What was the hardest bug in this project?"**
See §8 — the short version: a chain of "this looks concurrent but actually isn't," each one hiding the next, until the responsiveness of the realtime WebSocket loop was fully restored. The very last one (`is_playing_tts` tracking "server done sending" instead of "client done playing") is a good one to lead with — it's subtle, it's exactly the kind of bug that *looks* like a completely different system (TTS queueing) when the real cause is a one-line state-tracking mistake in the client.

**"What would you do differently / what's missing for production?"**
Session-aware multi-turn conversation (the groundwork — `sessionId` — is already threaded through but unused), proper per-user authorization checks, externalized secrets, a similarity threshold on retrieval, sentence-level streaming for lower perceived latency, and real acoustic echo cancellation instead of relying on energy-threshold VAD alone.
