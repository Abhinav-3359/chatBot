import os

import httpx
from dotenv import load_dotenv

load_dotenv()


class RAGService:

    def __init__(self):
        self.base_url = os.getenv(
            "JAVA_BACKEND_URL", "http://localhost:8080"
        ) + "/chatbots"

    async def ask(
        self,
        chatbot_id: int,
        text: str,
        token: str
    ):
        """
        token is the caller's own JWT (from their own /users/login),
        passed in per-connection from the voice websocket - not a
        shared/static credential. The Java backend's ownership checks
        run as whoever this token belongs to, same as if they'd typed
        the question into the web UI.

        No session_id here anymore - the backend now keys conversation
        memory by (user, chatbot) from the token + chatbot_id alone, so
        voice and text chat against the same chatbot share one
        continuing conversation instead of each voice call starting a
        fresh, disconnected one.
        """

        url = f"{self.base_url}/{chatbot_id}/voice-query"

        payload = {
            "text": text
        }

        print()
        print("=" * 60)
        print("Calling Java RAG service...")
        print("URL:", url)
        print("Chatbot ID:", chatbot_id)
        print("Text:", text)
        print("=" * 60)

        try:

            async with httpx.AsyncClient(
                timeout=httpx.Timeout(
                    connect=10.0,
                    read=60.0,
                    write=10.0,
                    pool=10.0
                )
            ) as client:

                response = await client.post(
                    url,
                    json=payload,
                    headers={"Authorization": f"Bearer {token}"}
                )

            print("RAG STATUS:", response.status_code)
            print("RAG BODY:", response.text)

            response.raise_for_status()

            data = response.json()

            answer = data["data"]["answer"]

            print()
            print("=" * 60)
            print("RAG ANSWER:")
            print(answer)
            print("=" * 60)

            return answer

        except httpx.TimeoutException:

            print("RAG REQUEST TIMEOUT")

            raise

        except httpx.HTTPStatusError as e:

            print("RAG HTTP ERROR")
            print("Status:", e.response.status_code)
            print("Body:", e.response.text)

            raise

        except Exception:

            print("RAG REQUEST FAILED")

            import traceback
            traceback.print_exc()

            raise


rag_service = RAGService()
