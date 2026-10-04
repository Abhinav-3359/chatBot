class AudioBuffer:

    def __init__(self):
        self.chunks = []
        self.total_bytes = 0

    def add_chunk(self, chunk: bytes):
        self.chunks.append(chunk)
        self.total_bytes += len(chunk)

    def get_audio(self) -> bytes:
        return b"".join(self.chunks)

    def clear(self):
        self.chunks.clear()
        self.total_bytes = 0

    def size(self):
        return len(self.chunks)

    def byte_count(self):
        return self.total_bytes