package com.ctrlf.education.service;

import com.ctrlf.common.dto.MutationResponse;
import com.ctrlf.education.dto.EducationRequests.CreateEducationRequest;
import com.ctrlf.education.dto.EducationRequests.UpdateEducationRequest;
import com.ctrlf.education.dto.EducationRequests.VideoProgressUpdateRequest;
import com.ctrlf.education.dto.EducationResponses;
import com.ctrlf.education.dto.EducationResponses.EducationDetailResponse;
import com.ctrlf.education.dto.EducationResponses.EducationVideosResponse;
import com.ctrlf.education.dto.EducationResponses.VideoProgressResponse;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationProgress;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.EducationVideoProgress;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 교육 도메인 비즈니스 로직을 담당하는 서비스.
 * <p>
 * - 교육 생성/조회/수정/삭제<br>
 * - 교육 영상 목록 및 사용자 진행률 조회<br>
 * - 영상 시청 진행률 업데이트 및 교육 이수 처리<br>
 * 를 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class EducationService {

    private static final Logger log = LoggerFactory.getLogger(EducationService.class);

    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;
    private final EducationVideoProgressRepository educationVideoProgressRepository;
    private final EducationProgressRepository educationProgressRepository;
    private final ObjectMapper objectMapper;

    @Value("${ctrlf.infra.base-url:http://localhost:9003}")
    private String infraBaseUrl;

    /**
     * 교육 생성.
     *
     * @param req 생성 요청
     * @return 생성된 교육 ID
     */
    @Transactional
    public MutationResponse<UUID> createEducation(CreateEducationRequest req) {
        validateCreate(req);
        Education e = new Education();
        e.setTitle(req.getTitle());
        e.setCategory(req.getCategory());
        e.setEduType(req.getEduType());
        e.setDescription(req.getDescription());
        e.setPassScore(req.getPassScore());
        e.setPassRatio(req.getPassRatio());
        e.setRequire(req.getRequire());
        UUID id = educationRepository.save(e).getId();
        return new MutationResponse<>(id);
    }

    /**
     * 교육 및 영상 목록(내 목록).
     * - completed/category/sort 파라미터를 받아 사용자 기준으로 영상 목록과 진행 정보를 포함해 반환
     * - 페이지네이션 없음(상한 1000)
     */
    /**
     * 사용자 기준 교육 및 영상 목록 집계.
     *
     * - 정렬/카테고리/이수여부 필터를 적용해 교육 목록을 조회한 뒤,
     *   각 교육에 포함된 영상 목록과 사용자 진행 정보를 합성한다.
     * - 영상 시청완료 판단은 education.pass_ratio(%) 이상 시청 또는 진행 엔티티의 완료 플래그로 결정한다.
     * - 교육 진행률은 포함된 영상 진행률의 평균으로 계산한다.
     * - 페이지네이션은 적용하지 않고 최대 1000건까지 반환한다.
     *
     * @param completed 이수 여부 필터(옵션)
     * @param category 카테고리(MANDATORY/JOB/ETC, 옵션)
     * @param sort 정렬 기준(UPDATED|TITLE, 기본 UPDATED)
     * @param userUuid 로그인 사용자 UUID(옵션)
     * @return 교육 목록(영상 목록/진행 포함)
     */
    public List<EducationResponses.EducationListItem> getEducationsMe(
        Boolean completed,
        String eduType,
        String sort,
        Optional<UUID> userUuid
    ) {
        // 1. 정렬 키 정규화
        String sortKey = (!StringUtils.hasText(sort)) ? "UPDATED" : sort.trim().toUpperCase();
        if (!"UPDATED".equals(sortKey) && !"TITLE".equals(sortKey)) {
            sortKey = "UPDATED";
        }

        // 2. 교육 유형 필터 정규화(미지정이면 null)
        String eduTypeFilter = !StringUtils.hasText(eduType) ? null : eduType.trim().toUpperCase();
        int offset = 0;
        int size = 1000;

        // 3. 교육 기본 목록 조회(사용자 기준 일부 플래그 포함)
        List<Object[]> rows = educationRepository.findEducationsNative(
            offset, size, completed, null, eduTypeFilter, userUuid.orElse(null), sortKey
        );

        // 4. 교육 목록 결과 생성
        List<EducationResponses.EducationListItem> result = new ArrayList<>();
        for (Object[] r : rows) {
            UUID eduId = (UUID) r[0];
            String title = (String) r[1];
            String description = (String) r[2];
            String catStr = (String) r[3];
            EducationTopic cat = catStr != null ? EducationTopic.valueOf(catStr) : null;
            Boolean required = (Boolean) r[4];
            Boolean hasProgress = (Boolean) r[6];

            // 5. 교육별 영상/진행 정보 결합 (PUBLISHED 상태만)
            List<EducationVideo> vids = educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
                eduId, "PUBLISHED");
            List<EducationResponses.EducationVideosResponse.VideoItem> videoItems = new ArrayList<>();
            int sumPct = 0;

            // 6. 교육 정보 조회 (passRatio, eduTypeCategory 등)
            Education edu = educationRepository.findById(eduId).orElse(null);
            Integer passRatio = edu != null && edu.getPassRatio() != null ? edu.getPassRatio() : 100;
            EducationCategory eduTypeCategory = edu != null ? edu.getEduType() : null;
            for (EducationVideo v : vids) {
                Integer resume = 0;
                Integer total = 0;
                Boolean completedV = false;
                Integer pctV = 0;

                if (userUuid.isPresent()) {
                    var pv = educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoId(userUuid.get(), eduId, v.getId());
                    if (pv.isPresent()) {
                        var p = pv.get();
                        resume = p.getLastPositionSeconds() != null ? p.getLastPositionSeconds() : 0;
                        total = p.getTotalWatchSeconds() != null ? p.getTotalWatchSeconds() : 0;
                        completedV = p.getIsCompleted() != null && p.getIsCompleted();
                        pctV = p.getProgress() != null ? p.getProgress() : 0;
                    }
                }
                int durationSec = v.getDuration() != null ? v.getDuration() : 0;
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
                videoItems.add(new EducationResponses.EducationVideosResponse.VideoItem(
                    v.getId(),
                    v.getTitle(),
                    v.getFileUrl(),
                    durationSec,
                    v.getVersion() != null ? v.getVersion() : 1,
                    v.getDepartmentScope(),
                    resume,
                    completedV,
                    total,
                    pctV,
                    vStatus
                ));
            }
            // 7. 교육 진행률: 포함된 영상 진행률 평균
            int eduProgress = vids.isEmpty() ? 0 : (sumPct / Math.max(vids.size(), 1));
            String watchStatus;
            
            // 8. 교육 시청 상태: pass_ratio 기준 또는 진행 여부로 라벨링
            if (eduProgress >= (passRatio != null ? passRatio : 100)) {
                watchStatus = "시청완료";
            } else if (Boolean.TRUE.equals(hasProgress) || eduProgress > 0) {
                watchStatus = "시청중";
            } else {
                watchStatus = "시청전";
            }

            // 9. 교육 목록 결과 추가
            result.add(new EducationResponses.EducationListItem(
                eduId,
                title,
                description,
                cat,
                eduTypeCategory,
                required != null && required,
                eduProgress,
                watchStatus,
                videoItems
            ));
        }
        return result;
    }


    /**
     * 교육 상세 조회.
     *
     * @param id 교육 ID
     * @return 상세 응답
     * @throws IllegalArgumentException 존재하지 않으면
     */
    public EducationResponses.EducationDetailResponse getEducationDetail(UUID id) {
        Education e = educationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found"));
        // 총 duration은 교육에 속한 영상들의 duration 합산
        List<EducationVideo> videos = educationVideoRepository.findByEducationId(id);
        int totalDuration = 0;
        for (EducationVideo v : videos) {
            totalDuration += v.getDuration() != null ? v.getDuration() : 0;
        }
        return EducationDetailResponse.builder()
            .id(e.getId())
            .title(e.getTitle())
            .description(e.getDescription())
            .category(e.getCategory())
            .eduType(e.getEduType())
            .require(e.getRequire())
            .passScore(e.getPassScore())
            .passRatio(e.getPassRatio())
            .duration(totalDuration)
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .sections(Collections.emptyList())
            .build();
    }

    /**
     * 교육 수정(부분 수정 허용).
     *
     * @param id 교육 ID
     * @param req 수정 요청
     * @return 갱신된 updatedAt
     * @throws IllegalArgumentException 존재하지 않으면
     */
    @Transactional
    public Instant updateEducation(UUID id, UpdateEducationRequest req) {
        Education e = educationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found"));
        if (StringUtils.hasText(req.getTitle())) e.setTitle(req.getTitle());
        if (req.getDescription() != null) e.setDescription(req.getDescription());
        if (req.getCategory() != null) e.setCategory(req.getCategory());
        if (req.getEduType() != null) e.setEduType(req.getEduType());
        if (req.getRequire() != null) e.setRequire(req.getRequire());
        if (req.getPassScore() != null) e.setPassScore(req.getPassScore());
        if (req.getPassRatio() != null) e.setPassRatio(req.getPassRatio());
        educationRepository.saveAndFlush(e);
        return e.getUpdatedAt();
    }

    /**
     * 교육 삭제.
     * 연관된 영상/진행 정보는 단순 삭제 처리합니다.
     *
     * @param id 교육 ID
     */
    @Transactional
    public void deleteEducation(UUID id) {
        educationVideoProgressRepository.softDeleteByEducationId(id);
        educationVideoRepository.softDeleteByEducationId(id);
        int affected = educationRepository.softDeleteById(id);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found");
        }
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
            id, "PUBLISHED");
        // 응답으로 내려줄 영상 항목 DTO 리스트
        List<EducationVideosResponse.VideoItem> items = new ArrayList<>();
        // 영상 시청 완료 기준 비율(education.pass_ratio, 기본 100)
        Integer passRatio = e.getPassRatio() != null ? e.getPassRatio() : 100;
        for (EducationVideo v : videos) {
            // 사용자 부서 필터링: departmentScope가 설정된 경우 사용자 부서와 매칭 확인
            if (!isVideoAccessibleByDepartment(v, userDepartments)) {
                continue; // 부서 권한이 없으면 스킵
            }
            // 사용자별 이어보기 위치/누적 시청시간/완료 여부 기본값
            Integer resume = 0;
            Integer total = 0;
            Boolean completed = false;
            Integer pct = 0;
            // 사용자 UUID가 있으면 진행 정보 조회
            if (userUuid.isPresent()) {
                var pv = educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoId(userUuid.get(), id, v.getId());
                if (pv.isPresent()) {
                    var p = pv.get();
                    resume = p.getLastPositionSeconds() != null ? p.getLastPositionSeconds() : 0;
                    total = p.getTotalWatchSeconds() != null ? p.getTotalWatchSeconds() : 0;
                    completed = p.getIsCompleted() != null && p.getIsCompleted();
                    pct = p.getProgress() != null ? p.getProgress() : 0;
                }
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
            // 단일 영상 항목 구성
            items.add(new EducationVideosResponse.VideoItem(
                v.getId(),
                v.getTitle(),
                v.getFileUrl(),
                durationSec,
                v.getVersion() != null ? v.getVersion() : 1,
                v.getDepartmentScope(),
                resume,
                completed,
                total,
                pct,
                watchStatus
            ));
        }
        // 목록 응답 생성
        return EducationVideosResponse.builder()
            .id(e.getId())
            .title(e.getTitle())
            .videos(items)
            .build();
    }

    /**
     * 영상이 사용자 부서에서 접근 가능한지 확인합니다.
     * - departmentScope가 null이거나 비어있으면 모든 부서에서 접근 가능
     * - departmentScope에 사용자 부서 중 하나라도 포함되면 접근 가능
     */
    private boolean isVideoAccessibleByDepartment(EducationVideo video, List<String> userDepartments) {
        String deptScope = video.getDepartmentScope();
        // departmentScope가 없으면 모든 부서 접근 가능
        if (deptScope == null || deptScope.isBlank()) {
            return true;
        }
        // 사용자 부서가 없으면 접근 불가
        if (userDepartments == null || userDepartments.isEmpty()) {
            return false;
        }
        // departmentScope JSON 파싱
        try {
            List<String> allowedDepts = objectMapper.readValue(deptScope, new TypeReference<List<String>>() {});
            if (allowedDepts == null || allowedDepts.isEmpty()) {
                return true; // 빈 리스트면 모든 부서 접근 가능
            }
            // 사용자 부서 중 하나라도 허용 목록에 있으면 접근 가능
            for (String userDept : userDepartments) {
                if (allowedDepts.contains(userDept)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // 파싱 실패 시 접근 허용 (관대하게 처리)
            return true;
        }
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
            educationId, "PUBLISHED");
        int avg = 0;
        boolean allCompleted = false;
        if (!publishedVideos.isEmpty()) {
            // PUBLISHED 영상들의 진행률 합산 및 완료 개수 집계
            int sum = 0;
            int completedCount = 0;
            for (EducationVideo v : publishedVideos) {
                Optional<EducationVideoProgress> pOpt = educationVideoProgressRepository
                    .findByUserUuidAndEducationIdAndVideoId(userUuid, educationId, v.getId());
                
                if (pOpt.isPresent()) {
                    EducationVideoProgress p = pOpt.get();
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
            educationId, "PUBLISHED");
        
        if (publishedVideos.isEmpty()) {
            result.put("status", "FAILED");
            result.put("message", "해당 교육에 노출 가능한 영상이 없습니다.");
            return result;
        }
        
        // 3. PUBLISHED 영상들의 진행 정보 조회 및 완료 여부 확인
        boolean ok = true;
        for (EducationVideo video : publishedVideos) {
            Optional<EducationVideoProgress> progressOpt = educationVideoProgressRepository
                .findByUserUuidAndEducationIdAndVideoId(userUuid, educationId, video.getId());
            
            if (progressOpt.isEmpty() || 
                progressOpt.get().getIsCompleted() == null || 
                !progressOpt.get().getIsCompleted()) {
                ok = false;
                break;
            }
        }
        if (ok) {
            // EducationProgress 조회 또는 생성
            EducationProgress progress = educationProgressRepository
                .findByUserUuidAndEducationId(userUuid, educationId)
                .orElseGet(() -> {
                    EducationProgress newProgress = new EducationProgress();
                    newProgress.setUserUuid(userUuid);
                    newProgress.setEducationId(educationId);
                    newProgress.setProgress(100);
                    return newProgress;
                });
                
            // 4. 이수 완료 처리
            Instant completedAt = Instant.now();
            progress.setIsCompleted(true);
            progress.setCompletedAt(completedAt);
            progress.setProgress(100);
            educationProgressRepository.save(progress);

            // 5. 결과 반환
            result.put("status", "COMPLETED");
            result.put("completedAt", completedAt.toString());
        } else {
            result.put("status", "FAILED");
            result.put("message", "영상 이수 조건 미충족");
        }
        return result;
    }

    /**
     * 생성 요청 유효성 검사.
     *
     * @param req 요청 본문
     */
    private void validateCreate(CreateEducationRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is required");
        if (!StringUtils.hasText(req.getTitle())) throw new IllegalArgumentException("title is required");
        if (req.getCategory() == null) throw new IllegalArgumentException("category is required");
        if (req.getRequire() == null) throw new IllegalArgumentException("require is required");
    }

    // ========================
    // 대시보드 통계 관련 메서드
    // ========================

    /**
     * infra-service에서 사용자 부서 정보 조회.
     */
    private String fetchUserDepartmentFromInfraService(UUID userId) {
        if (userId == null) {
            return null;
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
                return null;
            }
            
            // attributes에서 department 추출
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) userMap.get("attributes");
            if (attributes != null) {
                @SuppressWarnings("unchecked")
                List<String> deptList = (List<String>) attributes.get("department");
                if (deptList != null && !deptList.isEmpty()) {
                    return deptList.get(0);
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("사용자 부서 정보 조회 실패: userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 기간 필터에 따른 시작 날짜 계산.
     */
    private Instant calculateStartDate(Integer periodDays) {
        if (periodDays == null || periodDays <= 0) {
            periodDays = 30; // 기본값: 30일
        }
        return Instant.now().minus(periodDays, ChronoUnit.DAYS);
    }

    /**
     * 대시보드 요약 통계 조회.
     */
    public EducationResponses.DashboardSummaryResponse getDashboardSummary(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        // 모든 교육 진행 현황 조회 (기간 필터)
        List<EducationProgress> allProgresses = educationProgressRepository.findAll().stream()
            .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(startDate))
            .filter(p -> p.getDeletedAt() == null)
            .collect(Collectors.toList());

        // 부서 필터 적용
        if (department != null && !department.isBlank()) {
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = fetchUserDepartmentFromInfraService(p.getUserUuid());
                    return department.equals(userDept);
                })
                .collect(Collectors.toList());
        }

        // 전체 평균 이수율 계산
        long totalUsers = allProgresses.stream()
            .map(EducationProgress::getUserUuid)
            .distinct()
            .count();
        
        long completedUsers = allProgresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getUserUuid)
            .distinct()
            .count();

        double overallAverage = totalUsers > 0 ? (double) completedUsers / totalUsers * 100 : 0.0;

        // 미이수자 수 계산
        // 필수 교육(require=true) 중 이수하지 않은 사용자 수
        List<Education> requiredEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> Boolean.TRUE.equals(e.getRequire()))
            .collect(Collectors.toList());

        // 필수 교육 대상 사용자 수집
        List<UUID> allUserUuids = allProgresses.stream()
            .map(EducationProgress::getUserUuid)
            .distinct()
            .collect(Collectors.toList());

        // 각 필수 교육별로 이수하지 않은 사용자 카운트
        long nonCompleterCount = 0;
        for (Education reqEdu : requiredEducations) {
            List<UUID> completedUserUuids = allProgresses.stream()
                .filter(p -> reqEdu.getId().equals(p.getEducationId()))
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .map(EducationProgress::getUserUuid)
                .distinct()
                .collect(Collectors.toList());

            // 필수 교육을 이수하지 않은 사용자 수 (전체 사용자 중 이수하지 않은 사용자)
            // 간단히: 전체 사용자 수에서 이수자 수를 뺌 (실제로는 전체 사용자 수를 알아야 하지만, 현재는 진행 현황 기준)
            long nonCompletersForEdu = allUserUuids.size() - completedUserUuids.size();
            if (nonCompletersForEdu > 0) {
                nonCompleterCount += nonCompletersForEdu;
            }
        }

        // 4대 의무교육 평균 계산
        List<Education> mandatoryEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getEduType() == EducationCategory.MANDATORY)
            .collect(Collectors.toList());
        
        double mandatoryAverage = calculateCategoryAverage(mandatoryEducations, allProgresses);

        // 직무교육 평균 계산
        List<Education> jobEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getEduType() == EducationCategory.JOB)
            .collect(Collectors.toList());
        
        double jobAverage = calculateCategoryAverage(jobEducations, allProgresses);

        return new EducationResponses.DashboardSummaryResponse(
            overallAverage,
            nonCompleterCount,
            mandatoryAverage,
            jobAverage
        );
    }

    /**
     * 카테고리별 평균 이수율 계산.
     */
    private double calculateCategoryAverage(List<Education> educations, List<EducationProgress> allProgresses) {
        if (educations.isEmpty()) {
            return 0.0;
        }

        double totalRate = 0.0;
        int count = 0;

        for (Education edu : educations) {
            List<EducationProgress> eduProgresses = allProgresses.stream()
                .filter(p -> edu.getId().equals(p.getEducationId()))
                .collect(Collectors.toList());

            if (!eduProgresses.isEmpty()) {
                long totalUsers = eduProgresses.stream()
                    .map(EducationProgress::getUserUuid)
                    .distinct()
                    .count();
                
                long completedUsers = eduProgresses.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                    .map(EducationProgress::getUserUuid)
                    .distinct()
                    .count();

                if (totalUsers > 0) {
                    totalRate += (double) completedUsers / totalUsers * 100;
                    count++;
                }
            }
        }

        return count > 0 ? totalRate / count : 0.0;
    }

    /**
     * 4대 의무교육 이수율 조회.
     */
    public EducationResponses.MandatoryCompletionResponse getMandatoryCompletion(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        List<EducationProgress> allProgresses = educationProgressRepository.findAll().stream()
            .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(startDate))
            .filter(p -> p.getDeletedAt() == null)
            .collect(Collectors.toList());

        // 부서 필터 적용
        if (department != null && !department.isBlank()) {
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = fetchUserDepartmentFromInfraService(p.getUserUuid());
                    return department.equals(userDept);
                })
                .collect(Collectors.toList());
        }

        // 각 의무교육별 이수율 계산
        double sexualHarassment = calculateTopicCompletionRate(
            EducationTopic.SEXUAL_HARASSMENT_PREVENTION, allProgresses);
        double personalInfo = calculateTopicCompletionRate(
            EducationTopic.PERSONAL_INFO_PROTECTION, allProgresses);
        double workplaceBullying = calculateTopicCompletionRate(
            EducationTopic.WORKPLACE_BULLYING, allProgresses);
        double disabilityAwareness = calculateTopicCompletionRate(
            EducationTopic.DISABILITY_AWARENESS, allProgresses);

        return new EducationResponses.MandatoryCompletionResponse(
            sexualHarassment,
            personalInfo,
            workplaceBullying,
            disabilityAwareness
        );
    }

    /**
     * 주제별 이수율 계산.
     */
    private double calculateTopicCompletionRate(EducationTopic topic, List<EducationProgress> allProgresses) {
        List<Education> topicEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getCategory() == topic)
            .collect(Collectors.toList());

        if (topicEducations.isEmpty()) {
            return 0.0;
        }

        List<UUID> educationIds = topicEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toList());

        List<EducationProgress> topicProgresses = allProgresses.stream()
            .filter(p -> educationIds.contains(p.getEducationId()))
            .collect(Collectors.toList());

        if (topicProgresses.isEmpty()) {
            return 0.0;
        }

        long totalUsers = topicProgresses.stream()
            .map(EducationProgress::getUserUuid)
            .distinct()
            .count();
        
        long completedUsers = topicProgresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getUserUuid)
            .distinct()
            .count();

        return totalUsers > 0 ? (double) completedUsers / totalUsers * 100 : 0.0;
    }

    /**
     * 직무교육 이수 현황 조회.
     */
    public EducationResponses.JobEducationCompletionResponse getJobEducationCompletion(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        List<Education> jobEducations = educationRepository.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> e.getEduType() == EducationCategory.JOB)
            .collect(Collectors.toList());

        List<EducationProgress> allProgresses = educationProgressRepository.findAll().stream()
            .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(startDate))
            .filter(p -> p.getDeletedAt() == null)
            .collect(Collectors.toList());

        // 부서 필터 적용
        if (department != null && !department.isBlank()) {
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = fetchUserDepartmentFromInfraService(p.getUserUuid());
                    return department.equals(userDept);
                })
                .collect(Collectors.toList());
        }

        List<EducationResponses.JobEducationCompletionItem> items = new ArrayList<>();

        for (Education edu : jobEducations) {
            List<EducationProgress> eduProgresses = allProgresses.stream()
                .filter(p -> edu.getId().equals(p.getEducationId()))
                .collect(Collectors.toList());

            long learnerCount = eduProgresses.stream()
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            // 상태 결정: 모든 학습자가 완료했으면 "이수 완료", 아니면 "진행 중"
            long completedCount = eduProgresses.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            String status = (learnerCount > 0 && completedCount == learnerCount) ? "이수 완료" : "진행 중";

            items.add(new EducationResponses.JobEducationCompletionItem(
                edu.getId(),
                edu.getTitle(),
                status,
                learnerCount
            ));
        }

        return new EducationResponses.JobEducationCompletionResponse(items);
    }

    /**
     * 부서별 이수율 현황 조회.
     */
    public EducationResponses.DepartmentCompletionResponse getDepartmentCompletion(Integer periodDays) {
        Instant startDate = calculateStartDate(periodDays);
        
        List<EducationProgress> allProgresses = educationProgressRepository.findAll().stream()
            .filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(startDate))
            .filter(p -> p.getDeletedAt() == null)
            .collect(Collectors.toList());

        // 부서별로 그룹화
        Map<String, List<EducationProgress>> deptProgressMap = new HashMap<>();
        
        for (EducationProgress progress : allProgresses) {
            String dept = fetchUserDepartmentFromInfraService(progress.getUserUuid());
            if (dept != null && !dept.isBlank()) {
                deptProgressMap.computeIfAbsent(dept, k -> new ArrayList<>()).add(progress);
            }
        }

        List<EducationResponses.DepartmentCompletionItem> items = new ArrayList<>();

        for (Map.Entry<String, List<EducationProgress>> entry : deptProgressMap.entrySet()) {
            String dept = entry.getKey();
            List<EducationProgress> deptProgresses = entry.getValue();

            long targetCount = deptProgresses.stream()
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            long completerCount = deptProgresses.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            double completionRate = targetCount > 0 ? (double) completerCount / targetCount * 100 : 0.0;
            long nonCompleterCount = targetCount - completerCount;

            items.add(new EducationResponses.DepartmentCompletionItem(
                dept,
                targetCount,
                completerCount,
                completionRate,
                nonCompleterCount
            ));
        }

        // 이수율 기준으로 정렬 (내림차순)
        items.sort((a, b) -> Double.compare(b.getCompletionRate(), a.getCompletionRate()));

        return new EducationResponses.DepartmentCompletionResponse(items);
    }

}

