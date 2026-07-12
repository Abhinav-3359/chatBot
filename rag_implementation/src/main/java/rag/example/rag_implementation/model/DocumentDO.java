package rag.example.rag_implementation.model;

import lombok.*;

@Getter
@Setter
public class DocumentDO {
    private long id;
    private long chatbotId;
    private String documentName;
    private String documentType;
}
