package com.barbershop.account;

import java.security.SecureRandom;

/**
 * Generated start passwords for barber logins: 16 characters from an alphabet without look-alikes
 * (0/O, 1/l/I), about 94 bits of entropy. Shown to the admin once; only the hash is stored.
 */
final class StartPasswords {

    static final int LENGTH = 16;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private StartPasswords() {}

    static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
