import sounddevice as sd
import numpy as np
from services.tts_service import TTSService

print("Loading Kokoro...")

tts = TTSService()

print("Loaded!")

for sentence in ("Hello Abhinav.", "I am now integrated into your assistant."):

    pcm_chunks = list(tts.generate(sentence))

    audio = np.frombuffer(b"".join(pcm_chunks), dtype=np.int16)

    sd.play(audio, samplerate=24000)
    sd.wait()

print("Done")