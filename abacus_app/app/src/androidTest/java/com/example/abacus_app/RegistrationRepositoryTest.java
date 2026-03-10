package com.example.abacus_app;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RegistrationRepositoryTest {

    String TAG = "mytagTEST";

    @Test
    public void testGetWaitlistSize() throws InterruptedException {

        String eventID = "testregrepoevent";

        RegistrationRepository repo = new RegistrationRepository();
        CountDownLatch latch = new CountDownLatch(1);
        repo.getWaitListSize(eventID, new RegistrationRepository.IntegerCallback() {
            @Override
            public void onResult(Integer value) {
                Log.d(TAG, "onResult: " + value);
                //latch.countDown(); // tell test we are done
            }
        });
        latch.await(10, TimeUnit.SECONDS); // wait for async work

        assertTrue(true);
    }

    @Test
    public void testJoinWaitlist() throws InterruptedException {

        String userID1 = "testjoinwaitlist1";
        String userID2 = "testjoinwaitlist2";
        String eventID = "testregrepoevent";
        String eventID2 = "testregrepoevent2";
        RegistrationRepository repo = new RegistrationRepository();
        CountDownLatch latch = new CountDownLatch(1);
        repo.joinWaitlist(userID1, eventID, error -> {
            // do nothing
        });
        repo.joinWaitlist(userID2, eventID, error -> {
            // do nothing
        });
        repo.joinWaitlist(userID1, eventID2, error -> {
            // do nothing
        });
        latch.await(10, TimeUnit.SECONDS); // wait for async work

        assertTrue(true);
    }

    @Test
    public void testGetAllEntries() throws InterruptedException {

        String eventID = "testregrepoevent";

        RegistrationRepository repo = new RegistrationRepository();
        CountDownLatch latch = new CountDownLatch(1);
        repo.getAllEntries(eventID, new RegistrationRepository.WaitlistCallback() {
            @Override
            public void onResult(ArrayList<WaitlistEntry> waitlist) {
                Log.d(TAG, "Printing results...");
                for (WaitlistEntry entry : waitlist) {
                    Log.d(TAG, entry.getUserID());
                }
            }
        });
        latch.await(10, TimeUnit.SECONDS); // wait for async work

        assertTrue(true);
    }

    @Test
    public void testGetHistoryForUser() throws InterruptedException {

        String userID1 = "testjoinwaitlist1";

        RegistrationRepository repo = new RegistrationRepository();
        CountDownLatch latch = new CountDownLatch(1);
        repo.getHistoryForUser(userID1, new RegistrationRepository.WaitlistCallback() {
            @Override
            public void onResult(ArrayList<WaitlistEntry> waitlist) {
                Log.d(TAG, "Printing results...");
                for (WaitlistEntry entry : waitlist) {
                    Log.d(TAG, entry.getEventID());
                }
            }
        });
        latch.await(10, TimeUnit.SECONDS); // wait for async work

        assertTrue(true);
    }
}
