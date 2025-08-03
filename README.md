# 던전톡 POC Local RAG

포스트 아포칼립스 TRPG를 위한 로컬 RAG(Retrieval-Augmented Generation) 시스템입니다.

## ✨ 주요 기능

- 🎲 AI 던전마스터 (Ollama + Gemini API)
- 📚 벡터 검색을 통한 게임 지식 베이스
- 🗄️ PostgreSQL + pgvector를 활용한 벡터 저장소
- 🎮 실시간 게임 진행 및 상태 관리
- 📄 TRPG 문서 자동 처리 및 청킹

## 🛠️ 기술 스택

- **Backend**: Spring Boot 3.5.4, Spring AI
- **Database**: PostgreSQL + pgvector
- **AI**: Ollama (llama3.2, nomic-embed-text), Gemini API
- **Build Tool**: Gradle

## 🚀 시작하기

### 필수 요구사항

1. **Java 17** 이상
2. **PostgreSQL** with **pgvector** extension
3. **Ollama** (로컬 AI 모델)

### 설정

1. **환경변수 설정**
   ```bash
   cp .env.example .env
   # .env 파일을 열어서 API 키 등을 설정하세요
   ```

2. **PostgreSQL 설정**
   ```sql
   CREATE DATABASE dungeontalk_rag;
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

3. **Ollama 모델 설치**
   ```bash
   ollama pull llama3.2
   ollama pull nomic-embed-text
   ```

### 실행

```bash
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 실행됩니다.

## 📡 API 엔드포인트

### RAG 검색
- `POST /api/rag/search` - RAG 기반 질문 답변
- `POST /api/rag/document` - 문서 추가
- `GET /api/rag/status` - 시스템 상태 확인

### 게임 진행
- `POST /api/game/action` - 게임 액션 처리
- `POST /api/game/start` - 게임 시작
- `GET /api/game/status` - 게임 상태 확인

### 문서 관리
- `POST /api/documents/load-trpg-docs` - TRPG 문서 로딩
- `POST /api/documents/add-test-doc` - 테스트 문서 추가

### 데이터베이스 테스트
- `GET /api/db-test/connection` - 데이터베이스 연결 테스트

## 🔧 설정

주요 설정은 `src/main/resources/application.properties`에서 관리됩니다:

- 데이터베이스 연결
- Ollama 설정
- RAG 파라미터
- 벡터 저장소 설정

보안이 중요한 설정(API 키 등)은 환경변수를 사용하세요.

## 📁 프로젝트 구조

```
src/main/java/com/new_ai/
├── controller/          # REST API 컨트롤러
│   ├── RAGController.java
│   ├── GameController.java
│   ├── DocumentController.java
│   └── DatabaseTestController.java
├── service/            # 비즈니스 로직
│   ├── LocalRAGService.java
│   ├── GameService.java
│   ├── DocumentProcessor.java
│   ├── DocumentLoader.java
│   └── GeminiService.java
└── config/             # 설정
    └── WebConfig.java
```

## 🔒 보안

- API 키는 환경변수로 관리
- 데이터베이스 비밀번호는 실제 운영에서 변경 필요
- CORS 설정은 운영 환경에 맞게 조정

## 🤝 기여

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 라이선스

이 프로젝트는 POC(Proof of Concept)로 개발되었습니다.