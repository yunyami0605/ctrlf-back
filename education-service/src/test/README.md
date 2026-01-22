# Education Service 테스트

## 개요

이 디렉토리에는 `education-service`의 단위 테스트가 포함되어 있습니다.

## 테스트 구조

```
src/test/java/com/ctrlf/education/
├── service/
│   ├── EducationServiceTest.java          # EducationService 단위 테스트
│   └── AdminEducationServiceTest.java      # AdminEducationService 단위 테스트
├── repository/
│   └── EducationRepositoryTest.java        # EducationRepository 통합 테스트
└── exception/
    └── GlobalExceptionHandlerTest.java    # GlobalExceptionHandler 테스트
```

## 테스트 실행

### 모든 테스트 실행
```bash
cd ctrlf-back/education-service
./gradlew test
```

### 특정 테스트 클래스 실행
```bash
./gradlew test --tests EducationServiceTest
```

### 특정 테스트 메서드 실행
```bash
./gradlew test --tests EducationServiceTest.getEducationsMe_Success
```

## 테스트 커버리지

테스트 커버리지를 확인하려면:

```bash
./gradlew test jacocoTestReport
```

커버리지 리포트는 `build/reports/jacoco/test/html/index.html`에서 확인할 수 있습니다.

## 테스트 작성 가이드

### 단위 테스트 (Unit Test)

- **Mockito**를 사용하여 의존성을 모킹합니다
- `@ExtendWith(MockitoExtension.class)` 사용
- `@Mock`으로 의존성 모킹
- `@InjectMocks`로 테스트 대상 주입

예시:
```java
@ExtendWith(MockitoExtension.class)
class EducationServiceTest {
    @Mock
    private EducationRepository educationRepository;
    
    @InjectMocks
    private EducationService educationService;
}
```

### 통합 테스트 (Integration Test)

- **@DataJpaTest**를 사용하여 실제 데이터베이스와 통합 테스트
- H2 인메모리 데이터베이스 사용
- `TestEntityManager`로 엔티티 관리

예시:
```java
@DataJpaTest
@ActiveProfiles("test")
class EducationRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private EducationRepository educationRepository;
}
```

## 테스트 작성 원칙

1. **Given-When-Then** 패턴 사용
2. **@DisplayName**으로 테스트 설명 명확히
3. **AssertJ**를 사용한 명확한 어설션
4. 각 테스트는 독립적으로 실행 가능해야 함
5. 테스트 메서드명은 `메서드명_시나리오_예상결과` 형식

## 주요 테스트 시나리오

### EducationService
- ✅ 교육 목록 조회 (성공/실패)
- ✅ 교육 영상 목록 조회
- ✅ 영상 진행률 업데이트
- ✅ 사용자 권한 검증

### AdminEducationService
- ✅ 교육 생성
- ✅ 교육 상세 조회
- ✅ 교육 수정
- ✅ 교육 삭제

### GlobalExceptionHandler
- ✅ EntityNotFoundException 처리 (404)
- ✅ BusinessException 처리
- ✅ ValidationException 처리 (400)
- ✅ 일반 Exception 처리 (500)
