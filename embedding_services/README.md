---
title: Sakha Voice Service
emoji: 🎙️
colorFrom: blue
colorTo: purple
sdk: docker
app_port: 9000
---

# Sakha voice/embedding service

FastAPI service handling two things:
- `/embed`, `/embed_query` - sentence embeddings (BAAI/bge-base-en-v1.5), called by the Java backend.
- `/voice` - the real-time voice WebSocket (VAD, Whisper transcription, Kokoro TTS).

The YAML block above is Hugging Face Spaces' required metadata when this
directory is deployed there as a Docker Space - harmless if you're
deploying somewhere else, just ignored.

Environment variables (see `.env.example`):
- `GROQ_API_KEY` - not used by the live path, only the dead `services/llm_service.py`.
- `JAVA_BACKEND_URL` - where the Java backend lives (used by `rag_service.py`).
