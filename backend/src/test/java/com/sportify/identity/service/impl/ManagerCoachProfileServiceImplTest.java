package com.sportify.identity.service.impl;

import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ManagerCoachProfileResponse;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.UserAvatarStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerCoachProfileServiceImplTest {

    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private UserAvatarStore avatarStore;
    @Mock
    private AuditService auditService;
    @Mock
    private Clock clock;
    @InjectMocks
    private ManagerCoachProfileServiceImpl service;

    private final UserAccount actor = UserAccount.builder().id(1L).email("manager@sportify.com").build();

    private static CoachProfile coach() {
        UserAccount account = UserAccount.builder().id(7L).fullName("Nguyen Minh Khoa").role(Role.builder().code("COACH").build()).avatarUrl(null).build();
        return CoachProfile.builder().id(7L).userAccount(account).headline("Old").yearsExperience(3)
                .isPublic(true).displayOrder(0).build();
    }

    @Test
    void getProfile_mapsAccountAndProfileFields() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));

        ManagerCoachProfileResponse response = service.getProfile(7L);

        assertEquals(new ManagerCoachProfileResponse(7L, "Nguyen Minh Khoa", null, "Old", null, 3, null, true, 0), response);
    }

    @Test
    void updateProfile_trimsTextStoresPhotoOnAccountAndLogsChangedFields() {
        CoachProfile profile = coach();
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        when(coachProfileRepository.save(profile)).thenReturn(profile);

        ManagerCoachProfileResponse response = service.updateProfile(7L, new ManagerCoachProfileUpdateRequest(
                " https://images.example.test/khoa.jpg ", "  Badminton coach  ", "  ", 7, " Beginners, Doubles ", false, 2), actor);

        assertEquals("https://images.example.test/khoa.jpg", profile.getUserAccount().getAvatarUrl());
        assertEquals("Badminton coach", response.headline());
        assertNull(response.bio());
        assertEquals("Beginners, Doubles", response.specialties());
        assertFalse(response.isPublic());
        assertEquals(2, response.displayOrder());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(AuditAction.USER_UPDATED, event.getValue().getAction());
        assertEquals(7L, event.getValue().getEntityId());
        assertEquals(actor, event.getValue().getActor());
        assertEquals(List.of("avatarUrl", "headline", "yearsExperience", "specialties", "isPublic", "displayOrder"),
                event.getValue().getDetails().get("fields"));
    }

    @Test
    void updateProfile_withoutChanges_writesNothing() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));

        service.updateProfile(7L, new ManagerCoachProfileUpdateRequest("", "Old", null, 3, null, true, 0), actor);

        verify(coachProfileRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void unknownCoach_throwsNotFound() {
        when(coachProfileRepository.findWithAccountById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProfile(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.updateProfile(9L,
                new ManagerCoachProfileUpdateRequest(null, null, null, null, null, true, 0), actor));
        verifyNoInteractions(auditService);
    }

    private static final byte[] JPEG = CoachPhotoValidatorTest.image("jpeg", 2, 2);

    @Test
    void uploadPhoto_storesImageVersionsLinkAndLogs() {
        CoachProfile profile = coach();
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        when(coachProfileRepository.save(profile)).thenReturn(profile);
        when(clock.millis()).thenReturn(1761200000000L);

        ManagerCoachProfileResponse response = service.uploadPhoto(7L, JPEG, actor);

        verify(avatarStore).save(7L, "image/jpeg", JPEG);
        assertEquals("/api/v1/users/7/avatar?v=1761200000000", response.avatarUrl());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals("Coach photo uploaded", event.getValue().getSummary());
        assertEquals("image/jpeg", event.getValue().getDetails().get("contentType"));
    }

    @Test
    void uploadPhoto_rejectsEmptyOversizedAndNonImageFilesWithoutWrites() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));
        byte[] oversized = new byte[ManagerCoachProfileServiceImpl.MAX_PHOTO_BYTES + 1];
        System.arraycopy(JPEG, 0, oversized, 0, JPEG.length);

        assertEquals("Choose a photo to upload", assertThrows(BusinessRuleException.class, () -> service.uploadPhoto(7L, new byte[0], actor)).getMessage());
        assertEquals("Photo must be at most 2 MB", assertThrows(BusinessRuleException.class, () -> service.uploadPhoto(7L, oversized, actor)).getMessage());
        assertEquals("Photo must be a valid JPEG, PNG or WebP image", assertThrows(BusinessRuleException.class, () -> service.uploadPhoto(7L, "text".getBytes(), actor)).getMessage());
        verifyNoInteractions(avatarStore, auditService);
    }

    @Test
    void updateProfile_removingAnUploadedPhotoDeletesTheStoredImage() {
        CoachProfile profile = coach();
        profile.getUserAccount().setAvatarUrl("/api/v1/users/7/avatar?v=1");
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        when(coachProfileRepository.save(profile)).thenReturn(profile);

        service.updateProfile(7L, new ManagerCoachProfileUpdateRequest("", "Old", null, 3, null, true, 0), actor);

        verify(avatarStore).delete(7L);
        assertNull(profile.getUserAccount().getAvatarUrl());
    }

    @Test
    void updateProfile_keepingTheUploadedPhotoDoesNotDeleteIt() {
        CoachProfile profile = coach();
        profile.getUserAccount().setAvatarUrl("/api/v1/users/7/avatar?v=1");
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        when(coachProfileRepository.save(profile)).thenReturn(profile);

        service.updateProfile(7L, new ManagerCoachProfileUpdateRequest("/api/v1/users/7/avatar?v=1", "New", null, 3, null, true, 0), actor);

        verify(avatarStore, never()).delete(any());
    }

    @Test
    void updateProfile_rejectsAnotherAccountsUploadedPhotoWithoutWrites() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));

        FieldValidationException error = assertThrows(FieldValidationException.class, () -> service.updateProfile(7L,
                new ManagerCoachProfileUpdateRequest("/api/v1/users/8/avatar?v=1", "Old", null, 3, null, true, 0), actor));

        assertEquals("Use the current photo uploaded for this coach", error.getDetails().get("avatarUrl"));
        verify(coachProfileRepository, never()).save(any());
        verifyNoInteractions(avatarStore, auditService);
    }
    @Test
    void nonCoachAccountWithLingeringProfileIsNotEditable() {
        CoachProfile profile = coach();
        profile.getUserAccount().setRole(Role.builder().code("MEMBER").build());
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        assertThrows(ResourceNotFoundException.class, () -> service.getProfile(7L));
        assertThrows(ResourceNotFoundException.class, () -> service.uploadPhoto(7L, JPEG, actor));
        assertThrows(ResourceNotFoundException.class, () -> service.updateProfile(7L,
                new ManagerCoachProfileUpdateRequest(null, null, null, null, null, true, 0), actor));
        verifyNoInteractions(avatarStore, auditService);
    }

    @Test
    void fabricatedStaleAndPrefixOnlyUploadedLinksAreRejectedWithoutWrites() {
        CoachProfile profile = coach();
        profile.getUserAccount().setAvatarUrl("/api/v1/users/7/avatar?v=2");
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(profile));
        for (String url : List.of("/api/v1/users/7/avatar?v=1", "/api/v1/users/7/avatar-extra", "/api/v1/users/7/avatar?v=999")) {
            assertThrows(FieldValidationException.class, () -> service.updateProfile(7L,
                    new ManagerCoachProfileUpdateRequest(url, "Old", null, 3, null, true, 0), actor));
        }
        verify(coachProfileRepository, never()).save(any());
        verifyNoInteractions(avatarStore, auditService);
    }

    @Test
    void truncatedImageIsRejectedWithoutPersistenceOrAudit() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));
        assertThrows(BusinessRuleException.class, () -> service.uploadPhoto(7L,
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}, actor));
        verify(coachProfileRepository, never()).save(any());
        verifyNoInteractions(avatarStore, auditService);
    }

    @Test
    void malformedAndCredentialedNewPublicLinksAreRejectedWithoutWrites() {
        when(coachProfileRepository.findWithAccountById(7L)).thenReturn(Optional.of(coach()));
        for (String url : List.of("https:///missing-host.png", "https://private:password@example.test/photo.png", "http://example.test:99999/photo.png")) {
            assertThrows(FieldValidationException.class, () -> service.updateProfile(7L,
                    new ManagerCoachProfileUpdateRequest(url, "Old", null, 3, null, true, 0), actor));
        }
        verify(coachProfileRepository, never()).save(any());
        verifyNoInteractions(avatarStore, auditService);
    }
}
