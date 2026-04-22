package org.booklore.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "oidc_session")
public class OidcSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "oidc_subject", nullable = false)
    private String oidcSubject;

    @Column(name = "oidc_issuer", nullable = false, length = 512)
    private String oidcIssuer;

    @Column(name = "oidc_session_id")
    private String oidcSessionId;

    @Column(name = "id_token_hint", columnDefinition = "TEXT")
    private String idTokenHint;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
