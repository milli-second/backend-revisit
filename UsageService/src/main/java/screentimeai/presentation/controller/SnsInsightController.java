package screentimeai.presentation.controller;

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
     *
     * @param userId 사용자 ID
     * @return SNS 사용 비율 및 새벽 접속 비율
     */
    @GetMapping("/{userId}")
    public ResponseEntity<SnsInsightResponse> getSnsInsight(@PathVariable String userId) {
        log.info("GET /api/usage/{}", userId);

        UUID subjectId = UUID.fromString(userId);

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
