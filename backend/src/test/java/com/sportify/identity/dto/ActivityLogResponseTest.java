package com.sportify.identity.dto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
class ActivityLogResponseTest {
    @Test void unicodeAndNullableTarget() {
        String name="Nguy\u1ec5n \ud83c\udfbe";
        var r=new ActivityLogResponse(91L,null,17L,name,"COACH","PASSWORD_CHANGED",null,null);
        assertThat(r.actorName()).isEqualTo(name); assertThat(r.actorRole()).isEqualTo("COACH");
        assertThat(r.entityType()).isNull(); assertThat(r.entityId()).isNull();
        assertThat(r.shortDetail()).isEqualTo("Password changed");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" "})
    void missingActor(String name) {
        var r=new ActivityLogResponse(1L,null,null,name,null,"LOGIN","USER_ACCOUNT",null);
        assertThat(r.actorName()).isEqualTo("System / unavailable actor"); assertThat(r.actorRole()).isNull();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"OLD_EVENT","Legacy sensitive-value",""})
    void unknownHistoricalAction(String action) {
        var r=new ActivityLogResponse(1L,null,null,null,null,action,"LEGACY",11L);
        assertThat(r.action()).isEqualTo(action); assertThat(r.shortDetail()).isEqualTo("Activity recorded");
    }
    @Test void boundedDetailAndExplicitProjection() {
        for(String action:ActivityLogQuery.ACTIONS) assertThat(new ActivityLogResponse(1L,null,null,null,null,action,"TEST",4L).shortDetail())
                .isNotBlank().hasSizeLessThanOrEqualTo(60);
        assertThat(java.util.Arrays.stream(ActivityLogResponse.class.getRecordComponents()).map(c->c.getName()))
                .containsExactly("id","createdAt","actorId","actorName","actorRole","action","entityType","entityId","shortDetail");
    }
}
