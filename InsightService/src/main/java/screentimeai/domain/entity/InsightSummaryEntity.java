package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "insight_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsightSummaryEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Lob
    @Column(name = "summary", nullable = false)
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
