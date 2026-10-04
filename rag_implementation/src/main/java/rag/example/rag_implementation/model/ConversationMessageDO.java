package rag.example.rag_implementation.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConversationMessageDO {
    private Long id;
    private Long chatbotId;
    private Long userId;
    private String role;
    private String content;
    private String model;
    private Integer totalTokens;
    private Long latencyMs;
    private LocalDateTime createdAt;
}
