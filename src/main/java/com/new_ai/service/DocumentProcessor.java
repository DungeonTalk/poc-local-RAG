package com.new_ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
public class DocumentProcessor {
    
    @Autowired
    private VectorStore vectorStore;
    
    public void processDocumentsFromDirectory(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile)
                     .filter(path -> path.toString().endsWith(".txt"))
                     .forEach(this::processDocument);
            }
            
        } catch (IOException e) {
            throw new RuntimeException("문서 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }
    
    private void processDocument(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String fileName = filePath.getFileName().toString();
            
            // 메타데이터 추출
            Map<String, Object> metadata = extractMetadata(fileName, content);
            
            // 문서 청킹
            List<String> chunks = chunkDocument(content, 1000, 200);
            
            // 각 청크를 벡터 스토어에 저장
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("chunk_index", i);
                chunkMetadata.put("total_chunks", chunks.size());
                chunkMetadata.put("source_file", fileName);
                
                documents.add(new Document(chunks.get(i), chunkMetadata));
            }
            
            vectorStore.add(documents);
            System.out.println("처리 완료: " + fileName + " (" + chunks.size() + " 청크)");
            
        } catch (IOException e) {
            System.err.println("파일 읽기 오류: " + filePath + " - " + e.getMessage());
        }
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
        } else if (fileName.startsWith("세계관_") || fileName.startsWith("워커_")) {
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
        if (content.contains("전투") || content.contains("피해")) {
            tags.add("combat");
        }
        if (content.contains("의료") || content.contains("치료")) {
            tags.add("medical");
        }
        if (content.contains("희귀") || content.contains("유니크")) {
            tags.add("rare");
        }
        if (content.contains("중요") || content.contains("필수")) {
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
            
            // 문장 경계에서 자르기 (마침표, 느낌표, 물음표)
            if (end < content.length()) {
                int lastSentenceEnd = content.lastIndexOf('.', end);
                if (lastSentenceEnd == -1) lastSentenceEnd = content.lastIndexOf('!', end);
                if (lastSentenceEnd == -1) lastSentenceEnd = content.lastIndexOf('?', end);
                if (lastSentenceEnd == -1) lastSentenceEnd = content.lastIndexOf('\n', end);
                
                if (lastSentenceEnd > start) {
                    end = lastSentenceEnd + 1;
                }
            }
            
            chunks.add(content.substring(start, end).trim());
            start = Math.max(start + chunkSize - overlap, end);
        }
        
        return chunks;
    }
}