package com.ctrlf.education.service;

import com.ctrlf.education.dto.EducationRequests.VideoProgressUpdateRequest;
import com.ctrlf.education.dto.EducationResponses;
import com.ctrlf.education.dto.EducationResponses.EducationVideosResponse;
import com.ctrlf.education.dto.EducationResponses.VideoProgressResponse;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationProgress;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.EducationVideoProgress;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import com.ctrlf.education.video.entity.SourceSetDocument;
import com.ctrlf.education.script.client.InfraRagClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 교육 도메인 비즈니스 로직을 담당하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class EducationService {

    private static final Logger log = LoggerFactory.getLogger(EducationService.class);

    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;
    private final EducationVideoProgressRepository educationVideoProgressRepository;
    private final EducationProgressRepository educationProgressRepository;
    private final InfraRagClient infraRagClient;
    private final SourceSetDocumentRepository sourceSetDocumentRepository;

    /**
     * 사용자 기준 교육 및 영상 목록 집계.
     *
     * @param completed 이수 여부 필터(옵션)
     * @param eduType 교육유형(MANDATORY/JOB/ETC, 옵션)
     * @param sort 정렬 기준(UPDATED|TITLE, 기본 UPDATED)
     * @param userUuid 로그인 사용자 UUID(옵션)
     * @param userDepartments 사용자 부서 목록(옵션)
     * @return 교육 목록(영상 목록/진행 포함)
     */
    public List<EducationResponses.EducationListItem> getEducationsMe(
        Boolean completed,
        String eduType,
        String sort,
        Optional<UUID> userUuid,
        List<String> userDepartments
    ) {
        // 1. 정렬 설정
        String sortKey = (!StringUtils.hasText(sort)) ? "UPDATED" : sort.trim().toUpperCase();
        Sort sortOrder = "TITLE".equals(sortKey)
            ? Sort.by("title").ascending()
            : Sort.by("updatedAt").descending();
        
        Pageable pageable = PageRequest.of(0, 1000, sortOrder);

        // 2. 교육 유형 필터 변환
        EducationCategory eduTypeFilter = null;
        if (StringUtils.hasText(eduType)) {
            try {
                eduTypeFilter = EducationCategory.valueOf(eduType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid eduType: {}", eduType);
            }
        }

        // 3. 교육 목록 조회
        List<Education> educations = educationRepository.findEducations(eduTypeFilter, pageable);

        // 4. 사용자 완료/진행 정보를 한 번에 조회
        Set<UUID> completedEducationIds = new HashSet<>();
        Set<UUID> inProgressEducationIds = new HashSet<>();
        if (userUuid.isPresent()) {
            completedEducationIds.addAll(educationRepository.findCompletedEducationIdsByUser(userUuid.get()));
            inProgressEducationIds.addAll(educationRepository.findInProgressEducationIdsByUser(userUuid.get()));
        }

        // 5. 모든 교육의 PUBLISHED 영상을 한 번에 조회
        Set<UUID> educationIds = educations.stream().map(Education::getId).collect(Collectors.toSet());
        List<EducationVideo> allVideos = educationIds.isEmpty() 
            ? Collections.emptyList() 
            : educationVideoRepository.findByEducationIdInAndStatus(educationIds, VideoStatus.PUBLISHED);
        
        // 교육별 영상 맵 구성
        Map<UUID, List<EducationVideo>> videosByEducationId = allVideos.stream()
            .collect(Collectors.groupingBy(EducationVideo::getEducationId));

        // 6. 사용자의 모든 영상 진행 정보를 한 번에 조회
        Map<UUID, EducationVideoProgress> progressByVideoId = new HashMap<>();
        if (userUuid.isPresent() && !educationIds.isEmpty()) {
            List<EducationVideoProgress> allProgress = educationVideoProgressRepository
                .findByUserUuidAndEducationIdIn(userUuid.get(), educationIds);
            for (EducationVideoProgress p : allProgress) {
                progressByVideoId.put(p.getVideoId(), p);
            }
        }

        // 7. 모든 SourceSetDocument를 한 번에 조회
        Set<UUID> sourceSetIds = allVideos.stream()
            .map(EducationVideo::getSourceSetId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<UUID, List<SourceSetDocument>> docsBySourceSetId = new HashMap<>();
        if (!sourceSetIds.isEmpty()) {
            List<SourceSetDocument> allDocs = sourceSetDocumentRepository.findBySourceSetIdIn(sourceSetIds);
            docsBySourceSetId = allDocs.stream()
                .collect(Collectors.groupingBy(doc -> doc.getSourceSet().getId()));
        }

        // 8. 모든 문서 정보를 한 번에 조회
        Set<UUID> documentIds = new HashSet<>();
        for (List<SourceSetDocument> docs : docsBySourceSetId.values()) {
            if (!docs.isEmpty()) {
                documentIds.add(docs.get(0).getDocumentId());
            }
        }
        Map<UUID, InfraRagClient.DocumentInfoResponse> docInfoCache = new HashMap<>();
        for (UUID docId : documentIds) {
            try {
                InfraRagClient.DocumentInfoResponse docInfo = infraRagClient.getDocument(docId.toString());
                if (docInfo != null) {
                    docInfoCache.put(docId, docInfo);
                }
            } catch (Exception ex) {
                log.debug("문서 정보 조회 실패: documentId={}, error={}", docId, ex.getMessage());
            }
        }

        // 9. 교육 목록 결과 생성
        List<EducationResponses.EducationListItem> result = new ArrayList<>();
        for (Education edu : educations) {
            // 부서 필터링
            if (!isEducationAccessibleByDepartment(edu, userDepartments)) {
                continue;
            }

            // completed 필터 적용
            boolean isCompleted = completedEducationIds.contains(edu.getId());
            if (completed != null && completed != isCompleted) {
                continue;
            }

            boolean hasProgress = inProgressEducationIds.contains(edu.getId());
            
            Integer passRatio = edu.getPassRatio() != null ? edu.getPassRatio() : 100;
            EducationCategory eduTypeCategory = edu.getEduType();
            Integer version = edu.getVersion();
            Instant startAt = edu.getStartAt();
            Instant endAt = edu.getEndAt();

            // 10. 교육별 영상/진행 정보 결합 (캐시된 데이터 사용)
            UUID eduId = edu.getId();
            List<EducationVideo> videos = videosByEducationId.getOrDefault(eduId, Collections.emptyList());
            List<EducationResponses.EducationVideosResponse.VideoItem> videoItems = new ArrayList<>();
            int sumPct = 0;
            int validVideoCount = 0;
            
            for (EducationVideo video : videos) {
                // 영상도 부서 필터링 적용 (Education 객체 직접 전달)
                if (!isEducationAccessibleByDepartment(edu, userDepartments)) {
                    continue; // 부서 권한이 없으면 스킵
                }
                Integer resume = 0;
                Integer total = 0;
                Boolean completedV = false;
                Integer pctV = 0;

                // 캐시된 진행 정보 사용
                EducationVideoProgress p = progressByVideoId.get(video.getId());
                if (p != null) {
                    resume = p.getLastPositionSeconds() != null ? p.getLastPositionSeconds() : 0;
                    total = p.getTotalWatchSeconds() != null ? p.getTotalWatchSeconds() : 0;
                    completedV = p.getIsCompleted() != null && p.getIsCompleted();
                    pctV = p.getProgress() != null ? p.getProgress() : 0;
                }
                
                int durationSec = video.getDuration() != null ? video.getDuration() : 0;
                // 진행률 값이 없으면 position/duration으로 보정 계산
                if ((pctV == null || pctV == 0) && durationSec > 0 && resume != null && resume > 0) {
                    pctV = Math.min(100, Math.max(0, (int) Math.round((resume * 100.0) / durationSec)));
                }
                String vStatus;
                // 영상 시청완료: 완료 플래그이거나 pass_ratio 이상 시청
                if (Boolean.TRUE.equals(completedV) || (pctV != null && pctV >= (passRatio != null ? passRatio : 100))) {
                    vStatus = "시청완료";
                    pctV = 100;
                } else if ((pctV != null && pctV > 0) || (resume != null && resume > 0)) {
                    vStatus = "시청중";
                } else {
                    vStatus = "시청전";
                }
                
                sumPct += pctV != null ? pctV : 0;
                validVideoCount++;
                
                // S3 URL을 presigned URL로 변환
                String fileUrl = video.getFileUrl();
                String presignedUrl = infraRagClient.getPresignedDownloadUrl(fileUrl);
                
                // 캐시된 SourceSetDocument 및 문서 정보 사용
                String sourceFileName = null;
                String sourceFileUrl = null;
                if (video.getSourceSetId() != null) {
                    List<SourceSetDocument> documents = docsBySourceSetId.getOrDefault(video.getSourceSetId(), Collections.emptyList());
                    if (!documents.isEmpty()) {
                        UUID documentId = documents.get(0).getDocumentId();
                        InfraRagClient.DocumentInfoResponse docInfo = docInfoCache.get(documentId);
                        if (docInfo != null) {
                            if (docInfo.getSourceUrl() != null) {
                                sourceFileUrl = docInfo.getSourceUrl();
                            }
                            if (docInfo.getTitle() != null && !docInfo.getTitle().isBlank()) {
                                sourceFileName = docInfo.getTitle();
                            }
                        }
                    }
                }
                
                videoItems.add(new EducationResponses.EducationVideosResponse.VideoItem(
                    video.getId(),
                    video.getTitle(),
                    presignedUrl != null ? presignedUrl : fileUrl,
                    durationSec,
                    video.getVersion() != null ? video.getVersion() : 1,
                    video.getStatus() != null ? video.getStatus().name() : null,
                    edu.getDepartmentScope(),
                    resume,
                    completedV,
                    total,
                    pctV,
                    vStatus,
                    sourceFileName,
                    sourceFileUrl
                ));
            }
            // 11. 교육 진행률: 포함된 영상 진행률 평균
            int eduProgress = validVideoCount == 0 ? 0 : (sumPct / validVideoCount);
            String watchStatus;
            
            // 12. 교육 시청 상태: pass_ratio 기준 또는 진행 여부로 라벨링
            if (eduProgress >= (passRatio != null ? passRatio : 100)) {
                watchStatus = "시청완료";
            } else if (Boolean.TRUE.equals(hasProgress) || eduProgress > 0) {
                watchStatus = "시청중";
            } else {
                watchStatus = "시청전";
            }

            // 13. 교육 목록 결과 추가
            result.add(new EducationResponses.EducationListItem(
                eduId,
                edu.getTitle(),
                edu.getDescription(),
                edu.getCategory(),
                eduTypeCategory,
                edu.getRequire() != null && edu.getRequire(),
                version,
                startAt,
                endAt,
                eduProgress,
                watchStatus,
                videoItems
            ));
        }
        return result;
    }
    
    /**
     * 교육 영상 목록 조회. 사용자 UUID가 있으면 각 영상의 진행률 정보를 포함합니다.
     * 사용자 부서 목록이 있으면 해당 부서에 속하는 영상만 필터링합니다.
     *
     * @param id 교육 ID
     * @param userUuid 사용자 UUID(옵션)
     * @param userDepartments 사용자 부서 목록(옵션)
     * @return 영상 목록 응답
     */
    public EducationVideosResponse getEducationVideos(UUID id, Optional<UUID> userUuid, List<String> userDepartments) {
        Education e = educationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found"));
        // 해당 교육에 속한 영상 목록 조회 (PUBLISHED 상태만)
        List<EducationVideo> videos = educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
            id, VideoStatus.PUBLISHED);
        
        // 1. 모든 영상의 진행 정보를 한 번에 조회
        Set<UUID> videoIds = videos.stream().map(EducationVideo::getId).collect(Collectors.toSet());
        Map<UUID, EducationVideoProgress> progressByVideoId = new HashMap<>();
        if (userUuid.isPresent() && !videoIds.isEmpty()) {
            List<EducationVideoProgress> allProgress = educationVideoProgressRepository
                .findByUserUuidAndEducationIdAndVideoIdIn(userUuid.get(), id, videoIds);
            for (EducationVideoProgress p : allProgress) {
                progressByVideoId.put(p.getVideoId(), p);
            }
        }

        // 2. 모든 SourceSetDocument를 한 번에 조회
        Set<UUID> sourceSetIds = videos.stream()
            .map(EducationVideo::getSourceSetId)
            .filter(ssId -> ssId != null)
            .collect(Collectors.toSet());
        Map<UUID, List<SourceSetDocument>> docsBySourceSetId = new HashMap<>();
        if (!sourceSetIds.isEmpty()) {
            List<SourceSetDocument> allDocs = sourceSetDocumentRepository.findBySourceSetIdIn(sourceSetIds);
            docsBySourceSetId = allDocs.stream()
                .collect(Collectors.groupingBy(doc -> doc.getSourceSet().getId()));
        }

        // 3. 모든 문서 정보를 한 번에 조회
        Set<UUID> documentIds = new HashSet<>();
        for (List<SourceSetDocument> docs : docsBySourceSetId.values()) {
            if (!docs.isEmpty()) {
                documentIds.add(docs.get(0).getDocumentId());
            }
        }
        Map<UUID, InfraRagClient.DocumentInfoResponse> docInfoCache = new HashMap<>();
        for (UUID docId : documentIds) {
            try {
                InfraRagClient.DocumentInfoResponse docInfo = infraRagClient.getDocument(docId.toString());
                if (docInfo != null) {
                    docInfoCache.put(docId, docInfo);
                }
            } catch (Exception ex) {
                log.debug("문서 정보 조회 실패: documentId={}, error={}", docId, ex.getMessage());
            }
        }
        
        // 응답으로 내려줄 영상 항목 DTO 리스트
        List<EducationVideosResponse.VideoItem> items = new ArrayList<>();
        // 영상 시청 완료 기준 비율(education.pass_ratio, 기본 100)
        Integer passRatio = e.getPassRatio() != null ? e.getPassRatio() : 100;
        
        for (EducationVideo v : videos) {
            // 사용자 부서 필터링: Education 객체 직접 사용
            if (!isEducationAccessibleByDepartment(e, userDepartments)) {
                continue; // 부서 권한이 없으면 스킵
            }
            // 사용자별 이어보기 위치/누적 시청시간/완료 여부 기본값
            Integer resume = 0;
            Integer total = 0;
            Boolean completed = false;
            Integer pct = 0;
            
            // 캐시된 진행 정보 사용
            EducationVideoProgress p = progressByVideoId.get(v.getId());
            if (p != null) {
                resume = p.getLastPositionSeconds() != null ? p.getLastPositionSeconds() : 0;
                total = p.getTotalWatchSeconds() != null ? p.getTotalWatchSeconds() : 0;
                completed = p.getIsCompleted() != null && p.getIsCompleted();
                pct = p.getProgress() != null ? p.getProgress() : 0;
            }
            
            // 진행률 계산 보정: progress 값이 없으면 duration/position으로 계산
            int durationSec = v.getDuration() != null ? v.getDuration() : 0;
            if ((pct == null || pct == 0) && durationSec > 0 && resume != null && resume > 0) {
                pct = Math.min(100, Math.max(0, (int) Math.round((resume * 100.0) / durationSec)));
            }
            // 시청 상태 라벨
            String watchStatus;
            if (Boolean.TRUE.equals(completed) || (pct != null && pct >= (passRatio != null ? passRatio : 100))) {
                watchStatus = "시청완료";
                pct = 100;
            } else if ((pct != null && pct > 0) || (resume != null && resume > 0)) {
                watchStatus = "시청중";
            } else {
                watchStatus = "시청전";
            }
            // S3 URL을 presigned URL로 변환
            String fileUrl = v.getFileUrl();
            String presignedUrl = infraRagClient.getPresignedDownloadUrl(fileUrl);
            
            // 캐시된 SourceSetDocument 및 문서 정보 사용
            String sourceFileName = null;
            String sourceFileUrl = null;
            if (v.getSourceSetId() != null) {
                List<SourceSetDocument> documents = docsBySourceSetId.getOrDefault(v.getSourceSetId(), Collections.emptyList());
                if (!documents.isEmpty()) {
                    UUID documentId = documents.get(0).getDocumentId();
                    InfraRagClient.DocumentInfoResponse docInfo = docInfoCache.get(documentId);
                    if (docInfo != null) {
                        if (docInfo.getSourceUrl() != null) {
                            sourceFileUrl = docInfo.getSourceUrl();
                        }
                        if (docInfo.getTitle() != null && !docInfo.getTitle().isBlank()) {
                            sourceFileName = docInfo.getTitle();
                        }
                    }
                }
            }
            
            // 단일 영상 항목 구성
            items.add(new EducationVideosResponse.VideoItem(
                v.getId(),
                v.getTitle(),
                presignedUrl != null ? presignedUrl : fileUrl,
                durationSec,
                v.getVersion() != null ? v.getVersion() : 1,
                v.getStatus() != null ? v.getStatus().name() : null,
                e.getDepartmentScope(),
                resume,
                completed,
                total,
                pct,
                watchStatus,
                sourceFileName,
                sourceFileUrl
            ));
        }
        // 목록 응답 생성
        return EducationVideosResponse.builder()
            .id(e.getId())
            .title(e.getTitle())
            .startAt(e.getStartAt())
            .endAt(e.getEndAt())
            .departmentScope(e.getDepartmentScope())
            .videos(items)
            .build();
    }

    /**
     * 교육이 사용자 부서에서 접근 가능한지 확인합니다.
     * - departmentScope가 null이거나 비어있으면 모든 부서에서 접근 가능
     * - departmentScope에 '전체 부서'가 포함되어 있으면 모든 사용자 접근 가능
     * - departmentScope에 사용자 부서 중 하나라도 포함되면 접근 가능
     */
    private boolean isEducationAccessibleByDepartment(Education education, List<String> userDepartments) {
        if (education == null) {
            return true; // Education을 찾을 수 없으면 접근 허용
        }
        String[] deptScope = education.getDepartmentScope();
        // departmentScope가 없으면 모든 부서 접근 가능
        if (deptScope == null || deptScope.length == 0) {
            return true;
        }
        
        // departmentScope에 '전체 부서'가 포함되어 있으면 모든 사용자 접근 가능
        for (String allowedDept : deptScope) {
            if ("전체 부서".equals(allowedDept)) {
                return true;
            }
        }
        
        // 사용자 부서가 없으면 접근 불가
        if (userDepartments == null || userDepartments.isEmpty()) {
            return false;
        }
        
        // 사용자 부서 중 하나라도 허용 목록에 있으면 접근 가능
        for (String userDept : userDepartments) {
            for (String allowedDept : deptScope) {
                if (allowedDept != null && allowedDept.equals(userDept)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 영상 시청 진행률 업데이트.
     *
     * @param educationId 교육 ID
     * @param videoId 영상 ID
     * @param userUuid 사용자 UUID(필수)
     * @param req 진행률 요청
     * @return 결과 요약 응답
     * @throws IllegalArgumentException 사용자 없음 등 잘못된 요청
     */
    @Transactional
    public VideoProgressResponse updateVideoProgress(UUID educationId, UUID videoId, UUID userUuid, VideoProgressUpdateRequest req) {
        // 1. 사용자 UUID 필수 검증
        if (userUuid == null) {
            throw new IllegalArgumentException("user required");
        }
        
        // 2. 영상 엔티티 조회 및 검증
        EducationVideo video = educationVideoRepository.findById(videoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "video not found"));
        
        // 3. 해당 교육에 속한 영상인지 검증
        if (!video.getEducationId().equals(educationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video does not belong to this education");
        }
        
        // 4. Education 엔티티 조회 (passRatio 가져오기)
        Education education = educationRepository.findById(educationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found"));
        Integer passRatio = education.getPassRatio() != null ? education.getPassRatio() : 100;
        
        // 5. 요청 값 파싱 및 기본값 설정
        int position = req.getPosition() != null ? req.getPosition() : 0;
        int duration = video.getDuration() != null && video.getDuration() > 0 ? video.getDuration() : 1;
        int watch = req.getWatchTime() != null ? req.getWatchTime() : 0;

        // 6. 진행 엔티티 조회(없으면 새로 생성)
        EducationVideoProgress progress = educationVideoProgressRepository
            .findByUserUuidAndEducationIdAndVideoId(userUuid, educationId, videoId)
            .orElseGet(() -> {
                return EducationVideoProgress.create(userUuid, educationId, videoId);
            });

        // 7. 마지막 시청 위치 및 누적 시청 시간 갱신
        progress.setLastPositionSeconds(position);
        int currentTotal = progress.getTotalWatchSeconds() != null ? progress.getTotalWatchSeconds() : 0;
        progress.setTotalWatchSeconds(currentTotal + Math.max(0, watch));

        // 8. 진행률(%) 계산 및 완료 여부 반영 (passRatio 기준)
        int pct = Math.min(100, Math.max(0, (int) Math.round((position * 100.0) / duration)));
        progress.setProgress(pct);
        progress.setIsCompleted(pct >= passRatio);

        // 9. 변경 사항 저장
        educationVideoProgressRepository.save(progress);

        // 10. 교육 전체 진행률(간단히 평균) - PUBLISHED 영상만 기준
        List<EducationVideo> publishedVideos = educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
            educationId, VideoStatus.PUBLISHED);
        int avg = 0;
        boolean allCompleted = false;
        if (!publishedVideos.isEmpty()) {
            // 모든 PUBLISHED 영상의 진행 정보를 한 번에 조회
            Set<UUID> publishedVideoIds = publishedVideos.stream()
                .map(EducationVideo::getId)
                .collect(Collectors.toSet());
            List<EducationVideoProgress> allProgress = educationVideoProgressRepository
                .findByUserUuidAndEducationIdAndVideoIdIn(userUuid, educationId, publishedVideoIds);
            Map<UUID, EducationVideoProgress> progressMap = allProgress.stream()
                .collect(Collectors.toMap(EducationVideoProgress::getVideoId, p -> p));
            
            // PUBLISHED 영상들의 진행률 합산 및 완료 개수 집계
            int sum = 0;
            int completedCount = 0;
            for (EducationVideo v : publishedVideos) {
                EducationVideoProgress p = progressMap.get(v.getId());
                if (p != null) {
                    sum += p.getProgress() != null ? p.getProgress() : 0;
                    if (p.getIsCompleted() != null && p.getIsCompleted()) {
                        completedCount++;
                    }
                }
            }
            // 평균 진행률 및 전체 완료 여부 계산
            avg = sum / publishedVideos.size();
            allCompleted = completedCount == publishedVideos.size();
        }
        
        // 11. 모든 PUBLISHED 영상이 완료되면 EducationProgress 자동 완료 처리
        if (allCompleted && !publishedVideos.isEmpty()) {
            EducationProgress eduProgress = educationProgressRepository
                .findByUserUuidAndEducationId(userUuid, educationId)
                .orElseGet(() -> {
                    EducationProgress newProgress = new EducationProgress();
                    newProgress.setUserUuid(userUuid);
                    newProgress.setEducationId(educationId);
                    return newProgress;
                });
            
            // 아직 완료 처리되지 않은 경우에만 업데이트
            if (eduProgress.getIsCompleted() == null || !eduProgress.getIsCompleted()) {
                eduProgress.setIsCompleted(true);
                eduProgress.setCompletedAt(Instant.now());
                eduProgress.setProgress(100);
                educationProgressRepository.save(eduProgress);
            }
        }
        
        // 응답 DTO 구성
        return VideoProgressResponse.builder()
            .updated(true)
            .progress(pct)
            .isCompleted(progress.getIsCompleted() != null && progress.getIsCompleted())
            .totalWatchSeconds(progress.getTotalWatchSeconds() != null ? progress.getTotalWatchSeconds() : 0)
            .eduProgress(avg)
            .eduCompleted(allCompleted)
            .build();
    }

    /**
     * 교육 시청 완료 처리.
     * 모든 영상이 완료 상태인지 검증하여 완료/실패를 반환합니다.
     *
     * @param educationId 교육 ID
     * @param userUuid 사용자 UUID
     * @return 처리 결과 맵
     */
    @Transactional
    public Map<String, Object> completeEducation(UUID educationId, UUID userUuid) {
        // 1. 사용자 UUID 필수 검증
        Map<String, Object> result = new HashMap<>();
        if (userUuid == null) {
            result.put("status", "FAILED");
            result.put("message", "user required");
            return result;
        }
        // 2. PUBLISHED 상태의 영상 목록 조회
        List<EducationVideo> publishedVideos = educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
            educationId, VideoStatus.PUBLISHED);
        
        if (publishedVideos.isEmpty()) {
            result.put("status", "FAILED");
            result.put("message", "해당 교육에 노출 가능한 영상이 없습니다.");
            return result;
        }
        
        // 3. 모든 PUBLISHED 영상의 진행 정보를 한 번에 조회
        Set<UUID> publishedVideoIds = publishedVideos.stream()
            .map(EducationVideo::getId)
            .collect(Collectors.toSet());
        List<EducationVideoProgress> allProgress = educationVideoProgressRepository
            .findByUserUuidAndEducationIdAndVideoIdIn(userUuid, educationId, publishedVideoIds);
        Map<UUID, EducationVideoProgress> progressMap = allProgress.stream()
            .collect(Collectors.toMap(EducationVideoProgress::getVideoId, p -> p));
        
        // 4. PUBLISHED 영상들의 완료 여부 확인
        boolean ok = true;
        for (EducationVideo video : publishedVideos) {
            EducationVideoProgress progress = progressMap.get(video.getId());
            
            if (progress == null || 
                progress.getIsCompleted() == null || 
                !progress.getIsCompleted()) {
                ok = false;
                break;
            }
        }
        if (ok) {
            // EducationProgress 조회 또는 생성
            EducationProgress eduProgress = educationProgressRepository
                .findByUserUuidAndEducationId(userUuid, educationId)
                .orElseGet(() -> {
                    EducationProgress newProgress = new EducationProgress();
                    newProgress.setUserUuid(userUuid);
                    newProgress.setEducationId(educationId);
                    newProgress.setProgress(100);
                    return newProgress;
                });
                
            // 5. 이수 완료 처리
            Instant completedAt = Instant.now();
            eduProgress.setIsCompleted(true);
            eduProgress.setCompletedAt(completedAt);
            eduProgress.setProgress(100);
            educationProgressRepository.save(eduProgress);

            // 5. 결과 반환
            result.put("status", "COMPLETED");
            result.put("completedAt", completedAt.toString());
        } else {
            result.put("status", "FAILED");
            result.put("message", "영상 이수 조건 미충족");
        }
        return result;
    }
}
