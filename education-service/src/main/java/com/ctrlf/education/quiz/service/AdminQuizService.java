package com.ctrlf.education.quiz.service;

import com.ctrlf.education.quiz.dto.QuizResponse.DashboardSummaryResponse;
import com.ctrlf.education.quiz.dto.QuizResponse.DepartmentScoreItem;
import com.ctrlf.education.quiz.dto.QuizResponse.DepartmentScoreResponse;
import com.ctrlf.education.quiz.dto.QuizResponse.QuizStatsItem;
import com.ctrlf.education.quiz.dto.QuizResponse.QuizStatsResponse;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.quiz.entity.QuizAttempt;
import com.ctrlf.education.quiz.repository.QuizAttemptRepository;
import com.ctrlf.education.repository.EducationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀴즈 관리자 서비스.
 */
@Service
@RequiredArgsConstructor
public class AdminQuizService {

    private static final Logger log = LoggerFactory.getLogger(AdminQuizService.class);

    private final QuizAttemptRepository attemptRepository;
    private final EducationRepository educationRepository;

    /**
     * 대시보드 요약 통계 조회.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        // 제출 완료된 퀴즈 시도 조회 (기간/부서 필터)
        List<QuizAttempt> allAttempts = attemptRepository.findSubmittedAfterDate(startDate, department);

        if (allAttempts.isEmpty()) {
            return new DashboardSummaryResponse(0.0, 0L, 0.0, 0.0);
        }

        // 전체 평균 점수 계산
        double overallAverage = allAttempts.stream()
            .filter(a -> a.getScore() != null)
            .mapToInt(QuizAttempt::getScore)
            .average()
            .orElse(0.0);

        // 응시자 수 (고유 사용자 수)
        long participantCount = allAttempts.stream()
            .map(QuizAttempt::getUserUuid)
            .distinct()
            .count();

        // 통과율 계산 (80점 이상)
        long passedCount = allAttempts.stream()
            .filter(a -> a.getScore() != null && a.getScore() >= 80)
            .count();
        double passRate = allAttempts.size() > 0 ? (double) passedCount / allAttempts.size() * 100 : 0.0;

        // 응시율 계산 (전체 사용자 대비 응시자 비율)
        double participationRate = 100.0;

        return new DashboardSummaryResponse(
            overallAverage,
            participantCount,
            passRate,
            participationRate
        );
    }

    /**
     * 부서별 평균 점수 조회.
     */
    @Transactional(readOnly = true)
    public DepartmentScoreResponse getDepartmentScores(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        // 제출 완료된 퀴즈 시도 조회 (기간/부서 필터, 부서가 있는 것만)
        List<QuizAttempt> allAttempts = attemptRepository.findSubmittedAfterDateWithDepartment(startDate, department);

        // 부서별로 그룹화
        Map<String, List<QuizAttempt>> deptAttemptsMap = allAttempts.stream()
            .collect(Collectors.groupingBy(QuizAttempt::getDepartment));

        List<DepartmentScoreItem> items = new ArrayList<>();

        for (Map.Entry<String, List<QuizAttempt>> entry : deptAttemptsMap.entrySet()) {
            String dept = entry.getKey();
            List<QuizAttempt> deptAttempts = entry.getValue();

            // 평균 점수 계산
            double avgScore = deptAttempts.stream()
                .filter(a -> a.getScore() != null)
                .mapToInt(QuizAttempt::getScore)
                .average()
                .orElse(0.0);

            // 응시자 수 (고유 사용자 수)
            long participantCount = deptAttempts.stream()
                .map(QuizAttempt::getUserUuid)
                .distinct()
                .count();

            items.add(new DepartmentScoreItem(
                dept,
                avgScore,
                participantCount
            ));
        }

        // 평균 점수 기준으로 내림차순 정렬
        items.sort((a, b) -> Double.compare(b.getAverageScore(), a.getAverageScore()));

        return new DepartmentScoreResponse(items);
    }

    /**
     * 퀴즈별 통계 조회.
     * - 각 교육의 최신 버전 퀴즈만 통계에 포함
     * - 각 교육당 회차를 통합하여 결과 반환
     */
    @Transactional(readOnly = true)
    public QuizStatsResponse getQuizStats(Integer periodDays, String department) {
        Instant startDate = calculateStartDate(periodDays);
        
        // 제출 완료된 퀴즈 시도 조회 (기간/부서 필터)
        List<QuizAttempt> allAttempts = attemptRepository.findSubmittedAfterDate(startDate, department);

        // 교육별 최신 버전 찾기 (QuizAttempt의 version 기준)
        Map<UUID, Integer> latestVersionByEducation = new HashMap<>();
        for (QuizAttempt attempt : allAttempts) {
            UUID eduId = attempt.getEducationId();
            if (eduId == null) {
                continue;
            }
            Integer version = attempt.getVersion();
            if (version == null) {
                continue;
            }
            latestVersionByEducation.merge(eduId, version, Math::max);
        }

        // 최신 버전의 퀴즈 시도만 필터링
        List<QuizAttempt> latestVersionAttempts = allAttempts.stream()
            .filter(a -> {
                UUID eduId = a.getEducationId();
                if (eduId == null) {
                    return false;
                }
                Integer latestVersion = latestVersionByEducation.get(eduId);
                if (latestVersion == null) {
                    return false;
                }
                Integer attemptVersion = a.getVersion();
                return attemptVersion != null && attemptVersion.equals(latestVersion);
            })
            .collect(Collectors.toList());

        // 교육 정보 조회 (N+1 문제 해결 - findAllById 사용)
        Set<UUID> educationIds = latestVersionAttempts.stream()
            .map(QuizAttempt::getEducationId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<UUID, Education> educationMap = new HashMap<>();
        if (!educationIds.isEmpty()) {
            List<Education> educations = educationRepository.findAllById(educationIds);
            for (Education edu : educations) {
                if (edu.getDeletedAt() == null) {
                    educationMap.put(edu.getId(), edu);
                }
            }
        }

        // 교육별로 그룹화 (회차 통합)
        Map<UUID, List<QuizAttempt>> groupedAttempts = new HashMap<>();
        for (QuizAttempt attempt : latestVersionAttempts) {
            UUID eduId = attempt.getEducationId();
            if (eduId == null || !educationMap.containsKey(eduId)) {
                continue;
            }
            groupedAttempts.computeIfAbsent(eduId, k -> new ArrayList<>()).add(attempt);
        }

        List<QuizStatsItem> items = new ArrayList<>();

        for (Map.Entry<UUID, List<QuizAttempt>> entry : groupedAttempts.entrySet()) {
            UUID educationId = entry.getKey();
            List<QuizAttempt> attempts = entry.getValue();
            if (attempts.isEmpty()) {
                continue;
            }

            Education education = educationMap.get(educationId);
            if (education == null) {
                continue;
            }

            // 평균 점수 계산
            double avgScore = attempts.stream()
                .filter(a -> a.getScore() != null)
                .mapToInt(QuizAttempt::getScore)
                .average()
                .orElse(0.0);

            // 응시 수 (모든 회차 통합)
            long attemptCount = attempts.size();

            // 통과율 계산 (80점 이상)
            long passedCount = attempts.stream()
                .filter(a -> a.getScore() != null && a.getScore() >= 80)
                .count();
            double passRate = attempts.size() > 0 ? (double) passedCount / attempts.size() * 100 : 0.0;

            items.add(new QuizStatsItem(
                educationId,
                education.getTitle() != null ? education.getTitle() : "",
                avgScore,
                attemptCount,
                passRate
            ));
        }

        // 평균 점수 기준으로 내림차순 정렬
        items.sort((a, b) -> Double.compare(b.getAverageScore(), a.getAverageScore()));

        return new QuizStatsResponse(items);
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
}
