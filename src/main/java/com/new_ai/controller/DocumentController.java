package com.new_ai.controller;

import com.new_ai.service.DocumentProcessor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {
    
    @Autowired
    private DocumentProcessor documentProcessor;
    
    @Autowired
    private VectorStore vectorStore;
    
    @PostMapping("/process-directory")
    public ResponseEntity<Map<String, Object>> processDirectory(@RequestBody Map<String, String> request) {
        try {
            String directoryPath = request.get("directoryPath");
            
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Directory path is required"));
            }
            
            documentProcessor.processDocumentsFromDirectory(directoryPath);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Documents processed successfully",
                    "directoryPath", directoryPath,
                    "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process documents: " + e.getMessage()));
        }
    }
    
    @PostMapping("/load-trpg-docs")
    public ResponseEntity<Map<String, Object>> loadTrpgDocuments() {
        try {
            String documentsPath = "C:\\Users\\PC\\Downloads\\RAG\\RAG";
            File docDir = new File(documentsPath);
            
            if (!docDir.exists() || !docDir.isDirectory()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "TRPG 문서 디렉토리를 찾을 수 없습니다: " + documentsPath);
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            List<Document> allDocuments = new ArrayList<>();
            File[] files = docDir.listFiles((dir, name) -> name.endsWith(".txt"));
            
            if (files == null || files.length == 0) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "TRPG 문서 파일들을 찾을 수 없습니다");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            int successCount = 0;
            int errorCount = 0;
            List<String> loadedFiles = new ArrayList<>();
            List<String> errorFiles = new ArrayList<>();
            
            for (File file : files) {
                try {
                    FileSystemResource resource = new FileSystemResource(file);
                    TextReader textReader = new TextReader(resource);
                    List<Document> documents = textReader.get();
                    
                    for (Document doc : documents) {
                        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                        metadata.put("filename", file.getName());
                        metadata.put("source", "TRPG_Documents");
                        metadata.put("type", determineDocumentType(file.getName()));
                        metadata.put("title", extractTitle(file.getName()));
                        metadata.put("loadTime", System.currentTimeMillis());
                        
                        Document enhancedDoc = new Document(doc.getContent(), metadata);
                        allDocuments.add(enhancedDoc);
                    }
                    
                    loadedFiles.add(file.getName());
                    successCount++;
                    
                } catch (Exception e) {
                    errorFiles.add(file.getName() + " (" + e.getMessage() + ")");
                    errorCount++;
                }
            }
            
            if (!allDocuments.isEmpty()) {
                vectorStore.add(allDocuments);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✅ TRPG 문서 로딩 완료!");
            response.put("totalFiles", files.length);
            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            response.put("totalDocuments", allDocuments.size());
            response.put("loadedFiles", loadedFiles);
            if (!errorFiles.isEmpty()) {
                response.put("errorFiles", errorFiles);
            }
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "문서 로딩 중 오류 발생: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/add-test-doc")
    public ResponseEntity<Map<String, Object>> addTestDocument(@RequestParam String content) {
        try {
            String decodedContent = URLDecoder.decode(content, StandardCharsets.UTF_8);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "test");
            metadata.put("title", "테스트 문서");
            metadata.put("source", "manual_test");
            metadata.put("timestamp", System.currentTimeMillis());
            
            Document testDoc = new Document(decodedContent, metadata);
            vectorStore.add(List.of(testDoc));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "✅ 테스트 문서 추가 완료!");
            response.put("content", decodedContent);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "테스트 문서 추가 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    private String determineDocumentType(String filename) {
        if (filename.startsWith("NPC_")) return "NPC";
        if (filename.startsWith("아이템_")) return "아이템";
        if (filename.startsWith("시나리오_")) return "시나리오";
        if (filename.startsWith("규칙_")) return "규칙";
        if (filename.startsWith("장소_")) return "장소";
        if (filename.startsWith("세계관_")) return "세계관";
        if (filename.startsWith("워커_")) return "워커";
        if (filename.startsWith("생존자집단_")) return "생존자집단";
        if (filename.startsWith("퀘스트_")) return "퀘스트";
        if (filename.startsWith("게임_")) return "게임규칙";
        return "기타";
    }
    
    private String extractTitle(String filename) {
        String nameWithoutExt = filename.replace(".txt", "");
        return nameWithoutExt.replace("_", " ");
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLoaderStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("ready", true);
        status.put("message", "문서 로더 준비 완료");
        status.put("documentsPath", "C:\\Users\\PC\\Downloads\\RAG\\RAG");
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
}