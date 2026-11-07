package com.sportify.catalog.service;

import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationalMemberPolicyTest {
    @Test void reservedIdsRemainQuarantinedWithSimulationDisabled() {
        var jdbc=mock(JdbcTemplate.class);
        var policy=new OperationalMemberPolicy(jdbc,new MockEnvironment()
                .withProperty("app.payment.unified-demo.enabled","false")
                .withProperty("app.payment.unified-demo.member-ids","7, 9"));
        assertThatThrownBy(() -> policy.requireRealMember(9)).isInstanceOf(BusinessRuleException.class);
        assertThat(policy.isSyntheticMember(9)).isTrue();
    }

    @Test void persistedProvenanceDeniesRenamedMembersWithoutAnyDemoProfile() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(7L),eq(7L),eq(7L))).thenReturn(1);
        var policy=new OperationalMemberPolicy(jdbc,new MockEnvironment());
        assertThatThrownBy(() -> policy.requireRealMember(7))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(policy.isSyntheticMember(7)).isTrue();
    }

    @Test void ordinaryMemberWithoutSyntheticProvenanceRemainsEligible() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(7L),eq(7L),eq(7L))).thenReturn(0);
        var policy=new OperationalMemberPolicy(jdbc,new MockEnvironment());
        assertThatCode(() -> policy.requireRealMember(7))
                .doesNotThrowAnyException();
        assertThat(policy.isSyntheticMember(7)).isFalse();
    }
}
