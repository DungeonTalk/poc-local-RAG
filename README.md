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

## 🧠 RAG 시스템 구현 상세

### 📊 모델 아키텍처

본 시스템은 **하이브리드 RAG 아키텍처**를 구현하여 로컬 모델과 클라우드 API의 장점을 모두 활용합니다.

#### 🔹 **임베딩 모델**
- **모델**: `nomic-embed-text` (Ollama)
- **차원**: 768차원 벡터
- **특징**: 한국어/영어 멀티링구얼 지원
- **용도**: TRPG 문서를 벡터로 변환하여 의미적 유사도 검색
- **장점**: 완전 로컬 실행, 개인정보 보호

#### 🔹 **생성 모델 (듀얼 시스템)**

**1차 생성: Gemini 1.5 Flash (Google)**
- **모델**: `gemini-1.5-flash:generateContent`
- **토큰 제한**: 300토큰 (빠른 응답)
- **온도**: 0.7 (창의성과 일관성 균형)
- **용도**: 상황별 맞춤형 TRPG 응답 생성
- **장점**: 뛰어난 한국어 이해력, 창의적 스토리텔링

**2차 생성: 로컬 Fallback**
- **모델**: `llama3.2` (Ollama)
- **용도**: Gemini API 실패 시 백업 응답
- **특징**: 규칙 기반 + 템플릿 응답 생성
- **장점**: 항상 사용 가능, 안정적 동작

### 🏗️ RAG 파이프라인

#### **1단계: 문서 처리 (Document Processing)**
```java
DocumentProcessor.java → DocumentLoader.java
```
- **청킹 전략**: 
  - 기본 청크 크기: 1000자
  - 오버랩: 200자
  - 문장 경계 인식 분할
- **메타데이터 추출**:
  - 문서 타입 (NPC, 아이템, 시나리오, 규칙 등)
  - 카테고리 분류 (전투, 의료, 희귀도 등)
  - 세계관 태그 (아포칼립스)

#### **2단계: 벡터화 및 저장**
```java
VectorStore (Spring AI) → PostgreSQL + pgvector
```
- **인덱싱**: HNSW (Hierarchical Navigable Small World)
- **거리 측정**: 코사인 유사도
- **저장소**: PostgreSQL 테이블에 벡터와 메타데이터 함께 저장

#### **3단계: 검색 (Retrieval)**
```java
LocalRAGService.searchRelevantDocuments()
```
- **검색 방식**: 
  - Top-K 검색 (기본 5개)
  - 유사도 임계값: 0.7
  - 메타데이터 필터링 (world_type, category)
- **키워드 최적화**: 
  - 액션별 키워드 매핑
  - 동의어 처리 (닥터/의사, 워커/좀비)

#### **4단계: 컨텍스트 구성 (Context Building)**
```java
LocalRAGService.buildContext()
```
- **컨텍스트 길이**: 최대 400자 per document
- **랭킹**: 유사도 순으로 상위 3개 문서 선택
- **포맷팅**: 구조화된 프롬프트 템플릿 적용

#### **5단계: 생성 (Generation)**
```java
GeminiService.generateGameResponse() 
↓ (실패시)
GameService.generateFallbackResponse()
```

**Gemini API 프롬프트 구조**:
```
당신은 포스트 아포칼립스 TRPG 던전마스터입니다.

세계관: 2040년 아케론 바이러스 5년 후, 워커(좀비) 세상
현재 상황: [캐릭터 정보 + HP + 위치]
배경 정보: [RAG 검색 결과]
플레이어 행동: "[사용자 입력]"

지침: 긴장감 있는 150자 이내 한국어 응답, 이모지 사용
```

### ⚙️ 핵심 구현 기술

#### **벡터 데이터베이스 최적화**
```properties
# PostgreSQL + pgvector 설정
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=768
```

#### **지능형 문서 분류**
```java
// 파일명 기반 자동 분류
if (fileName.startsWith("NPC_")) → type: "npc", category: "character"
if (fileName.startsWith("아이템_무기")) → type: "item", category: "weapon"
// 내용 기반 태그 추출
if (content.contains("전투|피해|공격")) → tags.add("combat")
```

#### **동적 응답 생성**
```java
// 액션별 최적화된 키워드 매핑
if (action.contains("닥터|의사|치료")) → searchQuery = "닥터 리오 의료"
if (action.contains("워커|좀비|감염")) → searchQuery = "워커 바이러스"
```

#### **성능 최적화**
- **비동기 처리**: Spring WebFlux 지원
- **연결 풀링**: HikariCP 최적화 설정
- **캐싱**: 검색 결과 메모리 캐시
- **타임아웃**: Gemini API 15초 제한

### 📈 시스템 성능 지표

- **검색 속도**: ~100ms (로컬 벡터 검색)
- **응답 생성**: ~2-5초 (Gemini API)
- **Fallback 응답**: ~50ms (로컬 생성)
- **동시 사용자**: 최대 10명 (커넥션 풀 제한)
- **문서 처리**: ~20개 파일/초

### 🔄 확장 가능성

1. **모델 업그레이드**: Llama 3.3, GPT-4o 등으로 교체 가능
2. **벡터 DB 확장**: Chroma, Weaviate 등 다른 벡터 DB 지원
3. **멀티모달**: 이미지, 오디오 처리 확장
4. **실시간 학습**: 사용자 피드백 기반 모델 파인튜닝

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