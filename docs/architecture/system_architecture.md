### System Architecture Specification (updated)

1. Overview

본 시스템은 AWS Cloud(EKS)와 사내 On-premise(AI/Vector)로 구성된 MSA(Microservice Architecture)이다.  
트래픽은 CloudFront → ALB → EKS Ingress로 유입되며, 서비스는 경로기반 라우팅으로 분기된다.

2. Core Components
   2.1 AWS Cloud Services

   - CloudFront: 정적 리소스(S3) CDN 및 사용자 진입점
   - S3: 프론트엔드 빌드 산출물 및 교육 자료 저장
   - ALB: 외부 트래픽을 EKS Ingress로 전달
   - Keycloak(IAM): 인증/인가(Access Token 발급 및 검증, 운영 적용 대상)

3. AWS EKS Cluster (Application Layer)

아래 4개의 Spring Boot 마이크로서비스로 구성된다.

3.1 Chat Service

- 기능: LLM 대화 세션/섹션/메시지, FAQ
- 데이터: PostgreSQL chat 스키마 (개발환경), Flyway 관리

  3.2 Education Service

- 기능: 교육 관리, 영상/스크립트, 진행도, 필수 교육 목록, 교육 삭제
- 데이터: PostgreSQL education 스키마 (개발환경), Flyway 관리

  3.3 Infra Service

- 기능: 시스템/이벤트/에러 로그, 사용자 프로필, RAG 문서/청크
- 데이터: PostgreSQL infra 스키마 (개발환경), Flyway 관리

  3.4 Quiz Service

- 기능: 퀴즈 시도, 문항, 제출/채점, 이탈 추적
- 데이터: PostgreSQL quiz 스키마 (개발환경), Flyway 관리

4. On-premise (AI Layer, 파이썬으로 구현하고 현재 레포랑 분리되서 다른 서버에서 동작)
   4.1 LLM Service

   - LLM Inference (Qwen/Llama/Gemma 등)
   - 역할: RAG 문서 처리, 스크립트/나레이션 생성 등

     4.2 Milvus Vector DB

   - 임베딩 저장소, rag_document_chunk 조회/검색에 활용

     4.3 Logging Pipeline

   - Fluentbit(수집) → Loki(저장) → Grafana(대시보드)

5. Networking & Traffic Flow

   - User → CloudFront
   - CloudFront → ALB
   - ALB → K8s Ingress
   - Ingress → Application Services (chat / education / infra / quiz)
   - 필요 시 Application Service → On-premise LLM Service (VPN 경유)

6. Deployment Pipeline (CI/CD)
   6.1 Workflow

   - GitHub push → GitHub Actions
   - Docker Image Build → DockerHub push
   - ArgoCD로 EKS 자동 배포

7. Database Architecture

   - 개발환경: 단일 PostgreSQL 인스턴스 내 서비스별 스키마 분리(chat, education, infra, quiz)
   - 운영환경: 서비스별 독립 인스턴스 또는 클러스터로 분리 가능
   - 마이그레이션: 각 서비스별 Flyway로 관리

8. Security Architecture

   - 목표: Keycloak Access Token 검증(게이트웨이/Ingress), 각 서비스는 Resource Server
   - 현재(개발편의): 공통 모듈에서 permitAll + CORS 허용
     - 임시 사용자 컨텍스트는 `X-User-UUID` 헤더로 전달 가능
   - 서비스 간 통신: 내부 네트워크(private subnet) 제한

9. Logging & Monitoring

   - AWS CloudWatch: 기본 인프라 모니터링
   - On-premise: Fluentbit → Loki → Grafana
   - Infra Service DB: Error/System Event 로그 적재

10. Build & Runtime Environment

    - Backend: Spring Boot 3.3, Java 17, Gradle (multi-module)
    - Container/Orchestration: Docker / Kubernetes (EKS)
    - AI Server: Python, FastAPI / Triton / vLLM, Milvus

11. Directory Structure
    backend-platform/
    ├─ chat-service/
    ├─ education-service/
    ├─ infra-service/
    ├─ quiz-service/
    ├─ libs/
    │ ├─ common-dto/
    │ ├─ common-security/
    │ ├─ common-utils/
    └─ docs/
    └─ architecture/
    └─ overview.md

12. System Summary

- Architecture: Distributed Microservice + RAG + LLM Inference
- Services: chat, education, infra, quiz, llm
- DB: PostgreSQL (개발: 스키마 분리 / 운영: 분리 가능), Milvus(Vector)
- Network: CloudFront → ALB → EKS → LLM (VPN)
- Auth: Keycloak(운영), 개발은 임시 permitAll
- Deployment: GitHub Actions + DockerHub + EKS
- Logging: Fluentbit → Loki → Grafana
