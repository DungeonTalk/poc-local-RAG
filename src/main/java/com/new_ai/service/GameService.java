package com.new_ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GameService {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private GeminiService geminiService;
    
    private final Random random = new Random();
    
    public Map<String, Object> initializeGame(String characterName, String characterClass) {
        Map<String, Object> character = new HashMap<>();
        character.put("name", characterName);
        character.put("class", characterClass);
        character.put("hp", 25);
        character.put("maxHp", 25);
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("str", rollStat());
        stats.put("dex", rollStat());
        stats.put("int", rollStat());
        stats.put("con", rollStat());
        stats.put("per", rollStat());
        stats.put("wil", rollStat());
        character.put("stats", stats);
        
        Map<String, Object> gameState = new HashMap<>();
        gameState.put("character", character);
        gameState.put("inventory", new ArrayList<>());
        gameState.put("location", "폐허 외곽");
        gameState.put("gameHistory", new ArrayList<>());
        
        return gameState;
    }
    
    public Map<String, Object> processPlayerAction(String action, Map<String, Object> gameState) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // RAG에서 관련 정보 검색 (빠른 검색)
            System.out.println("=== RAG 검색 시작 ===");
            List<Document> relevantDocs = searchRelevantContent(action);
            System.out.println("=== RAG 검색 완료: " + relevantDocs.size() + "개 문서 ===");
            
            // AI 응답 생성 (Gemini + fallback)
            String aiResponse = generateAIResponse(action, relevantDocs, gameState);
            
            // 게임 상태 업데이트
            Map<String, Object> newGameState = updateGameState(action, gameState);
            
            // 주사위 굴리기가 필요한지 판단
            Map<String, Object> diceRoll = checkForDiceRoll(action);
            
            result.put("response", aiResponse);
            result.put("newGameState", newGameState);
            
            if (diceRoll != null) {
                result.put("diceRoll", diceRoll);
            }
            
        } catch (Exception e) {
            System.err.println("=== GameService 오류 발생 ===");
            System.err.println("오류 메시지: " + e.getMessage());
            e.printStackTrace();
            result.put("response", "❌ 예상치 못한 일이 발생했습니다. 던전마스터가 상황을 정리하고 있습니다...");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    private List<Document> searchRelevantContent(String action) {
        try {
            // 액션에서 키워드 추출
            String searchQuery = extractKeywords(action);
            return vectorStore.similaritySearch(searchQuery);
        } catch (Exception e) {
            System.err.println("RAG 검색 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private String extractKeywords(String action) {
        // 간단한 키워드 추출 로직
        action = action.toLowerCase();
        
        if (action.contains("닥터") || action.contains("의사") || action.contains("치료")) {
            return "닥터 리오 의료";
        } else if (action.contains("워커") || action.contains("좀비") || action.contains("감염")) {
            return "워커 바이러스";
        } else if (action.contains("무기") || action.contains("총") || action.contains("칼")) {
            return "무기 장비";
        } else if (action.contains("탐색") || action.contains("수색") || action.contains("찾")) {
            return "탐색 아이템";
        } else if (action.contains("캠프") || action.contains("에덴")) {
            return "뉴 에덴 캠프";
        } else if (action.contains("농장") || action.contains("에버그린")) {
            return "에버그린 농장";
        } else {
            return action; // 원본 액션으로 검색
        }
    }
    
    private String generateAIResponse(String action, List<Document> relevantDocs, Map<String, Object> gameState) {
        try {
            // RAG 컨텍스트 준비
            String ragContext = buildRagContext(relevantDocs);
            
            // Gemini API를 사용한 AI 응답 생성
            if (geminiService.isApiKeyConfigured()) {
                return geminiService.generateGameResponse(action, ragContext, gameState);
            } else {
                // Gemini API가 설정되지 않은 경우 fallback 응답
                return generateFallbackResponse(action, relevantDocs);
            }
            
        } catch (Exception e) {
            System.err.println("AI 응답 생성 오류: " + e.getMessage());
            return generateFallbackResponse(action, relevantDocs);
        }
    }
    
    private String buildRagContext(List<Document> relevantDocs) {
        if (relevantDocs.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < Math.min(3, relevantDocs.size()); i++) {
            Document doc = relevantDocs.get(i);
            String content = doc.getContent();
            
            // 문서 메타데이터 추가
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata.containsKey("title")) {
                context.append("[").append(metadata.get("title")).append("]\n");
            }
            
            // 내용 요약 (너무 길면 자르기)
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            context.append(content).append("\n\n");
        }
        
        return context.toString();
    }
    
    private String generateFallbackResponse(String action, List<Document> relevantDocs) {
        StringBuilder response = new StringBuilder();
        
        // 동적 응답 생성
        if (action.toLowerCase().contains("살펴") || action.toLowerCase().contains("둘러")) {
            response.append(generateLookAroundResponse());
            
        } else if (action.toLowerCase().contains("이동") || action.toLowerCase().contains("간다")) {
            response.append(generateMovementResponse());
            
        } else if (action.toLowerCase().contains("휴식") || action.toLowerCase().contains("쉰다")) {
            response.append(generateRestResponse());
            
        } else if (action.toLowerCase().contains("탐색") || action.toLowerCase().contains("찾") || action.toLowerCase().contains("수색")) {
            response.append(generateSearchResponse());
            
        } else if (action.toLowerCase().contains("공격") || action.toLowerCase().contains("싸우")) {
            response.append(generateCombatResponse());
            
        } else if (action.toLowerCase().contains("말") || action.toLowerCase().contains("대화") || action.toLowerCase().contains("소리")) {
            response.append(generateInteractionResponse());
            
        } else {
            response.append("🎲 ").append(getRandomResponse()).append("\n\n");
            response.append(getRandomEnvironmentDetail()).append("\n\n");
        }
        
        // RAG 정보 추가
        if (!relevantDocs.isEmpty()) {
            response.append("📋 관련 정보:\n");
            Document doc = relevantDocs.get(0);
            String content = doc.getContent();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            response.append(content).append("\n\n");
        }
        
        response.append(getRandomPrompt());
        return response.toString();
    }
    
    private String generateLookAroundResponse() {
        String[] openings = {
            "🔍 당신은 주변을 신중하게 살펴봅니다.",
            "👁️ 당신의 시선이 주변을 꼼꼼히 훑어봅니다.",
            "🔎 당신은 경계심을 늦추지 않고 주변을 관찰합니다."
        };
        
        String[] environments = {
            "폐허가 된 건물들 사이로 차가운 바람이 불어옵니다.",
            "버려진 차량들이 거리 곳곳에 흩어져 있습니다.",
            "깨진 유리창들이 햇빛을 반사하며 번쩍입니다.",
            "담쟁이덩굴이 건물 벽면을 타고 올라가고 있습니다."
        };
        
        String[] details = {
            "멀리서 까마귀들이 날아오르며, 어딘가에서 금속이 부딪히는 소리가 들립니다.",
            "바람에 날리는 종이 조각들이 마치 유령처럼 춤을 춥니다.",
            "멀리서 개 짖는 소리가 들리다가 갑자기 조용해집니다.",
            "잔해 더미에서 뭔가 유용한 것을 찾을 수 있을지도 모릅니다."
        };
        
        return openings[random.nextInt(openings.length)] + "\n\n" +
               environments[random.nextInt(environments.length)] + " " +
               details[random.nextInt(details.length)] + "\n\n";
    }
    
    private String generateMovementResponse() {
        String[] movements = {
            "🚶 당신은 조심스럽게 발걸음을 옮깁니다.",
            "👣 당신은 소음을 최소화하며 이동합니다.",
            "🏃 당신은 경계를 늦추지 않고 앞으로 나아갑니다."
        };
        
        String[] sounds = {
            "발밑에서 부서진 유리 조각들이 바스락거립니다.",
            "마른 낙엽들이 발걸음에 바스락댑니다.",
            "자갈길이 발걸음마다 작은 소리를 냅니다."
        };
        
        String[] observations = {
            "주변은 여전히 고요하지만, 당신의 감각은 날카롭게 깨어있습니다.",
            "어딘가에서 당신을 지켜보는 시선을 느낍니다.",
            "새로운 지역으로 들어서자 공기의 냄새가 조금 달라집니다."
        };
        
        return movements[random.nextInt(movements.length)] + "\n\n" +
               sounds[random.nextInt(sounds.length)] + " " +
               observations[random.nextInt(observations.length)] + "\n\n";
    }
    
    private String generateRestResponse() {
        String[] restActions = {
            "😴 당신은 안전한 곳을 찾아 잠시 휴식을 취합니다.",
            "🛌 당신은 몸을 웅크리고 짧은 휴식을 취합니다.",
            "💤 당신은 경계를 늦추지 않으면서도 몸을 회복시킵니다."
        };
        
        String[] recoveries = {
            "피로가 조금 풀리는 것을 느낍니다.",
            "긴장했던 근육들이 조금씩 이완됩니다.",
            "깊게 숨을 들이마시며 마음을 진정시킵니다."
        };
        
        String[] warnings = {
            "하지만 이곳에서 너무 오래 머물기는 위험할 것 같습니다.",
            "멀리서 들리는 소음이 당신을 불안하게 만듭니다.",
            "시간이 지날수록 이곳이 안전하지 않다는 느낌이 듭니다."
        };
        
        return restActions[random.nextInt(restActions.length)] + "\n\n" +
               recoveries[random.nextInt(recoveries.length)] + " " +
               warnings[random.nextInt(warnings.length)] + "\n\n";
    }
    
    private String generateSearchResponse() {
        String[] searchActions = {
            "🔍 당신은 주변을 샅샅이 뒤져봅니다.",
            "🎒 당신은 유용한 물건을 찾기 위해 탐색을 시작합니다.",
            "🔦 당신은 신중하게 주변 잔해를 조사합니다."
        };
        
        String[] findings = {
            "버려진 가방에서 몇 가지 물건을 발견했습니다.",
            "무너진 벽 틈새에서 뭔가 반짝이는 것을 봅니다.",
            "쓰레기 더미 아래에서 쓸만한 도구를 찾았습니다.",
            "아쉽게도 특별한 것은 찾지 못했지만, 주변 지형을 파악했습니다."
        };
        
        return searchActions[random.nextInt(searchActions.length)] + "\n\n" +
               findings[random.nextInt(findings.length)] + "\n\n";
    }
    
    private String generateCombatResponse() {
        String[] combatActions = {
            "⚔️ 당신은 전투 태세를 취합니다.",
            "🛡️ 당신은 방어 자세로 들어갑니다.",
            "💥 당신은 공격적으로 나아갑니다."
        };
        
        String[] outcomes = {
            "하지만 주변에는 싸울 대상이 보이지 않습니다.",
            "그림자가 움직이는 것 같았지만, 바람에 흔들리는 천 조각이었습니다.",
            "긴장된 순간이 지나가고 다시 고요함이 찾아옵니다."
        };
        
        return combatActions[random.nextInt(combatActions.length)] + "\n\n" +
               outcomes[random.nextInt(outcomes.length)] + "\n\n";
    }
    
    private String generateInteractionResponse() {
        String[] interactions = {
            "📢 당신은 조심스럽게 말을 건넵니다.",
            "👋 당신은 누군가에게 신호를 보냅니다.",
            "🗣️ 당신은 주변에 있을지 모를 누군가에게 말을 겁니다."
        };
        
        String[] responses = {
            "하지만 대답은 돌아오지 않고, 오직 메아리만이 들립니다.",
            "멀리서 발자국 소리가 들리더니 곧 조용해집니다.",
            "바람만이 당신의 말에 응답하는 듯 나뭇잎을 흔듭니다."
        };
        
        return interactions[random.nextInt(interactions.length)] + "\n\n" +
               responses[random.nextInt(responses.length)] + "\n\n";
    }
    
    private String getRandomEnvironmentDetail() {
        String[] details = {
            "하늘에는 먹구름이 몰려들고 있습니다.",
            "어딘가에서 라디오 잡음 같은 소리가 미약하게 들립니다.",
            "바람이 당신의 옷자락을 흔들며 지나갑니다.",
            "멀리서 연기가 피어오르는 것이 보입니다.",
            "길고양이 한 마리가 당신을 쳐다보다가 재빨리 사라집니다."
        };
        
        return details[random.nextInt(details.length)];
    }
    
    private String getRandomPrompt() {
        String[] prompts = {
            "다음에 무엇을 하시겠습니까?",
            "어떤 행동을 취하시겠습니까?",
            "무엇을 하고 싶으신가요?",
            "다음 행동을 선택해주세요.",
            "어떻게 하시겠습니까?"
        };
        
        return prompts[random.nextInt(prompts.length)];
    }
    
    private Map<String, Object> updateGameState(String action, Map<String, Object> gameState) {
        Map<String, Object> newState = new HashMap<>(gameState);
        
        // 액션에 따른 상태 변화
        if (action.toLowerCase().contains("휴식")) {
            Map<String, Object> character = (Map<String, Object>) newState.get("character");
            Integer currentHp = (Integer) character.get("hp");
            Integer maxHp = (Integer) character.get("maxHp");
            
            if (currentHp < maxHp) {
                character.put("hp", Math.min(maxHp, currentHp + 2));
            }
        }
        
        // 게임 히스토리 업데이트
        List<String> history = (List<String>) newState.getOrDefault("gameHistory", new ArrayList<>());
        history.add(action);
        newState.put("gameHistory", history);
        
        return newState;
    }
    
    private Map<String, Object> checkForDiceRoll(String action) {
        // 주사위가 필요한 액션 판단
        if (action.toLowerCase().contains("공격") || 
            action.toLowerCase().contains("탐색") ||
            action.toLowerCase().contains("수리") ||
            action.toLowerCase().contains("잠금")) {
            
            int roll = random.nextInt(20) + 1;
            boolean success = roll >= 10; // 기본 DC 10
            
            Map<String, Object> diceRoll = new HashMap<>();
            diceRoll.put("type", "d20");
            diceRoll.put("result", roll);
            diceRoll.put("success", success);
            
            return diceRoll;
        }
        
        return null;
    }
    
    private String getRandomResponse() {
        String[] responses = {
                "당신의 행동에 주변 환경이 미묘하게 반응합니다.",
                "조심스럽게 다음 단계를 계획해야 할 것 같습니다.",
                "멀리서 무언가 움직이는 소리가 들립니다.",
                "이곳의 분위기가 예사롭지 않습니다.",
                "당신의 직감이 무언가 중요한 것을 놓치고 있다고 말합니다."
        };
        
        return responses[random.nextInt(responses.length)];
    }
    
    private int rollStat() {
        return random.nextInt(6) + 8; // 8-13 범위
    }
    
    public String getIntroduction() {
        return "🌅 황혼의 새벽 세계에 오신 것을 환영합니다!\n\n" +
               "아케론 바이러스가 세상을 바꾼 지 5년... " +
               "당신은 폐허가 된 도시 외곽에서 새로운 하루를 맞이했습니다.\n\n" +
               "생존이 최우선인 이 세계에서, 당신의 선택이 운명을 결정할 것입니다.\n\n" +
               "준비되셨나요?";
    }
}