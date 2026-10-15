# 11 - JPA Mapping Guide

This guide outlines the conventions for mapping the SQL Server schema to Spring Data JPA (Hibernate 6) in Java 17+.

## Maven Dependencies

```xml
<dependencies>
    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <!-- Microsoft SQL Server JDBC Driver -->
    <dependency>
        <groupId>com.microsoft.sqlserver</groupId>
        <artifactId>mssql-jdbc</artifactId>
        <scope>runtime</scope>
    </dependency>
    <!-- Flyway for Migrations -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-sqlserver</artifactId>
    </dependency>
</dependencies>
```

## application.yml

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=false;sendStringParametersAsUnicode=true
    username: sa
    password: YourPassword
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
  jpa:
    open-in-view: false # Crucial for performance
    hibernate:
      ddl-auto: validate # Flyway handles schema
    properties:
      hibernate:
        format_sql: true
        # Tell Hibernate to treat String as NVARCHAR
        use_nationalized_character_data: true
        jdbc:
          time_zone: Asia/Ho_Chi_Minh
```

## Entity Mapping Conventions

### Base Auditable Entity

Use `@MappedSuperclass` for common audit fields.

```java
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and setters
}
```

### Shared Primary Key (`@MapsId`)

For 1:1 relationships like `MemberProfile` sharing `UserAccount`'s ID.

```java
@Entity
@Table(name = "member_profile")
public class MemberProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserAccount userAccount;

    @Column(name = "member_code", unique = true, nullable = false)
    private String memberCode;
    
    // ...
}
```

### Enums

SQL Server uses `NVARCHAR` for enums. In Hibernate 6, `@Enumerated(EnumType.STRING)` is sufficient, provided `use_nationalized_character_data` is true.

```java
public enum MembershipStatus {
    PENDING_PAYMENT, SCHEDULED, ACTIVE, EXPIRED, CANCELLED
}

@Entity
@Table(name = "membership")
public class Membership extends AuditableEntity {
    // ...
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;
}
```

With Java 17+, you can use enhanced `switch` expressions on these enums in the service layer.

### Sequences for Business Codes

To generate codes like `MEM-0001`, you can use native SQL queries in the repository or a custom generator. A simple repository method:

```java
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    
    @Query(value = "SELECT NEXT VALUE FOR seq_member_code", nativeQuery = true)
    Long getNextMemberCodeSequence();
    
    // Default method to format
    default String generateNextMemberCode() {
        return String.format("MEM-%04d", getNextMemberCodeSequence());
    }
}
```

### Atomic Updates (`@Modifying`)

For concurrent operations like booking a seat (see 09-business-rules):

```java
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    
    @Modifying
    @Query("UPDATE ClassSession cs SET cs.bookedCount = cs.bookedCount + 1 WHERE cs.id = :id AND cs.bookedCount < cs.capacity")
    int tryReserveSeat(@Param("id") Long id);
}
```

### Projections (Java 17 Records)

Use Java 17 Records for read-only DTOs, avoiding entity overhead.

```java
public record SessionSummaryDto(
    Long id,
    String className,
    LocalDate sessionDate,
    LocalTime startTime,
    int seatsLeft
) {}

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {
    
    @Query("""
        SELECT new com.example.dto.SessionSummaryDto(
            cs.id, sc.name, cs.sessionDate, cs.startTime, (cs.capacity - cs.bookedCount)
        )
        FROM ClassSession cs
        JOIN cs.sportClass sc
        WHERE cs.status = 'PUBLISHED'
    """)
    List<SessionSummaryDto> findAvailableSessions();
}
```
*(Notice the use of Java Text Blocks `"""` for JPQL readability).*

## Common Pitfalls

1. **LAZY vs EAGER:** Always default `@ManyToOne` and `@OneToOne` to `fetch = FetchType.LAZY`. Hibernate defaults these to `EAGER`, causing N+1 query problems.
2. **N+1 Queries:** Use `@EntityGraph` to fetch related entities in a single query when needed.
3. **Optimistic Locking:** Ensure entities with a `version` column include `@Version`. When a `ObjectOptimisticLockingFailureException` occurs, map it to an HTTP 409 Conflict.
4. **Filtered Unique Indexes:** If you create an index like `CREATE UNIQUE INDEX ux_active_booking ON booking (session_id, member_id) WHERE status = 'CONFIRMED'`, Hibernate schema validation might not recognize the `WHERE` clause. Use `ddl-auto: validate` with caution if it complains, or configure Flyway properly. Ensure `QUOTED_IDENTIFIER ON` is set when creating these indexes (the JDBC driver usually handles this).
