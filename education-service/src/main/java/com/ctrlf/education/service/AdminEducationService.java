package com.ctrlf.education.service;

import com.ctrlf.common.dto.MutationResponse;
import com.ctrlf.education.dto.EducationRequests.CreateEducationRequest;
import com.ctrlf.education.dto.EducationRequests.UpdateEducationRequest;
import com.ctrlf.education.dto.EducationResponses;
import com.ctrlf.education.dto.EducationResponses.EducationDetailResponse;
import com.ctrlf.education.dto.EducationResponses.EducationVideosResponse;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationCategory;
import com.ctrlf.education.entity.EducationProgress;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.repository.EducationProgressRepository;
import com.ctrlf.education.script.client.InfraRagClient;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.entity.SourceSetDocument;
import com.ctrlf.education.video.repository.EducationVideoProgressRepository;
import com.ctrlf.education.video.repository.EducationVideoRepository;
import com.ctrlf.education.video.repository.SourceSetDocumentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * 교육 어드민 관련 비즈니스 로직을 담당하는 서비스.
 * <p>
 * - 교육 생성/조회/수정/삭제<br>
 * - 대시보드 통계 조회<br>
 * 를 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminEducationService {

    private static final Logger log = LoggerFactory.getLogger(AdminEducationService.class);

    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;
    private final EducationVideoProgressRepository educationVideoProgressRepository;
    private final EducationProgressRepository educationProgressRepository;
    private final SourceSetDocumentRepository sourceSetDocumentRepository;
    private final InfraRagClient infraRagClient;

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
        e.setVersion(req.getVersion());
        e.setPassScore(req.getPassScore());
        e.setPassRatio(req.getPassRatio());
        e.setRequire(req.getRequire());
        e.setStartAt(req.getStartAt());
        e.setEndAt(req.getEndAt());
        e.setDepartmentScope(req.getDepartmentScope());
        UUID id = educationRepository.save(e).getId();
        return new MutationResponse<>(id);
    }

    /**
     * 교육 상세 조회.
     *
     * @param id 교육 ID
     * @return 상세 응답
     * @throws ResponseStatusException 존재하지 않으면
     */
    public EducationDetailResponse getEducationDetail(UUID id) {
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
            .startAt(e.getStartAt())
            .endAt(e.getEndAt())
            .departmentScope(e.getDepartmentScope())
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
     * @throws ResponseStatusException 존재하지 않으면
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
        if (req.getVersion() != null) e.setVersion(req.getVersion());
        if (req.getStartAt() != null) e.setStartAt(req.getStartAt());
        if (req.getEndAt() != null) e.setEndAt(req.getEndAt());
        if (req.getDepartmentScope() != null) e.setDepartmentScope(req.getDepartmentScope());
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
     * 전체 교육과 하위 영상 목록 조회(ADMIN).
     *
     * @param status 영상 상태 필터 (옵션)
     * @return 교육 및 영상 목록
     */
    public List<EducationVideosResponse> getAllEducationsWithVideos(VideoStatus status) {
        // 1. 삭제되지 않은 교육만 조회
        List<Education> edus = educationRepository.findAllActive();
        
        // 2. 삭제되지 않은 영상을 한 번에 조회 (status 필터 적용)
        List<EducationVideo> allVideos = (status != null)
            ? educationVideoRepository.findAllByStatusNotDeleted(status)
            : educationVideoRepository.findAllNotDeleted();
        
        // educationId로 그룹화
        Map<UUID, List<EducationVideo>> videosByEducationId = allVideos.stream()
            .collect(Collectors.groupingBy(EducationVideo::getEducationId));
        
        // 3. sourceSetId 수집 후 한 번에 조회
        Set<UUID> allSourceSetIds = allVideos.stream()
            .map(EducationVideo::getSourceSetId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        
        Map<UUID, List<SourceSetDocument>> documentsBySourceSetId = new HashMap<>();
        if (!allSourceSetIds.isEmpty()) {
            List<SourceSetDocument> allDocuments = sourceSetDocumentRepository.findBySourceSetIdIn(allSourceSetIds);
            documentsBySourceSetId = allDocuments.stream()
                .collect(Collectors.groupingBy(doc -> doc.getSourceSet().getId()));
        }
        
        // 4. documentId 수집 후 배치로 외부 API 호출
        Set<UUID> allDocumentIds = documentsBySourceSetId.values().stream()
            .flatMap(List::stream)
            .map(SourceSetDocument::getDocumentId)
            .collect(Collectors.toSet());
        
        Map<UUID, InfraRagClient.DocumentInfoResponse> documentInfoMap = fetchDocumentInfoBatch(allDocumentIds);
        
        // 5. 결과 생성
        List<EducationVideosResponse> result = new ArrayList<>();
        for (Education e : edus) {
            List<EducationVideo> videos = videosByEducationId.getOrDefault(e.getId(), Collections.emptyList());
            
            List<EducationVideosResponse.VideoItem> items = new ArrayList<>();
            for (EducationVideo v : videos) {
                String sourceFileName = null;
                String sourceFileUrl = null;

                if (v.getSourceSetId() != null) {
                    List<SourceSetDocument> documents = documentsBySourceSetId.getOrDefault(v.getSourceSetId(), Collections.emptyList());
                    if (!documents.isEmpty()) {
                        UUID documentId = documents.get(0).getDocumentId();
                        InfraRagClient.DocumentInfoResponse docInfo = documentInfoMap.get(documentId);
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

                items.add(new EducationVideosResponse.VideoItem(
                    v.getId(),
                    v.getTitle(),
                    v.getFileUrl(),
                    v.getDuration() != null ? v.getDuration() : 0,
                    v.getVersion() != null ? v.getVersion() : 1,
                    v.getStatus() != null ? v.getStatus().name() : null,
                    e.getDepartmentScope(),
                    null, // resumePosition
                    null, // isCompleted
                    null, // totalWatchSeconds
                    null, // progressPercent
                    null, // watchStatus
                    sourceFileName,
                    sourceFileUrl
                ));
            }
            
            result.add(EducationVideosResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .startAt(e.getStartAt())
                .endAt(e.getEndAt())
                .departmentScope(e.getDepartmentScope())
                .videos(items)
                .build());
        }
        return result;
    }
    
    /**
     * 문서 정보를 배치로 조회 (N+1 해결용).
     */
    private Map<UUID, InfraRagClient.DocumentInfoResponse> fetchDocumentInfoBatch(Set<UUID> documentIds) {
        Map<UUID, InfraRagClient.DocumentInfoResponse> result = new HashMap<>();
        for (UUID documentId : documentIds) {
            try {
                InfraRagClient.DocumentInfoResponse docInfo = infraRagClient.getDocument(documentId.toString());
                if (docInfo != null) {
                    result.put(documentId, docInfo);
                }
            } catch (Exception ex) {
                log.debug("문서 정보 조회 실패: documentId={}", documentId);
            }
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
     * infra-service에서 부서별 전체 사용자 수 조회.
     */
    private long fetchDepartmentUserCountFromInfraService(String department) {
        try {
            RestClient restClient = RestClient.builder()
                .baseUrl(infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl)
                .build();
            
            String uri = "/admin/users/count";
            if (department != null && !department.isBlank()) {
                uri += "?department=" + java.net.URLEncoder.encode(department.trim(), java.nio.charset.StandardCharsets.UTF_8);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Map.class);

                log.info("response 444: {}", response);
            
            if (response != null && response.containsKey("count")) {
                Object count = response.get("count");
                if (count instanceof Number) {
                    return ((Number) count).longValue();
                }
            }
            return 0;
        } catch (Exception e) {
            log.warn("부서별 사용자 수 조회 실패: department={}, error={}", department, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * infra-service에서 모든 사용자의 부서 정보를 한 번에 조회 (N+1 해결용).
     * @return userId -> department 매핑
     */
    private Map<UUID, String> fetchAllUserDepartmentsFromInfraService() {
        Map<UUID, String> userDepartmentMap = new HashMap<>();
        try {
            RestClient restClient = RestClient.builder()
                .baseUrl(infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl)
                .build();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                .uri("/admin/users/search?page=0&size=1000")
                .retrieve()
                .body(Map.class);
            
            if (response != null && response.containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> users = (List<Map<String, Object>>) response.get("items");
                if (users != null) {
                    for (Map<String, Object> user : users) {
                        String idStr = (String) user.get("id");
                        if (idStr == null) continue;
                        
                        UUID userId = UUID.fromString(idStr);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> attributes = (Map<String, Object>) user.get("attributes");
                        String department = null;
                        if (attributes != null) {
                            @SuppressWarnings("unchecked")
                            List<String> deptList = (List<String>) attributes.get("department");
                            if (deptList != null && !deptList.isEmpty()) {
                                department = deptList.get(0);
                            }
                        }
                        if (department != null) {
                            userDepartmentMap.put(userId, department);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("전체 사용자 부서 정보 조회 실패: error={}", e.getMessage());
        }
        return userDepartmentMap;
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
        // 기간 필터 적용
        Instant startDate = calculateStartDate(periodDays);
        
        // 사용자-부서 매핑을 한 번에 조회 (N+1 해결)
        Map<UUID, String> userDepartmentMap = fetchAllUserDepartmentsFromInfraService();
        
        // 기간 내에 완료된 진행 기록만 조회
        List<EducationProgress> allProgresses = educationProgressRepository.findCompletedAfter(startDate);

        // 전체 대상자 수 계산 (부서 필터 적용 시)
        long totalTargetUsers = 0;

        if (department != null && !department.isBlank()) {
            String departmentTrimmed = department.trim();
            // 해당 부서의 전체 사용자 수 조회
            totalTargetUsers = fetchDepartmentUserCountFromInfraService(departmentTrimmed);
            
            // 부서 필터 적용 (캐시된 매핑 사용)
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = userDepartmentMap.get(p.getUserUuid());
                    if (userDept == null || userDept.isBlank()) {
                        return false;
                    }
                    return departmentTrimmed.equals(userDept.trim());
                })
                .collect(Collectors.toList());

        } else {
            // 부서 필터가 없으면 전체 사용자 수 조회
            totalTargetUsers = fetchDepartmentUserCountFromInfraService(null);
        }

        // 삭제되지 않은 교육만 조회
        List<Education> allEducations = educationRepository.findAllActive();
        
        long totalEducationCount = allEducations.size();
        
        // 전체 평균 이수율 계산
        double overallAverage = 0.0;

        if (totalTargetUsers > 0 && totalEducationCount > 0) {
            Map<UUID, Long> completedCountByUser = allProgresses.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .collect(Collectors.groupingBy(
                    EducationProgress::getUserUuid,
                    Collectors.counting()
                ));
            
            long totalCompletedEducations = completedCountByUser.values().stream()
                .mapToLong(Long::longValue)
                .sum();
            
            overallAverage = (double) totalCompletedEducations / (totalTargetUsers * totalEducationCount) * 100;
        }

        // 미이수자 수 계산 (allEducations 재사용)
        List<Education> requiredEducations = allEducations.stream()
            .filter(e -> Boolean.TRUE.equals(e.getRequire()))
            .collect(Collectors.toList());

        long nonCompleterCount = 0;
        for (Education reqEdu : requiredEducations) {
            Set<UUID> completedUserUuids = allProgresses.stream()
                .filter(p -> reqEdu.getId().equals(p.getEducationId()))
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .map(EducationProgress::getUserUuid)
                .collect(Collectors.toSet());

            long nonCompletersForEdu = totalTargetUsers - completedUserUuids.size();
            if (nonCompletersForEdu > 0) {
                nonCompleterCount += nonCompletersForEdu;
            }
        }

        // 4대 의무교육 평균 계산 (allEducations 재사용)
        List<Education> mandatoryEducations = allEducations.stream()
            .filter(e -> e.getEduType() == EducationCategory.MANDATORY)
            .collect(Collectors.toList());
        
        double mandatoryAverage = calculateCategoryAverage(mandatoryEducations, allProgresses, totalTargetUsers);

        // 직무교육 평균 계산 (allEducations 재사용)
        List<Education> jobEducations = allEducations.stream()
            .filter(e -> e.getEduType() == EducationCategory.JOB)
            .collect(Collectors.toList());
        
        double jobAverage = calculateCategoryAverage(jobEducations, allProgresses, totalTargetUsers);

        return new EducationResponses.DashboardSummaryResponse(
            overallAverage,
            nonCompleterCount,
            mandatoryAverage,
            jobAverage
        );
    }

    /**
     * 카테고리별 평균 이수율 계산.
     * 
     * @param educations 교육 목록
     * @param allProgresses 전체 진행 기록 (이미 부서 필터링됨)
     * @param totalTargetUsers 전체 대상자 수 (0이면 진행 기록 기반으로 계산)
     */
    private double calculateCategoryAverage(List<Education> educations, List<EducationProgress> allProgresses, long totalTargetUsers) {
        if (educations.isEmpty()) {
            return 0.0;
        }

        double totalRate = 0.0;
        int count = 0;

        for (Education edu : educations) {
            List<EducationProgress> eduProgresses = allProgresses.stream()
                .filter(p -> edu.getId().equals(p.getEducationId()))
                .collect(Collectors.toList());

            // 완료한 사용자 수 계산
            long completedUsers = eduProgresses.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            // 전체 대상자 수가 제공된 경우 사용, 아니면 진행 기록 기반으로 계산
            long totalUsers = totalTargetUsers > 0 ? totalTargetUsers : eduProgresses.stream()
                .map(EducationProgress::getUserUuid)
                .distinct()
                .count();

            if (totalUsers > 0) {
                totalRate += (double) completedUsers / totalUsers * 100;
                count++;
            }
        }

        return count > 0 ? totalRate / count : 0.0;
    }

    /**
     * 4대 의무교육 이수율 조회.
     */
    public EducationResponses.MandatoryCompletionResponse getMandatoryCompletion(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        // 사용자-부서 매핑을 한 번에 조회 (N+1 해결)
        Map<UUID, String> userDepartmentMap = fetchAllUserDepartmentsFromInfraService();
        
        // 기간 내에 완료된 진행 기록만 조회
        List<EducationProgress> allProgresses = educationProgressRepository.findCompletedAfter(startDate);

        // 전체 대상자 수 계산 (부서 필터 적용 시)
        long totalTargetUsers = 0;
        if (department != null && !department.isBlank()) {
            String departmentTrimmed = department.trim();
            totalTargetUsers = fetchDepartmentUserCountFromInfraService(departmentTrimmed);
            
            // 부서 필터 적용 (캐시된 매핑 사용)
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = userDepartmentMap.get(p.getUserUuid());
                    if (userDept == null || userDept.isBlank()) {
                        return false;
                    }
                    return departmentTrimmed.equals(userDept.trim());
                })
                .collect(Collectors.toList());
        } else {
            totalTargetUsers = fetchDepartmentUserCountFromInfraService(null);
        }

        // 삭제되지 않은 교육만 조회
        List<Education> allEducations = educationRepository.findAllActive();

        // 각 의무교육별 이수율 계산 (캐시된 교육 목록 사용)
        double sexualHarassment = calculateTopicCompletionRateWithCache(
            EducationTopic.SEXUAL_HARASSMENT_PREVENTION, allProgresses, totalTargetUsers, allEducations);
        double personalInfo = calculateTopicCompletionRateWithCache(
            EducationTopic.PERSONAL_INFO_PROTECTION, allProgresses, totalTargetUsers, allEducations);
        double workplaceBullying = calculateTopicCompletionRateWithCache(
            EducationTopic.WORKPLACE_BULLYING, allProgresses, totalTargetUsers, allEducations);
        double disabilityAwareness = calculateTopicCompletionRateWithCache(
            EducationTopic.DISABILITY_AWARENESS, allProgresses, totalTargetUsers, allEducations);

        return new EducationResponses.MandatoryCompletionResponse(
            sexualHarassment,
            personalInfo,
            workplaceBullying,
            disabilityAwareness
        );
    }

    /**
     * 주제별 이수율 계산 (캐시된 교육 목록 사용).
     * N+1 문제 해결: 교육 목록을 파라미터로 받아서 재사용
     */
    private double calculateTopicCompletionRateWithCache(EducationTopic topic, List<EducationProgress> allProgresses, 
            long totalTargetUsers, List<Education> allEducations) {
        List<Education> topicEducations = allEducations.stream()
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

        long completedUsers = topicProgresses.stream()
            .filter(p -> Boolean.TRUE.equals(p.getIsCompleted()))
            .map(EducationProgress::getUserUuid)
            .distinct()
            .count();

        long totalUsers = totalTargetUsers > 0 ? totalTargetUsers : topicProgresses.stream()
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
        
        // 사용자-부서 매핑을 한 번에 조회 (N+1 해결)
        Map<UUID, String> userDepartmentMap = fetchAllUserDepartmentsFromInfraService();
        
        // 삭제되지 않은 직무 교육만 조회
        List<Education> jobEducations = educationRepository.findAllActive().stream()
            .filter(e -> e.getEduType() == EducationCategory.JOB)
            .collect(Collectors.toList());

        // 기간 내에 완료된 진행 기록만 조회
        List<EducationProgress> allProgresses = educationProgressRepository.findCompletedAfter(startDate);

        // 부서 필터 적용 (캐시된 매핑 사용)
        if (department != null && !department.isBlank()) {
            String departmentTrimmed = department.trim();
            allProgresses = allProgresses.stream()
                .filter(p -> {
                    String userDept = userDepartmentMap.get(p.getUserUuid());
                    if (userDept == null || userDept.isBlank()) {
                        return false;
                    }
                    return departmentTrimmed.equals(userDept.trim());
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
        
        // 사용자-부서 매핑을 한 번에 조회 (N+1 해결)
        Map<UUID, String> userDepartmentMap = fetchAllUserDepartmentsFromInfraService();
        
        // 기간 내에 완료된 진행 기록만 조회
        List<EducationProgress> completedProgresses = educationProgressRepository.findCompletedAfter(startDate);

        // 삭제되지 않은 모든 진행 기록 조회
        List<EducationProgress> allProgresses = educationProgressRepository.findAllNotDeleted();

        // infra-service에서 모든 부서 목록 조회
        Map<String, Long> departmentUserCountMap = fetchAllDepartmentUserCountsFromInfraService();

        // 부서별 진행 기록 그룹화 (캐시된 매핑 사용)
        Map<String, List<EducationProgress>> deptProgressMap = new HashMap<>();
        Map<String, Set<UUID>> deptUserSet = new HashMap<>();
        
        for (EducationProgress progress : allProgresses) {
            String dept = userDepartmentMap.get(progress.getUserUuid());
            if (dept != null && !dept.isBlank()) {
                deptProgressMap.computeIfAbsent(dept, k -> new ArrayList<>()).add(progress);
                deptUserSet.computeIfAbsent(dept, k -> new HashSet<>()).add(progress.getUserUuid());
            }
        }

        // 완료된 진행 기록도 부서별로 그룹화 (캐시된 매핑 사용)
        Map<String, Set<UUID>> deptCompletedUserSet = new HashMap<>();
        for (EducationProgress progress : completedProgresses) {
            String dept = userDepartmentMap.get(progress.getUserUuid());
            if (dept != null && !dept.isBlank() && Boolean.TRUE.equals(progress.getIsCompleted())) {
                deptCompletedUserSet.computeIfAbsent(dept, k -> new HashSet<>()).add(progress.getUserUuid());
            }
        }

        List<EducationResponses.DepartmentCompletionItem> items = new ArrayList<>();

        // 모든 부서에 대해 결과 생성
        for (Map.Entry<String, Long> deptEntry : departmentUserCountMap.entrySet()) {
            String dept = deptEntry.getKey();
            long totalTargetUsers = deptEntry.getValue();
            
            Set<UUID> completedUsers = deptCompletedUserSet.getOrDefault(dept, Collections.emptySet());
            long completerCount = completedUsers.size();
            
            double completionRate = totalTargetUsers > 0 ? (double) completerCount / totalTargetUsers * 100 : 0.0;
            long nonCompleterCount = totalTargetUsers - completerCount;

            items.add(new EducationResponses.DepartmentCompletionItem(
                dept,
                totalTargetUsers,
                completerCount,
                completionRate,
                nonCompleterCount
            ));
        }

        // 이수율 기준으로 정렬 (내림차순)
        items.sort((a, b) -> Double.compare(b.getCompletionRate(), a.getCompletionRate()));

        return new EducationResponses.DepartmentCompletionResponse(items);
    }
    
    /**
     * infra-service에서 모든 부서별 사용자 수를 조회.
     */
    private Map<String, Long> fetchAllDepartmentUserCountsFromInfraService() {
        Map<String, Long> departmentCountMap = new HashMap<>();
        try {
            RestClient restClient = RestClient.builder()
                .baseUrl(infraBaseUrl.endsWith("/") ? infraBaseUrl.substring(0, infraBaseUrl.length() - 1) : infraBaseUrl)
                .build();
            
            // 전체 사용자 목록 조회 (큰 페이지로)
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
            .uri("/admin/users/search?page=0&size=1000")
                .retrieve()
                .body(Map.class);
            
            if (response != null && response.containsKey("items")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> users = (List<Map<String, Object>>) response.get("items");
                if (users != null) {
                    for (Map<String, Object> user : users) {
                        // attributes에서 department 추출
                        @SuppressWarnings("unchecked")
                        Map<String, Object> attributes = (Map<String, Object>) user.get("attributes");
                        String department = "Others";
                        if (attributes != null) {
                            @SuppressWarnings("unchecked")
                            List<String> deptList = (List<String>) attributes.get("department");
                            if (deptList != null && !deptList.isEmpty()) {
                                department = deptList.get(0);
                            }
                        }
                        departmentCountMap.put(department, departmentCountMap.getOrDefault(department, 0L) + 1);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("모든 부서별 사용자 수 조회 실패: error={}", e.getMessage());
        }
        return departmentCountMap;
    }
}
