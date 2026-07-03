package com.newswiki.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "claims")
public class ClaimEntity extends WikiEntity {
    @Column(nullable = false)
    private String title;

    @Column(name = "claim_type", nullable = false)
    private String claimType;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;
}
