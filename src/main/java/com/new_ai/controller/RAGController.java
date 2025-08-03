package com.new_ai.controller;

import com.new_ai.service.LocalRAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RAGController {
    
    @Autowired
    private LocalRAGService ragService;
    
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            String worldType = request.getOrDefault("worldType", "all");
            String sessionId = request.getOrDefault("sessionId", "default");
            
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Query cannot be empty"));
            }
            
            String response = ragService.searchAndGenerate(query, worldType, sessionId);
            
            return ResponseEntity.ok(Map.of(
                    "response", response,
                    "query", query,
                    "worldType", worldType,
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
    
    @PostMapping("/document")
    public ResponseEntity<Map<String, Object>> addDocument(@RequestBody Map<String, Object> request) {
        System.out.println("=== Document Addition Request ===");
        System.out.println("Request received: " + request);
        
        try {
            if (request == null || request.isEmpty()) {
                System.out.println("ERROR: Request is null or empty");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Request body cannot be empty"));
            }
            
            String content = (String) request.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", Map.of());
            
            System.out.println("Content: " + (content != null ? content.substring(0, Math.min(50, content.length())) + "..." : "null"));
            System.out.println("Metadata: " + metadata);
            
            if (content == null || content.trim().isEmpty()) {
                System.out.println("ERROR: Content is empty");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Content cannot be empty"));
            }
            
            System.out.println("Calling ragService.addDocument...");
            ragService.addDocument(content, metadata);
            System.out.println("Document added successfully!");
            
            return ResponseEntity.ok(Map.of(
                    "message", "Document added successfully",
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            System.err.println("ERROR in addDocument: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add document: " + e.getMessage()));
        }
    }
    
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint(@RequestBody(required = false) String body) {
        System.out.println("=== Test Endpoint Called ===");
        System.out.println("Raw body: " + body);
        return ResponseEntity.ok(Map.of(
                "message", "Test endpoint working",
                "receivedBody", body != null ? body : "null",
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    @GetMapping("/search-simple")
    public ResponseEntity<Map<String, Object>> searchSimple(
            @RequestParam String q,
            @RequestParam(defaultValue = "apocalypse") String worldType) {
        try {
            System.out.println("=== Simple Search Request ===");
            System.out.println("Query: " + q);
            System.out.println("World Type: " + worldType);
            
            String response = ragService.searchAndGenerate(q, worldType, "simple-search");
            
            return ResponseEntity.ok(Map.of(
                    "response", response,
                    "query", q,
                    "worldType", worldType,
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            System.err.println("ERROR in simple search: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            int documentCount = ragService.getDocumentCount();
            
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "documentCount", documentCount,
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }
}