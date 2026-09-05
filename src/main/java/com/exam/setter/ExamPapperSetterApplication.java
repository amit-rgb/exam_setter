package com.exam.setter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class ExamPapperSetterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPapperSetterApplication.class, args);
    }

    @Bean
    CommandLineRunner verificationRunner(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        return args -> {
            System.out.println("\n--- STARTING HARDWARE & PIPELINE VERIFICATION ---");

            // 1. Verify Cloud LLM HTTPS Round-Trip
            ChatClient chatClient = chatClientBuilder.build();
            String llmResponse = chatClient.prompt()
                    .user("Respond with the single word: READY")
                    .call()
                    .content();
            System.out.println("[API Check] Cloud LLM Handshake Response: " + llmResponse);

            // 2. Verify Vector Store Write & Cosine Distance Indexing
            Document testDoc = new Document(
                    "Newton's second law states that F equals m times a.",
                    Map.of("subject", "Physics", "verified", true)
            );
            vectorStore.add(List.of(testDoc));
            System.out.println("[DB Check] Successfully wrote test vector embedding to PostgreSQL.");

            // 3. Verify Vector Store Retrieval (Similarity Search)
            List<Document> queryResults = vectorStore.similaritySearch(
                    SearchRequest.builder().query("force and mass acceleration equation").topK(1).build()
            );

            if (!queryResults.isEmpty()) {
                System.out.println("[Vector Check] Retrieved nearest neighbor chunk: " + queryResults.get(0).getText());
                System.out.println("--- SYSTEM VERIFICATION SUCCESSFUL: READY FOR STEP 3 ---\n");
            } else {
                System.err.println("--- SYSTEM VERIFICATION FAILED: NO VECTORS RETURNED ---");
            }
        };
    }
}