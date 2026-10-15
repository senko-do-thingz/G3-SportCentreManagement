package com.sportify.identity.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%&*?";
    private static final String ALL = LOWER + UPPER + DIGITS + SPECIAL;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] password = new char[20];
        password[0] = pick(LOWER);
        password[1] = pick(UPPER);
        password[2] = pick(DIGITS);
        password[3] = pick(SPECIAL);
        for (int i = 4; i < password.length; i++) {
            password[i] = pick(ALL);
        }
        for (int i = password.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char value = password[i];
            password[i] = password[j];
            password[j] = value;
        }
        return new String(password);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
