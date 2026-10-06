package com.sportify.identity.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAccountIsNewTest {

    private JpaMetamodelEntityInformation<UserAccount, Long> entityInformation;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        Metamodel metamodel = mock(Metamodel.class);
        EntityType<UserAccount> entityType = mock(EntityType.class);
        SingularAttribute idAttr = mock(SingularAttribute.class);
        SingularAttribute versionAttr = mock(SingularAttribute.class);
        Type idType = mock(Type.class);
        PersistenceUnitUtil persistenceUnitUtil = mock(PersistenceUnitUtil.class);

        when(metamodel.managedType(UserAccount.class)).thenReturn(entityType);
        when(entityType.getName()).thenReturn("UserAccount");
        when(entityType.hasSingleIdAttribute()).thenReturn(true);
        when(idType.getJavaType()).thenReturn(Long.class);
        when(entityType.getIdType()).thenReturn(idType);
        when(entityType.getId(any())).thenReturn(idAttr);
        when(idAttr.getName()).thenReturn("id");
        when(idAttr.getJavaType()).thenReturn(Long.class);

        when(entityType.getVersion(any())).thenReturn(versionAttr);
        when(versionAttr.getName()).thenReturn("version");
        when(versionAttr.getJavaType()).thenReturn(Integer.class);

        entityInformation = new JpaMetamodelEntityInformation<>(UserAccount.class, metamodel, persistenceUnitUtil);
    }

    @Test
    void builderDefaultVersionZeroCausesIsNewToBeFalse() {
        UserAccount user = UserAccount.builder()
                .email("test@sportify.com")
                .fullName("Test User")
                .build();

        // UserAccount has @Builder.Default private Integer version = 0
        // Because version is non-null (0), Spring Data JpaMetamodelEntityInformation.isNew returns false
        assertFalse(entityInformation.isNew(user),
                "UserAccount built with default version 0 must be treated as not new (isNew == false)");
    }

    @Test
    void nullVersionCausesIsNewToBeTrue() {
        UserAccount user = UserAccount.builder()
                .email("test@sportify.com")
                .fullName("Test User")
                .version(null)
                .build();

        // When version is null, Spring Data JpaMetamodelEntityInformation.isNew returns true
        assertTrue(entityInformation.isNew(user),
                "UserAccount with version == null must be treated as new (isNew == true)");
    }

    @Test
    void simpleJpaRepositoryDelegatesToMergeWhenVersionIsZero() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        SimpleJpaRepository<UserAccount, Long> repository = new SimpleJpaRepository<>(entityInformation, em);

        UserAccount user = UserAccount.builder()
                .email("test@sportify.com")
                .fullName("Test User")
                .build();

        when(em.merge(user)).thenReturn(user);

        repository.save(user);

        // Because isNew is false, SimpleJpaRepository calls merge instead of persist
        verify(em).merge(user);
        verify(em, never()).persist(any());
    }

    @Test
    void simpleJpaRepositoryDelegatesToPersistWhenVersionIsNull() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(em.getEntityManagerFactory()).thenReturn(emf);

        SimpleJpaRepository<UserAccount, Long> repository = new SimpleJpaRepository<>(entityInformation, em);

        UserAccount user = UserAccount.builder()
                .email("test@sportify.com")
                .fullName("Test User")
                .version(null)
                .build();

        repository.save(user);

        // Because isNew is true, SimpleJpaRepository calls persist
        verify(em).persist(user);
        verify(em, never()).merge(any());
    }
}
