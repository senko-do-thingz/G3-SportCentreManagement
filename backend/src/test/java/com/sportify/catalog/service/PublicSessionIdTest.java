package com.sportify.catalog.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PublicSessionIdTest {
    @ParameterizedTest @ValueSource(strings={"031126FB01","290200TE99","010100SW01","311299BK01"})
    void acceptsCanonicalCalendarIdentities(String code) {assertThat(new PublicSessionId(code).value()).isEqualTo(code);}
    @ParameterizedTest @ValueSource(strings={"31","031126fb01","310226FB01","290201TE01","031126XX01","031126BM00","031126BM100","031126BM01 ","000000FB01"})
    void rejectsSurrogatesMalformedDatesAndUnsupportedCodes(String code) {
        assertThatThrownBy(()->new PublicSessionId(code)).isInstanceOf(com.sportify.core.exception.BusinessRuleException.class);
    }
}
