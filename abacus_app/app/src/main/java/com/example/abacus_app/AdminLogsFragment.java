package com.example.abacus_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

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

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.admin_logs_swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadImages();
            viewModel.loadProfiles();
            swipeRefresh.setRefreshing(false);
        });

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

        // ── Search bar — filters whichever tab is active via shared ViewModel ──
        TextInputEditText searchBar = view.findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString().trim().toLowerCase());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                searchBar.clearFocus();
                return true;
            }
            return false;
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

                // Observe both images and search query so the list re-filters on either change
                vm.getImages().observe(getViewLifecycleOwner(), events -> {
                    applyImageFilter(vm, imageList, adapter, layoutEmpty, rv);
                });
                vm.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
                    applyImageFilter(vm, imageList, adapter, layoutEmpty, rv);
                });

            } else {
                List<User> profileList = new ArrayList<>();
                AdminProfileAdapter adapter = new AdminProfileAdapter(profileList, user ->
                        confirmDelete(
                                "Delete this profile?",
                                "The profile will be marked as deleted.",
                                () -> vm.deleteProfile(user.getUid())));
                rv.setAdapter(adapter);

                // Observe both profiles and search query so the list re-filters on either change
                vm.getProfiles().observe(getViewLifecycleOwner(), users -> {
                    applyProfileFilter(vm, profileList, adapter, layoutEmpty, rv);
                });
                vm.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
                    applyProfileFilter(vm, profileList, adapter, layoutEmpty, rv);
                });
            }
        }

        /** Filters the images list by the current search query (organizer name or email). */
        private void applyImageFilter(AdminViewModel vm, List<Event> imageList,
                                      AdminImageAdapter adapter, View layoutEmpty, RecyclerView rv) {
            List<Event> source = vm.getImages().getValue();
            String query = vm.getSearchQuery().getValue() != null
                    ? vm.getSearchQuery().getValue() : "";
            imageList.clear();
            if (source != null) {
                for (Event e : source) {
                    if (query.isEmpty()) {
                        imageList.add(e);
                    } else {
                        // Match against organizer ID, title, or description
                        boolean match =
                                (e.getTitle() != null && e.getTitle().toLowerCase().contains(query))
                                        || (e.getOrganizerId() != null && e.getOrganizerId().toLowerCase().contains(query));
                        if (match) imageList.add(e);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            layoutEmpty.setVisibility(imageList.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(imageList.isEmpty() ? View.GONE : View.VISIBLE);
        }

        /** Filters the profiles list by name, email, or role. */
        private void applyProfileFilter(AdminViewModel vm, List<User> profileList,
                                        AdminProfileAdapter adapter, View layoutEmpty, RecyclerView rv) {
            List<User> source = vm.getProfiles().getValue();
            String query = vm.getSearchQuery().getValue() != null
                    ? vm.getSearchQuery().getValue() : "";
            profileList.clear();
            if (source != null) {
                for (User u : source) {
                    if (query.isEmpty()) {
                        profileList.add(u);
                    } else {
                        boolean match =
                                (u.getName()  != null && u.getName().toLowerCase().contains(query))
                                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(query))
                                        || (u.getRole()  != null && u.getRole().toLowerCase().contains(query));
                        if (match) profileList.add(u);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            layoutEmpty.setVisibility(profileList.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(profileList.isEmpty() ? View.GONE : View.VISIBLE);
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