package com.new_ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Component
public class DocumentLoader implements CommandLineRunner {
    
    @Autowired
    private VectorStore vectorStore;
    
    private static final String DEFAULT_DOCUMENTS_PATH = "C:\\Users\\PC\\Downloads\\RAG\\RAG";
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== DocumentLoader Starting ===");
        
        // 환경 변수나 인수로 경로 지정 가능
        String documentsPath = System.getProperty("documents.path", DEFAULT_DOCUMENTS_PATH);
        
        // --load-documents 인수가 있을 때만 실행
        boolean shouldLoad = Arrays.stream(args).anyMatch(arg -> "--load-documents".equals(arg));
        
        if (shouldLoad) {
            System.out.println("Loading documents from: " + documentsPath);
            loadDocumentsFromDirectory(documentsPath);
        } else {
            System.out.println("Document loading skipped. Use --load-documents to load documents.");
        }
    }
    
    public void loadDocumentsFromDirectory(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                System.err.println("Directory does not exist: " + directoryPath);
                return;
            }
            
            List<Document> allDocuments = new ArrayList<>();
            int processedCount = 0;
            
            try (Stream<Path> paths = Files.walk(dir)) {
                List<Path> txtFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".txt"))
                        .toList();
                
                System.out.println("Found " + txtFiles.size() + " text files");
                
                for (Path filePath : txtFiles) {
                    try {
                        List<Document> documents = processDocument(filePath);
                        allDocuments.addAll(documents);
                        processedCount++;
                        System.out.println("Processed: " + filePath.getFileName() + " (" + documents.size() + " chunks)");
                    } catch (Exception e) {
                        System.err.println("Error processing file " + filePath + ": " + e.getMessage());
                    }
                }
            }
            
            if (!allDocuments.isEmpty()) {
                System.out.println("Adding " + allDocuments.size() + " document chunks to vector store...");
                vectorStore.add(allDocuments);
                System.out.println("Successfully loaded " + processedCount + " files with " + allDocuments.size() + " total chunks");
            } else {
                System.out.println("No documents to add");
            }
            
        } catch (IOException e) {
            System.err.println("Error loading documents: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private List<Document> processDocument(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String fileName = filePath.getFileName().toString();
        
        // 메타데이터 추출
        Map<String, Object> baseMetadata = extractMetadata(fileName, content);
        
        // 문서 청킹
        List<String> chunks = chunkDocument(content, 800, 150);
        
        // 각 청크를 Document로 변환
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMetadata = new HashMap<>(baseMetadata);
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("total_chunks", chunks.size());
            chunkMetadata.put("source_file", fileName);
            
            documents.add(new Document(chunks.get(i), chunkMetadata));
        }
        
        return documents;
    }
    
    private Map<String, Object> extractMetadata(String fileName, String content) {
        Map<String, Object> metadata = new HashMap<>();
        
        // 파일명에서 타입과 카테고리 추출
        if (fileName.startsWith("NPC_")) {
            metadata.put("type", "npc");
            metadata.put("category", "character");
        } else if (fileName.startsWith("아이템_")) {
            metadata.put("type", "item");
            if (fileName.contains("무기")) {
                metadata.put("category", "weapon");
            } else if (fileName.contains("소모품")) {
                metadata.put("category", "consumable");
            } else {
                metadata.put("category", "equipment");
            }
        } else if (fileName.startsWith("시나리오_")) {
            metadata.put("type", "scenario");
            metadata.put("category", "quest");
        } else if (fileName.startsWith("규칙_")) {
            metadata.put("type", "rule");
            metadata.put("category", "combat");
        } else if (fileName.startsWith("장소_")) {
            metadata.put("type", "location");
            metadata.put("category", "environment");
        } else if (fileName.startsWith("세계관_") || fileName.startsWith("워커_") || fileName.startsWith("생존자집단_")) {
            metadata.put("type", "lore");
            metadata.put("category", "background");
        } else {
            metadata.put("type", "general");
            metadata.put("category", "misc");
        }
        
        // 세계관 타입 (기본은 아포칼립스)
        metadata.put("world_type", "apocalypse");
        
        // 중요도 태그
        List<String> tags = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("전투") || lowerContent.contains("피해") || lowerContent.contains("공격")) {
            tags.add("combat");
        }
        if (lowerContent.contains("의료") || lowerContent.contains("치료") || lowerContent.contains("의약품")) {
            tags.add("medical");
        }
        if (lowerContent.contains("희귀") || lowerContent.contains("유니크") || lowerContent.contains("특수")) {
            tags.add("rare");
        }
        if (lowerContent.contains("중요") || lowerContent.contains("필수") || lowerContent.contains("핵심")) {
            tags.add("important");
        }
        
        metadata.put("tags", tags);
        metadata.put("title", fileName.replace(".txt", ""));
        metadata.put("created_at", new Date());
        
        return metadata;
    }
    
    private List<String> chunkDocument(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        
        if (content.length() <= chunkSize) {
            chunks.add(content);
            return chunks;
        }
        
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            
            // 문장 경계에서 자르기 시도
            if (end < content.length()) {
                // 한국어 문장 끝 찾기
                int lastSentenceEnd = Math.max(
                    Math.max(content.lastIndexOf('.', end), content.lastIndexOf('!', end)),
                    Math.max(content.lastIndexOf('?', end), content.lastIndexOf('\n', end))
                );
                
                if (lastSentenceEnd > start) {
                    end = lastSentenceEnd + 1;
                }
            }
            
            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            start = Math.max(start + chunkSize - overlap, end);
        }
        
        return chunks;
    }
}