from collections import deque

from services.audio_buffer import AudioBuffer


# Chunks of raw audio to keep from *before* speech is detected, so the
# first phoneme of a word isn't lost while the VAD is still deciding.
# 10 chunks * 20ms = ~200ms.
PRE_ROLL_CHUNKS = 10


class VoiceSession:

    def __init__(self):
        self.buffer = AudioBuffer()
        self.recording = False
        self.silence_chunks = 0
        self.pre_roll = deque(maxlen=PRE_ROLL_CHUNKS)
        self.last_speech_chunk_index = 0

    def start(self):
        self.recording = True

    def stop(self):
        self.recording = False

    def is_recording(self):
        return self.recording

    def reset_silence(self):
        self.silence_chunks = 0

    def increment_silence(self):
        self.silence_chunks += 1

    def silence_count(self):
        return self.silence_chunks

    def track_pre_roll(self, chunk: bytes):
        self.pre_roll.append(chunk)

    def consume_pre_roll(self):
        chunks = list(self.pre_roll)
        self.pre_roll.clear()
        return chunks

    def mark_speech_chunk(self):
        self.last_speech_chunk_index = self.buffer.size()

    def reset(self):
        self.buffer.clear()
        self.recording = False
        self.silence_chunks = 0
        self.last_speech_chunk_index = 0