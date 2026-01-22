package com.ctrlf.education.service;

import com.ctrlf.common.dto.MutationResponse;
import com.ctrlf.education.dto.EducationRequests.CreateEducationRequest;
import com.ctrlf.education.dto.EducationResponses.EducationDetailResponse;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AdminEducationService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminEducationService 테스트")
class AdminEducationServiceTest {

    @Mock
    private EducationRepository educationRepository;

    @Mock
    private EducationVideoRepository educationVideoRepository;

    @Mock
    private EducationVideoProgressRepository educationVideoProgressRepository;

    @Mock
    private EducationProgressRepository educationProgressRepository;

    @Mock
    private SourceSetDocumentRepository sourceSetDocumentRepository;

    @Mock
    private InfraRagClient infraRagClient;

    @InjectMocks
    private AdminEducationService adminEducationService;

    private UUID testEducationId;
    private Education testEducation;

    @BeforeEach
    void setUp() {
        testEducationId = UUID.randomUUID();
        ReflectionTestUtils.setField(adminEducationService, "infraBaseUrl", "http://localhost:9003");

        testEducation = new Education();
        testEducation.setId(testEducationId);
        testEducation.setTitle("테스트 교육");
        testEducation.setCategory(EducationTopic.JOB_DUTY);
        testEducation.setEduType(EducationCategory.MANDATORY);
        testEducation.setRequire(true);
        testEducation.setPassScore(80);
        testEducation.setDescription("테스트 설명");
    }

    @Test
    @DisplayName("교육 생성 - 성공")
    void createEducation_Success() {
        // given
        CreateEducationRequest request = new CreateEducationRequest();
        request.setTitle("새 교육");
        request.setCategory(EducationTopic.JOB_DUTY);
        request.setEduType(EducationCategory.MANDATORY);
        request.setRequire(true);

        when(educationRepository.save(any(Education.class))).thenAnswer(invocation -> {
            Education saved = invocation.getArgument(0);
            saved.setId(testEducationId);
            return saved;
        });

        // when
        MutationResponse<UUID> result = adminEducationService.createEducation(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @DisplayName("교육 상세 조회 - 성공")
    void getEducationDetail_Success() {
        // given
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));
        when(educationVideoRepository.findByEducationId(testEducationId)).thenReturn(Collections.emptyList());

        // when
        EducationDetailResponse result = adminEducationService.getEducationDetail(testEducationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testEducationId);
        assertThat(result.getTitle()).isEqualTo("테스트 교육");
    }

    @Test
    @DisplayName("교육 상세 조회 - 교육을 찾을 수 없음")
    void getEducationDetail_EducationNotFound() {
        // given
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminEducationService.getEducationDetail(testEducationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("교육 상세 조회 - 영상 duration 합산")
    void getEducationDetail_WithVideos() {
        // given
        EducationVideo video1 = EducationVideo.create(
            testEducationId, "영상1", null, 300, 1, VideoStatus.PUBLISHED
        );
        EducationVideo video2 = EducationVideo.create(
            testEducationId, "영상2", null, 600, 1, VideoStatus.PUBLISHED
        );

        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));
        when(educationVideoRepository.findByEducationId(testEducationId))
            .thenReturn(List.of(video1, video2));

        // when
        EducationDetailResponse result = adminEducationService.getEducationDetail(testEducationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDuration()).isEqualTo(900); // 300 + 600
    }
}
