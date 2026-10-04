import os

from dotenv import load_dotenv
from groq import Groq

load_dotenv()


class LLMService:

    def __init__(self):

        self.client = Groq(
            api_key=os.getenv("GROQ_API_KEY")
        )

    def generate(self, messages: list) -> str:

        completion = self.client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=messages,
            temperature=0.7,
            max_completion_tokens=512
        )

        return completion.choices[0].message.content

    def generate_stream(self, messages: list):

        return self.client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=messages,
            temperature=0.7,
            max_completion_tokens=512,
            stream=True
        )


llm_service = LLMService()