package screentimeai.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import screentimeai.domain.entity.DailyMetricsSaved;
import screentimeai.domain.entity.InsightDailyCategoryMetricsEntity;
import screentimeai.domain.entity.InsightDailySummaryEntity;
import screentimeai.domain.entity.UserClassificationEntity;
import screentimeai.domain.model.UserClassification;
import screentimeai.domain.service.UserClassificationService;
import screentimeai.infra.repository.InsightDailyCategoryMetricsRepository;
import screentimeai.infra.repository.InsightDailySummaryRepository;
import screentimeai.infra.repository.UserClassificationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 인사이트 데이터 영속화 서비스
 *
 * DB 저장 로직을 담당하며 트랜잭션 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsightPersistenceService {

    private final InsightDailySummaryRepository dailySummaryRepository;
    private final InsightDailyCategoryMetricsRepository categoryMetricsRepository;
    private final UserClassificationRepository userClassificationRepository;
    private final UserClassificationService classificationService;

    /**
     * DailyMetricsSaved 이벤트 처리 및 DB 저장
     */
    @Transactional
    public void processDailyMetrics(DailyMetricsSaved metrics) {
        log.info("Processing daily metrics for subject: {}", metrics.getSubjectId());

        try {
            // 1. 일일 요약 저장
            saveInsightDailySummary(metrics);

            // 2. 카테고리별 메트릭 저장
            saveCategoryMetrics(metrics);

            // 3. 사용자 분류 수행 및 저장
            UserClassification classification = classificationService.classify(metrics);
            saveUserClassification(metrics, classification);

            // 4. 분류 결과 로깅
            logClassificationResult(metrics, classification);

            log.info("✅ 데이터베이스 저장 완료: subjectId={}, date={}",
                    metrics.getSubjectId(), metrics.getSummaryDate());

        } catch (Exception e) {
            log.error("데이터베이스 저장 실패: subjectId={}, date={}",
                    metrics.getSubjectId(), metrics.getSummaryDate(), e);
            throw e;  // 트랜잭션 롤백
        }
    }

    private void saveInsightDailySummary(DailyMetricsSaved metrics) {
        InsightDailySummaryEntity summary = InsightDailySummaryEntity.builder()
                .subjectId(metrics.getSubjectId())
                .summaryDate(metrics.getSummaryDate())
                .totalUsageMinutes(metrics.getTotalUsageMinutes())
                .dailyPickupCount(metrics.getDailyPickupCount())
                .morningMinutes(metrics.getMorningMinutes())
                .afternoonMinutes(metrics.getAfternoonMinutes())
                .eveningMinutes(metrics.getEveningMinutes())
                .build();

        dailySummaryRepository.save(summary);
        log.debug("일일 요약 저장: {}", summary);
    }

    private void saveCategoryMetrics(DailyMetricsSaved metrics) {
        List<InsightDailyCategoryMetricsEntity> categoryMetrics = new ArrayList<>();

        Map<Integer, Float> categoryRatios = Map.of(
                1, metrics.getGameRatio(),
                2, metrics.getSocialCommRatio(),
                3, metrics.getLearningRatio(),
                4, metrics.getEntertainmentRatio()
        );

        int totalUsageMinutes = metrics.getTotalUsageMinutes();

        for (Map.Entry<Integer, Float> entry : categoryRatios.entrySet()) {
            Integer categoryId = entry.getKey();
            Float ratio = entry.getValue();
            int usageMinutes = Math.round(totalUsageMinutes * ratio);

            if (usageMinutes > 0) {
                InsightDailyCategoryMetricsEntity metric = InsightDailyCategoryMetricsEntity.builder()
                        .subjectId(metrics.getSubjectId())
                        .summaryDate(metrics.getSummaryDate())
                        .categoryId(categoryId)
                        .usageMinutes(usageMinutes)
                        .build();
                categoryMetrics.add(metric);
            }
        }

        categoryMetricsRepository.saveAll(categoryMetrics);
        log.debug("카테고리 메트릭 저장: {}건", categoryMetrics.size());
    }

    private void saveUserClassification(DailyMetricsSaved metrics, UserClassification classification) {
        String classificationType = classification.getContentPreference().name() + "|" +
                classification.getBehaviorPattern().name();

        UserClassificationEntity entity = UserClassificationEntity.builder()
                .subjectId(metrics.getSubjectId())
                .classifiedDate(metrics.getSummaryDate())
                .classificationType(classificationType)
                .behaviorPattern(classification.getBehaviorPattern().name())
                .contentPreference(classification.getContentPreference().name())
                .build();

        userClassificationRepository.save(entity);
        log.debug("사용자 분류 저장: {}", entity);
    }

    private void logClassificationResult(DailyMetricsSaved metrics, UserClassification classification) {
        log.info("=".repeat(80));
        log.info("사용자 분류 결과");
        log.info("=".repeat(80));
        log.info("🆔 사용자 ID: {}", metrics.getSubjectId());
        log.info("📅 날짜: {}", metrics.getSummaryDate());
        log.info("🏷️  행동 패턴: {}", classification.getBehaviorPatternName());
        log.info("🏷️  콘텐츠 선호: {}", classification.getContentPreferenceName());
        log.info("=".repeat(80));
    }
}
