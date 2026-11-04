package com.sportify.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** NAPAS account transfer TLV format, verified against the official VietQR example. */
public final class VietQrPayload {
    private VietQrPayload() {}
    public static String encode(String bin,String account,BigDecimal amount,String reference) {
        if(bin==null || !bin.matches("[0-9]{6}") || account==null || !account.matches("[0-9]{6,19}")
                || reference==null || !reference.matches("[A-Za-z0-9 ]{1,25}") || amount==null
                || amount.signum()<=0 || amount.compareTo(new BigDecimal("9999999999999"))>0)
            throw new IllegalArgumentException("Invalid VietQR transfer fields");
        String value;
        try { value=amount.setScale(0,RoundingMode.UNNECESSARY).toPlainString(); }
        catch(ArithmeticException invalid) { throw new IllegalArgumentException("VND transfer amount must be a whole number",invalid); }
        String beneficiary=tlv("00",bin)+tlv("01",account);
        String merchant=tlv("00","A000000727")+tlv("01",beneficiary)+tlv("02","QRIBFTTA");
        String payload=tlv("00","01")+tlv("01","12")+tlv("38",merchant)+tlv("53","704")
                +tlv("54",value)+tlv("58","VN")+tlv("62",tlv("08",reference))+"6304";
        int crc=0xffff;
        for(byte b:payload.getBytes(StandardCharsets.US_ASCII)) {
            crc^=(b&0xff)<<8;
            for(int bit=0;bit<8;bit++) crc=(crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1;
            crc&=0xffff;
        }
        return payload+String.format(Locale.ROOT,"%04X",crc);
    }
    private static String tlv(String tag,String value) {
        if(value.length()>99) throw new IllegalArgumentException("VietQR field is too long");
        return tag+String.format(Locale.ROOT,"%02d",value.length())+value;
    }
}
