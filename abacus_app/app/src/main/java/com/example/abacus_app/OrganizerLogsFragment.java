package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * OrganizerLogsFragment
 *
 * Shows organizer logs in two tabs:
 *   Activity    — waitlist joins and lottery draws
 *   Notifications — notifications sent to entrants
 */
public class OrganizerLogsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.organizer_logs_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipe = view.findViewById(R.id.org_logs_swipe_refresh);
        swipe.setOnRefreshListener(() -> swipe.setRefreshing(false));

        ViewPager2 viewPager = view.findViewById(R.id.view_pager);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);

        viewPager.setAdapter(new LogsPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(position == 0 ? "Activity" : "Notifications")
        ).attach();
    }

    // ── Pager adapter ─────────────────────────────────────────────────────────

    private static class LogsPagerAdapter extends FragmentStateAdapter {
        LogsPagerAdapter(Fragment f) {
            super(f.getChildFragmentManager(), f.getViewLifecycleOwner().getLifecycle());
        }

        @Override public int getItemCount() { return 2; }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LogsTabFragment.newInstance(position);
        }
    }

    // ── Tab fragment ──────────────────────────────────────────────────────────

    public static class LogsTabFragment extends Fragment {

        static final int TAB_ACTIVITY      = 0;
        static final int TAB_NOTIFICATIONS = 1;
        private static final String ARG_TAB = "tab";

        public static LogsTabFragment newInstance(int tab) {
            LogsTabFragment f = new LogsTabFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_TAB, tab);
            f.setArguments(args);
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_logs_tab, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            int tab = getArguments() != null
                    ? getArguments().getInt(ARG_TAB, TAB_ACTIVITY) : TAB_ACTIVITY;

            TextView title = view.findViewById(R.id.tv_logs_empty_title);
            TextView body  = view.findViewById(R.id.tv_logs_empty_body);

            if (tab == TAB_ACTIVITY) {
                title.setText("No activity yet");
                body.setText("Waitlist joins and lottery draws will appear here.");
            } else {
                title.setText("No notifications sent");
                body.setText("Notifications sent to entrants will be logged here.");
            }
        }
    }
}
