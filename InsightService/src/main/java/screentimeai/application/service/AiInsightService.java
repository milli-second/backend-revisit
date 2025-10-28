package screentimeai.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import screentimeai.domain.entity.InsightDailySummaryEntity;
import screentimeai.domain.entity.InsightSummaryEntity;
import screentimeai.domain.model.OpenAiRequest;
import screentimeai.domain.model.OpenAiResponse;
import screentimeai.infra.repository.InsightDailySummaryRepository;
import screentimeai.infra.repository.InsightSummaryRepository;
import screentimeai.presentation.dto.BehaviorPatternResponse;
import screentimeai.presentation.dto.ContentPreferenceResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightService {

    private final RestTemplate restTemplate;
    private final BehaviorPatternService behaviorPatternService;
    private final ContentPreferenceService contentPreferenceService;
    private final InsightDailySummaryRepository dailySummaryRepository;
    private final InsightSummaryRepository insightSummaryRepository;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.temperature}")
    private Double temperature;

    @Value("${openai.max-tokens}")
    private Integer maxTokens;

    /**
     * AI 기반 인사이트 요약 생성
     */
    @Transactional
    public String generateInsightSummary(UUID userId) {
        log.info("Generating AI insight summary for user: {}", userId);

        // 1. 캐시 확인 (24시간 이내)
        Optional<InsightSummaryEntity> cachedSummary = insightSummaryRepository.findByUserIdAndUpdatedAtAfter(
                userId,
                LocalDateTime.now().minusHours(24)
        );

        if (cachedSummary.isPresent()) {
            log.info("Returning cached insight summary for user: {}", userId);
            return cachedSummary.get().getSummary();
        }

        // 2. 데이터 수집
        List<BehaviorPatternResponse> patterns = behaviorPatternService.getWeeklyBehaviorPatterns(userId);
        List<ContentPreferenceResponse> preferences = contentPreferenceService.getWeeklyContentPreferences(userId);

        // 요일별 사용량 데이터
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate = yesterday.minusDays(6);
        List<InsightDailySummaryEntity> dailySummaries = dailySummaryRepository.findRecentBySubjectId(userId, startDate);

        // 데이터 충분성 검증
        if (patterns.isEmpty() && preferences.isEmpty() && dailySummaries.isEmpty()) {
            log.warn("No data available for user: {}", userId);
            return "아직 분석할 데이터가 충분하지 않습니다. 며칠 더 사용 후 다시 확인해주세요.";
        }

        // dailySummaries에 유효한 데이터가 있는지 확인
        long validDailySummaries = dailySummaries.stream()
                .filter(s -> s.getTotalUsageMinutes() != null && s.getTotalUsageMinutes() > 0)
                .count();

        if (validDailySummaries < 3 && patterns.isEmpty() && preferences.isEmpty()) {
            log.warn("Insufficient valid data for user: {}. Valid summaries: {}", userId, validDailySummaries);
            return "아직 분석할 데이터가 충분하지 않습니다. 며칠 더 사용 후 다시 확인해주세요.";
        }

        // 3. 프롬프트 생성
        String prompt = buildPrompt(patterns, preferences, dailySummaries);

        // 4. OpenAI API 호출
        String summary = callOpenAi(prompt);

        // 5. 결과 저장
        InsightSummaryEntity summaryEntity = InsightSummaryEntity.builder()
                .userId(userId)
                .summary(summary)
                .build();
        insightSummaryRepository.save(summaryEntity);


        log.info("AI insight generated and saved successfully for user: {}", userId);
        return summary;
    }

    /**
     * 프롬프트 생성
     */
    private String buildPrompt(
            List<BehaviorPatternResponse> patterns,
            List<ContentPreferenceResponse> preferences,
            List<InsightDailySummaryEntity> dailySummaries
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 사용자의 최근 7일간 스마트폰 사용 패턴 분석 결과입니다.\n");
        prompt.append("이 데이터를 바탕으로 핵심적인 인사이트를 한 문장으로 요약해주세요.\n\n");

        // 요일별 사용량 패턴
        if (!dailySummaries.isEmpty()) {
            prompt.append("### 요일별 사용량 (최근 7일)\n");
            dailySummaries.forEach(summary -> {
                DayOfWeek dayOfWeek = summary.getSummaryDate().getDayOfWeek();
                String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
                String activeHourInfo = summary.getMostActiveHourStart() != null
                        ? String.format(", 가장 활발한 시간: %d시-%d시",
                                summary.getMostActiveHourStart(),
                                summary.getMostActiveHourStart() + 2)
                        : "";
                prompt.append(String.format("- %s (%s): %d분, 픽업 횟수: %d회%s\n",
                        summary.getSummaryDate(),
                        dayName,
                        summary.getTotalUsageMinutes(),
                        summary.getDailyPickupCount(),
                        activeHourInfo));
            });
            prompt.append("\n");

            // 최대/최소 사용일 찾기
            InsightDailySummaryEntity maxUsage = dailySummaries.stream()
                    .max(Comparator.comparing(InsightDailySummaryEntity::getTotalUsageMinutes, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            InsightDailySummaryEntity minUsage = dailySummaries.stream()
                    .min(Comparator.comparing(InsightDailySummaryEntity::getTotalUsageMinutes, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);

            if (maxUsage != null && minUsage != null) {
                String maxDay = maxUsage.getSummaryDate().getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, Locale.KOREAN);
                String minDay = minUsage.getSummaryDate().getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, Locale.KOREAN);
                prompt.append(String.format("**참고**: 최대 사용일: %s (%d분), 최소 사용일: %s (%d분)\n\n",
                        maxDay, maxUsage.getTotalUsageMinutes(),
                        minDay, minUsage.getTotalUsageMinutes()));
            }
        }

        // 행동 패턴
        if (!patterns.isEmpty()) {
            prompt.append("### 행동 패턴 (최근 7일)\n");
            patterns.forEach(p -> {
                prompt.append(String.format("- %s: %s\n",
                        p.getDate(), p.getBehaviorPatternKo()));
            });
            prompt.append("\n");
        }

        // 콘텐츠 선호
        if (!preferences.isEmpty()) {
            prompt.append("### 콘텐츠 선호도 (최근 7일)\n");
            preferences.forEach(p -> {
                prompt.append(String.format("- %s: %s\n",
                        p.getDate(), p.getContentPreferenceKo()));
            });
            prompt.append("\n");
        }

        // 응답 형식 지정
        prompt.append("### 응답 요구사항\n");
        prompt.append("1. 한 문장으로 작성 (최대 2문장)\n");
        prompt.append("2. 반말 존댓말 ~요 체 사용 (예: ~해요, ~있어요, ~보여요)\n");
        prompt.append("3. 이모지 사용 금지\n");
        prompt.append("4. 가장 의미있는 변화나 특징만 언급 (요일별 사용량 패턴을 우선적으로 고려)\n");
        prompt.append("5. '최근 7일간', '이번 주' 등 시간 표현 사용 금지\n");
        prompt.append("6. 구체적인 날짜는 요일로 표현 (예: '목요일에 사용량이 최대치를 기록했어요')\n");
        prompt.append("7. 순수하게 내용만 전달\n");
        prompt.append("8. 행동 패턴과 콘텐츠 선호는 화면에서 별도 제공하므로, 요일별 사용량 패턴을 중심으로 인사이트 제공\n");
        prompt.append("9. 데이터가 부족한 경우 있는 데이터만 갖고 해석\n\n");
        prompt.append("**응답 예시**:\n");
        prompt.append("- 목요일에 사용량이 최대치를 기록했어요.\n");
        prompt.append("- 주말보다 평일에 스마트폰 사용이 더 많아요.\n");
        prompt.append("- 월요일에 픽업 횟수가 가장 높았어요.");

        return prompt.toString();
    }

    /**
     * OpenAI API 호출
     */
    private String callOpenAi(String prompt) {
        try {
            // Request 생성
            OpenAiRequest request = OpenAiRequest.builder()
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .messages(List.of(
                            OpenAiRequest.Message.builder()
                                    .role("user")
                                    .content(prompt)
                                    .build()
                    ))
                    .build();

            // Headers 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);

            // API 호출
            OpenAiResponse response = restTemplate.postForObject(
                    apiUrl,
                    entity,
                    OpenAiResponse.class
            );

            // 응답 추출
            if (response != null && !response.getChoices().isEmpty()) {
                return response.getChoices().get(0).getMessage().getContent().trim();
            }

            log.warn("Empty response from OpenAI");
            return "인사이트를 생성할 수 없습니다.";

        } catch (Exception e) {
            log.error("Failed to call OpenAI API", e);
            return "인사이트 생성 중 오류가 발생했습니다.";
        }
    }
}
