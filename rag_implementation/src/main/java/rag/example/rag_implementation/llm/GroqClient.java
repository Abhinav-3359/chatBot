package rag.example.rag_implementation.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import rag.example.rag_implementation.llm.LLMClient;
import rag.example.rag_implementation.llm.LLMRequestDO;
import rag.example.rag_implementation.llm.LLMResponse;
import rag.example.rag_implementation.llm.groq.GroqMessage;
import rag.example.rag_implementation.llm.groq.GroqRequest;
import rag.example.rag_implementation.llm.groq.GroqResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GroqClient implements LLMClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.base.url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String model;

    private static final Logger logger = LoggerFactory.getLogger(GroqClient.class);
    @Override
    public LLMResponse generate(LLMRequestDO request) {
       
        long start = System.currentTimeMillis();
        try{
         logger.info("Inside GroqClient.generate()");
        List<GroqMessage> groqMessages = request.getMessages().stream()
                .map(m -> new GroqMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());

        GroqRequest groqRequest = GroqRequest.builder()
                .model(model)
                .messages(groqMessages)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GroqRequest> entity =
                new HttpEntity<>(groqRequest, headers);

        ResponseEntity<GroqResponse> responseEntity =
                restTemplate.exchange(
                        baseUrl,
                        HttpMethod.POST,
                        entity,
                        GroqResponse.class
                );

        long latency = System.currentTimeMillis() - start;

        GroqResponse response = responseEntity.getBody();

        return LLMResponse.builder()
                .answer(response.getChoices().get(0).getMessage().getContent())
                .model(response.getModel())
                .promptTokens(response.getUsage().getPromptTokens())
                .completionTokens(response.getUsage().getCompletionTokens())
                .totalTokens(response.getUsage().getTotalTokens())
                .latency(latency)
                .build();
            }
            catch (Exception e) {
                logger.error("Error in GroqClient.generate(): {}", e.getMessage());
                throw new RuntimeException("Failed to generate response from Groq API", e);
            }
    }
}