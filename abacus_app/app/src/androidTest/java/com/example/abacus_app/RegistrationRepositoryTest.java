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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RegistrationRepositoryTest {

    @Test
    public void testJoinWaitlist() throws InterruptedException {

        String userID = "sampleuserid";
        String userID2 = "sampleuserid2";
        String eventID = "sampleeventid";
        RegistrationRepository repo = new RegistrationRepository();
        CountDownLatch latch = new CountDownLatch(1);
        repo.joinWaitlist(userID, eventID, error -> {
            repo.isOnWaitlist(userID, eventID, value -> {
                assertTrue(value);
                latch.countDown(); // tell test we are done
            });
        });
        repo.joinWaitlist(userID2, eventID, error -> {
            repo.isOnWaitlist(userID, eventID, value -> {
                // do nothing
            });
        });
        latch.await(10, TimeUnit.SECONDS); // wait for async work
    }
}
