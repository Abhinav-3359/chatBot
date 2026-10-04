package rag.example.rag_implementation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
@RestController
public class RagImplementationApplication {
	Logger log = (Logger) LoggerFactory.getLogger(RagImplementationApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(RagImplementationApplication.class, args);
	}

	@GetMapping("/hello")
	public String hello(@RequestParam(value = "name", defaultValue = "World") String name) {
		log.info("Hello {}!", name);
		return String.format("Hello %s!", name);
	}

}
