package rag.example.rag_implementation.exception;

public class ChatBotNotFoundException extends RuntimeException {

    public ChatBotNotFoundException(String message) {
        super(message);
    }

}