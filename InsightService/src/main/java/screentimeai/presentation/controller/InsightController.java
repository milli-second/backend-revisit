package screentimeai.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.presentation.dto.AiInsightResponse;
import screentimeai.presentation.dto.BehaviorPatternResponse;
import screentimeai.presentation.dto.ContentPreferenceResponse;
import screentimeai.application.service.AiInsightService;
import screentimeai.application.service.BehaviorPatternService;
import screentimeai.application.service.ContentPreferenceService;

import java.util.List;
import java.util.UUID;

/**
 * 인사이트 API 컨트롤러
 *
 * 사용자의 행동 패턴 및 콘텐츠 선호 정보 제공
 */
@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Insights", description = "사용자 인사이트 API")
public class InsightController {

    private final BehaviorPatternService behaviorPatternService;
    private final ContentPreferenceService contentPreferenceService;
    private final AiInsightService aiInsightService;

    /**
     * 어제부터 7일간의 행동 패턴 조회
     *
     * @param userId 사용자 ID
     * @return 7일간의 행동 패턴 데이터
     */
    @Operation(
            summary = "행동 패턴 분류 조회",
            description = "어제부터 7일간의 사용자 행동 패턴 분류를 조회합니다.\n\n" +
                    "**분류 유형**:\n" +
                    "- COMPULSIVE_CHECKER (강박적 확인형)\n" +
                    "- NORMAL_BEHAVIOR (일반 행동)\n\n" +
                    "**제공 정보**:\n" +
                    "- behaviorPattern: 영문 분류명\n" +
                    "- behaviorPatternKo: 한글 분류명"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BehaviorPatternResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "데이터 없음"
            )
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/behavior-patterns")
    public ResponseEntity<List<BehaviorPatternResponse>> getBehaviorPatterns(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        log.info("GET /insights/behavior-patterns for user: {}", userId);

        UUID subjectId = UUID.fromString(userId);
        List<BehaviorPatternResponse> patterns = behaviorPatternService.getWeeklyBehaviorPatterns(subjectId);

        if (patterns.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(patterns);
    }

    /**
     * 어제부터 7일간의 콘텐츠 선호 조회
     *
     * @param userId 사용자 ID
     * @return 7일간의 콘텐츠 선호 데이터
     */
    @Operation(
            summary = "콘텐츠 선호 분류 조회",
            description = "어제부터 7일간의 사용자 콘텐츠 선호 분류를 조회합니다.\n\n" +
                    "**분류 유형**:\n" +
                    "- GAME_FOCUSED (게임 집중형)\n" +
                    "- SOCIAL_FOCUSED (소셜 집중형)\n" +
                    "- LEARNING_FOCUSED (학습 집중형)\n" +
                    "- ENTERTAINMENT_FOCUSED (엔터테인먼트 집중형)\n" +
                    "- BALANCED (균형잡힌 사용)\n\n" +
                    "**제공 정보**:\n" +
                    "- contentPreference: 영문 분류명\n" +
                    "- contentPreferenceKo: 한글 분류명"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ContentPreferenceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "데이터 없음"
            )
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/content-preferences")
    public ResponseEntity<List<ContentPreferenceResponse>> getContentPreferences(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        log.info("GET /insights/content-preferences for user: {}", userId);

        UUID subjectId = UUID.fromString(userId);
        List<ContentPreferenceResponse> preferences = contentPreferenceService.getWeeklyContentPreferences(subjectId);

        if (preferences.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(preferences);
    }

    /**
     * AI 기반 인사이트 요약 조회
     *
     * @param userId 사용자 ID
     * @return AI가 생성한 인사이트 요약
     */
    @Operation(
            summary = "AI 인사이트 요약 조회",
            description = "최근 7일간의 요일별 사용량 패턴을 분석하여 " +
                    "AI가 생성한 한 줄 인사이트를 제공합니다.\n\n" +
                    "**분석 데이터**:\n" +
                    "- 요일별 총 사용시간 및 픽업 횟수 (우선)\n" +
                    "- 행동 패턴 (별도 API에서 제공)\n" +
                    "- 콘텐츠 선호 (별도 API에서 제공)\n\n" +
                    "**응답 형식**:\n" +
                    "- ~요 체 사용 (예: ~해요, ~있어요)\n" +
                    "- 이모지 없음\n" +
                    "- 요일별 패턴 중심\n" +
                    "- 1-2문장으로 핵심만 전달\n\n" +
                    "**응답 예시**:\n" +
                    "- \"목요일에 사용량이 최대치를 기록했어요.\"\n" +
                    "- \"주말보다 평일에 스마트폰 사용이 더 많아요.\"\n" +
                    "- \"월요일에 픽업 횟수가 가장 높았어요.\"\n" +
                    "- 데이터 부족: \"분석할 데이터가 충분하지 않습니다.\""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AiInsightResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "데이터 없음"
            )
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "JWT Authentication")
    @GetMapping("/ai-summary")
    public ResponseEntity<AiInsightResponse> getAiInsightSummary(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        log.info("GET /insights/ai-summary for user: {}", userId);

        UUID subjectId = UUID.fromString(userId);
        String summary = aiInsightService.generateInsightSummary(subjectId);

        AiInsightResponse response = AiInsightResponse.builder()
                .summary(summary)
                .build();

        return ResponseEntity.ok(response);
    }
}
