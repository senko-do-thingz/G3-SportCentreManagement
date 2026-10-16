package com.sportify.identity.service.impl;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.identity.dto.*;
import com.sportify.identity.repository.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ActivityLogServiceImplTest {
    @Mock ActivityLogRepository logs;
    @InjectMocks ActivityLogServiceImpl service;
    @Test void pageAndProjection() {
        var query=ActivityLogQuery.parse(17L,"PASSWORD_CHANGED","2026-10-01","2026-10-16","Ada",2,5);
        var row=new ActivityLogResponse(91L,null,17L,"Ada","MEMBER","PASSWORD_CHANGED","USER_ACCOUNT",17L);
        when(logs.search(query)).thenReturn(new PageImpl<>(List.of(row),PageRequest.of(2,5),11));
        var r=service.list(17L,"PASSWORD_CHANGED","2026-10-01","2026-10-16","Ada",2,5);
        assertThat(r.content()).containsExactly(row); assertThat(r.page()).isEqualTo(2); assertThat(r.size()).isEqualTo(5);
        assertThat(r.totalElements()).isEqualTo(11); assertThat(r.totalPages()).isEqualTo(3);
    }
    @Test void unknownActorEmpty() {
        when(logs.search(ActivityLogQuery.parse(Long.MAX_VALUE,null,null,null,null,0,20))).thenReturn(Page.empty(PageRequest.of(0,20)));
        var r=service.list(Long.MAX_VALUE,null,null,null,null,0,20);
        assertThat(r.content()).isEmpty(); assertThat(r.totalElements()).isZero(); assertThat(r.totalPages()).isZero();
    }
    @Test void invalidQueryNeverReads() {
        assertThatThrownBy(()->service.list(null,null,"invalid",null,null,0,20)).isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(logs);
    }
}
