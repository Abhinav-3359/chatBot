package rag.example.rag_implementation.exception;

public class ChatBotAccessDeniedException extends RuntimeException {

    public ChatBotAccessDeniedException(String message) {
        super(message);
    }

}
