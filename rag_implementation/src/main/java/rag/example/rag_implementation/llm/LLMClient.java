package rag.example.rag_implementation.llm;
public interface LLMClient {

    LLMResponse generate(LLMRequestDO request);

}