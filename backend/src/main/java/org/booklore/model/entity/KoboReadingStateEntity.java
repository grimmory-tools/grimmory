package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "kobo_reading_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KoboReadingStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "entitlement_id", nullable = false)
    private String entitlementId;

    @Column(name = "created")
    private String created;

    @UpdateTimestamp
    @Column(name = "last_modified")
    private String lastModified;

    @Column(name = "priority_timestamp")
    private String priorityTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_bookmark_json")
    private String currentBookmarkJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "statistics_json")
    private String statisticsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_info_json")
    private String statusInfoJson;

    @Column(name = "last_modified_string")
    private String lastModifiedString;
}
