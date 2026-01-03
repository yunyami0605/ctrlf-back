package com.ctrlf.education.video.service;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.script.entity.EducationScript;
import com.ctrlf.education.script.repository.EducationScriptRepository;
import com.ctrlf.education.video.client.VideoAiClient;
import com.ctrlf.education.video.dto.VideoDtos.AiVideoResponse;
import com.ctrlf.education.video.dto.VideoDtos.JobItem;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.RenderJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteCallback;
import com.ctrlf.education.video.dto.VideoDtos.VideoCompleteResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoCreateResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoJobUpdateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaItem;
import com.ctrlf.education.video.dto.VideoDtos.VideoMetaUpdateRequest;
import com.ctrlf.education.video.dto.VideoDtos.VideoRetryResponse;
import com.ctrlf.education.video.dto.VideoDtos.AuditHistoryItem;
import com.ctrlf.education.video.dto.VideoDtos.AuditHistoryResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewDetailResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewQueueItem;
import com.ctrlf.education.video.dto.VideoDtos.ReviewQueueResponse;
import com.ctrlf.education.video.dto.VideoDtos.ReviewStatsResponse;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatusResponse;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.EducationVideoReview;
import com.ctrlf.education.video.entity.RejectionStage;
import com.ctrlf.education.video.entity.VideoGenerationJob;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.EducationVideoReviewRepository;
import com.ctrlf.education.video.repository.VideoGenerationJobRepository;
import com.ctrlf.education.video.repository.SourceSetRepository;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.entity.SourceSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * 영상 생성 관련 비즈니스 로직을 처리하는 서비스.
 *
 * <p>주요 기능:
 * <ul>
 *   <li>영상 생성 Job 등록/조회/수정/삭제/재시도</li>
 *   <li>전처리/임베딩/스크립트 생성 요청 (AI 서버 호출)</li>
   *   <li>영상 생성 완료 콜백 처리</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    /** Job 상태 상수 */
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    private final EducationScriptRepository scriptRepository;
    private final VideoGenerationJobRepository jobRepository;
    private final EducationVideoRepository videoRepository;
    private final EducationVideoReviewRepository reviewRepository;
    private final EducationRepository educationRepository;
    private final VideoAiClient videoAiClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SourceSetRepository sourceSetRepository;
    private final EducationVideoProgressRepository videoProgressRepository;

    @Value("${ctrlf.infra.base-url:http://localhost:9003}")
    private String infraBaseUrl;

    /**
     * infra-service에서 사용자 정보 조회 (제작자 정보용).
     * 
     * @param userId 사용자 UUID
     * @return [부서명, 사용자명] 또는 [null, null] (조회 실패 시)
     */
    private String[] fetchUserInfoFromInfraService(UUID userId) {
        if (userId == null) {
            return new String[]{null, null};
        }
        
        try {
            RestClient restClient = RestClient.builder()
                .baseUrl(infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl)
                .build();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = restClient.get()
                .uri("/admin/users/{userId}", userId.toString())
                .retrieve()
                .body(Map.class);
            
            if (userMap == null) {
                return new String[]{null, null};
            }
            
            String username = (String) userMap.get("username");
            
            // attributes에서 department 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) userMap.get("attributes");
            String department = null;
            if (attributes != null) {
                @SuppressWarnings("unchecked")
                List<String> deptList = (List<String>) attributes.get("department");
                if (deptList != null && !deptList.isEmpty()) {
                    department = deptList.get(0);
                }
            }
            
            return new String[]{department, username};
        } catch (Exception e) {
            log.warn("infra-service에서 사용자 정보 조회 실패: userId={}, error={}", userId, e.getMessage());
            return new String[]{null, null};
        }
    }

    // ========================
    // 영상 생성 Job 관련 메서드
    // ========================

    /**
     * 영상 생성 Job을 등록하고 AI 서버에 요청합니다.
     *
     * @param request 영상 생성 요청 (eduId, scriptId, videoId)
     * @return Job 등록 결과 (jobId, status)
     * @throws ResponseStatusException 스크립트/영상이 존재하지 않을 경우 404
     */
    @Transactional
    public VideoJobResponse createVideoJob(VideoJobRequest request) {
        // 스크립트 존재 확인 및 조회
        EducationScript script = scriptRepository.findById(request.scriptId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "스크립트를 찾을 수 없습니다: " + request.scriptId()));

        // 영상 컨텐츠 존재 확인
        EducationVideo video = videoRepository.findById(request.videoId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상 컨텐츠를 찾을 수 없습니다: " + request.videoId()));

        // 스크립트 승인 상태 확인 (1차 승인 후에만 영상 생성 가능)
        if (!"SCRIPT_APPROVED".equals(video.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "영상 생성은 SCRIPT_APPROVED 상태에서만 가능합니다. " +
                "스크립트 검토 승인을 먼저 받아주세요. 현재 상태: " + video.getStatus());
        }

        // Job 생성
        VideoGenerationJob job = new VideoGenerationJob();
        job.setEducationId(request.eduId());
        job.setScriptId(request.scriptId());
        job.setStatus(STATUS_QUEUED);
        job.setRetryCount(0);
        jobRepository.save(job);

        // 영상 컨텐츠 상태 업데이트 및 Job 연결
        video.setGenerationJobId(job.getId());
        video.setStatus("PROCESSING"); // 영상 생성 중 상태
        videoRepository.save(video);

        // AI 서버에 렌더 생성 요청 (BestEffort)
        try {
            RenderJobRequest renderRequest = new RenderJobRequest(
                job.getId(),
                request.videoId().toString(),
                request.scriptId(),
                script.getVersion() != null ? script.getVersion() : 1, // 스크립트 버전 (기본값 1)
                null, // renderPolicyId는 선택적
                UUID.randomUUID() // requestId (멱등 키)
            );

            RenderJobResponse renderResponse = videoAiClient.createRenderJob(renderRequest);
            
            if (renderResponse != null && renderResponse.received()) {
                log.info("렌더 생성 요청 성공. jobId={}, videoId={}, scriptId={}, status={}",
                    renderResponse.jobId(), request.videoId(), request.scriptId(), renderResponse.status());
                
                // Job 상태를 PROCESSING으로 업데이트 (AI 서버가 PROCESSING으로 응답)
                job.setStatus("PROCESSING");
                jobRepository.save(job);
            } else {
                log.warn("렌더 생성 요청 실패. jobId={}, response={}", job.getId(), renderResponse);
                job.setStatus(STATUS_FAILED);
                job.setFailReason("AI 서버 요청 실패");
                jobRepository.save(job);
                video.setStatus("SCRIPT_APPROVED"); // 상태 롤백
                videoRepository.save(video);
            }
        } catch (Exception e) {
            log.error("렌더 생성 요청 중 오류. jobId={}, error={}", job.getId(), e.getMessage(), e);
            // 실패해도 Job은 생성되었으므로 상태만 업데이트
            job.setStatus(STATUS_FAILED);
            job.setFailReason("AI 서버 요청 중 오류: " + e.getMessage());
            jobRepository.save(job);
            video.setStatus("SCRIPT_APPROVED"); // 상태 롤백
            videoRepository.save(video);
        }

        return new VideoJobResponse(job.getId(), job.getStatus());
    }

    /**
     * 실패한 영상 생성 Job을 재시도합니다.
     *
     * @param jobId 재시도할 Job ID
     * @return 재시도 결과 (jobId, status, retryCount)
     * @throws ResponseStatusException Job이 존재하지 않거나 FAILED 상태가 아닐 경우
     */
    @Transactional
    public VideoRetryResponse retryVideoJob(UUID jobId) {
        VideoGenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: " + jobId));

        // 상태 검증
        if (STATUS_PROCESSING.equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리 중인 Job입니다: " + jobId);
        }
        if (!STATUS_FAILED.equals(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FAILED 상태의 Job만 재시도할 수 있습니다. 현재 상태: " + job.getStatus());
        }

        // 재시도 카운트 증가 및 상태 변경
        int newRetryCount = (job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1;
        job.setRetryCount(newRetryCount);
        job.setStatus(STATUS_QUEUED);
        job.setFailReason(null);
        jobRepository.save(job);

        // AI 서버 미구현: 모의 응답으로 처리
        AiVideoResponse aiResponse = new AiVideoResponse(jobId, true, STATUS_QUEUED);
            job.setStatus(aiResponse.status());
            jobRepository.save(job);
        log.info("[MOCK] 영상 재시도 처리. jobId={}, retryCount={}, status={}", jobId, newRetryCount, aiResponse.status());

        return new VideoRetryResponse(jobId, job.getStatus(), newRetryCount);
    }

    /**
     * AI 서버로부터 영상 생성 완료 콜백을 처리합니다.
     *
     * @param jobId    Job ID (path variable)
     * @param callback 콜백 데이터 (jobId, videoUrl, duration, status)
     * @return 저장 결과
     * @throws ResponseStatusException Job이 존재하지 않을 경우 404
     */
    @Transactional
    public VideoCompleteResponse handleVideoComplete(UUID jobId, VideoCompleteCallback callback) {
        // body의 jobId와 path variable의 jobId 일치 검증
        if (callback.jobId() != null && !callback.jobId().equals(jobId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Path variable의 jobId와 body의 jobId가 일치하지 않습니다: path=" + jobId + ", body=" + callback.jobId()
            );
        }

        VideoGenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: " + jobId));

        // Job 상태 업데이트
        job.setStatus(callback.status());
        job.setGeneratedVideoUrl(callback.videoUrl());
        job.setDuration(callback.duration());
        jobRepository.save(job);

        // 연결된 EducationVideo 업데이트
        videoRepository.findByGenerationJobId(jobId).ifPresent(video -> {
            if ("COMPLETED".equals(callback.status()) || "SUCCEEDED".equals(callback.status())) {
                video.setFileUrl(callback.videoUrl());
                video.setDuration(callback.duration());
                video.setStatus("READY"); // 영상 생성 완료 → READY (검토 전)
                videoRepository.save(video);
                log.info("영상 컨텐츠 업데이트 완료. videoId={}, status=READY, fileUrl={}", 
                    video.getId(), callback.videoUrl());
            } else if ("FAILED".equals(callback.status())) {
                video.setStatus("DRAFT"); // 실패 시 → DRAFT로 복귀
                videoRepository.save(video);
                log.warn("영상 생성 실패. videoId={}, status=DRAFT", video.getId());
            }
        });

        log.info("영상 생성 완료 콜백 처리. jobId={}, status={}, videoUrl={}", 
            jobId, callback.status(), callback.videoUrl());
        return new VideoCompleteResponse(true);
    }

    // ========================
    // 영상 생성 Job 관리 (목록/조회/수정/삭제)
    // ========================

    @Transactional(readOnly = true)
    public List<JobItem> listJobs(int page, int size) {
        Page<VideoGenerationJob> p = jobRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return p.map(this::toJobItem).getContent();
    }

    @Transactional(readOnly = true)
    public JobItem getJob(UUID jobId) {
        VideoGenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: " + jobId));
        return toJobItem(job);
    }

    @Transactional
    public JobItem updateJob(UUID jobId, VideoJobUpdateRequest req) {
        VideoGenerationJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: " + jobId));
        if (req.status() != null && !req.status().isBlank()) {
            job.setStatus(req.status());
        }
        if (req.failReason() != null) {
            job.setFailReason(req.failReason());
        }
        if (req.videoUrl() != null) {
            job.setGeneratedVideoUrl(req.videoUrl());
        }
        if (req.duration() != null) {
            job.setDuration(req.duration());
        }
        job = jobRepository.save(job);
        log.info("영상 생성 Job 업데이트. jobId={}, status={}", job.getId(), job.getStatus());
        return toJobItem(job);
    }

    @Transactional
    public void deleteJob(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: " + jobId);
        }
        jobRepository.deleteById(jobId);
        log.error("영상 생성 Job 삭제 완료. jobId={}", jobId);
    }

    private JobItem toJobItem(VideoGenerationJob j) {
        // jobId로 연결된 video 찾기
        UUID videoId = videoRepository.findByGenerationJobId(j.getId())
            .map(EducationVideo::getId)
            .orElse(null);
        
        return new JobItem(
            j.getId(),
            j.getScriptId(),
            j.getEducationId(),
            videoId,
            j.getStatus(),
            j.getRetryCount(),
            j.getGeneratedVideoUrl(),
            j.getDuration(),
            j.getCreatedAt() != null ? j.getCreatedAt().toString() : null,
            j.getUpdatedAt() != null ? j.getUpdatedAt().toString() : null,
            j.getFailReason()
        );
    }

    // ========================
    // 영상 컨텐츠 관리 (ADMIN)
    // ========================

    /**
     * DRAFT 상태의 새 영상 컨텐츠를 생성합니다.
     */
    @Transactional
    public VideoCreateResponse createVideoContent(VideoCreateRequest request, UUID creatorUuid) {
        EducationVideo video = EducationVideo.createDraft(request.educationId(), request.title(), creatorUuid);
        video = videoRepository.save(video);
        log.info("영상 컨텐츠 생성. videoId={}, title={}, status={}, creatorUuid={}", video.getId(), video.getTitle(), video.getStatus(), creatorUuid);
        return new VideoCreateResponse(video.getId(), video.getStatus());
    }

    /**
     * 영상 상세 조회.
     */
    @Transactional(readOnly = true)
    public VideoMetaItem getVideoContent(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        return toVideoMetaItem(video);
    }

    /**
     * 영상 목록 조회 (페이징).
     */
    @Transactional(readOnly = true)
    public List<VideoMetaItem> listVideoContents(int page, int size) {
        Page<EducationVideo> p = videoRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "orderIndex").and(Sort.by(Sort.Direction.ASC, "createdAt")))
        );
        return p.map(this::toVideoMetaItem).getContent();
    }

    /**
     * 영상 메타 정보 수정.
     */
    @Transactional
    public VideoMetaItem updateVideoContent(UUID videoId, VideoMetaUpdateRequest request) {
        EducationVideo video = findVideoOrThrow(videoId);
        if (request.title() != null) video.setTitle(request.title());
        if (request.fileUrl() != null) video.setFileUrl(request.fileUrl());
        if (request.version() != null) video.setVersion(request.version());
        if (request.duration() != null) video.setDuration(request.duration());
        if (request.status() != null) video.setStatus(request.status().name());
        if (request.orderIndex() != null) video.setOrderIndex(request.orderIndex());
        video = videoRepository.save(video);
        log.info("영상 컨텐츠 수정. videoId={}, status={}", video.getId(), video.getStatus());
        return toVideoMetaItem(video);
    }

    /**
     * 영상 삭제.
     */
    @Transactional
    public void deleteVideoContent(UUID videoId) {
        findVideoOrThrow(videoId);
        
        // source_set에서 video_id 참조를 먼저 해제 (외래키 제약 조건 해결)
        List<SourceSet> sourceSets = sourceSetRepository.findByVideoIdAndNotDeleted(videoId);
        for (SourceSet sourceSet : sourceSets) {
            sourceSet.setVideoId(null);
            sourceSetRepository.save(sourceSet);
            log.debug("SourceSet의 video_id 참조 해제. sourceSetId={}, videoId={}", sourceSet.getId(), videoId);
        }
        
        // education_video_progress에서 video_id 참조 레코드 삭제
        videoProgressRepository.deleteByVideoId(videoId);
        log.debug("EducationVideoProgress 레코드 삭제. videoId={}", videoId);
        
        // education_video_review에서 video_id 참조 레코드 삭제
        reviewRepository.deleteByVideoId(videoId);
        log.debug("EducationVideoReview 레코드 삭제. videoId={}", videoId);
        
        videoRepository.deleteById(videoId);
        log.info("영상 컨텐츠 삭제. videoId={}, 해제된 sourceSet 수={}", videoId, sourceSets.size());
    }

    /**
     * 검토 요청 (상태에 따라 자동 분기).
     * - SCRIPT_READY → SCRIPT_REVIEW_REQUESTED (1차 검토)
     * - READY → FINAL_REVIEW_REQUESTED (2차 검토)
     */
    @Transactional
    public VideoStatusResponse requestReview(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        String newStatus;
        
        if ("SCRIPT_READY".equals(prevStatus)) {
            // 1차 검토 요청 (스크립트)
            newStatus = "SCRIPT_REVIEW_REQUESTED";
            log.info("1차 검토 요청 (스크립트). videoId={}, {} → {}", videoId, prevStatus, newStatus);
        } else if ("READY".equals(prevStatus)) {
            // 2차 검토 요청 (영상)
            newStatus = "FINAL_REVIEW_REQUESTED";
            log.info("2차 검토 요청 (영상). videoId={}, {} → {}", videoId, prevStatus, newStatus);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "검토 요청은 SCRIPT_READY 또는 READY 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * 검토 승인 (상태에 따라 자동 분기).
     * - SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED (1차 승인 - 영상 생성 가능)
     * - FINAL_REVIEW_REQUESTED → PUBLISHED (2차 승인 = 게시)
     */
    @Transactional
    public VideoStatusResponse approveVideo(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        String newStatus;
        
        if ("SCRIPT_REVIEW_REQUESTED".equals(prevStatus)) {
            // 1차 승인 (스크립트)
            newStatus = "SCRIPT_APPROVED";
            log.info("1차 승인 (스크립트). videoId={}, {} → {}", videoId, prevStatus, newStatus);
        } else if ("FINAL_REVIEW_REQUESTED".equals(prevStatus)) {
            // 2차 승인 (영상) = 게시
            newStatus = "PUBLISHED";
            log.info("2차 승인 (영상) → 게시. videoId={}, {} → {}", videoId, prevStatus, newStatus);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "승인은 SCRIPT_REVIEW_REQUESTED 또는 FINAL_REVIEW_REQUESTED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * 검토 반려 (상태에 따라 자동 분기).
     * - SCRIPT_REVIEW_REQUESTED → SCRIPT_READY (1차 반려 - 스크립트 수정)
     * - FINAL_REVIEW_REQUESTED → READY (2차 반려 - 영상 수정)
     */
    @Transactional
    public VideoStatusResponse rejectVideo(UUID videoId, String reason, UUID reviewerUuid) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        String newStatus;
        
        if ("SCRIPT_REVIEW_REQUESTED".equals(prevStatus)) {
            // 1차 반려 (스크립트)
            newStatus = "SCRIPT_READY";
            log.info("1차 반려 (스크립트). videoId={}, {} → {}, reason={}", videoId, prevStatus, newStatus, reason);
        } else if ("FINAL_REVIEW_REQUESTED".equals(prevStatus)) {
            // 2차 반려 (영상)
            newStatus = "READY";
            log.info("2차 반려 (영상). videoId={}, {} → {}, reason={}", videoId, prevStatus, newStatus, reason);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "반려는 SCRIPT_REVIEW_REQUESTED 또는 FINAL_REVIEW_REQUESTED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        // 반려 사유 저장 (EducationVideoReview 테이블)
        if (reason != null && !reason.isBlank()) {
            RejectionStage rejectionStage = 
                "SCRIPT_REVIEW_REQUESTED".equals(prevStatus) 
                    ? RejectionStage.SCRIPT 
                    : RejectionStage.VIDEO;
            EducationVideoReview review = EducationVideoReview.createRejection(
                videoId,
                reason,
                reviewerUuid,
                rejectionStage
            );
            reviewRepository.save(review);
            log.debug("반려 사유 저장 완료. videoId={}, reason={}, reviewerUuid={}, stage={}", videoId, reason, reviewerUuid, rejectionStage);
        }
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * 스크립트 ID로 스크립트 승인 (1차 승인).
     * scriptId로 연결된 Video를 찾아서 SCRIPT_REVIEW_REQUESTED → SCRIPT_APPROVED로 변경.
     */
    @Transactional
    public VideoStatusResponse approveScript(UUID scriptId) {
        // scriptId로 연결된 Video 찾기
        List<EducationVideo> videos = videoRepository.findByScriptId(scriptId);
        if (videos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "스크립트에 연결된 영상을 찾을 수 없습니다: " + scriptId);
        }
        
        // 첫 번째 Video 사용 (일반적으로 1:1 관계)
        EducationVideo video = videos.get(0);
        String prevStatus = video.getStatus();
        
        if (!"SCRIPT_REVIEW_REQUESTED".equals(prevStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "스크립트 승인은 SCRIPT_REVIEW_REQUESTED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        String newStatus = "SCRIPT_APPROVED";
        log.info("스크립트 승인. scriptId={}, videoId={}, {} → {}", scriptId, video.getId(), prevStatus, newStatus);
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * 스크립트 ID로 스크립트 반려 (1차 반려).
     * scriptId로 연결된 Video를 찾아서 SCRIPT_REVIEW_REQUESTED → SCRIPT_READY로 변경.
     */
    @Transactional
    public VideoStatusResponse rejectScript(UUID scriptId, String reason, UUID reviewerUuid) {
        // scriptId로 연결된 Video 찾기
        List<EducationVideo> videos = videoRepository.findByScriptId(scriptId);
        if (videos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "스크립트에 연결된 영상을 찾을 수 없습니다: " + scriptId);
        }
        
        // 첫 번째 Video 사용 (일반적으로 1:1 관계)
        EducationVideo video = videos.get(0);
        String prevStatus = video.getStatus();
        
        if (!"SCRIPT_REVIEW_REQUESTED".equals(prevStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "스크립트 반려는 SCRIPT_REVIEW_REQUESTED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        String newStatus = "SCRIPT_READY";
        log.info("스크립트 반려. scriptId={}, videoId={}, {} → {}, reason={}", 
            scriptId, video.getId(), prevStatus, newStatus, reason);
        
        // 반려 사유 저장 (EducationVideoReview 테이블)
        if (reason != null && !reason.isBlank()) {
            EducationVideoReview review = EducationVideoReview.createRejection(
                video.getId(),
                reason,
                reviewerUuid,
                RejectionStage.SCRIPT // 스크립트 검토 단계 반려
            );
            reviewRepository.save(review);
            log.debug("스크립트 반려 사유 저장 완료. videoId={}, reason={}, reviewerUuid={}", video.getId(), reason, reviewerUuid);
        }
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * [Deprecated] 게시 - 이제 approve가 2차 승인 시 자동으로 PUBLISHED 처리.
     * 기존 API 호환을 위해 유지하되, SCRIPT_APPROVED 상태에서 호출 시 에러 안내.
     */
    @Transactional
    public VideoStatusResponse publishVideo(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        
        // 이미 PUBLISHED면 그냥 반환
        if ("PUBLISHED".equals(prevStatus)) {
            return new VideoStatusResponse(video.getId(), prevStatus, prevStatus, Instant.now().toString());
        }
        
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
            "게시는 2차 승인(approve) 시 자동으로 처리됩니다. " +
            "현재 상태: " + prevStatus + ". " +
            "READY 상태에서 review-request → approve 순서로 호출하세요.");
    }

    /**
     * 영상 비활성화 (PUBLISHED → DISABLED).
     * 게시된 영상을 비활성화하여 유저에게 노출되지 않도록 합니다.
     */
    @Transactional
    public VideoStatusResponse disableVideo(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        
        if (!"PUBLISHED".equals(prevStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "비활성화는 PUBLISHED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        String newStatus = "DISABLED";
        log.info("영상 비활성화. videoId={}, {} → {}", videoId, prevStatus, newStatus);
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * 영상 활성화 (DISABLED → PUBLISHED).
     * 비활성화된 영상을 다시 활성화하여 유저에게 노출합니다.
     */
    @Transactional
    public VideoStatusResponse enableVideo(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        
        if (!"DISABLED".equals(prevStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "활성화는 DISABLED 상태에서만 가능합니다. 현재 상태: " + prevStatus);
        }
        
        String newStatus = "PUBLISHED";
        log.info("영상 활성화. videoId={}, {} → {}", videoId, prevStatus, newStatus);
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    /**
     * [어드민 테스트용] 상태 강제 변경 (상태 검증 없음).
     */
    @Transactional
    public VideoStatusResponse forceChangeStatus(UUID videoId, String newStatus) {
        EducationVideo video = findVideoOrThrow(videoId);
        String prevStatus = video.getStatus();
        
        video.setStatus(newStatus);
        video = videoRepository.save(video);
        log.warn("[어드민 테스트] 상태 강제 변경. videoId={}, {} → {}", videoId, prevStatus, newStatus);
        
        return new VideoStatusResponse(video.getId(), prevStatus, video.getStatus(), Instant.now().toString());
    }

    // ========================
    // 헬퍼 메서드
    // ========================

    private EducationVideo findVideoOrThrow(UUID videoId) {
        return videoRepository.findById(videoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다: " + videoId));
    }

    private VideoMetaItem toVideoMetaItem(EducationVideo v) {
        VideoStatus status = null;
        if (v.getStatus() != null) {
            try {
                status = VideoStatus.valueOf(v.getStatus());
            } catch (IllegalArgumentException e) {
                log.warn("알 수 없는 영상 상태: {}, videoId={}", v.getStatus(), v.getId());
            }
        }
        // Education에서 departmentScope 가져오기
        List<String> departmentScope = null;
        if (v.getEducationId() != null) {
            Education education = educationRepository.findById(v.getEducationId()).orElse(null);
            if (education != null && education.getDepartmentScope() != null) {
                departmentScope = java.util.Arrays.stream(education.getDepartmentScope())
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());
            }
        }
        return new VideoMetaItem(
            v.getId(),
            v.getEducationId(),
            v.getTitle(),
            v.getGenerationJobId(),
            v.getScriptId(),
            v.getFileUrl(),
            v.getVersion(),
            v.getDuration(),
            status,
            departmentScope,
            v.getOrderIndex(),
            v.getCreatedAt() != null ? v.getCreatedAt().toString() : null
        );
    }

    // ========================
    // 검토(Review) 관련 메서드
    // ========================

    /**
     * 검토 목록 조회 (필터링, 정렬, 페이징).
     * 
     * @param statusFilter "pending" (검토 대기), "approved" (승인됨), "rejected" (반려됨), null (전체)
     * @param reviewStage "first" (1차), "second" (2차), "document" (문서), null (전체)
     * @param sort "latest" (최신순), "oldest" (오래된순), "title" (제목순), null (최신순 기본값)
     */
    public ReviewQueueResponse getReviewQueue(
        int page,
        int size,
        String search,
        Boolean myProcessingOnly,
        String statusFilter,
        String reviewStage,
        String sort,
        UUID reviewerUuid
    ) {
        // 상태별 영상 조회
        List<EducationVideo> allVideos;
        if ("approved".equals(statusFilter)) {
            // 승인됨 (PUBLISHED)
            allVideos = videoRepository.findApprovedVideos();
        } else if ("rejected".equals(statusFilter)) {
            // 반려됨 (EducationVideoReview가 있는 영상)
            allVideos = videoRepository.findRejectedVideos();
        } else {
            // 검토 대기 (기본값, SCRIPT_REVIEW_REQUESTED, FINAL_REVIEW_REQUESTED)
            allVideos = videoRepository.findPendingReviewVideos();
        }

        // 필터링
        final String reviewStageFilter = reviewStage; // 람다 내부에서 사용하기 위해 final 변수로 복사
        List<EducationVideo> filteredVideos = allVideos.stream()
            .filter(v -> {
                // 검토 단계 필터 (1차/2차/문서)
                if (reviewStageFilter != null && !reviewStageFilter.isBlank()) {
                    if ("first".equals(reviewStageFilter)) {
                        // 1차 검토만
                        if (!"SCRIPT_REVIEW_REQUESTED".equals(v.getStatus())) {
                            return false;
                        }
                    } else if ("second".equals(reviewStageFilter)) {
                        // 2차 검토만
                        if (!"FINAL_REVIEW_REQUESTED".equals(v.getStatus())) {
                            return false;
                        }
                    } else if ("document".equals(reviewStageFilter)) {
                        // 문서 타입 (현재는 모든 영상을 문서로 간주, 추후 확장 가능)
                        // TODO: 문서 타입 구분 로직 추가 필요
                    }
                }
                
                // 검색 필터 (제목, 부서, 제작자)
                if (search != null && !search.isBlank()) {
                    String lowerSearch = search.toLowerCase();
                    Education education = educationRepository.findById(v.getEducationId()).orElse(null);
                    boolean matchesTitle = v.getTitle() != null && v.getTitle().toLowerCase().contains(lowerSearch);
                    boolean matchesEducationTitle = education != null && education.getTitle() != null && 
                        education.getTitle().toLowerCase().contains(lowerSearch);
                    
                    // 제작자 정보 검색
                    boolean matchesCreator = false;
                    if (v.getCreatorUuid() != null) {
                        try {
                            String[] userInfo = fetchUserInfoFromInfraService(v.getCreatorUuid());
                            String creatorName = userInfo[1];
                            String creatorDepartment = userInfo[0];
                            if (creatorName != null && creatorName.toLowerCase().contains(lowerSearch)) {
                                matchesCreator = true;
                            } else if (creatorDepartment != null && creatorDepartment.toLowerCase().contains(lowerSearch)) {
                                matchesCreator = true;
                            }
                        } catch (Exception e) {
                            log.debug("제작자 정보 조회 실패 (검색 필터): videoId={}, creatorUuid={}, error={}", 
                                v.getId(), v.getCreatorUuid(), e.getMessage());
                        }
                    }
                    
                    if (!matchesTitle && !matchesEducationTitle && !matchesCreator) {
                        return false;
                    }
                }

                // 내 처리만 필터
                if (Boolean.TRUE.equals(myProcessingOnly) && reviewerUuid != null) {
                    List<EducationVideoReview> reviews = reviewRepository.findByVideoIdOrderByCreatedAtDesc(v.getId());
                    boolean hasMyReview = reviews.stream()
                        .anyMatch(r -> reviewerUuid.equals(r.getReviewerUuid()));
                    if (!hasMyReview) {
                        return false;
                    }
                }

                return true;
            })
            .sorted((v1, v2) -> {
                // 정렬 옵션
                if ("oldest".equals(sort)) {
                    // 오래된순
                    Instant t1 = v1.getCreatedAt() != null ? v1.getCreatedAt() : Instant.EPOCH;
                    Instant t2 = v2.getCreatedAt() != null ? v2.getCreatedAt() : Instant.EPOCH;
                    return t1.compareTo(t2);
                } else if ("title".equals(sort)) {
                    // 제목순
                    String title1 = v1.getTitle() != null ? v1.getTitle() : "";
                    String title2 = v2.getTitle() != null ? v2.getTitle() : "";
                    return title1.compareToIgnoreCase(title2);
                } else {
                    // 최신순 (기본값)
                    Instant t1 = v1.getCreatedAt() != null ? v1.getCreatedAt() : Instant.EPOCH;
                    Instant t2 = v2.getCreatedAt() != null ? v2.getCreatedAt() : Instant.EPOCH;
                    return t2.compareTo(t1);
                }
            })
            .collect(Collectors.toList());

        // 페이징
        int totalCount = filteredVideos.size();
        int totalPages = (int) Math.ceil((double) totalCount / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalCount);
        List<EducationVideo> pagedVideos = fromIndex < totalCount 
            ? filteredVideos.subList(fromIndex, toIndex)
            : Collections.emptyList();

        // Education 정보 조회 (한 번에)
        Map<UUID, Education> educationMap = educationRepository.findAllById(
            pagedVideos.stream().map(EducationVideo::getEducationId).distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Education::getId, e -> e));

        // DTO 변환
        List<ReviewQueueItem> items = pagedVideos.stream()
            .map(v -> {
                Education education = educationMap.get(v.getEducationId());
                VideoStatus status = null;
                try {
                    status = VideoStatus.valueOf(v.getStatus());
                } catch (IllegalArgumentException e) {
                    log.warn("알 수 없는 영상 상태: {}", v.getStatus());
                }

                String reviewStageLabel = null;
                if ("SCRIPT_REVIEW_REQUESTED".equals(v.getStatus())) {
                    reviewStageLabel = "1차";
                } else if ("FINAL_REVIEW_REQUESTED".equals(v.getStatus())) {
                    reviewStageLabel = "2차";
                } else if ("PUBLISHED".equals(v.getStatus())) {
                    reviewStageLabel = "승인됨";
                } else {
                    // 반려됨인 경우 리뷰에서 단계 확인
                    List<EducationVideoReview> reviews = reviewRepository.findByVideoIdOrderByCreatedAtDesc(v.getId());
                    if (!reviews.isEmpty()) {
                        EducationVideoReview latestReview = reviews.get(0);
                        if (latestReview.getRejectionStage() != null) {
                            reviewStageLabel = latestReview.getRejectionStage() == RejectionStage.SCRIPT ? "1차 반려" : "2차 반려";
                        } else {
                            reviewStageLabel = "반려됨";
                        }
                    }
                }
                
                // 제작자 정보 조회 (infra-service)
                UUID creatorUuid = v.getCreatorUuid();
                String creatorDepartment = null;
                String creatorName = null;
                if (creatorUuid != null) {
                    String[] userInfo = fetchUserInfoFromInfraService(creatorUuid);
                    creatorDepartment = userInfo[0];
                    creatorName = userInfo[1];
                }

                return new ReviewQueueItem(
                    v.getId(),
                    v.getEducationId(),
                    education != null ? education.getTitle() : "",
                    v.getTitle(),
                    status,
                    reviewStageLabel,
                    creatorDepartment,
                    creatorName,
                    creatorUuid,
                    v.getCreatedAt() != null ? v.getCreatedAt().toString() : "",
                    education != null && education.getCategory() != null ? education.getCategory().name() : "",
                    education != null && education.getEduType() != null ? education.getEduType().name() : ""
                );
            })
            .collect(Collectors.toList());

        // 통계 계산 (검토 대기 상태일 때만)
        long firstRoundCount = 0;
        long secondRoundCount = 0;
        long documentCount = allVideos.size();
        
        if (statusFilter == null || "pending".equals(statusFilter)) {
            firstRoundCount = allVideos.stream()
                .filter(v -> "SCRIPT_REVIEW_REQUESTED".equals(v.getStatus()))
                .count();
            secondRoundCount = allVideos.stream()
                .filter(v -> "FINAL_REVIEW_REQUESTED".equals(v.getStatus()))
                .count();
        }

        return new ReviewQueueResponse(
            items,
            (long) totalCount,
            page,
            size,
            totalPages,
            firstRoundCount,
            secondRoundCount,
            documentCount
        );
    }

    /**
     * 검토 통계 조회.
     */
    public ReviewStatsResponse getReviewStats(UUID reviewerUuid) {
        // 검토 대기 개수
        long pendingCount = videoRepository.findPendingReviewVideos().size();

        // 승인됨 개수
        Long approvedCount = videoRepository.countApprovedVideos();

        // 반려됨 개수
        Long rejectedCount = videoRepository.countRejectedVideos();

        // 내 활동 개수
        Long myActivityCount = reviewerUuid != null 
            ? videoRepository.countMyActivityVideos(reviewerUuid)
            : 0L;

        return new ReviewStatsResponse(
            pendingCount,
            approvedCount != null ? approvedCount : 0L,
            rejectedCount != null ? rejectedCount : 0L,
            myActivityCount != null ? myActivityCount : 0L
        );
    }

    /**
     * 영상 감사 이력 조회.
     */
    public AuditHistoryResponse getAuditHistory(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        List<EducationVideoReview> reviews = reviewRepository.findByVideoIdOrderByCreatedAtDesc(videoId);

        List<AuditHistoryItem> history = new ArrayList<>();

        // 영상 생성 이벤트
        if (video.getCreatedAt() != null) {
            String creatorName = "SYSTEM";
            UUID creatorUuid = video.getCreatorUuid();
            if (creatorUuid != null) {
                try {
                    String[] userInfo = fetchUserInfoFromInfraService(creatorUuid);
                    String name = userInfo[1];
                    if (name != null && !name.isBlank()) {
                        creatorName = name;
                    }
                } catch (Exception e) {
                    log.debug("제작자 정보 조회 실패 (감사 이력): videoId={}, creatorUuid={}, error={}", 
                        videoId, creatorUuid, e.getMessage());
                }
            }
            history.add(new AuditHistoryItem(
                "CREATED",
                "영상 생성",
                video.getCreatedAt().toString(),
                creatorName,
                creatorUuid,
                null,
                null
            ));
        }

        // 반려 이벤트
        for (EducationVideoReview review : reviews) {
            String reviewerName = "REVIEWER";
            UUID reviewerUuid = review.getReviewerUuid();
            if (reviewerUuid != null) {
                try {
                    String[] userInfo = fetchUserInfoFromInfraService(reviewerUuid);
                    String name = userInfo[1];
                    if (name != null && !name.isBlank()) {
                        reviewerName = name;
                    }
                } catch (Exception e) {
                    log.debug("리뷰어 정보 조회 실패 (감사 이력): videoId={}, reviewerUuid={}, error={}", 
                        videoId, reviewerUuid, e.getMessage());
                }
            }
            history.add(new AuditHistoryItem(
                "REJECTED",
                "검토 반려",
                review.getCreatedAt() != null ? review.getCreatedAt().toString() : "",
                reviewerName,
                reviewerUuid,
                review.getComment(),
                review.getRejectionStage() != null ? review.getRejectionStage().name() : null
            ));
        }

        // 자동 점검 이벤트
        history.add(new AuditHistoryItem(
            "AUTO_CHECKED",
            "PII/금칙어/품질 점검",
            video.getCreatedAt() != null ? video.getCreatedAt().toString() : "",
            "SYSTEM",
            null,
            null,
            null
        ));

        // 시간순 정렬 (최신순)
        history.sort((h1, h2) -> h2.timestamp().compareTo(h1.timestamp()));

        return new AuditHistoryResponse(
            videoId,
            video.getTitle(),
            history
        );
    }

    /**
     * 검토 상세 정보 조회.
     */
    public ReviewDetailResponse getReviewDetail(UUID videoId) {
        EducationVideo video = findVideoOrThrow(videoId);
        Education education = educationRepository.findById(video.getEducationId())
            .orElse(null);

        VideoStatus status = null;
        try {
            status = VideoStatus.valueOf(video.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 영상 상태: {}", video.getStatus());
        }

        String reviewStage = "SCRIPT_REVIEW_REQUESTED".equals(video.getStatus()) ? "1차" : 
            "FINAL_REVIEW_REQUESTED".equals(video.getStatus()) ? "2차" : "";

        // 제작자 정보 조회 (infra-service)
        UUID creatorUuid = video.getCreatorUuid();
        String creatorDepartment = null;
        String creatorName = null;
        if (creatorUuid != null) {
            String[] userInfo = fetchUserInfoFromInfraService(creatorUuid);
            creatorDepartment = userInfo[0];
            creatorName = userInfo[1];
        }

        // 스크립트 버전 조회
        Integer scriptVersion = null;
        if (video.getScriptId() != null) {
            EducationScript script = scriptRepository.findById(video.getScriptId()).orElse(null);
            if (script != null) {
                scriptVersion = script.getVersion();
            }
        }

        return new ReviewDetailResponse(
            videoId,
            video.getEducationId(),
            education != null ? education.getTitle() : "",
            video.getTitle(),
            status,
            reviewStage,
            creatorDepartment,
            creatorName,
            creatorUuid,
            video.getCreatedAt() != null ? video.getCreatedAt().toString() : "",
            video.getUpdatedAt() != null ? video.getUpdatedAt().toString() : "",
            education != null && education.getCategory() != null ? education.getCategory().name() : "",
            education != null && education.getEduType() != null ? education.getEduType().name() : "",
            video.getScriptId(),
            scriptVersion
        );
    }
}
