package com.ctrlf.education.quiz.service;

import com.ctrlf.education.entity.Education;
import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.quiz.dto.QuizResponse.TopicScoreItem;
import com.ctrlf.education.quiz.dto.QuizResponse.TopicScoreResponse;
import com.ctrlf.education.quiz.entity.QuizAttempt;
import com.ctrlf.education.quiz.repository.QuizAttemptRepository;
import com.ctrlf.education.repository.EducationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀴즈 내부 서비스.
 * Personalization 서비스를 위한 내부 API 비즈니스 로직을 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class InternalQuizService {

    private static final Logger log = LoggerFactory.getLogger(InternalQuizService.class);

    private final QuizAttemptRepository quizAttemptRepository;
    private final EducationRepository educationRepository;

    /**
     * 사용자의 특정 토픽 퀴즈 점수를 조회합니다 (Q7, Q18용).
     *
     * @param userUuid 사용자 UUID
     * @param topic 교육 토픽
     * @return 해당 토픽 퀴즈 점수 정보
     */
    @Transactional(readOnly = true)
    public TopicScoreResponse getScoreByTopic(UUID userUuid, EducationTopic topic) {
        log.info("getScoreByTopic: userUuid={}, topic={}", userUuid, topic);

        // 해당 토픽의 교육 목록 조회
        List<Education> topicEducations = educationRepository.findByTopic(topic);

        if (topicEducations.isEmpty()) {
            return TopicScoreResponse.builder()
                .topic(topic.name())
                .topicLabel(getTopicLabel(topic))
                .educationCount(0)
                .attemptedCount(0)
                .hasAttempt(false)
                .build();
        }

        List<UUID> topicEducationIds = topicEducations.stream()
            .map(Education::getId)
            .collect(Collectors.toList());

        // 사용자의 해당 토픽 교육 퀴즈 응시 내역 조회
        List<QuizAttempt> topicAttempts = quizAttemptRepository
            .findByUserUuidAndEducationIdInAndSubmittedAtIsNotNullOrderByCreatedAtDesc(userUuid, topicEducationIds);

        if (topicAttempts.isEmpty()) {
            return TopicScoreResponse.builder()
                .topic(topic.name())
                .topicLabel(getTopicLabel(topic))
                .educationCount(topicEducations.size())
                .attemptedCount(0)
                .hasAttempt(false)
                .build();
        }

        // 교육별로 그룹화하여 최고 점수 추출
        Map<UUID, List<QuizAttempt>> attemptsByEducation = topicAttempts.stream()
            .collect(Collectors.groupingBy(QuizAttempt::getEducationId));

        List<TopicScoreItem> items = topicEducations.stream()
            .map(edu -> {
                List<QuizAttempt> eduAttempts = attemptsByEducation.get(edu.getId());

                if (eduAttempts == null || eduAttempts.isEmpty()) {
                    return TopicScoreItem.builder()
                        .educationId(edu.getId().toString())
                        .title(edu.getTitle())
                        .hasAttempt(false)
                        .passScore(edu.getPassScore())
                        .build();
                }

                // 최고 점수 찾기
                QuizAttempt bestAttempt = eduAttempts.stream()
                    .filter(a -> a.getScore() != null)
                    .max(Comparator.comparingInt(QuizAttempt::getScore))
                    .orElse(null);

                if (bestAttempt == null) {
                    return TopicScoreItem.builder()
                        .educationId(edu.getId().toString())
                        .title(edu.getTitle())
                        .hasAttempt(true)
                        .attemptCount(eduAttempts.size())
                        .passScore(edu.getPassScore())
                        .build();
                }

                boolean passed = edu.getPassScore() != null && bestAttempt.getScore() >= edu.getPassScore();

                return TopicScoreItem.builder()
                    .educationId(edu.getId().toString())
                    .title(edu.getTitle())
                    .hasAttempt(true)
                    .bestScore(bestAttempt.getScore())
                    .passed(passed)
                    .attemptCount(eduAttempts.size())
                    .passScore(edu.getPassScore())
                    .lastAttemptAt(bestAttempt.getSubmittedAt() != null ? bestAttempt.getSubmittedAt().toString() : null)
                    .build();
            })
            .collect(Collectors.toList());

        // 통계 계산
        int attemptedCount = (int) items.stream().filter(TopicScoreItem::isHasAttempt).count();
        int passedCount = (int) items.stream().filter(i -> Boolean.TRUE.equals(i.getPassed())).count();

        // 평균 점수 계산 (응시한 교육만)
        double averageScore = items.stream()
            .filter(i -> i.getBestScore() != null)
            .mapToInt(TopicScoreItem::getBestScore)
            .average()
            .orElse(0.0);

        return TopicScoreResponse.builder()
            .topic(topic.name())
            .topicLabel(getTopicLabel(topic))
            .educationCount(topicEducations.size())
            .attemptedCount(attemptedCount)
            .passedCount(passedCount)
            .hasAttempt(attemptedCount > 0)
            .averageScore(Math.round(averageScore * 10) / 10.0)
            .items(items)
            .build();
    }

    /**
     * 토픽에 대한 한글 라벨 반환.
     */
    private String getTopicLabel(EducationTopic topic) {
        return switch (topic) {
            case WORKPLACE_BULLYING -> "직장 내 괴롭힘 예방";
            case SEXUAL_HARASSMENT_PREVENTION -> "성희롱 예방";
            case PERSONAL_INFO_PROTECTION -> "개인정보 보호";
            case DISABILITY_AWARENESS -> "장애인 인식 개선";
            case JOB_DUTY -> "직무 교육";
        };
    }
}
