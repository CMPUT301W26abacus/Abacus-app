package com.example.abacus_app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainSavedFragment extends Fragment {

    public MainSavedFragment() { super(R.layout.main_saved_fragment); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SwipeRefreshLayout swipe = view.findViewById(R.id.saved_swipe_refresh);
        swipe.setOnRefreshListener(() -> swipe.setRefreshing(false));
    }
}
