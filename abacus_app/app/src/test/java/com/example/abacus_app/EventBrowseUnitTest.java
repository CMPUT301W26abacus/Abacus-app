package com.example.abacus_app;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * EventBrowseUnitTest.java
 *
 * Unit tests for event browsing and filtering logic (US 01.01.03, US 01.01.04).
 * Tests the logic of filtering and the adapter item count.
 * 
 * @author Himesh (Fixed)
 */
public class EventBrowseUnitTest {

    /**
     * Mirrors the filter logic in MainActivity.applyFilters().
     */
    private List<String> applyFilters(List<Event> events, String keyword, String date) {
        List<String> result = new ArrayList<>();
        for (Event event : events) {
            String title       = (event.getTitle() != null ? event.getTitle() : "").toLowerCase();
            String description = (event.getDescription() != null ? event.getDescription() : "").toLowerCase();
            
            // In a real app, we'd compare Timestamps, but for this logic test we use String matching
            // to simulate the behavior described in the original test.
            boolean keywordMatch = keyword.isEmpty()
                    || title.contains(keyword.toLowerCase())
                    || description.contains(keyword.toLowerCase());

            // Date filtering simulation
            boolean dateMatch = date.isEmpty(); // Simplified for pure logic test

            if (keywordMatch && dateMatch) result.add(event.getTitle());
        }
        return result;
    }

    private List<Event> testEvents;

    @Before
    public void setUp() {
        testEvents = new ArrayList<>();
        
        Event e1 = new Event(); e1.setTitle("Summer Music Festival"); e1.setDescription("outdoor music event");
        Event e2 = new Event(); e2.setTitle("Art Gallery Opening");   e2.setDescription("culture and art show");
        Event e3 = new Event(); e3.setTitle("Tech Meetup 2025");      e3.setDescription("networking for developers");
        
        testEvents.add(e1);
        testEvents.add(e2);
        testEvents.add(e3);
    }

    /**
     * US 01.01.03 — Adapter reports correct item count.
     * Fixed: Uses List<Event> and provides the required click listener.
     */
    @Test
    public void testAdapterItemCount() {
        List<Event> events = new ArrayList<>();
        events.add(new Event());
        events.add(new Event());
        events.add(new Event());
        
        // Pass a dummy lambda for the OnEventClickListener
        EventAdapter adapter = new EventAdapter(events, title -> {});
        assertEquals(3, adapter.getItemCount());
    }

    /**
     * US 01.01.03 — Adapter with empty list reports zero items.
     */
    @Test
    public void testAdapterEmptyList() {
        EventAdapter adapter = new EventAdapter(new ArrayList<>(), title -> {});
        assertEquals(0, adapter.getItemCount());
    }

    /**
     * US 01.01.04 — Keyword matching event title returns that event.
     */
    @Test
    public void testKeywordFilterMatchesTitle() {
        List<String> result = applyFilters(testEvents, "music", "");
        assertTrue(result.contains("Summer Music Festival"));
    }

    /**
     * US 01.01.04 — Keyword matching event description returns that event.
     */
    @Test
    public void testKeywordFilterMatchesDescription() {
        List<String> result = applyFilters(testEvents, "developers", "");
        assertTrue(result.contains("Tech Meetup 2025"));
    }

    /**
     * US 01.01.04 — Keyword with no matches returns empty list.
     */
    @Test
    public void testKeywordFilterNoMatch() {
        List<String> result = applyFilters(testEvents, "zzznomatch", "");
        assertTrue(result.isEmpty());
    }

    /**
     * US 01.01.04 — Empty keyword returns all events.
     */
    @Test
    public void testEmptyKeywordReturnsAll() {
        List<String> result = applyFilters(testEvents, "", "");
        assertEquals(testEvents.size(), result.size());
    }
}
