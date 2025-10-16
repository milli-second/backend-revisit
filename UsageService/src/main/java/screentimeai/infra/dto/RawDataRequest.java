package screentimeai.infra.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import screentimeai.domain.entity.RawData;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "원본 사용 데이터 요청 DTO")
public class RawDataRequest {

    @Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("subject_id")
    private UUID subjectId;

    @Schema(description = "이벤트 발생 시간 (밀리초 epoch timestamp)", example = "1697270400000")
    @JsonProperty("timestamp")
    private Long timestamp;  // 밀리초 epoch timestamp

    @Schema(description = "UTC 오프셋 (분 단위)", example = "540")
    @JsonProperty("utcoffset")
    private Integer utcoffset;

    @Schema(description = "소스 타입 (ANDROID, IOS 등)", example = "ANDROID")
    @JsonProperty("source_type")
    private String sourceType;

    @Schema(description = "소스 정보")
    @JsonProperty("source_info")
    private String sourceInfo;

    @Schema(description = "패키지 이름", example = "com.example.app")
    @JsonProperty("package_name")
    private String packageName;

    @Schema(description = "앱/웹사이트 이름", example = "Example App")
    @JsonProperty("name")
    private String name;

    @Schema(description = "시스템 앱 여부 (0: 아니오, 1: 예)", example = "0")
    @JsonProperty("is_system_app")
    private Integer isSystemApp;

    @Schema(description = "업데이트된 시스템 앱 여부 (0: 아니오, 1: 예)", example = "0")
    @JsonProperty("is_updated_system_app")
    private Integer isUpdatedSystemApp;

    @Schema(description = "타입 (APP, WEBSITE 등)", example = "APP")
    @JsonProperty("type")
    private String type;

    /**
     * DTO를 Entity로 변환
     */
    public RawData toEntity() {
        return RawData.builder()
                .subjectId(subjectId)
                .timestamp(Instant.ofEpochMilli(timestamp))
                .utcoffset(utcoffset)
                .sourceType(sourceType)
                .sourceInfo(sourceInfo)
                .packageName(packageName)
                .name(name)
                .isSystemApp(isSystemApp)
                .isUpdatedSystemApp(isUpdatedSystemApp)
                .type(type)
                .build();
    }
}
