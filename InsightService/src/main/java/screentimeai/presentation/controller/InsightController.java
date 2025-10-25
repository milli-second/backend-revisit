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
import screentimeai.presentation.dto.BehaviorPatternResponse;
import screentimeai.presentation.dto.ContentPreferenceResponse;
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
    @GetMapping("/behavior-patterns/{userId}")
    public ResponseEntity<List<BehaviorPatternResponse>> getBehaviorPatterns(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String userId) {
        log.info("GET /insights/behavior-patterns/{}", userId);

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
    @GetMapping("/content-preferences/{userId}")
    public ResponseEntity<List<ContentPreferenceResponse>> getContentPreferences(
            @Parameter(description = "사용자 ID (UUID)", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable String userId) {
        log.info("GET /insights/content-preferences/{}", userId);

        UUID subjectId = UUID.fromString(userId);
        List<ContentPreferenceResponse> preferences = contentPreferenceService.getWeeklyContentPreferences(subjectId);

        if (preferences.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(preferences);
    }
}
