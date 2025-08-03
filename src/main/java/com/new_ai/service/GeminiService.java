package com.new_ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {
    
    @Value("${gemini.api.key}")
    private String apiKey;
    
    @Value("${gemini.api.url}")
    private String apiUrl;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public GeminiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    public String generateGameResponse(String playerAction, String ragContext, Map<String, Object> gameState) {
        // Gemini API 시도, 빠른 실패 시 fallback 사용
        System.out.println("=== Gemini API 시도 (15초 타임아웃) ===");
        try {
            String prompt = buildGamePrompt(playerAction, ragContext, gameState);
            System.out.println("=== 프롬프트: " + prompt.substring(0, Math.min(100, prompt.length())) + "... ===");
            
            Map<String, Object> requestBody = new HashMap<>();
            
            // Gemini API 요청 구조
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));
            
            // 생성 설정 - 빠른 응답을 위해 토큰 수 제한
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", 300);
            requestBody.put("generationConfig", generationConfig);
            
            String requestJson = objectMapper.writeValueAsString(requestBody);
            String url = apiUrl + "?key=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            
            System.out.println("=== HTTP 요청 전송 중... ===");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("=== HTTP 응답 수신: " + response.statusCode() + " ===");
            
            if (response.statusCode() == 200) {
                Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
                String result = extractResponseText(responseBody);
                System.out.println("=== Gemini 응답: " + result.substring(0, Math.min(50, result.length())) + "... ===");
                return result;
            } else {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            
        } catch (Exception e) {
            System.err.println("Gemini API 오류 (fallback 사용): " + e.getMessage());
            return generateSmartFallbackResponse(playerAction, ragContext, gameState);
        }
    }
    
    private String buildGamePrompt(String playerAction, String ragContext, Map<String, Object> gameState) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("당신은 포스트 아포칼립스 TRPG 던전마스터입니다.\n\n");
        
        prompt.append("세계관: 2040년 아케론 바이러스 5년 후, 워커(좀비) 세상, 생존 호러\n\n");
        
        if (gameState != null) {
            Map<String, Object> character = (Map<String, Object>) gameState.get("character");
            if (character != null) {
                prompt.append("현재: ").append(character.get("name"));
                prompt.append(" HP:").append(character.get("hp")).append("/").append(character.get("maxHp"));
                prompt.append(" 위치:").append(gameState.getOrDefault("location", "폐허")).append("\n\n");
            }
        }
        
        if (ragContext != null && !ragContext.trim().isEmpty()) {
            String shortContext = ragContext.length() > 200 ? ragContext.substring(0, 200) + "..." : ragContext;
            prompt.append("배경정보: ").append(shortContext).append("\n\n");
        }
        
        prompt.append("플레이어 행동: \"").append(playerAction).append("\"\n\n");
        
        prompt.append("지침: 긴장감 있는 150자 이내 한국어 응답, 이모지 사용, 다음 선택지 제시\n\n");
        prompt.append("응답:");
        
        return prompt.toString();
    }
    
    private String extractResponseText(Map<String, Object> responseBody) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            System.err.println("응답 파싱 오류: " + e.getMessage());
        }
        return "던전마스터가 잠시 말을 잃었습니다... 다시 시도해주세요.";
    }
    
    private String generateSmartFallbackResponse(String playerAction, String ragContext, Map<String, Object> gameState) {
        StringBuilder response = new StringBuilder();
        String action = playerAction.toLowerCase();
        
        // 액션별 맞춤 응답
        if (action.contains("살펴") || action.contains("둘러") || action.contains("look") || action.contains("관찰") || action.contains("보기")) {
            response.append("🔍 당신은 주변을 신중하게 살펴봅니다.\n\n");
            response.append("폐허가 된 건물들 사이로 차가운 바람이 불어옵니다. ");
            response.append("멀리서 까마귀들이 날아오르며, 어딘가에서 금속이 부딪히는 소리가 들립니다.\n\n");
            response.append("잔해 더미에서 뭔가 유용한 것을 찾을 수 있을지도 모릅니다. ");
            response.append("하지만 너무 오래 한 곳에 머물기는 위험합니다.");
            
        } else if (action.contains("이동") || action.contains("간다") || action.contains("move") || action.contains("걷") || action.contains("앞으로")) {
            response.append("🚶 당신은 조심스럽게 발걸음을 옮깁니다.\n\n");
            response.append("발밑에서 부서진 유리 조각들이 바스락거립니다. ");
            response.append("주변은 여전히 고요하지만, 당신의 감각은 날카롭게 깨어있습니다.\n\n");
            response.append("앞으로 가야 할 길이 여러 갈래로 나뉘어 있습니다.");
            
        } else if (action.contains("휴식") || action.contains("쉰다") || action.contains("rest") || action.contains("취합니다") || action.contains("잠") || action.contains("쉬기")) {
            response.append("😴 당신은 안전한 곳을 찾아 잠시 휴식을 취합니다.\n\n");
            response.append("피로가 조금 풀리는 것을 느낍니다. ");
            response.append("하지만 이곳에서 너무 오래 머물기는 위험할 것 같습니다.\n\n");
            response.append("체력이 약간 회복되었습니다.");
            
        } else if (action.contains("공격") || action.contains("attack") || action.contains("싸운다")) {
            response.append("⚔️ 긴장감이 고조됩니다!\n\n");
            response.append("당신은 무기를 움켜쥐고 전투 자세를 취합니다. ");
            response.append("상대의 움직임을 주의 깊게 관찰하며 기회를 노립니다.\n\n");
            response.append("🎲 공격 판정이 필요합니다! (d20 굴리기)");
            
        } else if (action.contains("탐색") || action.contains("찾") || action.contains("search")) {
            response.append("🔍 당신은 주변을 자세히 탐색하기 시작합니다.\n\n");
            response.append("먼지가 쌓인 잔해들 사이를 조심스럽게 뒤집니다. ");
            response.append("무언가 유용한 것이 숨어있을지도 모릅니다.\n\n");
            response.append("🎲 탐색 판정이 필요합니다! (d20 굴리기)");
            
        } else {
            String[] randomResponses = {
                "당신의 행동이 주변 환경에 미묘한 변화를 일으킵니다.",
                "조심스럽게 다음 단계를 계획해야 할 것 같습니다.",
                "이곳의 분위기가 예사롭지 않습니다.",
                "당신의 직감이 무언가 중요한 것을 말하고 있습니다."
            };
            Random random = new Random();
            response.append("🎲 ").append(randomResponses[random.nextInt(randomResponses.length)]).append("\n\n");
        }
        
        // RAG 컨텍스트가 있으면 추가
        if (ragContext != null && !ragContext.trim().isEmpty() && ragContext.length() > 50) {
            response.append("\n📋 관련 정보:\n");
            String shortContext = ragContext.length() > 150 ? ragContext.substring(0, 150) + "..." : ragContext;
            response.append(shortContext).append("\n\n");
        }
        
        response.append("다음에 무엇을 하시겠습니까?");
        return response.toString();
    }
    
    private String generateFallbackResponse(String playerAction) {
        String[] responses = {
                "🎲 당신의 행동이 주변 환경에 미묘한 변화를 일으킵니다. 조심스럽게 다음 단계를 계획해야 할 것 같습니다.",
                "⚠️ 이곳의 분위기가 예사롭지 않습니다. 멀리서 무언가 움직이는 소리가 들려옵니다.",
                "🔍 당신의 직감이 무언가 중요한 단서를 놓치고 있다고 속삭입니다. 더 신중하게 살펴볼 필요가 있습니다.",
                "💭 생존을 위해서는 현명한 판단이 필요합니다. 지금 이 순간의 선택이 운명을 가를 수 있습니다."
        };
        
        Random random = new Random();
        return responses[random.nextInt(responses.length)];
    }
    
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }
}