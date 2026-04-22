package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_email_provider_preference", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id"})
})
public class UserEmailProviderPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "default_provider_id", nullable = false)
    private Long defaultProviderId;
}

