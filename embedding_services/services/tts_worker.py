import asyncio


class TTSWorker:

    def __init__(self, websocket, tts_service):

        self.websocket = websocket
        self.tts_service = tts_service

        self.queue = asyncio.Queue()

        self.worker_task = None
        self.running = True

        # shared interrupt flag
        self.interrupted = False

        # True while a sentence has been dequeued and is actively being
        # generated/sent, including the moment between queue.get() and
        # the first chunk. Lets callers detect "AI is talking right
        # now" without depending on the queue being non-empty.
        self.speaking = False

    async def start(self):

        if self.worker_task is None:
            self.worker_task = asyncio.create_task(
                self._run()
            )

    async def speak(self, text: str):

        if text.strip() and not self.interrupted:
            await self.queue.put(text)

    async def interrupt(self):

        self.interrupted = True

        while not self.queue.empty():
            try:
                self.queue.get_nowait()
                self.queue.task_done()
            except asyncio.QueueEmpty:
                break

    def reset_interrupt(self):

        self.interrupted = False

    def is_speaking(self):

        return self.speaking

    async def wait_until_done(self):

        await self.queue.join()

    async def stop(self):

        self.running = False

        if self.worker_task:

            self.worker_task.cancel()

            try:
                await self.worker_task
            except asyncio.CancelledError:
                pass

    async def _run(self):

        while self.running:

            sentence = await self.queue.get()

            self.speaking = True

            try:

                if self.interrupted:
                    continue

                print(f"[TTS] {sentence}")

                # tts_service.generate() is a plain synchronous
                # generator wrapping Kokoro model inference, which is
                # CPU-bound with no internal awaits. Iterating it
                # directly here would block the *entire* event loop
                # (every connection, not just this task) for however
                # long synthesis takes, which starves the main receive
                # loop and delays "interrupt" messages. Pull each item
                # from it on a worker thread instead, so the loop stays
                # free between chunks.
                generator = self.tts_service.generate(sentence)

                _SENTINEL = object()

                while True:

                    if self.interrupted:
                        print("[TTS] INTERRUPTED")
                        break

                    audio_chunk = await asyncio.to_thread(
                        next,
                        generator,
                        _SENTINEL
                    )

                    if audio_chunk is _SENTINEL:
                        break

                    if self.interrupted:
                        print("[TTS] INTERRUPTED")
                        break

                    await self.websocket.send_bytes(audio_chunk)

                print("[TTS] Finished")

            except Exception as e:

                print("TTS Error:", e)

            finally:

                self.speaking = False

                self.queue.task_done()