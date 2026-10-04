import asyncio
import getpass
import json
import sys
import urllib.parse

import httpx
import numpy as np
import sounddevice as sd
import websockets


SAMPLE_RATE = 16000
TTS_SAMPLE_RATE = 24000

CHANNELS = 1

BLOCK_SIZE = 320

ENERGY_THRESHOLD = 1200

CHATBOT_ID = 2

JAVA_LOGIN_URL = "http://localhost:8080/users/login"


def login() -> str:
    """
    Logs in as a real user against the Java backend and returns their
    JWT, so the voice session acts as whoever is actually running this
    client - not a shared/static credential. Runs once, synchronously,
    before the websocket connects.
    """

    print("Log in to use voice chat.")

    email = input("Email: ").strip()
    password = getpass.getpass("Password: ")

    try:

        response = httpx.post(
            JAVA_LOGIN_URL,
            json={
                "email": email,
                "password": password
            },
            timeout=10.0
        )

        response.raise_for_status()

        return response.json()["data"]["token"]

    except Exception:

        print("Login failed - check your email/password and that the "
              "Java backend is running on localhost:8080.")

        import traceback
        traceback.print_exc()

        sys.exit(1)


audio_queue = asyncio.Queue()
playback_queue = asyncio.Queue()


loop = None

is_playing_tts = False

# True once the server says it has finished *sending* this response's
# audio. This is NOT the same as the client having finished *playing*
# it - the server sends audio far faster than real-time (that's why a
# long reply can queue up thousands of chunks locally), so
# "assistant_done" routinely arrives seconds before playback actually
# ends. is_playing_tts must stay True until the local queue genuinely
# drains, otherwise barge-in detection silently stops working for the
# remainder of every response.
response_fully_sent = False


# =========================================================
# MICROPHONE CALLBACK
# =========================================================

def audio_callback(
    indata,
    frames,
    time,
    status
):

    if status:

        print(
            "Audio status:",
            status
        )

    if loop is None:

        return

    loop.call_soon_threadsafe(
        audio_queue.put_nowait,
        bytes(indata)
    )


# =========================================================
# CLEAR TTS AUDIO
# =========================================================

def clear_playback_queue():

    cleared = 0

    while not playback_queue.empty():

        try:

            playback_queue.get_nowait()

            playback_queue.task_done()

            cleared += 1

        except asyncio.QueueEmpty:

            break

    if cleared:

        print(
            f"Cleared {cleared} TTS chunks."
        )


# =========================================================
# SEND MICROPHONE AUDIO
# =========================================================

async def send_audio(ws):

    global is_playing_tts

    while True:

        audio = await audio_queue.get()

        try:

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

            # ==============================================
            # USER STARTED SPEAKING DURING TTS
            # ==============================================

            if (
                is_speech
                and is_playing_tts
            ):

                print()
                print(
                    "[AUDIO INTERRUPTED]"
                )

                clear_playback_queue()

                await ws.send(
                    json.dumps(
                        {
                            "type": "interrupt"
                        }
                    )
                )

                is_playing_tts = False

            # ==============================================
            # SEND CONTROL MESSAGE
            # ==============================================

            msg_type = (
                "speech"
                if is_speech
                else "silence"
            )

            await ws.send(
                json.dumps(
                    {
                        "type": msg_type
                    }
                )
            )

            # ==============================================
            # SEND AUDIO
            # ==============================================

            await ws.send(audio)

        except websockets.ConnectionClosed as e:

            print()
            print(
                "Send loop stopped."
            )
            print(
                "Code:",
                e.code
            )
            print(
                "Reason:",
                e.reason
            )

            return

        except Exception:

            print(
                "Error in send_audio"
            )

            import traceback

            traceback.print_exc()

            return


# =========================================================
# AUDIO PLAYER
# =========================================================

async def audio_player():

    global is_playing_tts

    stream = sd.RawOutputStream(
        samplerate=TTS_SAMPLE_RATE,
        channels=1,
        dtype="int16"
    )

    stream.start()

    print(
        "Audio player started."
    )

    try:

        while True:

            audio = (
                await playback_queue.get()
            )

            try:

                # stream.write() blocks in real time until the output
                # buffer has room. Running it inline would stall the
                # whole event loop for the length of the audio, which
                # starves send_audio() (barge-in never gets sent) and
                # the websocket's own ping/pong (causing disconnects
                # on long replies). Push it to a thread instead.
                await asyncio.to_thread(
                    stream.write,
                    audio
                )

            except Exception:

                import traceback

                traceback.print_exc()

            finally:

                playback_queue.task_done()

                # Only now - after actually finishing a write - is it
                # safe to consider playback over, and only if the
                # server isn't going to send any more for this turn.
                if (
                    response_fully_sent
                    and playback_queue.empty()
                ):

                    is_playing_tts = False

    finally:

        stream.stop()

        stream.close()

        print(
            "Audio player stopped."
        )


# =========================================================
# RECEIVE SERVER MESSAGES
# =========================================================

async def receive_messages(ws):

    global is_playing_tts, response_fully_sent

    try:

        async for message in ws:

            # ==============================================
            # SERVER TTS AUDIO
            # ==============================================

            if isinstance(message, bytes):

                is_playing_tts = True

                await playback_queue.put(
                    message
                )

                continue

            # ==============================================
            # SERVER JSON
            # ==============================================

            try:

                data = json.loads(message)

            except json.JSONDecodeError:

                print(
                    "Invalid server message:",
                    message
                )

                continue

            message_type = data.get(
                "type"
            )

            # ==============================================
            # USER TRANSCRIPT
            # ==============================================

            if message_type == "assistant":

                # A new response cycle is starting - don't let a
                # leftover "fully sent" flag from the previous turn
                # cause audio_player() to mark us "not playing" the
                # instant this turn's first chunk drains a short queue.
                response_fully_sent = False

                print()

                print(
                    "=" * 60
                )

                print(
                    "USER:"
                )

                print(
                    data.get(
                        "transcript",
                        ""
                    )
                )

                print(
                    "=" * 60
                )

                print()

                print(
                    "AI: ",
                    end="",
                    flush=True
                )

            # ==============================================
            # AI RESPONSE
            # ==============================================

            elif message_type == "assistant_chunk":

                print(
                    data.get(
                        "chunk",
                        ""
                    ),
                    end="",
                    flush=True
                )

            # ==============================================
            # AI RESPONSE COMPLETE
            # ==============================================

            elif message_type == "assistant_done":

                # Server has finished *sending* this turn's audio, not
                # finished the client *playing* it - audio_player()
                # decides when is_playing_tts actually goes False,
                # once its local queue is genuinely empty.
                response_fully_sent = True

                print()
                print()

            # ==============================================
            # ERROR
            # ==============================================

            elif message_type == "error":

                is_playing_tts = False

                print()
                print(
                    "[SERVER ERROR]"
                )

                print(
                    data.get(
                        "message",
                        "Unknown error"
                    )
                )

    except websockets.ConnectionClosed as e:

        print()

        print(
            "Receive loop stopped."
        )

        print(
            "Code:",
            e.code
        )

        print(
            "Reason:",
            e.reason
        )

    except Exception:

        print(
            "Error in receive_messages"
        )

        import traceback

        traceback.print_exc()


# =========================================================
# MAIN
# =========================================================

async def main(token: str):

    global loop

    loop = asyncio.get_running_loop()

    websocket_url = (
        f"ws://localhost:9000/voice"
        f"?chatbotId={CHATBOT_ID}"
        f"&token={urllib.parse.quote(token)}"
    )

    print()
    print(
        "=" * 60
    )

    print(
        "VOICE TEST CLIENT"
    )

    print(
        "=" * 60
    )

    print(
        "Chatbot ID:",
        CHATBOT_ID
    )

    print(
        "WebSocket:",
        "ws://localhost:9000/voice"
        f"?chatbotId={CHATBOT_ID}&token=<hidden>"
    )

    print()

    try:

        async with websockets.connect(
            websocket_url,
            ping_interval=20,
            ping_timeout=20
        ) as ws:

            print(
                "Connected"
            )

            print(
                "Speak into your microphone..."
            )

            print()

            with sd.InputStream(
                samplerate=SAMPLE_RATE,
                channels=CHANNELS,
                dtype="int16",
                blocksize=BLOCK_SIZE,
                callback=audio_callback
            ):

                await asyncio.gather(
                    send_audio(ws),
                    receive_messages(ws),
                    audio_player()
                )

    except websockets.ConnectionClosed as e:

        print()
        print(
            "WebSocket closed."
        )

        print(
            "Code:",
            e.code
        )

        print(
            "Reason:",
            e.reason
        )

    except KeyboardInterrupt:

        print(
            "Stopped by user."
        )

    except Exception:

        print(
            "Client failed."
        )

        import traceback

        traceback.print_exc()


# =========================================================
# ENTRY POINT
# =========================================================

if __name__ == "__main__":

    jwt_token = login()

    asyncio.run(main(jwt_token))