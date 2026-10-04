from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from services.voice_session import VoiceSession
from services.whisper_service import whisper_service
from services.llm_service import llm_service
from services.conversation_session import ConversationSession
from services.tts_service import tts_service
from services.tts_worker import TTSWorker

import json
import traceback

router = APIRouter()

MAX_SILENCE_CHUNKS = 25


@router.websocket("/voice")
async def voice_socket(websocket: WebSocket):

    print("VOICE ENDPOINT HIT")

    await websocket.accept()
    print("ACCEPTED")

    session = VoiceSession()
    conversation = ConversationSession()

    tts_worker = TTSWorker(websocket, tts_service)
    await tts_worker.start()

    try:

        while True:

            control = json.loads(await websocket.receive_text())
            audio = await websocket.receive_bytes()

            if control["type"] == "speech":

                # interrupt immediately when user starts speaking
                if session.buffer.size() == 0:
                    print("[SERVER] INTERRUPT RECEIVED")
                    await tts_worker.interrupt()

                if not session.is_recording():

                    session.start()

                    print("\n==============================")
                    print("Recording Started")
                    print("==============================")

                session.reset_silence()
                session.buffer.add_chunk(audio)

            elif control["type"] == "silence":

                if session.is_recording():

                    session.increment_silence()
                    session.buffer.add_chunk(audio)

                    print(
                        f"Silence Chunks: {session.silence_count()}"
                    )

                    if session.silence_count() >= MAX_SILENCE_CHUNKS:

                        print("\n==============================")
                        print("Recording Finished")
                        print("==============================")

                        final_audio = session.buffer.get_audio()

                        print(
                            f"Final Audio Size: {len(final_audio)} bytes"
                        )

                        try:

                            print("Running Whisper...")

                            text = whisper_service.transcribe(final_audio)

                            print("=" * 60)
                            print("TRANSCRIPT:")
                            print(text)
                            print("=" * 60)

                            conversation.add_user(text)

                            await websocket.send_json(
                                {
                                    "type": "assistant",
                                    "transcript": text
                                }
                            )

                            # user finished speaking; allow TTS again
                            tts_worker.reset_interrupt()

                            response = ""
                            sentence_buffer = ""

                            stream = llm_service.generate_stream(
                                conversation.get_messages()
                            )

                            for chunk in stream:

                                # stop generation immediately if interrupted
                                if tts_worker.interrupted:
                                    print("[LLM] INTERRUPTED")
                                    break

                                delta = chunk.choices[0].delta.content

                                if not delta:
                                    continue

                                response += delta
                                sentence_buffer += delta

                                await websocket.send_json(
                                    {
                                        "type": "assistant_chunk",
                                        "chunk": delta
                                    }
                                )

                                if sentence_buffer.strip().endswith(
                                    (".", "!", "?")
                                ):

                                    sentence = sentence_buffer.strip()

                                    print(f"[QUEUE] {sentence}")

                                    await tts_worker.speak(sentence)

                                    sentence_buffer = ""

                            # queue remaining text only if not interrupted
                            if (
                                sentence_buffer.strip()
                                and not tts_worker.interrupted
                            ):

                                print(
                                    f"[QUEUE] {sentence_buffer.strip()}"
                                )

                                await tts_worker.speak(
                                    sentence_buffer.strip()
                                )

                            if not tts_worker.interrupted:

                                conversation.add_assistant(response)

                                await tts_worker.wait_until_done()

                                await websocket.send_json(
                                    {
                                        "type": "assistant_done"
                                    }
                                )

                                print("Streaming completed.")

                            else:

                                print(
                                    "[SERVER] RESPONSE CANCELLED DUE TO INTERRUPT"
                                )

                        except WebSocketDisconnect:

                            print("Client disconnected during response")
                            break

                        except Exception:

                            print("Generation failed")
                            traceback.print_exc()

                        session.buffer.clear()
                        session.stop()
                        session.reset_silence()

                        print("Session Reset.")

            print(
                f"Chunks: {session.buffer.size()} | "
                f"Total Bytes: {len(session.buffer.get_audio())}"
            )

    except WebSocketDisconnect:

        print("Client Disconnected")

    except Exception:

        print("Unexpected Error")
        traceback.print_exc()

    finally:

        await tts_worker.stop()

        print("Voice websocket closed.")