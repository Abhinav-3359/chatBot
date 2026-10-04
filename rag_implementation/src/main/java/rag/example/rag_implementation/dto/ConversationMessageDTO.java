package rag.example.rag_implementation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageDTO {

    private String role;

    private String content;

    private String model;

    private Integer totalTokens;

    private Long latencyMs;

    private LocalDateTime createdAt;
}
