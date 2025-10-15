package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "raw_data")
@IdClass(RawDataId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawData {

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Id
    @Column(name = "timestamp", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant timestamp;

    @Column(name = "utcoffset")
    private Integer utcoffset;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_info")
    private String sourceInfo;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "name")
    private String name;

    @Column(name = "is_system_app")
    private Integer isSystemApp;

    @Column(name = "is_updated_system_app")
    private Integer isUpdatedSystemApp;

    @Column(name = "type", nullable = false)
    private String type;  // 이벤트 타입 (USER_INTERACTION, MOVE_TO_FOREGROUND 등)
}

