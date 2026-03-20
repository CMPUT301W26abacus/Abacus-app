package com.example.abacus_app;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * OrganizerToolsFragment
 *
 * Landing page for organizer/admin tools, presented as tabs.
 * Organizers see one tab: "Events" (Create + Manage).
 * Admins see two tabs: "Events" + "Moderation" (Browse Users + Browse Images).
 */
public class OrganizerToolsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.organizer_tools_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SwipeRefreshLayout swipe = view.findViewById(R.id.tools_swipe_refresh);
        swipe.setOnRefreshListener(() -> swipe.setRefreshing(false));

        String role = ((MainActivity) requireActivity()).getUserRole();
        boolean isAdmin = "admin".equals(role);

        ViewPager2 viewPager = view.findViewById(R.id.view_pager);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout);

        viewPager.setAdapter(new ToolsPagerAdapter(this, isAdmin));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (isAdmin) {
                tab.setText(position == 0 ? "Events" : "Moderation");
            } else {
                tab.setText("Events");
            }
        }).attach();
    }

    // ── Pager adapter ─────────────────────────────────────────────────────────

    private static class ToolsPagerAdapter extends FragmentStateAdapter {
        private final boolean isAdmin;

        ToolsPagerAdapter(Fragment f, boolean isAdmin) {
            super(f.getChildFragmentManager(), f.getViewLifecycleOwner().getLifecycle());
            this.isAdmin = isAdmin;
        }

        @Override
        public int getItemCount() { return isAdmin ? 2 : 1; }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return isAdmin && position == 1
                    ? ToolsTabFragment.newInstance(ToolsTabFragment.TYPE_MODERATION)
                    : ToolsTabFragment.newInstance(ToolsTabFragment.TYPE_EVENTS);
        }
    }

    // ── Tab fragment ──────────────────────────────────────────────────────────

    public static class ToolsTabFragment extends Fragment {

        static final int TYPE_EVENTS     = 0;
        static final int TYPE_MODERATION = 1;
        private static final String ARG_TYPE = "type";

        public static ToolsTabFragment newInstance(int type) {
            ToolsTabFragment f = new ToolsTabFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_TYPE, type);
            f.setArguments(args);
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_tools_tab, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            int type = getArguments() != null
                    ? getArguments().getInt(ARG_TYPE, TYPE_EVENTS) : TYPE_EVENTS;

            LinearLayout cardContent = view.findViewById(R.id.tab_card_content);

            if (type == TYPE_EVENTS) {
                addButton(cardContent, "Create New Event", v ->
                        ((MainActivity) requireActivity())
                                .showFragment(R.id.organizerCreateFragment, false));
                addDivider(cardContent);
                addButton(cardContent, "Manage My Events", v ->
                        ((MainActivity) requireActivity())
                                .showFragment(R.id.organizerManageFragment, false));
            } else {
                // Moderation — admin only
                addButton(cardContent, "Browse User Profiles", v ->
                        ((MainActivity) requireActivity())
                                .showFragment(R.id.adminLogsFragment, true));
                addDivider(cardContent);
                addButton(cardContent, "Browse Event Images", v ->
                        ((MainActivity) requireActivity())
                                .showFragment(R.id.adminLogsFragment, true));
            }
        }

        private void addButton(LinearLayout parent, String text, View.OnClickListener listener) {
            Button btn = new Button(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            int height = dp(52);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, height);
            btn.setLayoutParams(params);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setPadding(dp(16), 0, dp(16), 0);
            btn.setText(text);
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            btn.setBackground(null);
            btn.setBackgroundResource(android.R.attr.selectableItemBackground != 0
                    ? 0 : 0);
            // Use selectableItemBackground ripple
            TypedValue outValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true);
            btn.setBackgroundResource(outValue.resourceId);
            btn.setOnClickListener(listener);
            parent.addView(btn);
        }

        private void addDivider(LinearLayout parent) {
            View divider = new View(requireContext());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
            p.setMarginStart(dp(16));
            p.setMarginEnd(dp(16));
            divider.setLayoutParams(p);
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.grey));
            parent.addView(divider);
        }

        private int dp(int value) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    value, getResources().getDisplayMetrics()));
        }
    }
}
