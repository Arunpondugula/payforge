package com.payforge.account.util;

/**
 * Generates and validates PayForge account numbers.
 *
 * Format: PYFORGE + 9 random digits + 1 Luhn check digit (17 chars total).
 * The check digit lets any caller detect a mistyped or corrupted account
 * number structurally, without needing a database lookup — the same
 * principle used by real credit card numbers (Luhn algorithm), IMEI
 * numbers, and ISBNs.
 *
 * This class has exactly one responsibility: account-number format and
 * checksum logic. It knows nothing about the Account entity itself.
 */
public final class AccountNumberGenerator {

    private static final String PREFIX = "PYFORGE";
    private static final int BODY_LENGTH = 9; // random digits, before the check digit

    private AccountNumberGenerator() {
        // Utility class — never meant to be instantiated.
        // A private constructor makes that unrepresentable, not just documented.
    }

    /**
     * Generates a new, structurally valid account number:
     * PYFORGE + 9 random digits + 1 computed Luhn check digit.
     */
    public static String generate() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < BODY_LENGTH; i++) {
            body.append((int) (Math.random() * 10));
        }
        int checkDigit = calculateLuhnCheckDigit(body.toString());
        return PREFIX + body + checkDigit;
    }

    /**
     * Validates that an account number is structurally well-formed:
     * correct prefix, correct length, and a Luhn checksum that matches.
     * Callers should use this to reject a malformed/mistyped account
     * number BEFORE attempting any database lookup.
     */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null
                || !accountNumber.startsWith(PREFIX)
                || accountNumber.length() != PREFIX.length() + BODY_LENGTH + 1) {
            return false;
        }
        String digits = accountNumber.substring(PREFIX.length());
        if (!digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String body = digits.substring(0, BODY_LENGTH);
        int providedCheckDigit = Character.getNumericValue(digits.charAt(BODY_LENGTH));
        int expectedCheckDigit = calculateLuhnCheckDigit(body);
        return providedCheckDigit == expectedCheckDigit;
    }

    /**
     * Standard Luhn algorithm — the same check-digit scheme used by
     * credit card numbers. Given the digits BEFORE the check digit,
     * returns the single digit that makes the full number's Luhn sum
     * divisible by 10.
     */
    private static int calculateLuhnCheckDigit(String digitsWithoutCheckDigit) {
        int sum = 0;
        boolean doubleDigit = true; // rightmost of the given digits is doubled first
        for (int i = digitsWithoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digitsWithoutCheckDigit.charAt(i));
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static int accountNumberLength() {
        return PREFIX.length() + BODY_LENGTH + 1;
    }
}