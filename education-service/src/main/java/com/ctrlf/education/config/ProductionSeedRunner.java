package com.ctrlf.education.config;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.repository.EducationVideoRepository;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로덕션 환경용 시드 데이터 주입기.
 * 활성화: --spring.profiles.active=prod,prod-seed
 * 
 * 주의사항:
 * - 기존 데이터를 삭제하지 않음
 * - 이미 존재하는 데이터는 스킵
 * - 새로운 데이터만 추가
 */
@Profile("prod-seed")
@Order(1)
@Component
public class ProductionSeedRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductionSeedRunner.class);

    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;

    public ProductionSeedRunner(
        EducationRepository educationRepository,
        EducationVideoRepository educationVideoRepository
    ) {
        this.educationRepository = educationRepository;
        this.educationVideoRepository = educationVideoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            log.info("Starting production seed data generation...");
            
            // 교육 시드 생성 (기존 데이터는 유지)
            seedEducations();
            seedVideoForPersonalInfoEducation();
            
            log.info("Production seed data generation completed successfully!");
        } catch (Exception e) {
            // 프로덕션에서는 시드 실패해도 서비스 시작을 막지 않음
            log.error("Failed to generate production seed data (non-blocking): {}", e.getMessage(), e);
            // 예외를 다시 던지지 않아서 서비스 시작이 계속됨
        }
    }

    /**
     * 교육 시드 생성 (기존 데이터는 유지)
     */
    private void seedEducations() {
        log.info("Starting to seed educations (production mode - no deletion)...");
        
        try {
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
            int[] startDaysAgo = {30, 35, 40, 45, 50, 55, 60, 65};
            
            for (int i = 0; i < departments.length; i++) {
                String title = departments[i] + " 직무 역량 강화 교육";
                // 이미 존재하는지 확인 (findAll 대신 findByTitleAndDeletedAtIsNull 사용)
                if (educationRepository.findByTitleAndDeletedAtIsNull(title).isPresent()) {
                    log.debug("Education already exists, skipping: {}", title);
                    continue;
                }
                
                try {
                    Education edu = new Education();
                    edu.setTitle(title);
                    edu.setCategory(EducationTopic.JOB_DUTY);
                    edu.setDescription(departmentDescriptions[i]);
                    edu.setPassScore(80);
                    edu.setPassRatio(90);
                    edu.setRequire(Boolean.TRUE);
                    edu.setEduType(EducationCategory.JOB);
                    edu.setVersion(1);
                    edu.setStartAt(Instant.now().minusSeconds(86400L * startDaysAgo[i]));
                    edu.setEndAt(Instant.now().plusSeconds(86400L * (180 - startDaysAgo[i])));
                    edu.setDepartmentScope(new String[]{departments[i]});
                    educationRepository.save(edu);
                    log.info("Created education: {}", title);
                } catch (Exception e) {
                    log.warn("Failed to create education '{}': {}", title, e.getMessage());
                    // 개별 실패해도 계속 진행
                }
            }

            // 2. 성희롱 예방 교육 (SEXUAL_HARASSMENT_PREVENTION) - edu_type: MANDATORY
            createEducationIfNotExists(
                "성희롱 예방 교육",
                EducationTopic.SEXUAL_HARASSMENT_PREVENTION,
                "직장 내 성희롱 예방 및 대응 방법에 대한 법정 필수 교육입니다.",
                EducationCategory.MANDATORY,
                60,
                120
            );

            // 3. 개인정보 보호 교육 (PERSONAL_INFO_PROTECTION) - edu_type: MANDATORY
            createEducationIfNotExists(
                "개인정보 보호 교육",
                EducationTopic.PERSONAL_INFO_PROTECTION,
                "개인정보 보호법에 따른 개인정보 취급 및 보호에 관한 법정 필수 교육입니다.",
                EducationCategory.MANDATORY,
                90,
                90
            );

            // 4. 직장 내 괴롭힘 예방 교육 (WORKPLACE_BULLYING) - edu_type: MANDATORY
            createEducationIfNotExists(
                "직장 내 괴롭힘 예방 교육",
                EducationTopic.WORKPLACE_BULLYING,
                "직장 내 괴롭힘 예방 및 대응 방법에 대한 법정 필수 교육입니다.",
                EducationCategory.MANDATORY,
                120,
                60
            );

            // 5. 장애인 인식 개선 교육 (DISABILITY_AWARENESS) - edu_type: MANDATORY
            createEducationIfNotExists(
                "장애인 인식 개선 교육",
                EducationTopic.DISABILITY_AWARENESS,
                "장애인에 대한 인식 개선 및 편견 해소를 위한 법정 필수 교육입니다.",
                EducationCategory.MANDATORY,
                15,
                165
            );
            
            log.info("Education seeding completed (production mode)");
        } catch (Exception e) {
            log.error("Error during education seeding: {}", e.getMessage(), e);
            throw e; // 상위에서 처리하도록
        }
    }
    
    /**
     * 교육이 없으면 생성하는 헬퍼 메서드
     */
    private void createEducationIfNotExists(
        String title,
        EducationTopic category,
        String description,
        EducationCategory eduType,
        int startDaysAgo,
        int endDaysFromNow
    ) {
        try {
            if (educationRepository.findByTitleAndDeletedAtIsNull(title).isPresent()) {
                log.debug("Education already exists, skipping: {}", title);
                return;
            }
            
            Education edu = new Education();
            edu.setTitle(title);
            edu.setCategory(category);
            edu.setDescription(description);
            edu.setPassScore(80);
            edu.setPassRatio(90);
            edu.setRequire(Boolean.TRUE);
            edu.setEduType(eduType);
            edu.setVersion(1);
            edu.setStartAt(Instant.now().minusSeconds(86400L * startDaysAgo));
            edu.setEndAt(Instant.now().plusSeconds(86400L * endDaysFromNow));
            edu.setDepartmentScope(new String[]{"전체 부서"});
            educationRepository.save(edu);
            log.info("Created education: {}", title);
        } catch (Exception e) {
            log.warn("Failed to create education '{}': {}", title, e.getMessage());
            // 개별 실패해도 계속 진행
        }
    }
    
    /**
     * 개인정보 보호 교육에만 영상 1개 생성 (기존 데이터는 유지)
     */
    private void seedVideoForPersonalInfoEducation() {
        log.info("Starting to seed video for personal info education (production mode)...");
        
        // 개인정보 보호 교육 찾기
        List<Education> allEducations = educationRepository.findAll();
        Education personalInfoEducation = allEducations.stream()
            .filter(e -> "개인정보 보호 교육".equals(e.getTitle()) && e.getDeletedAt() == null)
            .findFirst()
            .orElse(null);
        
        if (personalInfoEducation == null) {
            log.warn("Personal info education not found. Skipping video seeding.");
            return;
        }
        
        // 이미 영상이 있으면 스킵
        var existingVideos = educationVideoRepository.findByEducationId(personalInfoEducation.getId());
        if (!existingVideos.isEmpty()) {
            log.info("Video already exists for personal info education. Skipping.");
            return;
        }
        
        // 영상 1개 생성
        var video = EducationVideo.create(
            personalInfoEducation.getId(),
            "개인정보 보호 교육 - 기본편",
            "s3://ctrl-s3/videos/bc36db11-d500-4a7d-9a13-af71c06d5f5c.mp4",
            1200,
            1,
            VideoStatus.PUBLISHED
        );
        video.setOrderIndex(0);
        educationVideoRepository.save(video);
        
        log.info("Seed created: 1 video for personal info education, videoId={}", video.getId());
    }
}

