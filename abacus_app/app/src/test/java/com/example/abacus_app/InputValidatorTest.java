package com.example.abacus_app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link InputValidator}.
 *
 * <p>Covers:
 * <ul>
 *   <li>US 01.02.01 — Name validation: blank and whitespace-only values are rejected.</li>
 *   <li>US 01.02.01 — Email validation: valid addresses pass; malformed addresses fail;
 *       empty email is allowed (optional field).</li>
 *   <li>OWASP M1 — Improper Input Validation: search query sanitization strips SQL
 *       injection metacharacters and HTML tags.</li>
 * </ul>
 *
 * <p>Requires Robolectric because {@link android.util.Patterns#EMAIL_ADDRESS} is an
 * Android SDK field unavailable in a plain JVM environment.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class InputValidatorTest {

    // ── validateName — US 01.02.01 ────────────────────────────────────────────

    /** Empty string is not a valid name. */
    @Test
    public void validateName_emptyString_isInvalid() {
        InputValidator.ValidationResult result = InputValidator.validateName("");
        assertFalse(result.isValid);
        assertNotNull(result.errorMessage);
    }

    /** Whitespace-only strings must be treated the same as empty. */
    @Test
    public void validateName_whitespaceOnly_isInvalid() {
        InputValidator.ValidationResult result = InputValidator.validateName("   ");
        assertFalse(result.isValid);
    }

    /** Null input must be rejected without throwing. */
    @Test
    public void validateName_null_isInvalid() {
        InputValidator.ValidationResult result = InputValidator.validateName(null);
        assertFalse(result.isValid);
    }

    /** A normal non-blank name is valid. */
    @Test
    public void validateName_nonBlankName_isValid() {
        InputValidator.ValidationResult result = InputValidator.validateName("Alice Smith");
        assertTrue(result.isValid);
        assertNull(result.errorMessage);
    }

    /** A single character is enough to pass name validation. */
    @Test
    public void validateName_singleChar_isValid() {
        assertTrue(InputValidator.validateName("A").isValid);
    }

    // ── validateEmail — US 01.02.01 ───────────────────────────────────────────

    /** A well-formed email address must pass. */
    @Test
    public void validateEmail_validEmail_isValid() {
        assertTrue(InputValidator.validateEmail("alice@ualberta.ca").isValid);
    }

    /** An email missing the @ symbol must fail. */
    @Test
    public void validateEmail_missingAt_isInvalid() {
        InputValidator.ValidationResult result = InputValidator.validateEmail("aliceualberta.ca");
        assertFalse(result.isValid);
        assertNotNull(result.errorMessage);
    }

    /** An email missing a domain suffix must fail. */
    @Test
    public void validateEmail_missingDomain_isInvalid() {
        assertFalse(InputValidator.validateEmail("alice@").isValid);
    }

    /**
     * Empty email is valid — the field is optional (users without email can still
     * register with a phone number or device ID).
     */
    @Test
    public void validateEmail_emptyString_isValid() {
        assertTrue(InputValidator.validateEmail("").isValid);
    }

    /** Null email is treated the same as empty and must be valid. */
    @Test
    public void validateEmail_null_isValid() {
        assertTrue(InputValidator.validateEmail(null).isValid);
    }

    /** Subdomain addresses are valid. */
    @Test
    public void validateEmail_subdomainAddress_isValid() {
        assertTrue(InputValidator.validateEmail("bob.jones@cs.ualberta.ca").isValid);
    }

    // ── sanitizeSearchQuery — OWASP M1 ────────────────────────────────────────

    /** SQL injection metacharacters must be removed. */
    @Test
    public void sanitizeQuery_stripsInjectionChars() {
        String result = InputValidator.sanitizeSearchQuery("events'; DROP TABLE users;--");
        assertFalse("Semicolons should be stripped", result.contains(";"));
        assertFalse("Single quotes should be stripped", result.contains("'"));
        assertFalse("Double-dash comments should be stripped", result.contains("--"));
    }

    /** HTML tags must be stripped (XSS prevention). */
    @Test
    public void sanitizeQuery_stripsHtmlTags() {
        String result = InputValidator.sanitizeSearchQuery("<script>alert('xss')</script>music");
        assertFalse("Script tags should be stripped", result.contains("<script>"));
        assertTrue("Remaining safe text should be preserved", result.contains("music"));
    }

    /** Null input returns an empty string, not null or an exception. */
    @Test
    public void sanitizeQuery_null_returnsEmptyString() {
        assertEquals("", InputValidator.sanitizeSearchQuery(null));
    }

    /** A clean query passes through unchanged (up to max length). */
    @Test
    public void sanitizeQuery_cleanInput_unchanged() {
        String clean = "Edmonton folk music festival";
        assertEquals(clean, InputValidator.sanitizeSearchQuery(clean));
    }

    /** Queries longer than 100 characters are truncated. */
    @Test
    public void sanitizeQuery_longInput_truncatedTo100Chars() {
        String longQuery = "a".repeat(200);
        String result = InputValidator.sanitizeSearchQuery(longQuery);
        assertEquals(100, result.length());
    }
}
