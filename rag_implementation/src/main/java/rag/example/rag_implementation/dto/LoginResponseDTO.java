package rag.example.rag_implementation.dto;

import lombok.Builder;
import lombok.Getter;       
import lombok.Setter;
@Builder
@Getter
@Setter
public class LoginResponseDTO {

    private Long id;

    private String email;

    private String token;
}
