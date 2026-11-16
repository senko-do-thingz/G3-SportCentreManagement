package com.sportify.catalog.service;

import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberCheckInQrServiceTest {

    private final MemberProfileRepository profiles = mock(MemberProfileRepository.class);
    private final MemberCheckInQrService service = new MemberCheckInQrService(profiles);

    @Test
    void sameMemberAlwaysGetsTheIdenticalQr() {
        when(profiles.findById(7L)).thenReturn(Optional.of(MemberProfile.builder().id(7L).memberCode("MEM-0007").build()));

        var first = service.forMember(7L);
        var second = service.forMember(7L);

        assertThat(first).isEqualTo(second);
        assertThat(first.qrContent()).isEqualTo("MEM-0007").isEqualTo(first.memberCode());
        assertThat(MemberCheckInQrService.qrContent("MEM-0007")).isEqualTo(MemberCheckInQrService.qrContent("MEM-0007"));
    }

    @Test
    void differentMembersGetDifferentQr() {
        assertThat(MemberCheckInQrService.qrContent("MEM-0001")).isNotEqualTo(MemberCheckInQrService.qrContent("MEM-0002"));
    }

    @Test
    void unknownMemberIsReportedAsMemberNotFound() {
        when(profiles.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forMember(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Member not found");
    }
}
