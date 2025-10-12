package screentimeai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import screentimeai.domain.RawData;

import java.time.Instant;
import java.util.UUID;

@Data
public class RawDataRequest {

    @JsonProperty("subject_id")
    private UUID subjectId;

    @JsonProperty("timestamp")
    private Long timestamp;  // 밀리초 epoch timestamp

    @JsonProperty("utcoffset")
    private Integer utcoffset;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("source_info")
    private String sourceInfo;

    @JsonProperty("package_name")
    private String packageName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("is_system_app")
    private Integer isSystemApp;

    @JsonProperty("is_updated_system_app")
    private Integer isUpdatedSystemApp;

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
