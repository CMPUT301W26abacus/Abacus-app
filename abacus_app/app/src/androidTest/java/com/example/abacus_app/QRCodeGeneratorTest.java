/**
 * QRCodeGeneratorTest.java
 *
 * Instrumented tests for QRCodeGenerator.
 * Verifies that QR code bitmaps are generated correctly for valid inputs,
 * that the output dimensions match the requested size, and that invalid
 * inputs (null, empty) are handled gracefully without crashing.
 *
 * Run on a device or emulator via: androidTest
 */
package com.example.abacus_app;

import android.graphics.Bitmap;

import org.junit.Test;
import static org.junit.Assert.*;

public class QRCodeGeneratorTest {

    @Test
    public void testQRCodeNotNull() {
        // A valid input should return a non-null bitmap
        Bitmap result = QRCodeGenerator.generateQRCode("event_test_12345", 512);
        assertNotNull("QR code bitmap should not be null for valid input", result);
    }

    @Test
    public void testQRCodeIsCorrectSize() {
        // The bitmap should be exactly the size we requested
        int size = 512;
        Bitmap result = QRCodeGenerator.generateQRCode("event_test_12345", size);
        assertNotNull(result);
        assertEquals("QR bitmap width should match requested size", size, result.getWidth());
        assertEquals("QR bitmap height should match requested size", size, result.getHeight());
    }

    @Test
    public void testQRCodeIsSquare() {
        // QR codes are always square
        Bitmap result = QRCodeGenerator.generateQRCode("event_test_12345", 512);
        assertNotNull(result);
        assertEquals("QR bitmap should be square", result.getWidth(), result.getHeight());
    }

    @Test
    public void testNullInputReturnsNull() {
        // Null content should return null gracefully
        Bitmap result = QRCodeGenerator.generateQRCode(null, 512);
        assertNull("QR code should return null for null input", result);
    }

    @Test
    public void testEmptyInputReturnsNull() {
        // Empty string should return null gracefully
        Bitmap result = QRCodeGenerator.generateQRCode("", 512);
        assertNull("QR code should return null for empty input", result);
    }

    @Test
    public void testDifferentSizes() {
        // Should work for different sizes
        Bitmap small = QRCodeGenerator.generateQRCode("event_abc", 128);
        Bitmap large = QRCodeGenerator.generateQRCode("event_abc", 1024);
        assertNotNull("Small QR should not be null", small);
        assertNotNull("Large QR should not be null", large);
        assertEquals(128, small.getWidth());
        assertEquals(1024, large.getWidth());
    }

    @Test
    public void testLongEventId() {
        // Should handle a long Firestore-style document ID
        Bitmap result = QRCodeGenerator.generateQRCode("events/aB3kL9mNpQrStUvWxYz1234567890abcdef", 512);
        assertNotNull("QR code should handle long IDs", result);
    }
}