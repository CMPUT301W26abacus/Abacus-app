/**
 * EventQrFragmentTest.java
 *
 * Instrumented tests for EventQrFragment.
 * Verifies that the QR code is generated and displayed correctly,
 * that event name and ID are shown as passed via arguments,
 * and that the fragment handles missing arguments gracefully.
 *
 * Run on a device or emulator via: androidTest
 */
package com.example.abacus_app;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EventQrFragmentTest {

    @Test
    public void testQrImageViewIsPopulated() {
        // Launch fragment with valid event args
        Bundle args = new Bundle();
        args.putString(EventQrFragment.ARG_EVENT_ID, "event_abc_123");
        args.putString(EventQrFragment.ARG_EVENT_NAME, "Summer Music Festival");

        FragmentScenario<EventQrFragment> scenario =
                FragmentScenario.launchInContainer(EventQrFragment.class, args);

        scenario.onFragment(fragment -> {
            ImageView ivQrCode = fragment.requireView().findViewById(R.id.iv_qr_code);
            assertNotNull("QR ImageView should exist", ivQrCode);
            assertNotNull("QR ImageView should have a bitmap set", ivQrCode.getDrawable());
        });
    }

    @Test
    public void testEventNameIsDisplayed() {
        Bundle args = new Bundle();
        args.putString(EventQrFragment.ARG_EVENT_ID, "event_abc_123");
        args.putString(EventQrFragment.ARG_EVENT_NAME, "Summer Music Festival");

        FragmentScenario<EventQrFragment> scenario =
                FragmentScenario.launchInContainer(EventQrFragment.class, args);

        scenario.onFragment(fragment -> {
            TextView tvEventName = fragment.requireView().findViewById(R.id.tv_event_name);
            assertNotNull("Event name TextView should exist", tvEventName);
            assertEquals("Summer Music Festival", tvEventName.getText().toString());
        });
    }

    @Test
    public void testEventIdIsDisplayed() {
        Bundle args = new Bundle();
        args.putString(EventQrFragment.ARG_EVENT_ID, "event_abc_123");
        args.putString(EventQrFragment.ARG_EVENT_NAME, "Summer Music Festival");

        FragmentScenario<EventQrFragment> scenario =
                FragmentScenario.launchInContainer(EventQrFragment.class, args);

        scenario.onFragment(fragment -> {
            TextView tvEventId = fragment.requireView().findViewById(R.id.tv_event_id);
            assertNotNull("Event ID TextView should exist", tvEventId);
            assertEquals("event_abc_123", tvEventId.getText().toString());
        });
    }

    @Test
    public void testFallbackValuesWhenNoArgs() {
        // Launch without any arguments — should use hardcoded fallback values
        FragmentScenario<EventQrFragment> scenario =
                FragmentScenario.launchInContainer(EventQrFragment.class, null);

        scenario.onFragment(fragment -> {
            TextView tvEventName = fragment.requireView().findViewById(R.id.tv_event_name);
            TextView tvEventId = fragment.requireView().findViewById(R.id.tv_event_id);
            assertNotNull("Event name should fall back to default", tvEventName.getText());
            assertNotNull("Event ID should fall back to default", tvEventId.getText());
            // Confirm fallback values match what is hardcoded in the fragment
            assertEquals("Test Event", tvEventName.getText().toString());
            assertEquals("event_test_12345", tvEventId.getText().toString());
        });
    }

    @Test
    public void testQrStillGeneratedWithFallbackArgs() {
        // Even without args the QR ImageView should be populated via fallback ID
        FragmentScenario<EventQrFragment> scenario =
                FragmentScenario.launchInContainer(EventQrFragment.class, null);

        scenario.onFragment(fragment -> {
            ImageView ivQrCode = fragment.requireView().findViewById(R.id.iv_qr_code);
            assertNotNull("QR bitmap should be set even with fallback args",
                    ivQrCode.getDrawable());
        });
    }
}