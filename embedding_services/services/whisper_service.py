import numpy as np
from faster_whisper import WhisperModel


class WhisperService:

    def __init__(self):

        print("Loading Whisper Model...")

        self.model = WhisperModel(
            "base",
            device="cpu",        # change to "cuda" if using GPU
            compute_type="int8"
        )

        print("Whisper Loaded")

    # Biases decoding toward vocabulary we know will come up (names,
    # employer) that a base-sized model otherwise tends to mishear as
    # similar-sounding common phrases (e.g. "Abhinav" -> "excuse me",
    # "Infoedge" -> "up and out"). Update this if the chatbot's domain
    # changes.
    INITIAL_PROMPT = (
        "Abhinav Anand, InfoEdge, Naukri, NIT Jamshedpur, "
        "Java, Spring Boot, Kafka, Kubernetes."
    )

    def transcribe(self, audio_bytes: bytes) -> str:

        # int16 PCM
        audio = np.frombuffer(audio_bytes, dtype=np.int16)

        # convert to float32 [-1,1]
        audio = audio.astype(np.float32) / 32768.0

        # vad_filter runs faster-whisper's built-in Silero VAD over the
        # audio first, so leftover silence/noise (e.g. the trailing pad
        # we keep after an utterance) doesn't get hallucinated into text.
        segments, info = self.model.transcribe(
            audio,
            language="en",
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=500),
            initial_prompt=self.INITIAL_PROMPT
        )

        text = ""

        for segment in segments:
            text += segment.text

        return text.strip()


whisper_service = WhisperService()