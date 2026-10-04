import numpy as np
from kokoro_onnx import Kokoro


class TTSService:

    def __init__(self):

        self.kokoro = Kokoro(
            "models/kokoro-v1.0.int8.onnx",
            "models/voices-v1.0.bin"
        )
        self.voice = "af_heart"

    def generate(self, text: str):

        samples, sample_rate = self.kokoro.create(
            text,
            voice=self.voice,
            speed=1.0,
            lang="en-us"
        )

        # float32 -> int16 PCM
        pcm = (
            np.clip(samples, -1.0, 1.0) * 32767
        ).astype(np.int16)

        # Stream small chunks (~50ms)
        chunk_size = sample_rate // 20

        for i in range(0, len(pcm), chunk_size):
            yield pcm[i:i + chunk_size].tobytes()


tts_service = TTSService()