package screentimeai.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.domain.dto.ContentPreferenceResponse;
import screentimeai.domain.entity.UserClassificationEntity;
import screentimeai.domain.model.UserClassification;
import screentimeai.infra.repository.UserClassificationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 콘텐츠 선호 분류 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentPreferenceService {

    private final UserClassificationRepository classificationRepository;

    /**
     * 어제부터 7일치 콘텐츠 선호 분류 조회
     *
     * @param subjectId 사용자 ID
     * @return 7일간의 콘텐츠 선호 분류
     */
    @Transactional(readOnly = true)
    public List<ContentPreferenceResponse> getWeeklyContentPreferences(UUID subjectId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate = yesterday.minusDays(6); // 어제부터 7일 전까지

        log.debug("Fetching weekly content preferences: subjectId={}, from {} to {}",
                subjectId, startDate, yesterday);

        List<UserClassificationEntity> classifications = classificationRepository
                .findRecentBySubjectId(subjectId, startDate);

        List<ContentPreferenceResponse> responses = classifications.stream()
                .map(this::buildContentPreferenceResponse)
                .collect(Collectors.toList());

        log.info("Weekly content preferences fetched: subjectId={}, records={}", subjectId, responses.size());
        return responses;
    }

    private ContentPreferenceResponse buildContentPreferenceResponse(UserClassificationEntity entity) {
        String contentPreference = entity.getContentPreference();
        String contentPreferenceKo = getKoreanName(contentPreference);

        return ContentPreferenceResponse.builder()
                .date(entity.getClassifiedDate())
                .contentPreference(contentPreference)
                .contentPreferenceKo(contentPreferenceKo)
                .build();
    }

    private String getKoreanName(String contentPreference) {
        if (contentPreference == null) {
            return "알 수 없음";
        }

        try {
            UserClassification.ContentPreference preference =
                    UserClassification.ContentPreference.valueOf(contentPreference);
            return preference.getDisplayName();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown content preference: {}", contentPreference);
            return "알 수 없음";
        }
    }
}
