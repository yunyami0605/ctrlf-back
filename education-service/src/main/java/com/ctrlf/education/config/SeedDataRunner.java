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
import com.ctrlf.education.video.entity.SourceSet;
import com.ctrlf.education.video.entity.SourceSetDocument;
import com.ctrlf.education.quiz.entity.QuizAttempt;
import com.ctrlf.education.quiz.entity.QuizQuestion;
import com.ctrlf.education.quiz.repository.QuizAttemptRepository;
import com.ctrlf.education.quiz.repository.QuizQuestionRepository;
import com.ctrlf.education.quiz.repository.QuizLeaveTrackingRepository;
import com.ctrlf.common.dto.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 시드 데이터 주입기.
 * 활성화: --spring.profiles.active=local,local-seed
 */
@Profile("local-seed")
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
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizLeaveTrackingRepository quizLeaveTrackingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();
    private final String infraBaseUrl;
    private final RestClient restClient;

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
        QuizAttemptRepository quizAttemptRepository,
        QuizQuestionRepository quizQuestionRepository,
        QuizLeaveTrackingRepository quizLeaveTrackingRepository,
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
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizLeaveTrackingRepository = quizLeaveTrackingRepository;
        this.infraBaseUrl = infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl;
        this.restClient = RestClient.builder().baseUrl(this.infraBaseUrl).build();
    }

    @Override
    @Transactional
    public void run(String... args) {
        clearAllEducationData();
        
        // infra-service를 통해 실제 Keycloak 유저 정보 가져오기
        List<UserInfo> users = fetchUsersFromInfraService();
        if (users.isEmpty()) {
            log.warn("유저 데이터를 가져올 수 없어 시드 데이터 생성을 건너뜁니다.");
            return;
        }
        
        log.info("{} 명의 유저 정보를 가져왔습니다.", users.size());
        
        // 유저 정보를 사용하여 모든 데이터 생성
        seedScriptsAndJobs(users);
        seedEducationsVideosAndProgress(users);
        seedQuizData(users);
    }

    /**
     * 기존 교육 관련 데이터를 모두 삭제합니다.
     * FK 관계로 인해 자식 테이블부터 삭제합니다.
     */
    private void clearAllEducationData() {
        log.info("기존 교육 데이터 삭제 시작...");
        
        // 1. 교육 진행 현황 삭제 (education_id FK 참조)
        educationProgressRepository.deleteAll();
        log.info("교육 진행 현황 삭제 완료");
        
        // 2. 영상 진행률 삭제
        educationVideoProgressRepository.deleteAll();
        log.info("교육 영상 진행률 삭제 완료");
        
        // 3. 소스셋 문서 삭제 (source_set_id FK 참조)
        sourceSetDocumentRepository.deleteAll();
        log.info("소스셋 문서 삭제 완료");
        
        // 4. 소스셋 삭제 (video_id FK 참조하므로 video보다 먼저 삭제)
        sourceSetRepository.deleteAll();
        log.info("소스셋 삭제 완료");
        
        // 5. 영상 검토(리뷰) 삭제 (video_id FK 참조하므로 video보다 먼저 삭제)
        educationVideoReviewRepository.deleteAll();
        log.info("영상 검토 삭제 완료");
        
        // 6. 영상 삭제
        educationVideoRepository.deleteAll();
        log.info("교육 영상 삭제 완료");
        
        // 7. 영상 생성 작업(Job) 삭제 - script를 참조하므로 스크립트보다 먼저 삭제
        videoGenerationJobRepository.deleteAll();
        log.info("영상 생성 작업 삭제 완료");
        
        // 8. 스크립트 씬 삭제
        sceneRepository.deleteAll();
        log.info("스크립트 씬 삭제 완료");
        
        // 9. 스크립트 챕터 삭제
        chapterRepository.deleteAll();
        log.info("스크립트 챕터 삭제 완료");
        
        // 10. 스크립트 삭제
        scriptRepository.deleteAll();
        log.info("스크립트 삭제 완료");
        
        // 11. 퀴즈 문항 삭제 (attempt_id FK 참조)
        quizQuestionRepository.deleteAll();
        log.info("퀴즈 문항 삭제 완료");
        
        // 12. 퀴즈 이탈 추적 삭제 (attempt_id FK 참조)
        quizLeaveTrackingRepository.deleteAll();
        log.info("퀴즈 이탈 추적 삭제 완료");
        
        // 12. 퀴즈 시도 삭제 (education_id FK 참조)
        quizAttemptRepository.deleteAll();
        log.info("퀴즈 시도 삭제 완료");
        
        // 13. 교육 삭제
        educationRepository.deleteAll();
        log.info("교육 삭제 완료");
        
        log.info("기존 교육 데이터 삭제 완료!");
    }

    private void seedScriptsAndJobs(List<UserInfo> users) {
        // 5가지 교육 카테고리별 시드 데이터 생성
        List<Education> educations = new ArrayList<>();

        // 1. 직무 교육 (JOB_DUTY) - edu_type: JOB
        Education edu1 = new Education();
        edu1.setTitle("직무 역량 강화 교육");
        edu1.setCategory(EducationTopic.JOB_DUTY);
        edu1.setDescription("업무 수행에 필요한 핵심 역량을 강화하는 직무 교육입니다.");
        edu1.setPassScore(80);
        edu1.setPassRatio(90);
        edu1.setRequire(Boolean.TRUE);
        edu1.setEduType(EducationCategory.JOB);
        educations.add(edu1);

        // 2. 성희롱 예방 교육 (SEXUAL_HARASSMENT_PREVENTION) - edu_type: MANDATORY
        Education edu2 = new Education();
        edu2.setTitle("성희롱 예방 교육");
        edu2.setCategory(EducationTopic.SEXUAL_HARASSMENT_PREVENTION);
        edu2.setDescription("직장 내 성희롱 예방 및 대응 방법에 대한 법정 필수 교육입니다.");
        edu2.setPassScore(80);
        edu2.setPassRatio(90);
        edu2.setRequire(Boolean.TRUE);
        edu2.setEduType(EducationCategory.MANDATORY);
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
        educations.add(edu5);

        // 모든 교육 저장
        educationRepository.saveAll(educations);
        log.info("Seed created: {} 개의 교육 시드 데이터 생성 완료", educations.size());

        // 각 교육에 대해 스크립트 생성
        List<UUID> scriptIds = new ArrayList<>();
        String[] scriptTitles = {
            "직무 역량 강화 교육 영상",
            "성희롱 예방 교육 영상",
            "개인정보 보호 교육 영상",
            "직장 내 괴롭힘 예방 교육 영상",
            "장애인 인식 개선 교육 영상"
        };

        for (int i = 0; i < educations.size(); i++) {
            UUID scriptId = insertScript(educations.get(i).getId(), null,
                scriptTitles[i] + " 스크립트 내용입니다.", 1, scriptTitles[i]);
            scriptIds.add(scriptId);
        }

        // 스크립트 INSERT를 먼저 DB에 반영
        scriptRepository.flush();

        // 각 스크립트에 대해 챕터/씬 시드
        for (UUID scriptId : scriptIds) {
            seedChaptersAndScenes(scriptId);
        }

        // 챕터/씬 반영
        chapterRepository.flush();
        sceneRepository.flush();

        // Job 시드는 별도 러너(@Order(2))에서 처리합니다.
    }

    /**
     * 사용자용 API 확인을 위한 영상/진행 더미 데이터.
     * infra-service에서 가져온 실제 유저 정보를 사용합니다.
     */
    private void seedEducationsVideosAndProgress(List<UserInfo> users) {
        // 이미 영상이 있으면 스킵
        List<Education> edus = educationRepository.findAll();
        if (edus.isEmpty()) return;

        // 첫 번째 유저를 기본 유저로 사용 (또는 랜덤 선택)
        UUID demoUser = users.isEmpty() 
            ? UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            : users.get(0).userUuid;

        for (Education edu : edus) {
            var videos = educationVideoRepository.findByEducationId(edu.getId());
            // 영상이 없으면 더미 영상 추가
            if (videos.isEmpty()) {
                List<com.ctrlf.education.video.entity.EducationVideo> seeds = new ArrayList<>();
                String[] urls = new String[] {
                    "s3://ctrl-s3/video/13654077_3840_2160_30fps.mp4",
                    "s3://ctrl-s3/video/13671318_3840_2160_25fps.mp4",
                    "s3://ctrl-s3/video/14876583_3840_2160_30fps.mp4",
                    "s3://ctrl-s3/video/14899783_1920_1080_50fps.mp4",
                    "s3://ctrl-s3/video/14903571_3840_2160_25fps.mp4"
                };
                String[] titles = new String[] {
                    edu.getTitle() + " - 기본편",
                    edu.getTitle() + " - 심화편",
                    edu.getTitle() + " - 사례편",
                    edu.getTitle() + " - 실무편",
                    edu.getTitle() + " - 종합편"
                };
                int[] durations = new int[] { 1200, 900, 1100, 1000, 950 };
                // 제작자 UUID는 사용자 목록에서 랜덤 선택 (사용자가 있으면)
                UUID creatorUuid = users.isEmpty() ? null : users.get(random.nextInt(users.size())).userUuid;
                for (int i = 0; i < urls.length; i++) {
                    var v = com.ctrlf.education.video.entity.EducationVideo.create(
                        edu.getId(),
                        titles[i],
                        urls[i],
                        durations[i],
                        1,
                        "PUBLISHED"
                    );
                    v.setOrderIndex(i);
                    v.setCreatorUuid(creatorUuid);
                    seeds.add(v);
                }
                educationVideoRepository.saveAll(seeds);
                videos = educationVideoRepository.findByEducationId(edu.getId());
                log.info("Seed created: {} dummy videos for eduId={}", videos.size(), edu.getId());
            }
            if (videos.isEmpty()) {
                log.info("Seed skip: 교육에 연결된 영상이 없어 진행률 더미 생성을 건너뜁니다. eduId={}", edu.getId());
                continue;
            }
            /**
             * s3://ctrl-s3/video/13654077_3840_2160_30fps.mp4
             * s3://ctrl-s3/video/13654077_3840_2160_30fps.mp4
             * s3://ctrl-s3/video/13671318_3840_2160_25fps.mp4
             * s3://ctrl-s3/video/14876583_3840_2160_30fps.mp4
             * s3://ctrl-s3/video/14899783_1920_1080_50fps.mp4
             * s3://ctrl-s3/video/14903571_3840_2160_25fps.mp4
             */

            // 각 영상에 대해 source-set 생성
            for (var v : videos) {
                createSourceSetForVideo(edu.getId(), v.getId(), demoUser.toString());
            }
            
            // 진행률 더미: 각 유저별로 랜덤하게 진행률 생성
            // passRatio 기준으로 완료 여부 판단
            Integer passRatio = edu.getPassRatio() != null ? edu.getPassRatio() : 100;
            for (UserInfo user : users) {
                for (int i = 0; i < videos.size(); i++) {
                    var v = videos.get(i);
                    var p = educationVideoProgressRepository
                        .findByUserUuidAndEducationIdAndVideoId(user.userUuid, edu.getId(), v.getId())
                        .orElseGet(() -> com.ctrlf.education.video.entity.EducationVideoProgress.create(user.userUuid, edu.getId(), v.getId()));
                    
                    // 랜덤 진행률 설정
                    int progress = random.nextInt(101); // 0~100%
                    int totalSeconds = v.getDuration() != null ? v.getDuration() : 1200;
                    int watchedSeconds = (int) (totalSeconds * progress / 100.0);
                    
                    p.setLastPositionSeconds(watchedSeconds);
                    p.setTotalWatchSeconds(watchedSeconds);
                    p.setProgress(progress);
                    // passRatio 기준으로 완료 여부 판단
                    p.setIsCompleted(progress >= passRatio);
                    educationVideoProgressRepository.save(p);
                }
            }
        }
    }
    
    /**
     * 영상에 대한 source-set 생성
     */
    private void createSourceSetForVideo(UUID educationId, UUID videoId, String requestedBy) {
        // 이미 source-set이 있으면 스킵 (videoId로 조회)
        List<SourceSet> existingSourceSets = sourceSetRepository.findAll().stream()
            .filter(ss -> videoId.equals(ss.getVideoId()) && ss.getDeletedAt() == null)
            .toList();
        if (!existingSourceSets.isEmpty()) {
            log.debug("Source-set이 이미 존재합니다. videoId={}", videoId);
            return;
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
        
        // Source-set에 더미 문서 추가 (실제로는 infra-service의 RAG 문서를 사용해야 함)
        // 여기서는 더미 UUID를 사용
        UUID dummyDocumentId = UUID.randomUUID();
        SourceSetDocument doc = SourceSetDocument.create(sourceSet, dummyDocumentId);
        doc.markCompleted(); // 처리 완료 상태로 설정
        sourceSetDocumentRepository.save(doc);
        
        log.info("Seed created: SourceSet sourceSetId={}, videoId={}, educationId={}", 
            sourceSet.getId(), videoId, educationId);
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
        scriptRepository.save(s);
        log.info("Seed created: EducationScript scriptId={}, eduId={}, title={}", s.getId(), eduId, title);
        return s.getId();
    }

    private void seedChaptersAndScenes(UUID scriptId) {
        if (!chapterRepository.findByScriptIdOrderByChapterIndexAsc(scriptId).isEmpty()) {
            log.info("Seed skip: 챕터/씬이 이미 존재합니다. scriptId={}", scriptId);
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

    /**
     * 실제 유저 데이터 기반으로 퀴즈 시드 데이터 생성.
     * infra-service를 통해 Keycloak의 실제 유저 정보를 가져와 퀴즈 시도 데이터를 생성합니다.
     */
    private void seedQuizData(List<UserInfo> users) {
        log.info("퀴즈 시드 데이터 생성 시작...");
        
        List<Education> educations = educationRepository.findAll();
        if (educations.isEmpty()) {
            log.warn("교육 데이터가 없어 퀴즈 시드 데이터 생성을 건너뜁니다.");
            return;
        }

        if (users.isEmpty()) {
            log.warn("유저 데이터가 없어 퀴즈 시드 데이터 생성을 건너뜁니다.");
            return;
        }
        
        log.info("{} 명의 유저 정보로 퀴즈 데이터를 생성합니다.", users.size());

        for (UserInfo user : users) {
            UUID userUuid = user.userUuid;
            
            for (Education edu : educations) {
                // 각 교육당 최대 2번 시도
                int attemptCount = random.nextInt(2) + 1; // 1 또는 2
                
                for (int attemptNo = 1; attemptNo <= attemptCount; attemptNo++) {
                    QuizAttempt attempt = new QuizAttempt();
                    attempt.setUserUuid(userUuid);
                    attempt.setEducationId(edu.getId());
                    attempt.setAttemptNo(attemptNo);
                    attempt.setTimeLimit(900); // 15분
                    attempt.setDepartment(user.department);
                    // 최근 90일 내 랜덤 시간으로 생성 (다양한 기간 필터 테스트를 위해)
                    Instant createdAt = Instant.now().minusSeconds(random.nextInt(86400 * 90));
                    attempt.setCreatedAt(createdAt);
                    
                    // 제출 완료된 시도만 점수 설정
                    boolean submitted = random.nextDouble() > 0.2; // 80% 확률로 제출 완료
                    if (submitted) {
                        int score = 60 + random.nextInt(41); // 60~100점
                        boolean passed = score >= (edu.getPassScore() != null ? edu.getPassScore() : 80);
                        
                        attempt.setScore(score);
                        attempt.setPassed(passed);
                        // submittedAt은 createdAt보다 나중이지만, 최근 90일 내로 제한
                        Instant submittedAt = createdAt.plusSeconds(300 + random.nextInt(600)); // 시작 후 5~15분 후 제출
                        // submittedAt이 현재보다 미래인 경우 현재 시간으로 제한
                        if (submittedAt.isAfter(Instant.now())) {
                            submittedAt = Instant.now();
                        }
                        attempt.setSubmittedAt(submittedAt);
                        
                        // 퀴즈 문항 생성 (5개)
                        List<QuizQuestion> questions = createQuizQuestions(attempt.getId(), edu);
                        quizQuestionRepository.saveAll(questions);
                    }
                    
                    quizAttemptRepository.save(attempt);
                }
            }
        }
        
        quizAttemptRepository.flush();
        log.info("퀴즈 시드 데이터 생성 완료!");
    }

    /**
     * infra-service를 통해 Keycloak 유저 목록 가져오기
     */
    private List<UserInfo> fetchUsersFromInfraService() {
        List<UserInfo> users = new ArrayList<>();
        
        // 최대 5번 재시도 (Keycloak 시작 대기)
        int maxRetries = 5;
        int retryDelayMs = 2000; // 2초
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("infra-service에서 유저 목록 조회 중... (baseUrl: {}, 시도: {}/{})", infraBaseUrl, attempt, maxRetries);
                
                // infra-service의 /admin/users/search API 호출
                PageResponse<Map<String, Object>> pageResponse = restClient.get()
                    .uri("/admin/users/search?page=0&size=200")
                    .retrieve()
                    .body(new ParameterizedTypeReference<PageResponse<Map<String, Object>>>() {});
                
                if (pageResponse == null || pageResponse.getItems() == null || pageResponse.getItems().isEmpty()) {
                    log.warn("infra-service에서 유저 목록이 비어있습니다.");
                    return users;
                }
            
            for (Map<String, Object> userMap : pageResponse.getItems()) {
                try {
                    String userId = (String) userMap.get("id");
                    String username = (String) userMap.get("username");
                    Boolean enabled = (Boolean) userMap.get("enabled");
                    
                    // enabled가 false이거나 null인 경우 제외
                    if (enabled == null || !enabled) {
                        continue;
                    }
                    
                    // attributes에서 department 추출
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes = (Map<String, Object>) userMap.get("attributes");
                    String department = "기타";
                    if (attributes != null) {
                        @SuppressWarnings("unchecked")
                        List<String> deptList = (List<String>) attributes.get("department");
                        if (deptList != null && !deptList.isEmpty()) {
                            department = deptList.get(0);
                        }
                    }
                    
                    UUID userUuid = UUID.fromString(userId);
                    users.add(new UserInfo(userUuid, username, department));
                    log.debug("유저 정보 추가: username={}, department={}, userId={}", username, department, userId);
                } catch (Exception e) {
                    log.warn("유저 정보 파싱 실패: {}", userMap, e);
                }
            }
            
                log.info("infra-service에서 {} 명의 유저를 가져왔습니다.", users.size());
                return users; // 성공 시 즉시 반환
                
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 401) {
                    log.warn("infra-service 인증 오류 (시도 {}/{}): {}. Keycloak이 아직 시작되지 않았을 수 있습니다.", 
                        attempt, maxRetries, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue; // 재시도
                    }
                } else {
                    log.error("infra-service HTTP 오류 (시도 {}/{}): {}", attempt, maxRetries, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue; // 재시도
                    }
                }
            } catch (org.springframework.web.client.ResourceAccessException e) {
                log.warn("infra-service 연결 실패 (시도 {}/{}): {}. infra-service가 아직 시작되지 않았을 수 있습니다.", 
                    attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue; // 재시도
                }
            } catch (Exception e) {
                log.error("infra-service에서 유저 목록을 가져오는 중 오류 발생 (시도 {}/{}): {}", 
                    attempt, maxRetries, e.getMessage(), e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue; // 재시도
                }
            }
        }
        
        log.warn("infra-service에서 유저 목록을 가져오지 못했습니다. 퀴즈 시드 데이터 생성을 건너뜁니다.");
        return users;
    }

    /**
     * 퀴즈 문항 생성
     */
    private List<QuizQuestion> createQuizQuestions(UUID attemptId, Education edu) {
        List<QuizQuestion> questions = new ArrayList<>();
        String[] questionTemplates = {
            edu.getTitle() + "에 대한 올바른 설명은?",
            edu.getTitle() + "에서 중요한 사항은?",
            edu.getTitle() + "의 목적은?",
            edu.getTitle() + "에서 주의해야 할 점은?",
            edu.getTitle() + "의 핵심 내용은?"
        };
        
        String[] optionsTemplate = {"보기 1", "보기 2", "보기 3", "보기 4", "보기 5"};
        
        for (int i = 0; i < 5; i++) {
            QuizQuestion question = new QuizQuestion();
            question.setAttemptId(attemptId);
            question.setQuestion(questionTemplates[i]);
            
            try {
                question.setOptions(objectMapper.writeValueAsString(optionsTemplate));
            } catch (Exception e) {
                question.setOptions("[\"보기 1\", \"보기 2\", \"보기 3\", \"보기 4\", \"보기 5\"]");
            }
            
            question.setCorrectOptionIdx(random.nextInt(5)); // 0~4 중 랜덤
            question.setExplanation("정답 설명입니다.");
            question.setQuestionOrder(i);
            
            // 제출된 경우 사용자 선택값 설정
            question.setUserSelectedOptionIdx(random.nextInt(5)); // 0~4 중 랜덤
            
            questions.add(question);
        }
        
        return questions;
    }

    /**
     * 유저 정보 클래스
     */
    private static class UserInfo {
        final UUID userUuid;
        final String username;
        final String department;
        
        UserInfo(UUID userUuid, String username, String department) {
            this.userUuid = userUuid;
            this.username = username;
            this.department = department;
        }
    }
}

