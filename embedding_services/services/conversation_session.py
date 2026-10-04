class ConversationSession:

    def __init__(self):

        self.messages = [
            {
                "role": "system",
                "content": (
                    "You are a helpful AI voice assistant. "
                    "Keep answers conversational and concise unless the user asks for details."
                )
            }
        ]

    def add_user(self, text: str):
        self.messages.append(
            {
                "role": "user",
                "content": text
            }
        )

        if len(self.messages) > 20:
            self.messages = [self.messages[0]] + self.messages[-19:]

    def add_assistant(self, text: str):
        self.messages.append(
            {
                "role": "assistant",
                "content": text
            }
        )

        if len(self.messages) > 20:
            self.messages = [self.messages[0]] + self.messages[-19:]

    def get_messages(self):
        return self.messages

    def clear(self):
        system = self.messages[0]
        self.messages = [system]