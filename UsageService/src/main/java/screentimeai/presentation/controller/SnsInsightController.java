package screentimeai.presentation.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import screentimeai.domain.dto.SnsInsightResponse;
import screentimeai.domain.entity.UserSnsInsight;
import screentimeai.domain.service.SnsInsightService;

import java.util.UUID;

/**
 * SNS 인사이트 API 컨트롤러
 *
 * SNS 사용 패턴 정보 제공:
 * - 전체 중 SNS 비율
 * - SNS 중 새벽 접속 비율
 */
@RestController
@RequestMapping("/usage/insight")
@RequiredArgsConstructor
@Slf4j
public class SnsInsightController {

    private final SnsInsightService snsInsightService;

    /**
     * 어제 날짜 기준 SNS 인사이트 조회
     */
    @SecurityRequirement(name = "JWT Authentication")
    @GetMapping
    public ResponseEntity<SnsInsightResponse> getSnsInsight(
            @Parameter(description = "사용자 ID (JWT에서 자동 추출)", hidden = true)
            @RequestHeader("X-User-Id") String userId) {
        UUID subjectId = UUID.fromString(userId);
        log.info("GET /usage/insight for user: {}", subjectId);

        return snsInsightService.getYesterdayInsight(subjectId)
                .map(insight -> ResponseEntity.ok(toResponse(insight)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private SnsInsightResponse toResponse(UserSnsInsight insight) {
        return SnsInsightResponse.builder()
                .snsUsageRate(insight.getSnsUsageRate())
                .dawnAccessRate(insight.getDawnAccessRate())
                .calculatedDate(insight.getCalculatedDate())
                .build();
    }
}
