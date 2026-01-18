package com.ctrlf.education.config;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.entity.EducationScript;
import com.ctrlf.education.script.entity.EducationScriptChapter;
import com.ctrlf.education.script.entity.EducationScriptScene;
import com.ctrlf.education.script.repository.EducationScriptChapterRepository;
import com.ctrlf.education.script.repository.EducationScriptRepository;
import com.ctrlf.education.script.repository.EducationScriptSceneRepository;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.EducationVideoReviewRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import com.ctrlf.education.video.repository.SourceSetRepository;
import com.ctrlf.education.video.repository.VideoGenerationJobRepository;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.SourceSet;
import com.ctrlf.education.video.entity.SourceSetDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 시드 데이터 주입기.
 * 활성화: --spring.profiles.active=dev,local-seed 또는 dev,dev-seed
 */
@Profile({"local-seed", "dev-seed"})
@Order(1)
@Component
public class SeedDataRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private final EducationRepository educationRepository;
    private final EducationScriptRepository scriptRepository;
    private final EducationScriptChapterRepository chapterRepository;
    private final EducationScriptSceneRepository sceneRepository;
    private final EducationVideoRepository educationVideoRepository;
    private final EducationVideoProgressRepository educationVideoProgressRepository;
    private final EducationVideoReviewRepository educationVideoReviewRepository;
    private final EducationProgressRepository educationProgressRepository;
    private final VideoGenerationJobRepository videoGenerationJobRepository;
    private final SourceSetDocumentRepository sourceSetDocumentRepository;
    private final SourceSetRepository sourceSetRepository;

    private final String infraBaseUrl;
    private final RestClient restClient;
    private UUID seedDocumentId = null; // 시드 문서 ID 캐시

    public SeedDataRunner(
        EducationRepository educationRepository,
        EducationScriptRepository scriptRepository,
        EducationScriptChapterRepository chapterRepository,
        EducationScriptSceneRepository sceneRepository,
        EducationVideoRepository educationVideoRepository,
        EducationVideoProgressRepository educationVideoProgressRepository,
        EducationVideoReviewRepository educationVideoReviewRepository,
        EducationProgressRepository educationProgressRepository,
        VideoGenerationJobRepository videoGenerationJobRepository,
        SourceSetDocumentRepository sourceSetDocumentRepository,
        SourceSetRepository sourceSetRepository,
        @Value("${app.infra.base-url:http://localhost:9003}") String infraBaseUrl
    ) {
        this.educationRepository = educationRepository;
        this.scriptRepository = scriptRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.educationVideoRepository = educationVideoRepository;
        this.educationVideoProgressRepository = educationVideoProgressRepository;
        this.educationVideoReviewRepository = educationVideoReviewRepository;
        this.educationProgressRepository = educationProgressRepository;
        this.videoGenerationJobRepository = videoGenerationJobRepository;
        this.sourceSetDocumentRepository = sourceSetDocumentRepository;
        this.sourceSetRepository = sourceSetRepository;
        this.infraBaseUrl = infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl;
        this.restClient = RestClient.builder().baseUrl(this.infraBaseUrl).build();
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            log.info("Starting seed data generation...");
            clearAllEducationData();
            
            // 교육 시드만 생성
            seedEducations();
            seedAllEducationVideos();
            
            log.info("Seed data generation completed successfully!");
        } catch (Exception e) {
            log.error("Failed to generate seed data: {}", e.getMessage(), e);
            throw e; // 트랜잭션 롤백을 위해 예외를 다시 던짐
        }
    }

    /**
     * 기존 교육 관련 데이터를 모두 삭제합니다.
     * FK 관계로 인해 자식 테이블부터 삭제합니다.
     */
    private void clearAllEducationData() {
        log.info("Starting to delete existing education data...");
        
        // 1. 교육 진행 현황 삭제 (education_id FK 참조)
        educationProgressRepository.deleteAll();
        log.info("Education progress deleted");
        
        // 2. 영상 진행률 삭제
        educationVideoProgressRepository.deleteAll();
        log.info("Education video progress deleted");
        
        // 3. 소스셋 문서 삭제 (source_set_id FK 참조)
        sourceSetDocumentRepository.deleteAll();
        log.info("Source set documents deleted");
        
        // 4. 소스셋 삭제 (video_id FK 참조하므로 video보다 먼저 삭제)
        sourceSetRepository.deleteAll();
        log.info("Source sets deleted");
        
        // 5. 영상 검토(리뷰) 삭제 (video_id FK 참조하므로 video보다 먼저 삭제)
        educationVideoReviewRepository.deleteAll();
        log.info("Education video reviews deleted");
        
        // 6. 영상 삭제
        educationVideoRepository.deleteAll();
        log.info("Education videos deleted");
        
        // 7. 영상 생성 작업(Job) 삭제 - script를 참조하므로 스크립트보다 먼저 삭제
        videoGenerationJobRepository.deleteAll();
        log.info("Video generation jobs deleted");
        
        // 8. 스크립트 씬 삭제
        sceneRepository.deleteAll();
        log.info("Script scenes deleted");
        
        // 9. 스크립트 챕터 삭제
        chapterRepository.deleteAll();
        log.info("Script chapters deleted");
        
        // 10. 스크립트 삭제
        scriptRepository.deleteAll();
        log.info("Scripts deleted");
        
        // 11. 교육 삭제
        educationRepository.deleteAll();
        log.info("Educations deleted");
        
        log.info("Existing education data deletion completed!");
    }

    /**
     * 교육 시드만 생성
     */
    private void seedEducations() {
        log.info("Starting to seed educations...");
        List<Education> educations = new ArrayList<>();

        // 1. 직무 교육 (JOB_DUTY) - edu_type: JOB - 8개 부서별로 생성
        String[] departments = {"총무팀", "기획팀", "마케팅팀", "인사팀", "재무팀", "개발팀", "영업팀", "법무팀"};
        String[] departmentDescriptions = {
            "총무 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "기획 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "마케팅 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "인사 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "재무 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "개발 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "영업 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.",
            "법무 업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다."
        };
        int[] startDaysAgo = {30, 35, 40, 45, 50, 55, 60, 65}; // 각 교육마다 다른 시작일
        
        for (int i = 0; i < departments.length; i++) {
            Education edu = new Education();
            edu.setTitle(departments[i] + " 직무 역량 강화 교육");
            edu.setCategory(EducationTopic.JOB_DUTY);
            edu.setDescription(departmentDescriptions[i]);
            edu.setPassScore(80);
            edu.setPassRatio(90);
            edu.setRequire(Boolean.TRUE);
            edu.setEduType(EducationCategory.JOB);
            edu.setVersion(1);
            edu.setStartAt(Instant.now().minusSeconds(86400L * startDaysAgo[i])); // 각각 다른 시작일
            edu.setEndAt(Instant.now().plusSeconds(86400L * (180 - startDaysAgo[i]))); // 총 180일
            edu.setDepartmentScope(new String[]{departments[i]});
            educations.add(edu);
        }

        // 2. 성희롱 예방 교육 (SEXUAL_HARASSMENT_PREVENTION) - edu_type: MANDATORY
        Education edu2 = new Education();
        edu2.setTitle("성희롱 예방 교육");
        edu2.setCategory(EducationTopic.SEXUAL_HARASSMENT_PREVENTION);
        edu2.setDescription("직장 내 성희롱 예방 및 대응 방법에 대한 법정 필수 교육입니다.");
        edu2.setPassScore(80);
        edu2.setPassRatio(90);
        edu2.setRequire(Boolean.TRUE);
        edu2.setEduType(EducationCategory.MANDATORY);
        edu2.setVersion(1);
        edu2.setStartAt(Instant.now().minusSeconds(86400 * 60)); // 60일 전 시작
        edu2.setEndAt(Instant.now().plusSeconds(86400 * 120)); // 120일 후 종료 (총 180일)
        edu2.setDepartmentScope(new String[]{"전체 부서"});
        educations.add(edu2);

        // 3. 개인정보 보호 교육 (PERSONAL_INFO_PROTECTION) - edu_type: MANDATORY
        Education edu3 = new Education();
        edu3.setTitle("개인정보 보호 교육");
        edu3.setCategory(EducationTopic.PERSONAL_INFO_PROTECTION);
        edu3.setDescription("개인정보 보호법에 따른 개인정보 취급 및 보호에 관한 법정 필수 교육입니다.");
        edu3.setPassScore(80);
        edu3.setPassRatio(90);
        edu3.setRequire(Boolean.TRUE);
        edu3.setEduType(EducationCategory.MANDATORY);
        edu3.setVersion(1);
        edu3.setStartAt(Instant.now().minusSeconds(86400 * 90)); // 90일 전 시작
        edu3.setEndAt(Instant.now().plusSeconds(86400 * 90)); // 90일 후 종료 (총 180일)
        edu3.setDepartmentScope(new String[]{"전체 부서"});
        educations.add(edu3);

        // 4. 직장 내 괴롭힘 예방 교육 (WORKPLACE_BULLYING) - edu_type: MANDATORY
        Education edu4 = new Education();
        edu4.setTitle("직장 내 괴롭힘 예방 교육");
        edu4.setCategory(EducationTopic.WORKPLACE_BULLYING);
        edu4.setDescription("직장 내 괴롭힘 예방 및 대응 방법에 대한 법정 필수 교육입니다.");
        edu4.setPassScore(80);
        edu4.setPassRatio(90);
        edu4.setRequire(Boolean.TRUE);
        edu4.setEduType(EducationCategory.MANDATORY);
        edu4.setVersion(1);
        edu4.setStartAt(Instant.now().minusSeconds(86400 * 120)); // 120일 전 시작
        edu4.setEndAt(Instant.now().plusSeconds(86400 * 60)); // 60일 후 종료 (총 180일)
        edu4.setDepartmentScope(new String[]{"전체 부서"});
        educations.add(edu4);

        // 5. 장애인 인식 개선 교육 (DISABILITY_AWARENESS) - edu_type: MANDATORY
        Education edu5 = new Education();
        edu5.setTitle("장애인 인식 개선 교육");
        edu5.setCategory(EducationTopic.DISABILITY_AWARENESS);
        edu5.setDescription("장애인에 대한 인식 개선 및 편견 해소를 위한 법정 필수 교육입니다.");
        edu5.setPassScore(80);
        edu5.setPassRatio(90);
        edu5.setRequire(Boolean.TRUE);
        edu5.setEduType(EducationCategory.MANDATORY);
        edu5.setVersion(1);
        edu5.setStartAt(Instant.now().minusSeconds(86400 * 15)); // 15일 전 시작
        edu5.setEndAt(Instant.now().plusSeconds(86400 * 165)); // 165일 후 종료 (총 180일)
        edu5.setDepartmentScope(new String[]{"전체 부서"});
        educations.add(edu5);

        // 모든 교육 저장
        educationRepository.saveAll(educations);
        log.info("Seed created: {} education(s) created", educations.size());
    }
    
    /**
     * 모든 교육에 대한 영상 시드 생성
     */
    private void seedAllEducationVideos() {
        log.info("Starting to seed all education videos...");
        
        List<Education> educations = educationRepository.findAll();
        UUID creatorUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        
        // 비디오 시드 데이터 정의
        List<VideoSeedData> videoSeeds = List.of(
            // 1. 개인정보 보호 교육 - 8초
            new VideoSeedData("개인정보 보호 교육", "정보보호", 
                "s3://ctrl-s3/education_videos/정보보호.mp4", 8),
            // 2. 개인정보 보호 교육 - 1초
            new VideoSeedData("개인정보 보호 교육", "개인정보 보호 교육", 
                "s3://ctrl-s3/education_videos/정보보호.mp4", 1),
            // 3. 성희롱 예방 교육 - 1초
            new VideoSeedData("성희롱 예방 교육", "직장 내 성희롱 예방 교육", 
                "s3://ctrl-s3/education_videos/성희롱.mp4", 1),
            // 4. 직장 내 괴롭힘 예방 교육 - 25초
            new VideoSeedData("직장 내 괴롭힘 예방 교육", "직장 내 괴롭힘 판단 기준 및 대응", 
                "s3://ctrl-s3/education_videos/compa.mp4", 25),
            // 5. 장애인 인식 개선 교육 - 6초
            new VideoSeedData("장애인 인식 개선 교육", "함께 일하는 동료, 장애인 인식 개선", 
                "https://ctrl-s3.s3.ap-northeast-2.amazonaws.com/education_videos/hurt.mp4", 6),
            // 6. 장애인 인식 개선 교육 - 1초
            new VideoSeedData("장애인 인식 개선 교육", "장애인 인식 개선 우리의 동료", 
                "s3://ctrl-s3/education_videos/hurt.mp4", 1),
            // 7. 개발팀 직무 역량 강화 교육 - MSA - 8초
            new VideoSeedData("개발팀 직무 역량 강화 교육", "MSA 아키텍처 패턴", 
                "s3://ctrl-s3/education_videos/MSA.mp4", 8),
            // 8. 인사팀 직무 역량 강화 교육 - 4초
            new VideoSeedData("인사팀 직무 역량 강화 교육", "인사직무 핵심 교육", 
                "s3://ctrl-s3/education_videos/insa.mp4", 4),
            // 9. 개발팀 직무 역량 강화 교육 - CI/CD - 3초
            new VideoSeedData("개발팀 직무 역량 강화 교육", "CI/CD 실무 마스터", 
                "s3://ctrl-s3/education_videos/cicd.mp4", 3)
        );
        
        int orderIndex = 0;
        for (VideoSeedData seed : videoSeeds) {
            Education education = educations.stream()
                .filter(e -> seed.educationTitle.equals(e.getTitle()))
                .findFirst()
                .orElse(null);
            
            if (education == null) {
                log.warn("Education not found: {}. Skipping video: {}", seed.educationTitle, seed.videoTitle);
                continue;
            }
            
            // 영상 생성 (ACTIVE 상태, 이미 렌더링 완료된 영상)
            var video = com.ctrlf.education.video.entity.EducationVideo.create(
                education.getId(),
                seed.videoTitle,
                seed.fileUrl,
                seed.duration,
                1,      // version
                VideoStatus.PUBLISHED // status - 바로 재생 가능
            );
            
            // 같은 교육 내 기존 비디오 수 계산하여 orderIndex 설정
            int existingCount = educationVideoRepository.findByEducationId(education.getId()).size();
            video.setOrderIndex(existingCount);
            video.setCreatorUuid(creatorUuid);
            educationVideoRepository.save(video);
            
            // SourceSet 생성
            UUID sourceSetId = createSourceSetForVideo(
                education.getId(),
                video.getId(),
                creatorUuid.toString()
            );
            video.setSourceSetId(sourceSetId);
            
            // Script 생성
            UUID scriptId = insertScript(
                education.getId(),
                null,
                "{\"chapters\":[]}",
                1,
                seed.videoTitle + " 스크립트"
            );
            video.setScriptId(scriptId);
            educationVideoRepository.save(video);
            
            log.info("Seed created: video eduTitle={}, videoTitle={}, fileUrl={}, duration={}s",
                seed.educationTitle, seed.videoTitle, seed.fileUrl, seed.duration);
            orderIndex++;
        }
        
        log.info("Completed seeding {} education videos", videoSeeds.size());
    }
    
    /**
     * 비디오 시드 데이터를 담는 내부 클래스
     */
    private static class VideoSeedData {
        final String educationTitle;
        final String videoTitle;
        final String fileUrl;
        final int duration;
        
        VideoSeedData(String educationTitle, String videoTitle, String fileUrl, int duration) {
            this.educationTitle = educationTitle;
            this.videoTitle = videoTitle;
            this.fileUrl = fileUrl;
            this.duration = duration;
        }
    }
    
    /**
     * 시드 문서를 조회하거나 생성합니다.
     * 문서가 이미 존재하면 그 documentId를 반환하고, 없으면 업로드를 시도합니다.
     */
    private UUID getOrCreateSeedDocument(String uploaderUuid) {
        // 캐시된 documentId가 있으면 재사용
        if (seedDocumentId != null) {
            return seedDocumentId;
        }
        
        String seedFileUrl = "s3://ctrl-s3/docs/hr_safety_v3.pdf";
        String seedTitle = "산업안전 규정집 v3";
        String seedDomain = "HR";
        
        try {
            // 문서 목록에서 키워드로 검색
            List<Map<String, Object>> documents = restClient.get()
                .uri("/rag/documents?keyword=hr_safety_v3")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            
            if (documents != null && !documents.isEmpty()) {
                for (Map<String, Object> doc : documents) {
                    String id = (String) doc.get("id");
                    if (id != null) {
                        seedDocumentId = UUID.fromString(id);
                        log.info("Found existing seed document: documentId={}, fileUrl={}", seedDocumentId, seedFileUrl);
                        return seedDocumentId;
                    }
                }
            } else {
                log.debug("No documents found with keyword 'hr_safety_v3'");
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("Failed to search existing document (HTTP {}): {}", e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to search existing document: {}", e.getMessage());
        }
        
        // 문서가 없으면 업로드 시도 (JWT가 필요하므로 실패할 수 있음)
        try {
            Map<String, String> uploadRequest = Map.of(
                "title", seedTitle,
                "domain", seedDomain,
                "fileUrl", seedFileUrl
            );
            
            Map<String, Object> uploadResponse = restClient.post()
                .uri("/rag/documents/upload")
                .body(uploadRequest)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            
            if (uploadResponse != null) {
                String documentIdStr = (String) uploadResponse.get("documentId");
                if (documentIdStr != null) {
                    seedDocumentId = UUID.fromString(documentIdStr);
                    log.info("Uploaded seed document: documentId={}, fileUrl={}", seedDocumentId, seedFileUrl);
                    return seedDocumentId;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to upload seed document (JWT may be required): {}", e.getMessage());
        }
        
        // 문서 업로드도 실패하면 경고 출력
        log.warn("Seed document not found in infra-service and upload failed. Please upload document manually: {}", seedFileUrl);
        log.warn("Using dummy UUID for now. Document lookup will fail until document is uploaded.");
        seedDocumentId = UUID.randomUUID();
        return seedDocumentId;
    }

    /**
     * 영상에 대한 source-set 생성
     */
    private UUID createSourceSetForVideo(UUID educationId, UUID videoId, String requestedBy) {
        // 이미 source-set이 있으면 스킵 (videoId로 조회)
        List<SourceSet> existingSourceSets = sourceSetRepository.findAll().stream()
            .filter(ss -> videoId.equals(ss.getVideoId()) && ss.getDeletedAt() == null)
            .toList();
        if (!existingSourceSets.isEmpty()) {
            log.debug("Source-set already exists. videoId={}", videoId);
            return existingSourceSets.get(0).getId();
        }
        
        // Source-set 생성
        SourceSet sourceSet = SourceSet.create(
            "교육 영상 소스셋",
            "EDUCATION",
            requestedBy,
            educationId,
            videoId
        );
        sourceSetRepository.save(sourceSet);
        
        // Source-set에 실제 문서 추가
        // 시드 문서를 한 번만 조회/생성하고 재사용
        UUID documentId = getOrCreateSeedDocument(requestedBy);
        SourceSetDocument doc = SourceSetDocument.create(sourceSet, documentId);
        doc.markCompleted(); // 처리 완료 상태로 설정
        sourceSetDocumentRepository.save(doc);
        
        log.info("Seed created: SourceSet sourceSetId={}, videoId={}, educationId={}", 
            sourceSet.getId(), videoId, educationId);
        
        return sourceSet.getId();
    }

    private UUID insertScript(UUID eduId, UUID materialId, String content, Integer version, String title) {
        EducationScript s = new EducationScript();
        s.setId(UUID.randomUUID()); // UUID 수동 생성
        s.setEducationId(eduId);
        // Note: sourceDocId는 제거되었으며, 이제 SourceSet을 통해 문서와 연결됩니다.
        s.setTitle(title);
        s.setTotalDurationSec(720);
        // 변경된 스키마에 맞춰 raw_payload(JSONB)에 저장
        // content가 JSON이 아닐 수 있으므로 간단히 JSON 문자열로 포장
        String payload = content != null && content.trim().startsWith("{")
            ? content
            : "{\"script\":\"" + content.replace("\"", "\\\"") + "\"}";
        s.setRawPayload(payload);
        s.setVersion(version);
        s.setStatus("APPROVED"); // 스크립트 상태를 APPROVED로 설정
        scriptRepository.save(s);
        log.info("Seed created: EducationScript scriptId={}, eduId={}, title={}", s.getId(), eduId, title);
        return s.getId();
    }

    private void seedChaptersAndScenes(UUID scriptId) {
        if (!chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId).isEmpty()) {
            log.info("Seed skip: Chapters/scenes already exist. scriptId={}", scriptId);
            return;
        }
        // Chapter 0: 괴롭힘
        EducationScriptChapter ch0 = new EducationScriptChapter();
        ch0.setScriptId(scriptId);
        ch0.setChapterIndex(0);
        ch0.setTitle("괴롭힘");
        ch0.setDurationSec(180);
        chapterRepository.save(ch0);

        List<EducationScriptScene> ch0Scenes = new ArrayList<>();
        ch0Scenes.add(buildScene(scriptId, ch0.getId(), 1, "hook",
            "직장 내 괴롭힘이란 사용자에게 심리적, 물리적, 경제적 피해를 주는 행위를 의미합니다.",
            "직장 내 괴롭힘이란 사용자에게 심리적, 물리적, 경제적 피해를 주는 행위를 의미합니다.",
            "자료 원문 문장(텍스트) 강조 + 키워드 하이라이트",
            15, new int[]{1,2,3}, 0.95f));
        ch0Scenes.add(buildScene(scriptId, ch0.getId(), 2, "concept",
            "직장 내 괴롭힘이란 근로자의 정신적, 윤리적, 물리적 피해를 초래하는 행동을 의미합니다.",
            "직장 내 괴롭힘이란 사용자에게 심리적, 물리적, 경제적 피해를 주는 행위를 의미합니다.",
            "자료 원문 문장(텍스트) 강조 + 키워드 하이라이트",
            45, new int[]{1,2,3}, 0.9f));
        sceneRepository.saveAll(ch0Scenes);

        // Chapter 1: 직무
        EducationScriptChapter ch1 = new EducationScriptChapter();
        ch1.setScriptId(scriptId);
        ch1.setChapterIndex(1);
        ch1.setTitle("직무");
        ch1.setDurationSec(180);
        chapterRepository.save(ch1);

        List<EducationScriptScene> ch1Scenes = new ArrayList<>();
        ch1Scenes.add(buildScene(scriptId, ch1.getId(), 1, "hook",
            "업무상 적정 범위를 넘는 행위",
            "업무상 적정 범위를 넘는 행위",
            "자료 원문 문장(텍스트) 강조 + 키워드 하이라이트",
            15, new int[]{12,24,26}, 0.9f));
        ch1Scenes.add(buildScene(scriptId, ch1.getId(), 2, "example",
            "학교 교감이 전공과 무관한 과목 강의를 배정하고 위협을 가하는 등 부적절한 행위 사례.",
            "부적절한 인사 조치와 위협 사례",
            "자료 원문 문장(텍스트) 강조 + 키워드 하이라이트",
            30, new int[]{12,24,26}, 0.85f));
        sceneRepository.saveAll(ch1Scenes);
    }

    private EducationScriptScene buildScene(
        UUID scriptId, UUID chapterId, int sceneIndex, String purpose,
        String narration, String caption, String visual,
        int durationSec, int[] sourceChunks, Float confidence
    ) {
        EducationScriptScene s = new EducationScriptScene();
        s.setScriptId(scriptId);
        s.setChapterId(chapterId);
        s.setSceneIndex(sceneIndex);
        s.setPurpose(purpose);
        s.setNarration(narration);
        s.setCaption(caption);
        s.setVisual(visual);
        s.setDurationSec(durationSec);
        s.setSourceChunkIndexes(sourceChunks);
        s.setConfidenceScore(confidence);
        return s;
    }
}

