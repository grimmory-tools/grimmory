package org.booklore.model.entity;

import org.booklore.convertor.JpaJsonConverter;
import org.booklore.task.TaskStatus;
import org.booklore.model.enums.TaskType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.LazyGroup;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistoryEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(length = 512)
    private String message;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("lob")
    private String errorDetails;

    @Convert(converter = JpaJsonConverter.class)
    @Column(name = "task_options", columnDefinition = "TEXT")
    private Map<String, Object> taskOptions;
}
