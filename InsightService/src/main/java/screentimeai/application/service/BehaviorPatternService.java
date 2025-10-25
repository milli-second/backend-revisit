package screentimeai.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.presentation.dto.BehaviorPatternResponse;
import screentimeai.domain.entity.UserClassificationEntity;
import screentimeai.domain.model.UserClassification;
import screentimeai.infra.repository.UserClassificationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 행동 패턴 분류 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorPatternService {

    private final UserClassificationRepository classificationRepository;

    /**
     * 어제부터 7일치 행동 패턴 분류 조회
     *
     * @param subjectId 사용자 ID
     * @return 7일간의 행동 패턴 분류
     */
    @Transactional(readOnly = true)
    public List<BehaviorPatternResponse> getWeeklyBehaviorPatterns(UUID subjectId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate = yesterday.minusDays(6); // 어제부터 7일 전까지

        log.debug("Fetching weekly behavior patterns: subjectId={}, from {} to {}",
                subjectId, startDate, yesterday);

        List<UserClassificationEntity> classifications = classificationRepository
                .findRecentBySubjectId(subjectId, startDate);

        List<BehaviorPatternResponse> responses = classifications.stream()
                .map(this::buildBehaviorPatternResponse)
                .collect(Collectors.toList());

        log.info("Weekly behavior patterns fetched: subjectId={}, records={}", subjectId, responses.size());
        return responses;
    }

    private BehaviorPatternResponse buildBehaviorPatternResponse(UserClassificationEntity entity) {
        String behaviorPattern = entity.getBehaviorPattern();
        String behaviorPatternKo = getKoreanName(behaviorPattern);

        return BehaviorPatternResponse.builder()
                .date(entity.getClassifiedDate())
                .behaviorPattern(behaviorPattern)
                .behaviorPatternKo(behaviorPatternKo)
                .build();
    }

    private String getKoreanName(String behaviorPattern) {
        if (behaviorPattern == null) {
            return "알 수 없음";
        }

        try {
            UserClassification.BehaviorPattern pattern =
                    UserClassification.BehaviorPattern.valueOf(behaviorPattern);
            return pattern.getDisplayName();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown behavior pattern: {}", behaviorPattern);
            return "알 수 없음";
        }
    }
}
