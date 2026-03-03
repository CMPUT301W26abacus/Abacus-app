package com.example.abacus_app;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.search.SearchBar;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(80, 255, 255, 255));
        } else {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(180, 255, 255, 255));
        }

        SearchBar searchBar = findViewById(R.id.search_bar);
        setSearchBarTextColor(searchBar);

        List<String> testEvents = Arrays.asList(
                "Summer Music Festival",
                "Art Gallery Opening",
                "Community Food Drive",
                "Tech Meetup 2025",
                "Charity Run",
                "Open Mic Night",
                "Summer Music Festival",
                "Art Gallery Opening",
                "Community Food Drive",
                "Tech Meetup 2025",
                "Charity Run",
                "Open Mic Night"
        );

        RecyclerView recyclerView = findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new EventAdapter(testEvents));
    }

    private void setSearchBarTextColor(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(ContextCompat.getColor(this, R.color.black));
                ((TextView) child).setHintTextColor(ContextCompat.getColor(this, R.color.black));
            } else if (child instanceof ViewGroup) {
                setSearchBarTextColor((ViewGroup) child);
            }
        }
    }
}