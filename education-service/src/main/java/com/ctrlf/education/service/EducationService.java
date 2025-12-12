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
import com.ctrlf.education.entity.EducationVideo;
import com.ctrlf.education.entity.EducationVideoProgress;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.repository.EducationVideoProgressRepository;
import com.ctrlf.education.repository.EducationVideoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

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

    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;
    private final EducationVideoProgressRepository educationVideoProgressRepository;
    private final ObjectMapper objectMapper;

    /**
     * 교육 생성.
     *
     * @param req 생성 요청
     * @return 생성된 교육 ID
     */
    @Transactional
    public MutationResponse<UUID> createEducation(CreateEducationRequest req) {
        validateCreate(req);
        String departmentScopeJson = null;
        if (req.getDepartmentScope() != null) {
            try {
                departmentScopeJson = objectMapper.writeValueAsString(req.getDepartmentScope());
            } catch (JsonProcessingException ignored) {
            }
        }
        Education e = new Education();
        e.setTitle(req.getTitle());
        e.setCategory(parseCategory(req.getCategory()));
        e.setDepartmentScope(departmentScopeJson);
        e.setDescription(req.getDescription());
        e.setPassScore(req.getPassScore());
        e.setPassRatio(req.getPassRatio());
        e.setRequire(req.getRequire());
        UUID id = educationRepository.save(e).getId();
        return new MutationResponse<>(id);
    }

    /**
     * 교육 생성 - 표준 MutationResponse 형태 반환.
     */
    @Transactional
    public MutationResponse<UUID> createEducationMutation(CreateEducationRequest req) {
        MutationResponse<UUID> id = createEducation(req);
        return id;
    }

    /**
     * 교육 목록 조회(사용자 기준).
     *
     * - 카테고리/필수 여부/이수 상태(미이수/진행중/완료)/제목/설명/대상 부서를 반환합니다.
     * - sort: UPDATED(최신 업데이트순), TITLE(제목 오름차순)
     *
     * @param page 페이지 번호(0-base)
     * @param size 페이지 크기
     * @param completedFilter 이수 여부(로그인 사용자 있을 때만 적용)
     * @param yearFilter 연도 필터
     * @param categoryFilter 카테고리 필터(MANDATORY/JOB/ETC)
     * @param userUuid 사용자 UUID(옵션)
     * @param sort 정렬 기준(UPDATED|TITLE)
     * @return 목록 DTO
     */
    public List<EducationResponses.EducationListItem> getEducationsMe(
        Integer page,
        Integer size,
        Optional<Boolean> completedFilter,
        Optional<Integer> yearFilter,
        Optional<String> categoryFilter,
        Optional<UUID> userUuid,
        String sort
    ) {
        // sort 유효성 검증: 허용값(UPDATED|TITLE)이 아니면 400
        if (StringUtils.hasText(sort)) {
            String s = sort.trim().toUpperCase();
            if (!s.equals("UPDATED") && !s.equals("TITLE")) {
                throw new IllegalArgumentException("invalid sort: " + sort + " (allowed: UPDATED, TITLE)");
            }
        }
        int pageSafe = page == null ? 0 : Math.max(page, 0);
        int sizeSafe = size == null ? 10 : Math.min(Math.max(size, 1), 100);
        int offset = pageSafe * sizeSafe;
        Optional<String> sanitizedCategory = categoryFilter
            .filter(StringUtils::hasText)
            .map(s -> s.trim().toUpperCase())
            .filter(s -> s.equals("MANDATORY") || s.equals("JOB") || s.equals("ETC"));

        Optional<Boolean> effectiveCompleted = userUuid.isPresent() ? completedFilter : Optional.empty();

        // 정렬 키 정규화: 기본 UPDATED, 제목순은 TITLE
        String sortKey = (!StringUtils.hasText(sort) || "UPDATED".equalsIgnoreCase(sort.trim())) ? "UPDATED" : "TITLE";
        List<Object[]> rows = educationRepository.findEducationsNative(
            offset,
            sizeSafe,
            effectiveCompleted.orElse(null),
            yearFilter.orElse(null),
            sanitizedCategory.orElse(null),
            userUuid.orElse(null),
            sortKey
        );
        List<EducationResponses.EducationListItem> result = new ArrayList<>();
        for (Object[] r : rows) {
            UUID id = (UUID) r[0];
            String title = (String) r[1];
            String description = (String) r[2];
            String category = (String) r[3];
            Boolean required = (Boolean) r[4];
            String deptScopeJson = (String) r[5];
            Boolean isCompleted = (Boolean) r[6];
            Boolean hasProgress = (Boolean) r[7];
            List<String> targetDepts = new ArrayList<>();
            if (deptScopeJson != null && !deptScopeJson.isBlank()) {
                try {
                    targetDepts = objectMapper.readValue(deptScopeJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                } catch (Exception ignored) {
                }
            }
            // 이수 상태 계산: 완료 > 진행중(진행내역 존재) > 미이수
            String status;
            if (Boolean.TRUE.equals(isCompleted)) {
                status = "완료";
            } else if (Boolean.TRUE.equals(hasProgress)) {
                status = "진행중";
            } else {
                status = "미이수";
            }
            result.add(new EducationResponses.EducationListItem(
                id,
                title,
                description,
                category,
                required != null && required,
                status,
                targetDepts
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
            .duration(totalDuration)
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
        if (StringUtils.hasText(req.getCategory())) e.setCategory(parseCategory(req.getCategory()));
        if (req.getRequire() != null) e.setRequire(req.getRequire());
        if (req.getPassScore() != null) e.setPassScore(req.getPassScore());
        if (req.getPassRatio() != null) e.setPassRatio(req.getPassRatio());
        if (req.getDepartmentScope() != null) {
            try {
                e.setDepartmentScope(objectMapper.writeValueAsString(req.getDepartmentScope()));
            } catch (JsonProcessingException ignored) {
            }
        }
        educationRepository.saveAndFlush(e);
        return e.getUpdatedAt();
    }

    /**
     * 교육 수정 - 표준 MutationResponse 형태 반환.
     */
    @Transactional
    public MutationResponse<UUID> updateEducationMutation(UUID id, UpdateEducationRequest req) {
        updateEducation(id, req);
        return new MutationResponse<>(id);
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
     *
     * @param id 교육 ID
     * @param userUuid 사용자 UUID(옵션)
     * @return 영상 목록 응답
     */
    public EducationVideosResponse getEducationVideos(UUID id, Optional<UUID> userUuid) {
        Education e = educationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "education not found"));
        // 해당 교육에 속한 영상 목록 조회
        List<EducationVideo> videos = educationVideoRepository.findByEducationId(id);
        // 응답으로 내려줄 영상 항목 DTO 리스트
        List<EducationVideosResponse.VideoItem> items = new ArrayList<>();
        for (EducationVideo v : videos) {
            // 사용자별 이어보기 위치/누적 시청시간/완료 여부 기본값
            Integer resume = 0;
            Integer total = 0;
            Boolean completed = false;
            // 사용자 UUID가 있으면 진행 정보 조회
            if (userUuid.isPresent()) {
                var pv = educationVideoProgressRepository.findByUserUuidAndEducationIdAndVideoId(userUuid.get(), id, v.getId());
                if (pv.isPresent()) {
                    var p = pv.get();
                    resume = p.getLastPositionSeconds() != null ? p.getLastPositionSeconds() : 0;
                    total = p.getTotalWatchSeconds() != null ? p.getTotalWatchSeconds() : 0;
                    completed = p.getIsCompleted() != null && p.getIsCompleted();
                }
            }
            // 단일 영상 항목 구성
            items.add(new EducationVideosResponse.VideoItem(
                v.getId(),
                v.getFileUrl(),
                v.getDuration() != null ? v.getDuration() : 0,
                v.getVersion() != null ? v.getVersion() : 1,
                v.getIsMain() != null && v.getIsMain(),
                v.getTargetDeptCode(),
                null, // drmKey (optional)
                null, // playbackToken (optional)
                resume,
                completed,
                total
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
        // 사용자 UUID 필수 검증
        if (userUuid == null) {
            throw new IllegalArgumentException("user required");
        }
        // 요청 값 파싱 및 기본값 설정
        int position = req.getPosition() != null ? req.getPosition() : 0;
        int duration = Math.max(1, req.getDuration() != null ? req.getDuration() : 1);
        int watch = req.getWatchTime() != null ? req.getWatchTime() : 0;

        // 진행 엔티티 조회(없으면 새로 생성)
        EducationVideoProgress progress = educationVideoProgressRepository
            .findByUserUuidAndEducationIdAndVideoId(userUuid, educationId, videoId)
            .orElseGet(() -> {
                return EducationVideoProgress.create(userUuid, educationId, videoId);
            });
        // 마지막 시청 위치 및 누적 시청 시간 갱신
        progress.setLastPositionSeconds(position);
        int currentTotal = progress.getTotalWatchSeconds() != null ? progress.getTotalWatchSeconds() : 0;
        progress.setTotalWatchSeconds(currentTotal + Math.max(0, watch));
        // 진행률(%) 계산 및 완료 여부 반영
        int pct = Math.min(100, Math.max(0, (int) Math.round((position * 100.0) / duration)));
        progress.setProgress(pct);
        progress.setIsCompleted(pct >= 100);
        // 변경 사항 저장
        educationVideoProgressRepository.save(progress);

        // 교육 전체 진행률(간단히 평균)
        List<EducationVideoProgress> all = educationVideoProgressRepository.findByUserUuidAndEducationId(userUuid, educationId);
        int avg = 0;
        boolean allCompleted = false;
        if (!all.isEmpty()) {
            // 전체 영상 진행률 합산 및 완료 개수 집계
            int sum = 0; int completedCount = 0;
            for (var p : all) {
                sum += p.getProgress() != null ? p.getProgress() : 0;
                if (p.getIsCompleted() != null && p.getIsCompleted()) completedCount++;
            }
            // 평균 진행률 및 전체 완료 여부 계산
            avg = sum / all.size();
            allCompleted = completedCount == all.size();
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
     * 교육 이수 처리.
     * 모든 영상이 완료 상태인지 검증하여 완료/실패를 반환합니다.
     *
     * @param educationId 교육 ID
     * @param userUuid 사용자 UUID
     * @return 처리 결과 맵
     */
    @Transactional
    public Map<String, Object> completeEducation(UUID educationId, UUID userUuid) {
        Map<String, Object> result = new HashMap<>();
        if (userUuid == null) {
            result.put("status", "FAILED");
            result.put("message", "user required");
            return result;
        }
        List<EducationVideoProgress> all = educationVideoProgressRepository.findByUserUuidAndEducationId(userUuid, educationId);

        // 모두 영상 시청이 완료되었는지 확인
        boolean ok = !all.isEmpty() && all.stream().allMatch(p -> p.getIsCompleted() != null && p.getIsCompleted());
        if (ok) {
            result.put("status", "COMPLETED");
            result.put("completedAt", Instant.now().toString());
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
        if (!StringUtils.hasText(req.getCategory())) throw new IllegalArgumentException("category is required");
        if (req.getRequire() == null) throw new IllegalArgumentException("require is required");
    }

    /**
     * 문자열 카테고리를 enum(EducationCategory)으로 변환.
     * - 허용값: MANDATORY, JOB, ETC (대소문자 무시)
     */
    private EducationCategory parseCategory(String category) {
        String normalized = category == null ? null : category.trim().toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("category is required");
        }
        try {
            return EducationCategory.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid category: " + category + " (allowed: MANDATORY, JOB, ETC)");
        }
    }
}

