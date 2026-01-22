package com.ctrlf.education.service;

import com.ctrlf.education.dto.EducationRequests.VideoProgressUpdateRequest;
import com.ctrlf.education.dto.EducationResponses;
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
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * EducationService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EducationService 테스트")
class EducationServiceTest {

    @Mock
    private EducationRepository educationRepository;

    @Mock
    private EducationVideoRepository educationVideoRepository;

    @Mock
    private EducationVideoProgressRepository educationVideoProgressRepository;

    @Mock
    private EducationProgressRepository educationProgressRepository;

    @Mock
    private InfraRagClient infraRagClient;

    @Mock
    private SourceSetDocumentRepository sourceSetDocumentRepository;

    @InjectMocks
    private EducationService educationService;

    private UUID testEducationId;
    private UUID testVideoId;
    private UUID testUserId;
    private Education testEducation;
    private EducationVideo testVideo;

    @BeforeEach
    void setUp() {
        testEducationId = UUID.randomUUID();
        testVideoId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testEducation = new Education();
        testEducation.setId(testEducationId);
        testEducation.setTitle("테스트 교육");
        testEducation.setCategory(EducationTopic.JOB_DUTY);
        testEducation.setEduType(EducationCategory.MANDATORY);
        testEducation.setRequire(true);
        testEducation.setPassScore(80);
        testEducation.setPassRatio(100);

        testVideo = EducationVideo.create(
            testEducationId,
            "테스트 영상",
            null, // fileUrl
            600,  // duration
            1,    // version
            VideoStatus.PUBLISHED
        );
        testVideo.setId(testVideoId);
    }

    @Test
    @DisplayName("교육 목록 조회 - 성공")
    void getEducationsMe_Success() {
        // given
        List<Education> educations = List.of(testEducation);
        when(educationRepository.findEducations(any(), any())).thenReturn(educations);
        when(educationRepository.findCompletedEducationIdsByUser(testUserId)).thenReturn(Collections.emptyList());
        when(educationRepository.findInProgressEducationIdsByUser(testUserId)).thenReturn(Collections.emptyList());
        when(educationVideoRepository.findByEducationIdInAndStatus(anyCollection(), eq(VideoStatus.PUBLISHED)))
            .thenReturn(Collections.emptyList());
        when(educationVideoProgressRepository.findByUserUuidAndEducationIdIn(any(), anyCollection()))
            .thenReturn(Collections.emptyList());

        // when
        List<EducationResponses.EducationListItem> result = educationService.getEducationsMe(
            null, null, null, Optional.of(testUserId), Collections.emptyList()
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("테스트 교육");
    }

    @Test
    @DisplayName("교육 영상 목록 조회 - 성공")
    void getEducationVideos_Success() {
        // given
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));
        when(educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
            eq(testEducationId), eq(VideoStatus.PUBLISHED))).thenReturn(List.of(testVideo));
        when(educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoIdIn(
            any(), eq(testEducationId), anyCollection())).thenReturn(Collections.emptyList());

        // when
        EducationResponses.EducationVideosResponse result = educationService.getEducationVideos(
            testEducationId, Optional.of(testUserId), Collections.emptyList()
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getVideos()).hasSize(1);
        assertThat(result.getVideos().get(0).getTitle()).isEqualTo("테스트 영상");
    }

    @Test
    @DisplayName("교육 영상 목록 조회 - 교육을 찾을 수 없음")
    void getEducationVideos_EducationNotFound() {
        // given
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> educationService.getEducationVideos(
            testEducationId, Optional.of(testUserId), Collections.emptyList()
        )).isInstanceOf(ResponseStatusException.class)
          .extracting("statusCode.value")
          .isEqualTo(404);
    }

    @Test
    @DisplayName("영상 진행률 업데이트 - 성공")
    void updateVideoProgress_Success() {
        // given
        VideoProgressUpdateRequest request = new VideoProgressUpdateRequest();
        request.setPosition(100);
        request.setWatchTime(10);

        when(educationVideoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));
        when(educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoId(
            testUserId, testEducationId, testVideoId)).thenReturn(Optional.empty());
        when(educationVideoProgressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
            eq(testEducationId), eq(VideoStatus.PUBLISHED))).thenReturn(List.of(testVideo));
        when(educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoIdIn(
            eq(testUserId), eq(testEducationId), anyCollection())).thenReturn(Collections.emptyList());

        // when
        EducationResponses.VideoProgressResponse result = educationService.updateVideoProgress(
            testEducationId, testVideoId, testUserId, request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.isUpdated()).isTrue();
        assertThat(result.getTotalWatchSeconds()).isEqualTo(10);
    }

    @Test
    @DisplayName("영상 진행률 업데이트 - 사용자 UUID 없음")
    void updateVideoProgress_UserUuidRequired() {
        // given
        VideoProgressUpdateRequest request = new VideoProgressUpdateRequest();

        // when & then
        assertThatThrownBy(() -> educationService.updateVideoProgress(
            testEducationId, testVideoId, null, request
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("user required");
    }

    @Test
    @DisplayName("영상 진행률 업데이트 - 영상을 찾을 수 없음")
    void updateVideoProgress_VideoNotFound() {
        // given
        VideoProgressUpdateRequest request = new VideoProgressUpdateRequest();
        when(educationVideoRepository.findById(testVideoId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> educationService.updateVideoProgress(
            testEducationId, testVideoId, testUserId, request
        )).isInstanceOf(ResponseStatusException.class)
          .extracting("statusCode.value")
          .isEqualTo(404);
    }
}
