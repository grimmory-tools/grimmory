package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "email_provider")
public class EmailProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "auth", nullable = false)
    private boolean auth;

    @Column(name = "start_tls", nullable = false)
    private boolean startTls;

    @Column(name = "is_default", nullable = false)
    private boolean defaultProvider;
}
