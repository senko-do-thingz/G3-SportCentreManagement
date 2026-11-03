package com.sportify.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only alias resolution. The database registry owns allocation and retention. */
@Entity
@Immutable
@Table(name = "session_code_history")
public class SessionCodeHistory {
    @Id
    @Column(name = "session_code", length = 10)
    private String sessionCode;
    @Column(name = "session_id")
    private Long sessionId;
}
