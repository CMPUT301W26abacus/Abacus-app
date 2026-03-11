# Himesh's Implementation Plan - Organizer Tools (Halfway Checkpoint)

## Status: ALL CORE INFRASTRUCTURE AND UI FOR HALFWAY CHECKPOINT IMPLEMENTED

## 1. Data Model & Architecture
**Files:** `Event.java`, `EventRemoteDataSource.java`, `EventRepository.java`, `StorageRepository.java`
- [x] **Event Model**: POJO for Firestore with registration dates, capacity, geo-toggle, and poster URLs.
- [x] **Event Repository**: Centralized access for Firestore `events/` collection.
- [x] **Storage Repository**: Logic for Firebase Storage (Posters and QR codes).

## 2. Event Creation (US 02.01.01, 02.01.04, 02.03.01, 02.04.01, 02.02.03)
**Files:** `CreateEventFragment.java`, `CreateEventViewModel.java`, `fragment_create_event.xml`
- [x] **Registration Period**: UI buttons integrated with `MaterialDatePicker` and `MaterialTimePicker`.
- [x] **Waitlist Limit**: Optional capacity limit logic with UI toggle.
- [x] **Geolocation**: Requirement toggle implemented.
- [x] **Poster Upload**: Image picker implemented; logic to upload to Firebase Storage and save URL to Firestore is ready.

## 3. Entrant Management (US 02.02.01, 01.05.04)
**Files:** `ManageEventFragment.java`, `ManageEventViewModel.java`, `fragment_manage_event.xml`, `WaitlistAdapter.java`, `item_entrant.xml`
- [x] **Waitlist View**: Real-time list of entrants from Firestore sub-collection `events/{id}/waitlist`.
- [x] **Entrant Count**: Dynamic display of total entrants on the waitlist.
- [x] **Cancellation**: UI and logic hooks for manual entrant cancellation.

## 4. Navigation & Integration
- [x] **Nav Graph**: `createEventFragment` and `manageEventFragment` added to `nav_graph.xml`.
- [x] **CRC Alignment**: Class names and responsibilities match the team's official design document exactly.

## 5. Next Steps (Post-Checkpoint)
- Implement manual lottery draw logic (`runLottery()`).
- Implement CSV export for enrolled entrants (`US 02.06.05`).
- Implement Map view for entrant geolocation (`US 02.02.02`).
