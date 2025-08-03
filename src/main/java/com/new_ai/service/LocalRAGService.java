package com.new_ai.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LocalRAGService {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired(required = false)
    private ChatModel chatModel;
    
    @Value("${rag.search.top-k:5}")
    private int topK;
    
    @Value("${rag.similarity.threshold:0.7}")
    private double similarityThreshold;
    
    private static final String RAG_PROMPT_TEMPLATE = """
            당신은 TRPG 던전마스터입니다. 주어진 컨텍스트를 바탕으로 플레이어의 질문에 답변해주세요.
            
            컨텍스트:
            {context}
            
            질문: {question}
            
            답변 시 주의사항:
            1. 컨텍스트에 있는 정보만 사용하여 답변하세요
            2. 확실하지 않은 정보는 "확실하지 않습니다"라고 답변하세요
            3. TRPG 게임의 분위기를 유지하며 답변하세요
            
            답변:
            """;
    
    public String searchAndGenerate(String query, String worldType, String sessionId) {
        try {
            // 1. 벡터 검색으로 관련 문서 찾기
            List<Document> relevantDocs = searchRelevantDocuments(query, worldType);
            
            // 2. 컨텍스트 구성
            String context = buildContext(relevantDocs);
            
            // 3. LLM으로 답변 생성
            return generateResponse(query, context);
            
        } catch (Exception e) {
            return "죄송합니다. 현재 정보를 검색할 수 없습니다. 나중에 다시 시도해주세요.";
        }
    }
    
    private List<Document> searchRelevantDocuments(String query, String worldType) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        
        // 월드 타입 필터링 (메타데이터가 있는 경우)
        if (worldType != null && !worldType.isEmpty()) {
            documents = documents.stream()
                    .filter(doc -> {
                        Map<String, Object> metadata = doc.getMetadata();
                        String docWorldType = (String) metadata.get("world_type");
                        return docWorldType == null || docWorldType.equals("all") || docWorldType.equals(worldType);
                    })
                    .collect(Collectors.toList());
        }
        
        return documents;
    }
    
    private String buildContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));
    }
    
    private String generateResponse(String query, String context) {
        if (chatModel == null) {
            return "ChatModel이 설정되지 않아 검색 결과만 반환합니다:\n\n" + context;
        }
        
        try {
            PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "context", context,
                    "question", query
            ));
            
            return chatModel.call(prompt).getResult().getOutput().getContent();
        } catch (Exception e) {
            return "LLM 호출 중 오류가 발생했습니다. 검색 결과:\n\n" + context;
        }
    }
    
    public void addDocument(String content, Map<String, Object> metadata) {
        try {
            System.out.println("Adding document with content length: " + content.length());
            System.out.println("Metadata: " + metadata);
            
            Document document = new Document(content, metadata);
            vectorStore.add(List.of(document));
            
            System.out.println("Document added successfully");
        } catch (Exception e) {
            System.err.println("Error adding document: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to add document: " + e.getMessage(), e);
        }
    }
    
    public int getDocumentCount() {
        // 실제 구현에서는 VectorStore에서 문서 수를 조회하는 방법 사용
        return 0; // placeholder
    }
}