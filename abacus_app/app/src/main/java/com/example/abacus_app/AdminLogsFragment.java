package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only logs screen with two tabs: Images and Profiles.
 */
public class AdminLogsFragment extends Fragment {

    private AdminViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.admin_logs_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        viewModel.getProfiles().observe(getViewLifecycleOwner(), users ->
                Log.d("AdminLogsFragment", "Profiles LiveData fired, size=" + (users != null ? users.size() : "null")));
        viewModel.getImages().observe(getViewLifecycleOwner(), events ->
                Log.d("AdminLogsFragment", "Images LiveData fired, size=" + (events != null ? events.size() : "null")));

        ViewPager2 viewPager = view.findViewById(R.id.view_pager);
        TabLayout tabLayout  = view.findViewById(R.id.tab_layout);

        viewPager.setAdapter(new AdminPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Images" : "Profiles");
        }).attach();

        // Apply actual nav bar height as bottom padding at runtime — no hardcoded values
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, 0, 0, navBarHeight);
            ((ViewGroup) v).setClipToPadding(false);
            return insets;
        });

        viewModel.loadImages();
        viewModel.loadProfiles();

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });
    }

    // ── Pager adapter ─────────────────────────────────────────────────────────

    private class AdminPagerAdapter extends FragmentStateAdapter {
        AdminPagerAdapter(Fragment f) {
            super(f.getChildFragmentManager(), f.getViewLifecycleOwner().getLifecycle());
        }

        @Override public int getItemCount() { return 2; }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == 0
                    ? AdminTabFragment.newImagesTab()
                    : AdminTabFragment.newProfilesTab();
        }
    }

    // ── Tab fragment ──────────────────────────────────────────────────────────

    public static class AdminTabFragment extends Fragment {

        private static final String ARG_TAB      = "tab";
        private static final int    TAB_IMAGES   = 0;
        private static final int    TAB_PROFILES = 1;

        public static AdminTabFragment newImagesTab() {
            AdminTabFragment f = new AdminTabFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_TAB, TAB_IMAGES);
            f.setArguments(args);
            return f;
        }

        public static AdminTabFragment newProfilesTab() {
            AdminTabFragment f = new AdminTabFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_TAB, TAB_PROFILES);
            f.setArguments(args);
            return f;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.admin_tab_fragment, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            int tab = getArguments() != null
                    ? getArguments().getInt(ARG_TAB, TAB_IMAGES)
                    : TAB_IMAGES;

            RecyclerView rv          = view.findViewById(R.id.rv_admin);
            View         layoutEmpty = view.findViewById(R.id.layout_empty);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));

            AdminViewModel vm = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

            if (tab == TAB_IMAGES) {
                List<Event> imageList = new ArrayList<>();
                AdminImageAdapter adapter = new AdminImageAdapter(imageList, event ->
                        confirmDelete(
                                "Remove poster image?",
                                "This will remove the poster from the event but keep the event itself.",
                                () -> vm.deleteImage(event.getEventId())));
                rv.setAdapter(adapter);

                vm.getImages().observe(getViewLifecycleOwner(), events -> {
                    imageList.clear();
                    if (events != null) imageList.addAll(events);
                    adapter.notifyDataSetChanged();
                    layoutEmpty.setVisibility(imageList.isEmpty() ? View.VISIBLE : View.GONE);
                    rv.setVisibility(imageList.isEmpty() ? View.GONE : View.VISIBLE);
                });

            } else {
                List<User> profileList = new ArrayList<>();
                AdminProfileAdapter adapter = new AdminProfileAdapter(profileList, user ->
                        confirmDelete(
                                "Delete this profile?",
                                "The profile will be marked as deleted.",
                                () -> vm.deleteProfile(user.getUid())));
                rv.setAdapter(adapter);

                vm.getProfiles().observe(getViewLifecycleOwner(), users -> {
                    profileList.clear();
                    if (users != null) profileList.addAll(users);
                    adapter.notifyDataSetChanged();
                    layoutEmpty.setVisibility(profileList.isEmpty() ? View.VISIBLE : View.GONE);
                    rv.setVisibility(profileList.isEmpty() ? View.GONE : View.VISIBLE);
                });
            }
        }

        private void confirmDelete(String title, String message, Runnable onConfirm) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Delete", (d, w) -> onConfirm.run())
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
}