from fastapi import APIRouter, WebSocket, WebSocketDisconnect

import asyncio
import json
import traceback
import numpy as np

from services.voice_session import VoiceSession
from services.whisper_service import whisper_service
from services.tts_service import tts_service
from services.tts_worker import TTSWorker
from services.rag_service import rag_service


router = APIRouter()


MAX_SILENCE_CHUNKS = 60
ENERGY_THRESHOLD = 1000

# How much trailing silence (in chunks) to actually keep when building
# the audio sent to Whisper. We need to wait out MAX_SILENCE_CHUNKS to
# *detect* end-of-utterance, but sending all of that silence to Whisper
# makes short utterances mostly-silence, which is a known hallucination
# trigger (e.g. "Infoedge" -> "excuse me"). 15 chunks * 20ms = ~300ms,
# which is enough trailing context without drowning out the speech.
TRAILING_PAD_CHUNKS = 15

# Per-chunk debug logging (runs at ~50Hz while a connection is active).
# Every print() here is a blocking stdout write, and at that rate it's
# enough sustained I/O to itself eat into the event loop's responsiveness
# budget - keep this off unless actively debugging VAD behavior.
DEBUG_VAD = False


async def handle_utterance(
    websocket: WebSocket,
    tts_worker: TTSWorker,
    chatbot_id: int,
    token: str,
    final_audio: bytes
):
    """
    Runs Whisper -> RAG -> TTS for one finished utterance.

    This used to run inline in the main receive loop, with the loop
    awaiting it before calling websocket.receive() again. That meant
    the server couldn't read a client's "interrupt" message (or any
    further mic audio) until the *entire* response had finished
    generating and playing, so barge-in only ever took effect after
    the fact. Running it as its own task keeps the main loop free to
    receive and react to interrupt/audio messages immediately.
    """

    try:

        # =================================================
        # WHISPER
        # =================================================

        print(
            "Running Whisper..."
        )

        # whisper_service.transcribe() is a blocking, CPU-bound call
        # (faster-whisper on CPU). Called directly it would freeze the
        # whole event loop - including the main receive loop reading
        # the next "interrupt" message - for as long as transcription
        # takes. Run it on a worker thread instead.
        text = await asyncio.to_thread(
            whisper_service.transcribe,
            final_audio
        )

        print()
        print(
            "=" * 60
        )
        print(
            "TRANSCRIPT:"
        )
        print(text)
        print(
            "=" * 60
        )

        # =================================================
        # EMPTY TRANSCRIPT
        # =================================================

        if not text or not text.strip():

            print(
                "Empty transcript, ignoring."
            )

            return

        text = text.strip()

        # =================================================
        # SEND TRANSCRIPT TO CLIENT
        # =================================================

        await websocket.send_json(
            {
                "type": "assistant",
                "transcript": text
            }
        )

        # =================================================
        # RESET TTS INTERRUPT STATE
        # =================================================

        tts_worker.reset_interrupt()

        # =================================================
        # RAG
        # =================================================

        print()
        print(
            "Calling Java RAG service..."
        )

        response = (
            await rag_service.ask(
                chatbot_id=chatbot_id,
                text=text,
                token=token
            )
        )

        print()
        print(
            "=" * 60
        )
        print(
            "RAG RESPONSE:"
        )
        print(response)
        print(
            "=" * 60
        )

        # =================================================
        # SEND RESPONSE TEXT
        # =================================================

        await websocket.send_json(
            {
                "type": "assistant_chunk",
                "chunk": response
            }
        )

        # =================================================
        # TTS
        # =================================================

        print(
            "Starting TTS..."
        )

        await tts_worker.speak(
            response
        )

        await tts_worker.wait_until_done()

        # =================================================
        # RESPONSE COMPLETE
        # =================================================

        await websocket.send_json(
            {
                "type": "assistant_done"
            }
        )

        print(
            "RAG response completed."
        )

    except WebSocketDisconnect:

        print(
            "Client disconnected during response."
        )

    except Exception:

        print(
            "Generation failed."
        )

        traceback.print_exc()

        # =================================================
        # INFORM CLIENT
        # =================================================

        try:

            await websocket.send_json(
                {
                    "type": "error",
                    "message":
                        "Failed to generate response."
                }
            )

        except Exception:

            pass


@router.websocket("/voice")
async def voice_socket(websocket: WebSocket):

    print()
    print("=" * 60)
    print("VOICE ENDPOINT HIT")
    print("=" * 60)

    # The client authenticates as itself (its own /users/login JWT) and
    # says which chatbot it wants, rather than this service using one
    # shared/static credential for every connection - the Java backend's
    # ownership checks then run as whoever this token actually belongs
    # to, same as a normal typed-chat request.
    raw_chatbot_id = websocket.query_params.get("chatbotId")
    token = websocket.query_params.get("token")

    if not raw_chatbot_id or not token:

        print("Rejecting connection: missing chatbotId or token.")

        await websocket.close(code=4401)

        return

    try:
        chatbot_id = int(raw_chatbot_id)

    except ValueError:

        print("Rejecting connection: chatbotId is not a valid integer.")

        await websocket.close(code=4401)

        return

    await websocket.accept()

    print("Chatbot ID:", chatbot_id)
    print("ACCEPTED")

    session = VoiceSession()

    tts_worker = TTSWorker(
        websocket,
        tts_service
    )

    await tts_worker.start()

    # Keep references to spawned utterance tasks so they aren't
    # garbage-collected mid-flight, and so we can clean them up on
    # disconnect.
    background_tasks = set()

    try:

        while True:

            message = await websocket.receive()

            # =========================================================
            # CLIENT DISCONNECTED
            # =========================================================

            if message["type"] == "websocket.disconnect":

                print("Client disconnected.")

                break

            # =========================================================
            # TEXT CONTROL MESSAGE
            # =========================================================

            if message.get("text") is not None:

                try:

                    control = json.loads(
                        message["text"]
                    )

                except json.JSONDecodeError:

                    print(
                        "Invalid control message:",
                        message["text"]
                    )

                    continue

                if control.get("type") == "interrupt":

                    print(
                        "[SERVER] INTERRUPT RECEIVED"
                    )

                    await tts_worker.interrupt()

                continue

            # =========================================================
            # BINARY AUDIO
            # =========================================================

            if message.get("bytes") is None:

                continue

            audio = message["bytes"]

            if not audio:

                continue

            # =========================================================
            # ENERGY / VAD
            # =========================================================

            samples = np.frombuffer(
                audio,
                dtype=np.int16
            )

            if len(samples) == 0:

                continue

            energy = np.abs(samples).mean()

            is_speech = (
                energy >= ENERGY_THRESHOLD
            )

            if DEBUG_VAD:

                print(
                    f"[VAD] energy={energy:.0f} "
                    f"speech={is_speech} "
                    f"recording={session.is_recording()} "
                    f"silence={session.silence_count()}"
                )

            # =========================================================
            # SPEECH
            # =========================================================

            if is_speech:

                if session.buffer.size() == 0:

                    print()
                    print(
                        "[SERVER] USER STARTED SPEAKING"
                    )

                if not session.is_recording():

                    # Auto barge-in: the server's own VAD just detected
                    # the user starting to talk again. Don't rely on
                    # the client's separate "interrupt" message alone
                    # (its energy threshold doesn't match the server's,
                    # so quieter speech can trip this VAD but not the
                    # client's barge-in check) — if TTS is currently
                    # speaking, cut it off now rather than letting this
                    # new utterance's answer queue up behind it.
                    if tts_worker.is_speaking():

                        print(
                            "[SERVER] Barge-in: new speech detected "
                            "while TTS active, interrupting."
                        )

                        await tts_worker.interrupt()

                    # Prepend whatever we buffered just before speech
                    # was detected, so the first phoneme isn't lost.
                    for pre_roll_chunk in session.consume_pre_roll():

                        session.buffer.add_chunk(
                            pre_roll_chunk
                        )

                    session.start()

                    print()
                    print(
                        "=============================="
                    )
                    print(
                        "Recording Started"
                    )
                    print(
                        "=============================="
                    )

                session.reset_silence()

                session.buffer.add_chunk(
                    audio
                )

                session.mark_speech_chunk()

            # =========================================================
            # SILENCE
            # =========================================================

            else:

                if session.is_recording():

                    session.increment_silence()

                    session.buffer.add_chunk(
                        audio
                    )

                    if DEBUG_VAD:

                        print(
                            f"Silence Chunks: "
                            f"{session.silence_count()}"
                        )

                    # =================================================
                    # END OF UTTERANCE
                    # =================================================

                    if (
                        session.silence_count()
                        >= MAX_SILENCE_CHUNKS
                    ):

                        print()
                        print(
                            "=============================="
                        )
                        print(
                            "Recording Finished"
                        )
                        print(
                            "=============================="
                        )

                        # Trim the bulk of the trailing silence we had
                        # to wait through to *detect* end-of-utterance.
                        # Keeping all of it makes short utterances
                        # mostly-silence, which is a known Whisper
                        # hallucination trigger.
                        trimmed_length = min(
                            session.buffer.size(),
                            session.last_speech_chunk_index
                            + TRAILING_PAD_CHUNKS
                        )

                        final_audio = b"".join(
                            session.buffer.chunks[:trimmed_length]
                        )

                        print(
                            "Final Audio Size:",
                            len(final_audio),
                            "bytes",
                            f"(trimmed from "
                            f"{len(session.buffer.get_audio())} bytes)"
                        )

                        # Reset recording state immediately (not in a
                        # finally block after the pipeline runs) so the
                        # VAD is free to capture the next utterance
                        # right away, instead of only after Whisper/RAG/
                        # TTS for this one have finished.
                        session.reset()

                        print(
                            "Session Reset."
                        )

                        # Run Whisper/RAG/TTS in the background instead
                        # of awaiting it here. Awaiting it inline would
                        # stop this loop from calling websocket.receive()
                        # again until the full response had finished
                        # generating *and* playing, which meant an
                        # "interrupt" message sent by the client during
                        # that window wasn't read until it was already
                        # too late.
                        task = asyncio.create_task(
                            handle_utterance(
                                websocket,
                                tts_worker,
                                chatbot_id,
                                token,
                                final_audio
                            )
                        )

                        background_tasks.add(task)

                        task.add_done_callback(
                            background_tasks.discard
                        )

            # =========================================================
            # PRE-ROLL TRACKING (always, regardless of speech/silence)
            # =========================================================

            if not session.is_recording():

                session.track_pre_roll(audio)

            # =========================================================
            # DEBUG
            # =========================================================

            if DEBUG_VAD:

                print(
                    f"Chunks: "
                    f"{session.buffer.size()} | "
                    f"Total Bytes: "
                    f"{session.buffer.byte_count()}"
                )

    except WebSocketDisconnect:

        print(
            "Client Disconnected"
        )

    except Exception:

        print(
            "Unexpected Error"
        )

        traceback.print_exc()

    finally:

        for task in background_tasks:

            task.cancel()

        try:

            await tts_worker.stop()

        except Exception:

            traceback.print_exc()

        print(
            "Voice websocket closed."
        )