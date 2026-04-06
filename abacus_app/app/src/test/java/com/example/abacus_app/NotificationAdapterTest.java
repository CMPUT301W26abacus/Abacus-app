package com.example.abacus_app;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class NotificationAdapterTest {

    private Context context;
    private NotificationAdapter adapter;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        adapter = new NotificationAdapter();
    }

    @Test
    public void getItemCount_matchesNotificationListSize() {
        Notification notification = new Notification(
                "u1",
                "user@test.com",
                "e1",
                "Message",
                Notification.TYPE_SELECTED
        );

        adapter.setNotifications(Collections.singletonList(notification));

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void getItemViewType_changesWhenReadOnlyModeChanges() {
        Notification notification = new Notification(
                "u1",
                "user@test.com",
                "e1",
                "Message",
                Notification.TYPE_SELECTED
        );

        adapter.setNotifications(Collections.singletonList(notification));

        adapter.setReadOnly(false);
        int normalType = adapter.getItemViewType(0);

        adapter.setReadOnly(true);
        int logType = adapter.getItemViewType(0);

        assertNotEquals(normalType, logType);
    }
    }