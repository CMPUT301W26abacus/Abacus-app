package com.example.abacus_app;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests for {@link QRCodeGenerator}.
 *
 * <p>These tests run on a device or emulator ({@code androidTest/}) because
 * {@link Bitmap} and {@link android.graphics.Color} are Android framework classes
 * that do not exist on the plain JVM. There is no Android-free surface to test in
 * {@code test/} without either mocking the bitmap step or restructuring the source.
 *
 * <p><b>What is covered:</b>
 * <ul>
 *   <li>Null and empty content returns null without throwing.</li>
 *   <li>Valid content produces a non-null, correctly sized bitmap.</li>
 *   <li>The bitmap uses the RGB_565 config specified in the implementation.</li>
 *   <li>The quiet zone (margin) means the corners are white, not black.</li>
 *   <li>At least one black pixel exists — the QR code is not blank.</li>
 *   <li>Different content strings produce different bitmaps.</li>
 *   <li>Very long content still produces a result without crashing.</li>
 *   <li>Unicode content encodes without throwing.</li>
 *   <li>Size parameter is respected for both small and large outputs.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class QRCodeGeneratorTest {

    // ── Null / empty input ────────────────────────────────────────────────────

    /**
     * Null content should return null immediately without attempting encoding.
     *
     * <p><b>Requirement:</b> The method must never throw on null input.
     */
    @Test
    public void generateQRCode_nullContent_returnsNull() {
        Bitmap result = QRCodeGenerator.generateQRCode(null, 512);
        assertNull(result);
    }

    /**
     * An empty string should return null — there is nothing meaningful to encode.
     *
     * <p><b>Requirement:</b> Empty strings are rejected the same way as null.
     */
    @Test
    public void generateQRCode_emptyContent_returnsNull() {
        Bitmap result = QRCodeGenerator.generateQRCode("", 512);
        assertNull(result);
    }

    // ── Valid input: basic contract ───────────────────────────────────────────

    /**
     * A typical Firestore event ID should produce a non-null bitmap.
     *
     * <p><b>Requirement:</b> Valid content always yields a usable bitmap.
     */
    @Test
    public void generateQRCode_validEventId_returnsNonNull() {
        Bitmap result = QRCodeGenerator.generateQRCode("aBcDeFgH1234567890", 512);
        assertNotNull(result);
    }

    /**
     * The returned bitmap must be exactly the requested square size.
     *
     * <p><b>Requirement:</b> Callers rely on the bitmap fitting a fixed-size ImageView;
     * off-by-one dimensions would cause stretching.
     */
    @Test
    public void generateQRCode_validContent_bitmapHasCorrectDimensions() {
        int size = 512;
        Bitmap result = QRCodeGenerator.generateQRCode("event-id-dimension-test", size);

        assertNotNull(result);
        assertEquals("Width must match requested size",  size, result.getWidth());
        assertEquals("Height must match requested size", size, result.getHeight());
    }

    /**
     * The bitmap config must be {@link Bitmap.Config#RGB_565} as specified in the source.
     * A wrong config (e.g. ARGB_8888) would double memory usage unnecessarily.
     */
    @Test
    public void generateQRCode_validContent_bitmapConfigIsRGB565() {
        Bitmap result = QRCodeGenerator.generateQRCode("event-id-config-test", 256);

        assertNotNull(result);
        assertEquals(Bitmap.Config.RGB_565, result.getConfig());
    }

    // ── Pixel-level correctness ───────────────────────────────────────────────

    /**
     * The four corner pixels should be white because the QR quiet zone (margin=2)
     * surrounds the code. A black corner would indicate the margin hint is being ignored.
     *
     * <p><b>Requirement:</b> Quiet zone is required by the QR spec for reliable scanning.
     */
    @Test
    public void generateQRCode_validContent_cornerPixelsAreWhite() {
        int size = 512;
        Bitmap result = QRCodeGenerator.generateQRCode("event-corner-pixel-test", size);
        assertNotNull(result);

        int lastIdx = size - 1;
        assertEquals("Top-left corner should be white",     0xFFFFFFFF, result.getPixel(0, 0) | 0xFF000000);
        assertEquals("Top-right corner should be white",    0xFFFFFFFF, result.getPixel(lastIdx, 0) | 0xFF000000);
        assertEquals("Bottom-left corner should be white",  0xFFFFFFFF, result.getPixel(0, lastIdx) | 0xFF000000);
        assertEquals("Bottom-right corner should be white", 0xFFFFFFFF, result.getPixel(lastIdx, lastIdx) | 0xFF000000);
    }

    /**
     * At least one pixel in the bitmap must be black — the QR code is not a blank image.
     *
     * <p>Scans the top half of the bitmap where QR finder patterns always appear.
     */
    @Test
    public void generateQRCode_validContent_containsBlackPixels() {
        int size = 512;
        Bitmap result = QRCodeGenerator.generateQRCode("event-black-pixel-test", size);
        assertNotNull(result);

        boolean foundBlack = false;
        outer:
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size / 2; y++) {
                // RGB_565 black maps to 0xFF000000 when read back via getPixel (ARGB)
                if ((result.getPixel(x, y) & 0x00FFFFFF) == 0) {
                    foundBlack = true;
                    break outer;
                }
            }
        }
        assertTrue("QR bitmap must contain at least one black pixel", foundBlack);
    }

    // ── Content variations ────────────────────────────────────────────────────

    /**
     * Two different content strings must produce bitmaps that differ in at least one pixel.
     *
     * <p><b>Requirement:</b> Each event ID encodes a unique QR code. If two different IDs
     * produced identical bitmaps the scanner would route both to the same event.
     */
    @Test
    public void generateQRCode_differentContent_producesDifferentBitmaps() {
        int size = 256;
        Bitmap bitmapA = QRCodeGenerator.generateQRCode("event-id-alpha", size);
        Bitmap bitmapB = QRCodeGenerator.generateQRCode("event-id-beta",  size);

        assertNotNull(bitmapA);
        assertNotNull(bitmapB);

        boolean pixelDifFound = false;
        outer:
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (bitmapA.getPixel(x, y) != bitmapB.getPixel(x, y)) {
                    pixelDifFound = true;
                    break outer;
                }
            }
        }
        assertTrue("Different content must produce visually distinct QR codes", pixelDifFound);
    }

    /**
     * Unicode content (emoji, non-ASCII characters) must encode without throwing.
     * The implementation sets CHARACTER_SET=UTF-8 explicitly to support this.
     *
     * <p><b>Requirement:</b> Future event titles or IDs may contain accented characters.
     */
    @Test
    public void generateQRCode_unicodeContent_doesNotThrow() {
        Bitmap result = QRCodeGenerator.generateQRCode("Événement-Ünïcödé-🎉", 256);
        // We only assert no crash and a non-null result; pixel correctness is ZXing's job.
        assertNotNull(result);
    }

    /**
     * A very long string (300 characters) should still encode without returning null.
     * ZXing will increase the QR version automatically to accommodate more data.
     *
     * <p><b>Requirement:</b> If the content field ever holds a URL with query parameters,
     * the generator must not silently fail.
     */
    @Test
    public void generateQRCode_veryLongContent_doesNotReturnNull() {
        String longContent = "https://abacus.example.com/events/"
                + "a".repeat(300);  // 334 chars total — forces a higher QR version

        Bitmap result = QRCodeGenerator.generateQRCode(longContent, 512);
        assertNotNull("Long content should still encode successfully", result);
    }

    // ── Size variations ───────────────────────────────────────────────────────

    /**
     * A small bitmap (64×64) should still be generated correctly. This covers thumbnail
     * use cases where a small QR preview is shown in a list.
     */
    @Test
    public void generateQRCode_smallSize_returnsCorrectDimensions() {
        int size = 64;
        Bitmap result = QRCodeGenerator.generateQRCode("event-small-size", size);

        assertNotNull(result);
        assertEquals(size, result.getWidth());
        assertEquals(size, result.getHeight());
    }

    /**
     * A large bitmap (1024×1024) should be generated without an OOM or exception.
     * This covers print-quality QR codes.
     */
    @Test
    public void generateQRCode_largeSize_returnsCorrectDimensions() {
        int size = 1024;
        Bitmap result = QRCodeGenerator.generateQRCode("event-large-size", size);

        assertNotNull(result);
        assertEquals(size, result.getWidth());
        assertEquals(size, result.getHeight());
    }
}