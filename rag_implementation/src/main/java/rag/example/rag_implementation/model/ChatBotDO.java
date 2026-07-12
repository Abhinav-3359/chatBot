package rag.example.rag_implementation.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatBotDO {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    @JsonProperty("pinecone_namespace")
    private String pineConeNamespace;

}
