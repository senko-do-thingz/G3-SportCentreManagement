package com.sportify.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class VietQrPayloadTest {
    @Test void matchesOfficialVietQrGenerateExampleIncludingCrc() {
        assertThat(VietQrPayload.encode("970415","113366668888",new BigDecimal("79000"),"Ung Ho Quy Vac Xin"))
                .isEqualTo("00020101021238560010A0000007270126000697041501121133666688880208QRIBFTTA53037045405790005802VN62220818Ung Ho Quy Vac Xin63043ACF");
    }
    @Test void rejectsInvalidUnsupportedFormatAndFractionalVndAmounts() {
        for(BigDecimal amount:new BigDecimal[]{BigDecimal.ZERO,new BigDecimal("-1"),new BigDecimal("0.01"),new BigDecimal("10000000000000")})
            assertThatThrownBy(()->VietQrPayload.encode("970415","113366668888",amount,"SP1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VietQrPayload.encode("fake","113366668888",BigDecimal.ONE,"SP1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VietQrPayload.encode("970415","bad-account",BigDecimal.ONE,"SP1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VietQrPayload.encode("970415","113366668888",BigDecimal.ONE,"SCIN1.bad")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void directoryHonorsTransferFlagAndExpiresAfter24Hours() throws Exception {
        String snapshot="{\"capturedAt\":\"2026-11-04T00:00:00Z\",\"data\":[{\"bin\":\"970415\",\"code\":\"ICB\",\"shortName\":\"VietinBank\",\"transferSupported\":1},{\"bin\":\"970499\",\"code\":\"TEST\",\"shortName\":\"Unsupported fixture\",\"transferSupported\":0}]}";
        var resource=new ByteArrayResource(snapshot.getBytes(StandardCharsets.US_ASCII));
        var current=new VietQrBankDirectory(new ObjectMapper(),Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"),ZoneOffset.UTC),resource);
        assertThat(current.supported("970415")).isPresent(); assertThat(current.supported("970499")).isEmpty();
        assertThat(current.supported("999999")).isEmpty();
        var stale=new VietQrBankDirectory(new ObjectMapper(),Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC),resource);
        assertThat(stale.supported("970415")).isEmpty(); assertThat(stale.fresh()).isFalse();
    }
}
