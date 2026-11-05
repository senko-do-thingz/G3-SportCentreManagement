package com.sportify.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Component
public class VietQrBankDirectory {
    public record Bank(String bin,String code,String name,boolean transferSupported) {}
    private final List<Bank> banks;
    private final Instant capturedAt;
    private final Clock clock;
    public VietQrBankDirectory(ObjectMapper json,Clock clock,
            @Value("${app.payment.bank-transfer.bank-directory:classpath:payment/vietqr-banks.json}") Resource resource) throws IOException {
        this.clock=clock;
        try(var input=resource.getInputStream()) {
            var root=json.readTree(input); capturedAt=Instant.parse(root.path("capturedAt").asText());
            var result=new ArrayList<Bank>();
            root.path("data").forEach(bank->{
                String bin=bank.path("bin").asText();
                if(bin.matches("[0-9]{6}")) result.add(new Bank(bin,bank.path("code").asText(),bank.path("shortName").asText(),bank.path("transferSupported").asInt()==1));
            });
            banks=List.copyOf(result);
        }
    }
    public boolean fresh() { return !clock.instant().isBefore(capturedAt) && clock.instant().isBefore(capturedAt.plus(Duration.ofHours(24))); }
    public Optional<Bank> supported(String bin) { return fresh()?banks.stream().filter(b->b.bin().equals(bin)&&b.transferSupported()).findFirst():Optional.empty(); }
    public List<Bank> banks() { return banks; }
    public Instant capturedAt() { return capturedAt; }
}
