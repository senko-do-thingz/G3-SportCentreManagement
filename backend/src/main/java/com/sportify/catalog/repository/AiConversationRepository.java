package com.sportify.catalog.repository;

import com.sportify.catalog.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    @Query("SELECT c FROM AiConversation c WHERE c.user.id = :userId AND c.isDeleted = false AND c.isArchived = :isArchived ORDER BY c.updatedAt DESC")
    List<AiConversation> findByUserIdAndArchived(@Param("userId") Long userId, @Param("isArchived") boolean isArchived);

    @Query("SELECT c FROM AiConversation c WHERE c.user.id = :userId AND c.isDeleted = false ORDER BY c.updatedAt DESC")
    List<AiConversation> findAllActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM AiConversation c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.user.id = :userId AND c.isDeleted = false")
    Optional<AiConversation> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
