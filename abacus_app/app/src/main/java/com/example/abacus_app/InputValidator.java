package com.example.abacus_app;

import android.util.Patterns;

import java.util.regex.Pattern;

/**
 * Centralised input validation and sanitization utility.
 *
 * <p>All user-generated content (profile fields, search queries) should be
 * validated through this class before storage or display, satisfying
 * OWASP M1 — Improper Input Validation.
 *
 * <p>This class is stateless — all methods are static and thread-safe.
 *
 * <p>Ref: US 01.02.01 (profile field validation)
 */
public final class InputValidator {

    /** Matches SQL injection metacharacters: semicolons, quotes, comment markers. */
    private static final Pattern SQL_INJECTION_PATTERN =
            Pattern.compile("[;'\"\\-\\-]|(/\\*)|\\*/");

    /** Matches HTML/XML tags to prevent XSS in displayed content. */
    private static final Pattern XSS_PATTERN =
            Pattern.compile("<[^>]*>", Pattern.CASE_INSENSITIVE);

    /** Maximum allowed length for a search query string. */
    private static final int MAX_SEARCH_QUERY_LENGTH = 100;

    /** Maximum allowed length for a comment. */
    private static final int MAX_COMMENT_LENGTH = 500;

    private InputValidator() { /* utility class — no instances */ }

    // ── Result type ──────────────────────────────────────────────────────────

    /**
     * Holds the outcome of a validation check.
     * {@link #isValid} is {@code true} when the input passes; {@link #errorMessage}
     * is non-null only when {@link #isValid} is {@code false}.
     */
    public static final class ValidationResult {
        /** {@code true} if the input passed all validation rules. */
        public final boolean isValid;
        /** Human-readable error message, or {@code null} when valid. */
        public final String  errorMessage;

        private ValidationResult(boolean isValid, String errorMessage) {
            this.isValid       = isValid;
            this.errorMessage  = errorMessage;
        }

        static ValidationResult ok()                  { return new ValidationResult(true, null); }
        static ValidationResult error(String message) { return new ValidationResult(false, message); }
    }

    // ── Name ─────────────────────────────────────────────────────────────────

    /**
     * Validates a display name.
     *
     * <p>Rules: must not be blank after trimming.
     *
     * @param name the display name to validate
     * @return {@link ValidationResult} indicating pass or fail with a message
     */
    public static ValidationResult validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Name cannot be empty");
        }
        return ValidationResult.ok();
    }

    // ── Email ────────────────────────────────────────────────────────────────

    /**
     * Validates an email address.
     *
     * <p>Rules: empty email is allowed (some users have no email); non-empty
     * values must match {@link Patterns#EMAIL_ADDRESS}.
     *
     * @param email the email address to validate, or empty string
     * @return {@link ValidationResult} indicating pass or fail with a message
     */
    public static ValidationResult validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.ok(); // optional field
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return ValidationResult.error("Please enter a valid email address");
        }
        return ValidationResult.ok();
    }

    // ── Comment ──────────────────────────────────────────────────────────────

    /**
     * Validates a comment body.
     *
     * <p>Rules: must not be blank; must not exceed {@value #MAX_COMMENT_LENGTH}
     * characters; must not contain HTML tags (XSS prevention).
     *
     * @param text the comment text to validate
     * @return {@link ValidationResult} indicating pass or fail with a message
     */
    public static ValidationResult validateComment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return ValidationResult.error("Comment cannot be empty");
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            return ValidationResult.error("Comment must be " + MAX_COMMENT_LENGTH + " characters or fewer");
        }
        if (XSS_PATTERN.matcher(text).find()) {
            return ValidationResult.error("Comment contains invalid characters");
        }
        return ValidationResult.ok();
    }

    // ── Search query ─────────────────────────────────────────────────────────

    /**
     * Sanitizes a free-text search query by stripping SQL injection
     * metacharacters and HTML tags, then truncating to
     * {@value #MAX_SEARCH_QUERY_LENGTH} characters.
     *
     * @param query the raw search string from the user
     * @return a safe, trimmed query string (never null)
     */
    public static String sanitizeSearchQuery(String query) {
        if (query == null) return "";
        String sanitized = SQL_INJECTION_PATTERN.matcher(query).replaceAll("");
        sanitized = XSS_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitized.trim();
        if (sanitized.length() > MAX_SEARCH_QUERY_LENGTH) {
            sanitized = sanitized.substring(0, MAX_SEARCH_QUERY_LENGTH);
        }
        return sanitized;
    }
}
