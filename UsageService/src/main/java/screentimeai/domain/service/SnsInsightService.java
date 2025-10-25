package screentimeai.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.domain.entity.DailyUserMetrics;
import screentimeai.domain.entity.UserSnsInsight;
import screentimeai.infra.repository.DailyUserMetricsRepository;
import screentimeai.infra.repository.UserSnsInsightRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * SNS 인사이트 계산 서비스
 *
 * SNS 사용 패턴 분석:
 * - 전체 사용시간 중 SNS 비율
 * - SNS 사용시간 중 새벽 접속 비율
 *
 * SNS 카테고리: SOCIAL, COMMUNICATION, DATING
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnsInsightService {

    private final DailyUserMetricsRepository metricsRepository;
    private final UserSnsInsightRepository snsInsightRepository;

    /**
     * 특정 날짜의 SNS 인사이트 계산 및 저장
     *
     * @param subjectId 사용자 ID
     * @param targetDate 계산 대상 날짜
     */
    @Transactional
    public void calculateAndSave(UUID subjectId, LocalDate targetDate) {
        log.debug("Calculating SNS insight: subjectId={}, date={}", subjectId, targetDate);

        // 1. 일일 메트릭 조회
        Optional<DailyUserMetrics> metricsOpt = metricsRepository.findBySubjectIdAndSummaryDate(subjectId, targetDate);
        if (metricsOpt.isEmpty()) {
            log.warn("No metrics found for subjectId={}, date={}", subjectId, targetDate);
            return;
        }

        DailyUserMetrics metrics = metricsOpt.get();
        int totalUsageMinutes = metrics.getTotalUsageMinutes();
        int dawnSnsMinutes = metrics.getDawnSnsMinutes() != null ? metrics.getDawnSnsMinutes() : 0;

        if (totalUsageMinutes == 0) {
            log.debug("Total usage is 0, skipping SNS insight calculation");
            return;
        }

        // 2. SNS 카테고리 총 사용시간 계산 (social_comm_ratio 활용)
        // social_comm_ratio는 SOCIAL + COMMUNICATION + DATING의 합산 비율
        Float socialCommRatio = metrics.getSocialCommRatio() != null ? metrics.getSocialCommRatio() : 0.0f;
        int totalSnsMinutes = Math.round(totalUsageMinutes * socialCommRatio);

        // 3. 비율 계산
        float snsUsageRate = (float) totalSnsMinutes / totalUsageMinutes * 100;
        float dawnAccessRate = totalSnsMinutes > 0
                ? (float) dawnSnsMinutes / totalSnsMinutes * 100
                : 0.0f;

        // 4. 저장
        UserSnsInsight insight = UserSnsInsight.builder()
                .subjectId(subjectId)
                .calculatedDate(targetDate)
                .snsUsageRate(snsUsageRate)
                .dawnAccessRate(dawnAccessRate)
                .totalSnsMinutes(totalSnsMinutes)
                .build();

        snsInsightRepository.save(insight);

        log.info("SNS insight saved: subjectId={}, date={}, snsUsageRate={:.2f}%, dawnAccessRate={:.2f}%",
                subjectId, targetDate, snsUsageRate, dawnAccessRate);
    }

    /**
     * 어제 날짜의 SNS 인사이트 조회
     */
    @Transactional(readOnly = true)
    public Optional<UserSnsInsight> getYesterdayInsight(UUID subjectId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return snsInsightRepository.findBySubjectIdAndCalculatedDate(subjectId, yesterday);
    }
}
