package rag.example.rag_implementation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponseDTO {

    private long id;

    private String documentName;

    private String documentType;

    private int chunkCount;
}
