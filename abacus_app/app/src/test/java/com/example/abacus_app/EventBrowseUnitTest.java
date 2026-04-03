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
 * Tests are pure Java — no Android or Firebase dependencies required.
 *
 * Covers:
 * - EventAdapter item count and title binding
 * - Keyword filter logic (title match, no match, empty keyword)
 * - Date filter logic (match, no match, empty date)
 * - Combined keyword + date filter (AND logic)
 * - Empty state conditions
 */
public class EventBrowseUnitTest {

    // ── Helper: simulate applyFilters() logic from MainActivity ───────────────

    /**
     * Mirrors the filter logic in MainActivity.applyFilters().
     * Filters a list of mock events by keyword (title/description) and date.
     *
     * @param events  list of mock events as String[]{title, description, date}
     * @param keyword keyword to match against title and description
     * @param date    date string "yyyy-MM-dd" to match against event date
     * @return filtered list of matching event titles
     */
    private List<String> applyFilters(List<String[]> events, String keyword, String date) {
        List<String> result = new ArrayList<>();
        for (String[] event : events) {
            String title       = event[0].toLowerCase();
            String description = event[1].toLowerCase();
            String eventDate   = event[2];

            boolean keywordMatch = keyword.isEmpty()
                    || title.contains(keyword.toLowerCase())
                    || description.contains(keyword.toLowerCase());

            boolean dateMatch = date.isEmpty() || eventDate.equals(date);

            if (keywordMatch && dateMatch) result.add(event[0]);
        }
        return result;
    }

    // ── Test data ─────────────────────────────────────────────────────────────

    private List<String[]> testEvents;

    @Before
    public void setUp() {
        testEvents = new ArrayList<>();
        testEvents.add(new String[]{"Summer Music Festival", "outdoor music event", "2025-08-10"});
        testEvents.add(new String[]{"Art Gallery Opening",   "culture and art show", "2025-07-22"});
        testEvents.add(new String[]{"Tech Meetup 2025",      "networking for developers", "2025-09-05"});
        testEvents.add(new String[]{"Charity Run",           "fitness outdoor charity", "2025-07-04"});
        testEvents.add(new String[]{"Open Mic Night",        "music and comedy indoor", "2025-06-28"});
    }

    // ── EventAdapter tests (US 01.01.03) ──────────────────────────────────────

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
        EventAdapter adapter = new EventAdapter(events, (title, autoJoin) -> {});
        assertEquals(3, adapter.getItemCount());
    }

    /**
     * US 01.01.03 — Adapter with empty list reports zero items.
     */
    @Test
    public void testAdapterEmptyList() {
        EventAdapter adapter = new EventAdapter(new ArrayList<>(), null);
        assertEquals(0, adapter.getItemCount());
    }

    // ── Keyword filter tests (US 01.01.04) ────────────────────────────────────

    /**
     * US 01.01.04 — Keyword matching event title returns that event.
     */
    @Test
    public void testKeywordFilterMatchesTitle() {
        List<String> result = applyFilters(testEvents, "music", "");
        assertTrue(result.contains("Summer Music Festival"));
        assertTrue(result.contains("Open Mic Night"));
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

    /**
     * US 01.01.04 — Keyword filter is case-insensitive.
     */
    @Test
    public void testKeywordFilterCaseInsensitive() {
        List<String> lower = applyFilters(testEvents, "music", "");
        List<String> upper = applyFilters(testEvents, "MUSIC", "");
        assertEquals(lower, upper);
    }

    // ── Date filter tests (US 01.01.04) ───────────────────────────────────────

    /**
     * US 01.01.04 — Date filter returns only events on that exact date.
     */
    @Test
    public void testDateFilterExactMatch() {
        List<String> result = applyFilters(testEvents, "", "2025-08-10");
        assertEquals(1, result.size());
        assertEquals("Summer Music Festival", result.get(0));
    }

    /**
     * US 01.01.04 — Date with no matching events returns empty list.
     */
    @Test
    public void testDateFilterNoMatch() {
        List<String> result = applyFilters(testEvents, "", "2099-01-01");
        assertTrue(result.isEmpty());
    }

    /**
     * US 01.01.04 — Empty date returns all events.
     */
    @Test
    public void testEmptyDateReturnsAll() {
        List<String> result = applyFilters(testEvents, "", "");
        assertEquals(testEvents.size(), result.size());
    }

    // ── Combined filter tests (US 01.01.04) ───────────────────────────────────

    /**
     * US 01.01.04 — Keyword AND date both match returns correct event.
     */
    @Test
    public void testCombinedFilterBothMatch() {
        List<String> result = applyFilters(testEvents, "music", "2025-08-10");
        assertEquals(1, result.size());
        assertEquals("Summer Music Festival", result.get(0));
    }

    /**
     * US 01.01.04 — Keyword matches but date does not → empty result (AND logic).
     */
    @Test
    public void testCombinedFilterKeywordMatchDateNoMatch() {
        List<String> result = applyFilters(testEvents, "music", "2099-01-01");
        assertTrue(result.isEmpty());
    }

    /**
     * US 01.01.04 — Date matches but keyword does not → empty result (AND logic).
     */
    @Test
    public void testCombinedFilterDateMatchKeywordNoMatch() {
        List<String> result = applyFilters(testEvents, "zzznomatch", "2025-08-10");
        assertTrue(result.isEmpty());
    }

    // ── Empty state tests ─────────────────────────────────────────────────────

    /**
     * US 01.01.03 — No events in list produces empty result.
     */
    @Test
    public void testEmptyEventListProducesEmptyResult() {
        List<String> result = applyFilters(new ArrayList<>(), "", "");
        assertTrue(result.isEmpty());
    }
}