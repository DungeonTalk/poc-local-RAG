package com.new_ai.controller;

import com.new_ai.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "*")
public class GameController {
    
    @Autowired
    private GameService gameService;
    
    @PostMapping("/action")
    public ResponseEntity<Map<String, Object>> processAction(@RequestBody Map<String, Object> request) {
        try {
            System.out.println("=== 게임 액션 요청 받음 ===");
            System.out.println("요청 데이터: " + request);
            
            if (request == null || request.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "요청 본문이 비어있습니다"));
            }
            
            // 액션 추출
            String action = (String) request.get("action");
            Map<String, Object> gameState = (Map<String, Object>) request.getOrDefault("gameState", 
                Map.of(
                    "character", Map.of("name", "생존자", "hp", 25, "maxHp", 25),
                    "inventory", java.util.List.of(),
                    "location", "폐허 외곽",
                    "gameHistory", java.util.List.of()
                )
            );
            
            System.out.println("액션: " + action);
            System.out.println("게임상태: " + gameState);
            
            if (action == null || action.trim().isEmpty()) {
                System.out.println("에러: 액션이 비어있음");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "액션이 필요합니다"));
            }
            
            Map<String, Object> result = gameService.processPlayerAction(action, gameState);
            System.out.println("처리 결과: " + result);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("게임 액션 처리 오류: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "게임 처리 중 오류: " + e.getMessage()));
        }
    }
    
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startGame(@RequestBody Map<String, String> request) {
        try {
            String characterName = request.getOrDefault("characterName", "생존자");
            String characterClass = request.getOrDefault("characterClass", "신입 생존자");
            
            Map<String, Object> gameState = gameService.initializeGame(characterName, characterClass);
            
            return ResponseEntity.ok(Map.of(
                    "message", "게임이 시작되었습니다!",
                    "gameState", gameState,
                    "response", gameService.getIntroduction()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "게임 시작 중 오류: " + e.getMessage()));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGameStatus() {
        Map<String, Object> status = Map.of(
                "status", "ready",
                "message", "던전톡 게임 서버 준비 완료",
                "features", Map.of(
                        "aiDungeonMaster", true,
                        "ragSystem", true,
                        "realTimeGame", true,
                        "characterManagement", true
                ),
                "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/test-action")
    public ResponseEntity<Map<String, Object>> testAction(@RequestParam(defaultValue = "주변을 살펴본다") String action) {
        try {
            System.out.println("=== 테스트 액션 실행: " + action + " ===");
            
            // 테스트를 위해 다양한 HP 상태 시뮬레이션
            int currentHp = action.contains("휴식") ? 15 : 25;  // 휴식일 때 낮은 HP로 시작
            
            Map<String, Object> gameState = Map.of(
                "character", Map.of("name", "생존자", "hp", currentHp, "maxHp", 25),
                "inventory", java.util.List.of(),
                "location", "폐허 외곽"
            );
            
            Map<String, Object> result = gameService.processPlayerAction(action, gameState);
            System.out.println("테스트 결과: " + result);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("테스트 액션 오류: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "테스트 중 오류: " + e.getMessage()));
        }
    }
}