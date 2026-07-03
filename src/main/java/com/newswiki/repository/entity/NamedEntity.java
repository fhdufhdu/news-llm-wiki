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
@Table(name = "entities")
public class NamedEntity extends WikiEntity {
    @Column(nullable = false)
    private String name;

    @Column(name = "entity_type", nullable = false)
    private String entityType;
}
