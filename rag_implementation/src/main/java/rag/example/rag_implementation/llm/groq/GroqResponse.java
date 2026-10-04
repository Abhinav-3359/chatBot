package rag.example.rag_implementation.llm.groq;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GroqResponse {

    private String model;

    private List<GroqChoice> choices;

    private GroqUsage usage;

}