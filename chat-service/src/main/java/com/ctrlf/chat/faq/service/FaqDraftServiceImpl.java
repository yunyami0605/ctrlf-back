package com.ctrlf.chat.faq.service;

import com.ctrlf.chat.faq.dto.response.FaqDraftResponse;
import com.ctrlf.chat.faq.entity.*;
import com.ctrlf.chat.faq.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FaqDraftServiceImpl implements FaqDraftService {

    private final FaqDraftRepository faqDraftRepository;
    private final FaqRepository faqRepository;
    private final FaqRevisionRepository faqRevisionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FaqDraftResponse> getDrafts(String domain, String status) {
        List<FaqDraft> drafts;

        if (domain != null && status != null) {
            // status 파라미터 검증 및 변환 (PENDING -> DRAFT)
            FaqDraft.Status statusEnum = parseStatus(status);
            drafts = faqDraftRepository.findByDomainAndStatus(domain, statusEnum);
        } else if (status != null) {
            FaqDraft.Status statusEnum = parseStatus(status);
            drafts = faqDraftRepository.findByStatus(statusEnum);
        } else if (domain != null) {
            drafts = faqDraftRepository.findByDomain(domain);
        } else {
            drafts = faqDraftRepository.findAll();
        }

        return drafts.stream()
            .map(FaqDraftResponse::from)
            .toList();
    }

    /**
     * status 문자열을 FaqDraft.Status enum으로 변환합니다.
     * 
     * <p>프론트엔드에서 사용하는 한국어 상태값 및 다양한 영문 상태값을 지원합니다:</p>
     * <ul>
     *   <li>"대기중", "PENDING", "DRAFT" → DRAFT</li>
     *   <li>"승인됨", "PUBLISHED", "APPROVED" → PUBLISHED</li>
     *   <li>"반려됨", "REJECTED" → REJECTED</li>
     * </ul>
     */
    private FaqDraft.Status parseStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalizedStatus = status.trim();
        
        // 한국어 상태값 매핑
        if ("대기중".equals(normalizedStatus)) {
            return FaqDraft.Status.DRAFT;
        }
        if ("승인됨".equals(normalizedStatus)) {
            return FaqDraft.Status.PUBLISHED;
        }
        if ("반려됨".equals(normalizedStatus)) {
            return FaqDraft.Status.REJECTED;
        }
        
        // 영문 상태값 매핑 (대소문자 무시)
        String upperStatus = normalizedStatus.toUpperCase();
        if ("PENDING".equals(upperStatus) || "DRAFT".equals(upperStatus)) {
            return FaqDraft.Status.DRAFT;
        }
        if ("PUBLISHED".equals(upperStatus) || "APPROVED".equals(upperStatus)) {
            return FaqDraft.Status.PUBLISHED;
        }
        if ("REJECTED".equals(upperStatus)) {
            return FaqDraft.Status.REJECTED;
        }
        
        // 직접 enum 변환 시도
        try {
            return FaqDraft.Status.valueOf(upperStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("유효하지 않은 status 값입니다: %s (가능한 값: 대기중/DRAFT/PENDING, 승인됨/PUBLISHED/APPROVED, 반려됨/REJECTED)", status)
            );
        }
    }

    @Override
    public void approve(UUID draftUuid, String draftIdString, UUID reviewerId, String question, String answer) {
        log.info("[FAQ Draft 승인] 시작: draftUuid={}, draftIdString={}, reviewerId={}, question={}", 
            draftUuid, draftIdString, reviewerId, question);
        
        // UUID가 있으면 UUID로 조회, 없으면 faqDraftId로 조회
        FaqDraft draft;
        if (draftUuid != null) {
            draft = faqDraftRepository.findById(draftUuid)
                .orElseGet(() -> {
                    // UUID로 찾지 못하면 faqDraftId로 조회 시도
                    return faqDraftRepository.findByFaqDraftId(draftIdString)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("FAQ 초안을 찾을 수 없습니다. draftId=%s", draftIdString)
                        ));
                });
        } else {
            // UUID가 null이면 faqDraftId로 조회
            draft = faqDraftRepository.findByFaqDraftId(draftIdString)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("FAQ 초안을 찾을 수 없습니다. faqDraftId=%s", draftIdString)
                ));
        }
        
        log.info("[FAQ Draft 승인] Draft 조회 완료: draftId={}, domain={}, status={}", 
            draft.getId(), draft.getDomain(), draft.getStatus());

        // 이미 승인된 Draft는 다시 승인할 수 없음
        if (draft.getStatus() == FaqDraft.Status.PUBLISHED) {
            throw new IllegalStateException("이미 승인된 FAQ 초안입니다.");
        }

        // 이미 반려된 Draft는 승인할 수 없음
        if (draft.getStatus() == FaqDraft.Status.REJECTED) {
            throw new IllegalStateException("반려된 FAQ 초안은 승인할 수 없습니다.");
        }

        String routerDomain = draft.getDomain(); // 표준 도메인 (POLICY, EDU, HR, QUIZ, GENERAL)
        // 질문 내용을 분석하여 적절한 FAQ 도메인 결정
        String domain = determineFaqDomain(routerDomain, question, draft.getQuestion());
        
        log.info("[FAQ Draft 승인] 도메인 결정: routerDomain={} → faqDomain={}, question={}, draftId={}", 
            routerDomain, domain, question, draft.getId());
        
        // 도메인별 활성화된 FAQ 개수 확인
        List<Faq> activeFaqs = faqRepository.findByDomainAndIsActiveTrueOrderByPublishedAtAsc(domain);
        
        log.info("[FAQ Draft 승인] 도메인별 활성화된 FAQ 개수: domain={}, count={}, FAQ IDs={}", 
            domain, activeFaqs.size(),
            activeFaqs.stream().map(f -> f.getId().toString() + ":" + f.getQuestion().substring(0, Math.min(20, f.getQuestion().length()))).toList());
        
        // 도메인별 FAQ는 최대 2개로 제한
        // 새로운 FAQ를 승인하면, 기존 FAQ 중 가장 오래된 것(publishedAt이 가장 오래된 것)을 비활성화
        if (activeFaqs.size() >= 2) {
            // 가장 오래된 FAQ를 비활성화 (publishedAt이 가장 오래된 것)
            Faq oldestFaq = activeFaqs.get(0);
            log.info("[FAQ Draft 승인] 기존 FAQ 비활성화 시작: faqId={}, question={}, publishedAt={}", 
                oldestFaq.getId(), oldestFaq.getQuestion(), oldestFaq.getPublishedAt());
            
            oldestFaq.setIsActive(false);
            oldestFaq.setUpdatedAt(Instant.now());
            faqRepository.save(oldestFaq);
            
            log.info("[FAQ Draft 승인] 기존 FAQ 비활성화 완료: faqId={}, isActive={}", 
                oldestFaq.getId(), oldestFaq.getIsActive());
            
            // 관리자 이력 저장 (비활성화)
            faqRevisionRepository.save(
                FaqRevision.create(
                    "FAQ",
                    oldestFaq.getId(),
                    "DEACTIVATE",
                    reviewerId,
                    String.format("새로운 FAQ 승인으로 인한 자동 비활성화 (도메인: %s, 최대 2개 제한)", domain)
                )
            );
        } else {
            log.info("[FAQ Draft 승인] 도메인별 FAQ가 2개 미만이므로 기존 FAQ 비활성화 불필요: domain={}, count={}", 
                domain, activeFaqs.size());
        }

        // 게시 FAQ 생성
        Faq faq = new Faq();
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setDomain(domain);
        faq.setIsActive(true);
        faq.setPriority(1);  // 기본 우선순위 설정 (1~5, 기본값: 1)
        faq.setNeedsRecategorization(false);  // 기본값 설정
        faq.setPublishedAt(Instant.now());  // 게시 시각 설정
        faq.setCreatedAt(Instant.now());
        faq.setUpdatedAt(Instant.now());
        
        log.info("[FAQ Draft 승인] FAQ 생성 시작: domain={}, question={}, answer={}", 
            domain, question, answer != null ? answer.substring(0, Math.min(50, answer.length())) + "..." : "null");
        
        Faq savedFaq = faqRepository.save(faq);
        
        // FAQ 저장 확인 로그
        log.info("[FAQ Draft 승인] FAQ 생성 완료: faqId={}, domain={}, question={}, isActive={}, priority={}, publishedAt={}", 
            savedFaq.getId(), domain, question, savedFaq.getIsActive(), savedFaq.getPriority(), savedFaq.getPublishedAt());
        
        // 저장 후 즉시 조회하여 검증
        List<Faq> domainFaqs = faqRepository.findTop2ByDomainAndIsActiveTrueOrderByPublishedAtDesc(domain);
        log.info("[FAQ Draft 승인] 도메인별 FAQ 조회 검증: domain={}, 조회된 FAQ 개수={}, FAQ IDs={}", 
            domain, domainFaqs.size(), 
            domainFaqs.stream().map(f -> f.getId().toString()).toList());
        
        // 생성된 FAQ가 조회 결과에 포함되는지 확인
        boolean found = domainFaqs.stream().anyMatch(f -> f.getId().equals(savedFaq.getId()));
        if (found) {
            log.info("[FAQ Draft 승인] ✅ 생성된 FAQ가 도메인별 FAQ 목록에 포함됨: faqId={}", savedFaq.getId());
        } else {
            log.warn("[FAQ Draft 승인] ⚠️ 생성된 FAQ가 도메인별 FAQ 목록에 포함되지 않음: faqId={}, domain={}", 
                savedFaq.getId(), domain);
        }

        // 초안 상태 변경
        draft.publish(reviewerId);
        // 상태 변경 후 명시적으로 저장
        faqDraftRepository.save(draft);
        
        log.info("[FAQ Draft 승인] Draft 상태 변경 완료: draftId={}, status={}", 
            draft.getId(), draft.getStatus());

        // 관리자 이력 저장 (승인)
        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ_DRAFT",
                draft.getId(),
                "APPROVE",
                reviewerId,
                null
            )
        );
    }

    @Override
    public void reject(UUID draftUuid, String draftIdString, UUID reviewerId, String reason) {
        // UUID가 있으면 UUID로 조회, 없으면 faqDraftId로 조회
        FaqDraft draft;
        if (draftUuid != null) {
            draft = faqDraftRepository.findById(draftUuid)
                .orElseGet(() -> {
                    // UUID로 찾지 못하면 faqDraftId로 조회 시도
                    return faqDraftRepository.findByFaqDraftId(draftIdString)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("FAQ 초안을 찾을 수 없습니다. draftId=%s", draftIdString)
                        ));
                });
        } else {
            // UUID가 null이면 faqDraftId로 조회
            draft = faqDraftRepository.findByFaqDraftId(draftIdString)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("FAQ 초안을 찾을 수 없습니다. faqDraftId=%s", draftIdString)
                ));
        }

        // 이미 승인된 Draft는 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.PUBLISHED) {
            throw new IllegalStateException("이미 승인된 FAQ 초안은 반려할 수 없습니다.");
        }

        // 이미 반려된 Draft는 다시 반려할 수 없음
        if (draft.getStatus() == FaqDraft.Status.REJECTED) {
            throw new IllegalStateException("이미 반려된 FAQ 초안입니다.");
        }

        draft.reject(reviewerId);
        // 상태 변경 후 명시적으로 저장
        faqDraftRepository.save(draft);
        
        log.info("[FAQ Draft 반려] Draft 상태 변경 완료: draftId={}, status={}", 
            draft.getId(), draft.getStatus());

        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ_DRAFT",
                draft.getId(),
                "REJECT",
                reviewerId,
                reason
            )
        );
    }

    @Override
    public void delete(UUID draftUuid, String draftIdString, UUID reviewerId) {
        log.info("[FAQ Draft 삭제] 시작: draftUuid={}, draftIdString={}, reviewerId={}", 
            draftUuid, draftIdString, reviewerId);
        
        // UUID가 있으면 UUID로 조회, 없으면 faqDraftId로 조회
        FaqDraft draft;
        if (draftUuid != null) {
            draft = faqDraftRepository.findById(draftUuid)
                .orElseGet(() -> {
                    // UUID로 찾지 못하면 faqDraftId로 조회 시도
                    return faqDraftRepository.findByFaqDraftId(draftIdString)
                        .orElseThrow(() -> new IllegalArgumentException(
                            String.format("FAQ 초안을 찾을 수 없습니다. draftId=%s", draftIdString)
                        ));
                });
        } else {
            // UUID가 null이면 faqDraftId로 조회
            draft = faqDraftRepository.findByFaqDraftId(draftIdString)
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("FAQ 초안을 찾을 수 없습니다. faqDraftId=%s", draftIdString)
                ));
        }
        
        log.info("[FAQ Draft 삭제] Draft 조회 완료: draftId={}, domain={}, status={}, question={}", 
            draft.getId(), draft.getDomain(), draft.getStatus(), draft.getQuestion());
        
        // 삭제 이력 저장 (삭제 전에 저장)
        faqRevisionRepository.save(
            FaqRevision.create(
                "FAQ_DRAFT",
                draft.getId(),
                "DELETE",
                reviewerId,
                String.format("Draft 삭제 (도메인: %s, 상태: %s)", draft.getDomain(), draft.getStatus())
            )
        );
        
        // Draft 삭제
        faqDraftRepository.delete(draft);
        
        log.info("[FAQ Draft 삭제] 완료: draftId={}", draft.getId());
    }

    /**
     * 질문 내용을 분석하여 적절한 FAQ 도메인을 결정합니다.
     * 
     * <p>1순위: 질문 내용 키워드 분석</p>
     * <p>2순위: 표준 도메인 매핑</p>
     * 
     * @param routerDomain 표준 도메인 (POLICY, EDU, HR, QUIZ, GENERAL)
     * @param approvedQuestion 승인된 질문 (사용자가 수정한 질문)
     * @param draftQuestion Draft의 원본 질문
     * @return FAQ 도메인
     */
    private String determineFaqDomain(String routerDomain, String approvedQuestion, String draftQuestion) {
        // 질문 내용 분석 (승인된 질문 우선, 없으면 Draft 질문 사용)
        String questionToAnalyze = (approvedQuestion != null && !approvedQuestion.isBlank()) 
            ? approvedQuestion 
            : (draftQuestion != null ? draftQuestion : "");
        
        String questionUpper = questionToAnalyze.toUpperCase();
        
        // 계정 관련 키워드
        if (questionUpper.contains("계정") || questionUpper.contains("ACCOUNT") || 
            questionUpper.contains("로그인") || questionUpper.contains("비밀번호") ||
            questionUpper.contains("아이디") || questionUpper.contains("ID")) {
            return "ACCOUNT";
        }
        
        // 결재 관련 키워드
        if (questionUpper.contains("결재") || questionUpper.contains("APPROVAL") ||
            questionUpper.contains("승인") || questionUpper.contains("제출")) {
            return "APPROVAL";
        }
        
        // 급여 관련 키워드
        if (questionUpper.contains("급여") || questionUpper.contains("PAY") ||
            questionUpper.contains("월급") || questionUpper.contains("명세서") ||
            questionUpper.contains("계좌")) {
            return "PAY";
        }
        
        // 복지 관련 키워드
        if (questionUpper.contains("복지") || questionUpper.contains("WELFARE") ||
            questionUpper.contains("건강검진") || questionUpper.contains("포인트")) {
            return "WELFARE";
        }
        
        // 교육 관련 키워드
        if (questionUpper.contains("교육") || questionUpper.contains("EDUCATION") ||
            questionUpper.contains("EDU") || questionUpper.contains("수료증") ||
            questionUpper.contains("이수")) {
            return "EDUCATION";
        }
        
        // IT 관련 키워드
        if (questionUpper.contains("시스템") || questionUpper.contains("IT") ||
            questionUpper.contains("접속") || questionUpper.contains("VPN") ||
            questionUpper.contains("네트워크")) {
            return "IT";
        }
        
        // 보안 관련 키워드
        if (questionUpper.contains("보안") || questionUpper.contains("SECURITY") ||
            questionUpper.contains("인증") || questionUpper.contains("2FA") ||
            questionUpper.contains("잠금")) {
            return "SECURITY";
        }
        
        // 시설 관련 키워드
        if (questionUpper.contains("회의실") || questionUpper.contains("FACILITY") ||
            questionUpper.contains("주차") || questionUpper.contains("시설")) {
            return "FACILITY";
        }
        
        // 인사 관련 키워드 (HR 도메인)
        if (questionUpper.contains("인사") || questionUpper.contains("HR") ||
            questionUpper.contains("휴가") || questionUpper.contains("연차") ||
            questionUpper.contains("근태") || questionUpper.contains("출근") ||
            questionUpper.contains("퇴근")) {
            return "HR";
        }
        
        // 표준 도메인 매핑 (키워드 매칭 실패 시)
        return mapRouterDomainToFaqDomain(routerDomain);
    }

    /**
     * 표준 도메인(RouterDomain Enum)을 FAQ 도메인으로 매핑합니다.
     * 
     * <p>표준 도메인 → FAQ 도메인 매핑:</p>
     * <ul>
     *   <li>POLICY → SECURITY (사규/보안 정책)</li>
     *   <li>EDU → EDUCATION (4대 교육/직무 교육)</li>
     *   <li>HR → HR (인사/근태/복지/연차/급여) - 동일</li>
     *   <li>QUIZ → ETC (퀴즈/시험 관련 - FAQ에는 QUIZ 도메인이 없음)</li>
     *   <li>GENERAL → ETC (일반)</li>
     * </ul>
     * 
     * <p>이미 FAQ 도메인인 경우 그대로 반환합니다.</p>
     * 
     * @param routerDomain 표준 도메인 또는 FAQ 도메인
     * @return FAQ 도메인
     */
    private String mapRouterDomainToFaqDomain(String routerDomain) {
        if (routerDomain == null || routerDomain.isBlank()) {
            return "ETC"; // 기본값
        }
        
        String upperDomain = routerDomain.toUpperCase().trim();
        
        // 표준 도메인 → FAQ 도메인 매핑
        switch (upperDomain) {
            case "POLICY":
                return "SECURITY"; // 사규/보안 정책 → 보안
            case "EDU":
                return "EDUCATION"; // 4대 교육/직무 교육 → 교육
            case "HR":
                return "HR"; // 인사/근태/복지/연차/급여 → 인사 (동일)
            case "QUIZ":
                return "ETC"; // 퀴즈/시험 관련 → 기타
            case "GENERAL":
                return "ETC"; // 일반 → 기타
            default:
                // 이미 FAQ 도메인인 경우 (ACCOUNT, APPROVAL, PAY, WELFARE, IT, SECURITY, FACILITY, ETC 등)
                // 그대로 반환
                return upperDomain;
        }
    }
}
