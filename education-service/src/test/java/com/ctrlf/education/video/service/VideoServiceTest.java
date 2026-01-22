package com.ctrlf.education.video.service;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.script.entity.EducationScript;
import com.ctrlf.education.script.repository.EducationScriptRepository;
import com.ctrlf.education.video.client.VideoAiClient;
import com.ctrlf.education.video.dto.VideoDtos.LastVideoProgressResponse;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaItem;
import com.ctrlf.education.video.dto.VideoDtos.VideoRetryResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatusResponse;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.EducationVideoProgress;
import com.ctrlf.education.video.entity.EducationVideoReview;
import com.ctrlf.education.video.entity.VideoGenerationJob;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.EducationVideoReviewRepository;
import com.ctrlf.education.video.repository.VideoGenerationJobRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VideoService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoService 테스트")
class VideoServiceTest {

    @Mock
    private EducationScriptRepository scriptRepository;

    @Mock
    private VideoGenerationJobRepository jobRepository;

    @Mock
    private EducationVideoRepository videoRepository;

    @Mock
    private EducationVideoReviewRepository reviewRepository;

    @Mock
    private EducationRepository educationRepository;

    @Mock
    private VideoAiClient videoAiClient;

    @Mock
    private EducationVideoProgressRepository videoProgressRepository;

    @Mock
    private InfraRagClient infraRagClient;

    @InjectMocks
    private VideoService videoService;

    private UUID testJobId;
    private UUID testScriptId;
    private UUID testEducationId;
    private UUID testVideoId;
    private UUID testUserId;
    private UUID testReviewerId;
    private EducationScript testScript;
    private EducationVideo testVideo;
    private Education testEducation;
    private VideoGenerationJob testJob;
    private EducationVideoProgress testProgress;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testScriptId = UUID.randomUUID();
        testEducationId = UUID.randomUUID();
        testVideoId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testReviewerId = UUID.randomUUID();

        testScript = new EducationScript();
        testScript.setId(testScriptId);
        testScript.setEducationId(testEducationId);
        testScript.setVersion(1);
        testScript.setStatus("APPROVED");

        testEducation = new Education();
        testEducation.setId(testEducationId);
        testEducation.setTitle("테스트 교육");
        testEducation.setCategory(EducationTopic.JOB_DUTY);
        testEducation.setEduType(EducationCategory.MANDATORY);

        testVideo = EducationVideo.create(
            testEducationId,
            "테스트 영상",
            null,
            600,
            1,
            VideoStatus.SCRIPT_APPROVED
        );
        testVideo.setId(testVideoId);
        testVideo.setScriptId(testScriptId);

        testJob = new VideoGenerationJob();
        testJob.setId(testJobId);
        testJob.setEducationId(testEducationId);
        testJob.setScriptId(testScriptId);
        testJob.setStatus("QUEUED");
        testJob.setRetryCount(0);

        testProgress = EducationVideoProgress.create(testUserId, testEducationId, testVideoId);
        testProgress.setId(UUID.randomUUID());
        testProgress.setLastPositionSeconds(100);
        testProgress.setProgress(50);
        testProgress.setTotalWatchSeconds(300);

        ReflectionTestUtils.setField(videoService, "infraBaseUrl", "http://localhost:9003");
    }

    @Test
    @DisplayName("영상 생성 Job 생성 - 성공")
    void createVideoJob_Success() {
        // given
        VideoJobRequest request = new VideoJobRequest(
            testEducationId,
            testScriptId,
            testVideoId
        );
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(jobRepository.save(any(VideoGenerationJob.class))).thenAnswer(invocation -> {
            VideoGenerationJob saved = invocation.getArgument(0);
            saved.setId(testJobId);
            return saved;
        });
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoAiClient.createRenderJob(any(RenderJobRequest.class))).thenReturn(
            new RenderJobResponse(true, testJobId, "PROCESSING")
        );
        when(jobRepository.save(any(VideoGenerationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoJobResponse result = videoService.createVideoJob(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.jobId()).isEqualTo(testJobId);
        verify(jobRepository).save(any(VideoGenerationJob.class));
        verify(videoRepository).save(any(EducationVideo.class));
    }

    @Test
    @DisplayName("영상 생성 Job 생성 - 스크립트를 찾을 수 없음")
    void createVideoJob_ScriptNotFound() {
        // given
        VideoJobRequest request = new VideoJobRequest(
            testEducationId,
            testScriptId,
            testVideoId
        );
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> videoService.createVideoJob(request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("영상 생성 Job 생성 - 잘못된 상태")
    void createVideoJob_InvalidStatus() {
        // given
        testVideo.setStatus(VideoStatus.DRAFT);
        VideoJobRequest request = new VideoJobRequest(
            testEducationId,
            testScriptId,
            testVideoId
        );
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));

        // when & then
        assertThatThrownBy(() -> videoService.createVideoJob(request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(400);
    }

    @Test
    @DisplayName("영상 생성 Job 재시도 - 성공")
    void retryVideoJob_Success() {
        // given
        testJob.setStatus("FAILED");
        when(jobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(VideoGenerationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoRetryResponse result = videoService.retryVideoJob(testJobId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.jobId()).isEqualTo(testJobId);
        assertThat(result.retryCount()).isEqualTo(1);
        verify(jobRepository).save(any(VideoGenerationJob.class));
    }

    @Test
    @DisplayName("영상 생성 Job 재시도 - Job을 찾을 수 없음")
    void retryVideoJob_NotFound() {
        // given
        when(jobRepository.findById(testJobId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> videoService.retryVideoJob(testJobId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("영상 생성 Job 재시도 - PROCESSING 상태는 재시도 불가")
    void retryVideoJob_Processing() {
        // given
        testJob.setStatus("PROCESSING");
        when(jobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));

        // when & then
        assertThatThrownBy(() -> videoService.retryVideoJob(testJobId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(409);
    }

    @Test
    @DisplayName("영상 생성 완료 콜백 처리 - 성공")
    void handleVideoComplete_Success() {
        // given
        VideoCompleteCallback callback = new VideoCompleteCallback(
            testJobId,
            "https://video-url.com/video.mp4",
            600,
            "COMPLETED"
        );
        when(jobRepository.findById(testJobId)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(VideoGenerationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.findByGenerationJobId(testJobId)).thenReturn(Optional.of(testVideo));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoCompleteResponse result = videoService.handleVideoComplete(testJobId, callback);

        // then
        assertThat(result).isNotNull();
        assertThat(result.saved()).isTrue();
        verify(jobRepository).save(any(VideoGenerationJob.class));
        verify(videoRepository).save(any(EducationVideo.class));
    }

    @Test
    @DisplayName("영상 생성 완료 콜백 처리 - Job을 찾을 수 없음")
    void handleVideoComplete_JobNotFound() {
        // given
        VideoCompleteCallback callback = new VideoCompleteCallback(
            testJobId,
            "https://video-url.com/video.mp4",
            600,
            "COMPLETED"
        );
        when(jobRepository.findById(testJobId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> videoService.handleVideoComplete(testJobId, callback))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("영상 컨텐츠 생성 - 성공")
    void createVideoContent_Success() {
        // given
        VideoCreateRequest request = new VideoCreateRequest(
            testEducationId,
            "새 영상"
        );
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> {
            EducationVideo saved = invocation.getArgument(0);
            saved.setId(testVideoId);
            return saved;
        });

        // when
        VideoCreateResponse result = videoService.createVideoContent(request, testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.videoId()).isEqualTo(testVideoId);
        verify(videoRepository).save(any(EducationVideo.class));
    }

    @Test
    @DisplayName("영상 컨텐츠 조회 - 성공")
    void getVideoContent_Success() {
        // given
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));

        // when
        VideoMetaItem result = videoService.getVideoContent(testVideoId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testVideoId);
        assertThat(result.educationId()).isEqualTo(testEducationId);
    }

    @Test
    @DisplayName("영상 컨텐츠 조회 - 영상을 찾을 수 없음")
    void getVideoContent_NotFound() {
        // given
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> videoService.getVideoContent(testVideoId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(404);
    }

    @Test
    @DisplayName("검토 요청 - 1차 검토 (SCRIPT_READY → SCRIPT_REVIEW_REQUESTED)")
    void requestReview_FirstRound() {
        // given
        testVideo.setStatus(VideoStatus.SCRIPT_READY);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(scriptRepository.save(any(EducationScript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.requestReview(testVideoId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.videoId()).isEqualTo(testVideoId);
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.SCRIPT_REVIEW_REQUESTED.name());
        verify(scriptRepository).save(any(EducationScript.class));
        verify(videoRepository).save(any(EducationVideo.class));
    }

    @Test
    @DisplayName("검토 요청 - 2차 검토 (READY → FINAL_REVIEW_REQUESTED)")
    void requestReview_SecondRound() {
        // given
        testVideo.setStatus(VideoStatus.READY);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.requestReview(testVideoId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.FINAL_REVIEW_REQUESTED.name());
    }

    @Test
    @DisplayName("검토 요청 - 잘못된 상태")
    void requestReview_InvalidStatus() {
        // given
        testVideo.setStatus(VideoStatus.DRAFT);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));

        // when & then
        assertThatThrownBy(() -> videoService.requestReview(testVideoId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting("statusCode.value")
            .isEqualTo(400);
    }

    @Test
    @DisplayName("검토 승인 - 1차 승인 (SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED)")
    void approveVideo_FirstRound() {
        // given
        testVideo.setStatus(VideoStatus.SCRIPT_REVIEW_REQUESTED);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(scriptRepository.save(any(EducationScript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.approveVideo(testVideoId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.SCRIPT_APPROVED.name());
        verify(scriptRepository).save(any(EducationScript.class));
    }

    @Test
    @DisplayName("검토 승인 - 2차 승인 (FINAL_REVIEW_REQUESTED → PUBLISHED)")
    void approveVideo_SecondRound() {
        // given
        testVideo.setStatus(VideoStatus.FINAL_REVIEW_REQUESTED);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.approveVideo(testVideoId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.PUBLISHED.name());
    }

    @Test
    @DisplayName("검토 반려 - 1차 반려 (SCRIPT_REVIEW_REQUESTED → SCRIPT_READY)")
    void rejectVideo_FirstRound() {
        // given
        testVideo.setStatus(VideoStatus.SCRIPT_REVIEW_REQUESTED);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(scriptRepository.save(any(EducationScript.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(EducationVideoReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.rejectVideo(testVideoId, "반려 사유", testReviewerId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.SCRIPT_READY.name());
        verify(reviewRepository).save(any(EducationVideoReview.class));
    }

    @Test
    @DisplayName("검토 반려 - 2차 반려 (FINAL_REVIEW_REQUESTED → READY)")
    void rejectVideo_SecondRound() {
        // given
        testVideo.setStatus(VideoStatus.FINAL_REVIEW_REQUESTED);
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(scriptRepository.findById(testScriptId)).thenReturn(Optional.of(testScript));
        when(reviewRepository.save(any(EducationVideoReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.save(any(EducationVideo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        VideoStatusResponse result = videoService.rejectVideo(testVideoId, "반려 사유", testReviewerId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.currentStatus()).isEqualTo(VideoStatus.READY.name());
    }

    @Test
    @DisplayName("마지막 시청 영상 조회 - 성공")
    void getLastVideoProgress_Success() {
        // given
        when(videoProgressRepository.findLatestByUserUuid(testUserId)).thenReturn(List.of(testProgress));
        when(videoRepository.findById(testVideoId)).thenReturn(Optional.of(testVideo));
        when(educationRepository.findById(testEducationId)).thenReturn(Optional.of(testEducation));

        // when
        LastVideoProgressResponse result = videoService.getLastVideoProgress(testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.video_id()).isEqualTo(testVideoId.toString());
        assertThat(result.education_id()).isEqualTo(testEducationId.toString());
        assertThat(result.resume_position_seconds()).isEqualTo(100);
    }

    @Test
    @DisplayName("마지막 시청 영상 조회 - 진행 기록 없음")
    void getLastVideoProgress_NoProgress() {
        // given
        when(videoProgressRepository.findLatestByUserUuid(testUserId)).thenReturn(Collections.emptyList());

        // when
        LastVideoProgressResponse result = videoService.getLastVideoProgress(testUserId);

        // then
        assertThat(result).isNull();
    }
}
