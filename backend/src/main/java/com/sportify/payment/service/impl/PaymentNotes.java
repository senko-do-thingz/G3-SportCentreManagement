package com.sportify.payment.service.impl;

/**
 * Appends notes to payment.notes (NVARCHAR(500)). Segments are separated by " | "; when the column would
 * overflow, the oldest segments are dropped first so the newest note is always kept intact.
 */
final class PaymentNotes {

    private PaymentNotes() {
    }

    static String append(String existing, String append) {
        if (append == null || append.isBlank()) {
            return truncateSingleNote(existing);
        }
        if (existing == null || existing.isBlank()) {
            return truncateSingleNote(append);
        }

        // If append alone exceeds 500 characters, cut append to 500 safely and drop existing.
        if (append.length() > 500) {
            return truncateSingleNote(append);
        }

        // Here append is <= 500 characters and must be preserved intact.
        String candidate = existing + " | " + append;
        if (candidate.length() <= 500) {
            return candidate;
        }

        // Drop oldest segments separated by " | " until candidate length fits or no more separators.
        String curExisting = existing;
        while (curExisting.contains(" | ") && (curExisting.length() + 3 + append.length() > 500)) {
            int firstSep = curExisting.indexOf(" | ");
            curExisting = curExisting.substring(firstSep + 3);
        }

        if (curExisting.length() + 3 + append.length() <= 500) {
            return curExisting + " | " + append;
        }

        // If even the remaining single segment is too long to fit with " | " + append:
        int remainingBudget = 500 - 3 - append.length();
        if (remainingBudget <= 0) {
            return append;
        }

        // Cut oldest characters from curExisting (keep tail) to fit remainingBudget
        int start = curExisting.length() - remainingBudget;
        if (Character.isLowSurrogate(curExisting.charAt(start))) {
            start++;
        }
        String trimmedExisting = curExisting.substring(start);
        if (trimmedExisting.isEmpty()) {
            return append;
        }
        return trimmedExisting + " | " + append;
    }

    static String truncateSingleNote(String note) {
        if (note == null) {
            return null;
        }
        if (note.length() <= 500) {
            return note;
        }
        int cutoff = 500;
        if (Character.isHighSurrogate(note.charAt(cutoff - 1))) {
            cutoff--;
        }
        return note.substring(0, cutoff);
    }
}
