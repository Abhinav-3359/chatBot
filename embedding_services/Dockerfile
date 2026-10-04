FROM python:3.12-slim

WORKDIR /app

# espeak-ng: required by misaki (Kokoro's G2P engine) for phonemization -
# confirmed as an actual dependency on the dev machine, not a guess.
# ffmpeg: commonly needed by audio ML pipelines for format handling.
# curl: fetches the Kokoro ONNX model files below.
RUN apt-get update && apt-get install -y --no-install-recommends \
    espeak-ng \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# kokoro-onnx doesn't bundle/auto-download its weights like the old
# torch-based kokoro package did - fetch the int8-quantized model
# (114MB vs 326MB full precision) and its voice bank once at build time.
RUN mkdir -p models && \
    curl -L -o models/kokoro-v1.0.int8.onnx \
        https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/kokoro-v1.0.int8.onnx && \
    curl -L -o models/voices-v1.0.bin \
        https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/voices-v1.0.bin

COPY . .

# Whisper + Kokoro (ONNX) + the fastembed model load into memory at
# startup (we watched this take real time locally) - give health checks
# on whatever platform runs this a generous grace period.
EXPOSE 9000

CMD ["sh", "-c", "uvicorn app:app --host 0.0.0.0 --port ${PORT:-9000}"]
