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
 * Admin-only fragment that surfaces two moderation tabs — "Images" and "Profiles" — inside
 * a {@link ViewPager2} with a shared {@link TabLayout}.
 *
 * <p><b>Role in the application:</b> This is the main entry point for the moderation workflow.
 * Administrators land here after passing the role check in the navigation graph. The two tabs
 * allow them to (a) remove inappropriate poster images from events and (b) soft-delete user
 * profiles that violate community guidelines.
 *
 * <p><b>Design pattern:</b> The fragment acts as a thin host. All data and business logic live
 * in {@link AdminViewModel}, which is scoped to the Activity so that both the outer fragment
 * and its child {@link AdminTabFragment} instances share the same instance. The search bar in
 * this fragment writes to {@link AdminViewModel#setSearchQuery(String)}, and both child tabs
 * observe that query to re-filter their own lists reactively.
 *
 * <p><b>Navigation bar insets:</b> Bottom padding is applied at runtime using
 * {@link WindowInsetsCompat} so the last list item is never hidden behind the system nav bar.
 * No hardcoded pixel values are used.
 *
 * <p><b>Known issues / outstanding work:</b>
 * <ul>
 *   <li>The search bar is not reset when the user navigates away and returns. Consider saving
 *       and restoring the query text via {@code onSaveInstanceState}.</li>
 *   <li>{@link AdminViewModel#getError()} fires again after rotation because it is a plain
 *       {@link androidx.lifecycle.MutableLiveData}, not a {@code SingleLiveEvent}. Duplicate
 *       Toast messages may appear on device rotation during an active error state.</li>
 *   <li>Access control is enforced by the navigation graph, but this fragment does not
 *       independently verify the user's role at runtime. A middleware check would add
 *       defence-in-depth.</li>
 * </ul>
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

        // Role guard: only admins can access this fragment
        String role = ((MainActivity) requireActivity()).getEffectiveRole();
        if (!"admin".equals(role)) {
            requireActivity().onBackPressed();
            return;
        }

        // ViewModel is scoped to the Activity so child tabs share the same instance.
        viewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        // Debug observers — confirm LiveData emissions reach this fragment. Safe to remove in prod.
        viewModel.getProfiles().observe(getViewLifecycleOwner(), users ->
                Log.d("AdminLogsFragment", "Profiles LiveData fired, size=" + (users != null ? users.size() : "null")));
        viewModel.getImages().observe(getViewLifecycleOwner(), events ->
                Log.d("AdminLogsFragment", "Images LiveData fired, size=" + (events != null ? events.size() : "null")));

        // Pull-to-refresh reloads both collections from Firestore.
        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.admin_logs_swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadImages();
            viewModel.loadProfiles();
            swipeRefresh.setRefreshing(false); // spinner is hidden immediately; data streams asynchronously
        });

        ViewPager2 viewPager = view.findViewById(R.id.view_pager);
        TabLayout tabLayout  = view.findViewById(R.id.tab_layout);

        viewPager.setAdapter(new AdminPagerAdapter(this));

        // Attach tab titles to ViewPager2 pages.
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Images" : "Profiles");
        }).attach();

        // Apply the real navigation-bar height as bottom padding so no content is occluded.
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, 0, 0, navBarHeight);
            ((ViewGroup) v).setClipToPadding(false);
            return insets;
        });

        // Shared search bar: pushes lowercase, trimmed query into the ViewModel.
        // Both child tabs observe getSearchQuery() and re-filter their lists on each change.
        TextInputEditText searchBar = view.findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString().trim().toLowerCase());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Hide the soft keyboard when the user submits the search.
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

        // Kick off the initial data loads.
        viewModel.loadImages();
        viewModel.loadProfiles();

        // Surface any Firestore errors as a short Toast.
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });
    }

    // ── Pager adapter ─────────────────────────────────────────────────────────

    /**
     * Two-page adapter for the admin {@link ViewPager2}.
     *
     * <p>Page 0 is the Images tab; page 1 is the Profiles tab. Each page is an
     * {@link AdminTabFragment} with a {@code tab} argument that determines which
     * data source and adapter it wires up.
     */
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

    /**
     * A single tab page inside the admin logs {@link ViewPager2}.
     *
     * <p>Constructed via the {@link #newImagesTab()} or {@link #newProfilesTab()} factory
     * methods, which embed a {@code tab} argument that selects either the image or profile
     * dataset. The fragment shares the parent Activity's {@link AdminViewModel} so it
     * automatically reflects search queries typed in the outer {@link AdminLogsFragment}.
     *
     * <p>Filtering is done in-memory on the main thread (see {@link #applyImageFilter} and
     * {@link #applyProfileFilter}). For datasets larger than a few hundred rows this should
     * be moved to a background thread or replaced with a Firestore server-side query.
     */
    public static class AdminTabFragment extends Fragment {

        private static final String ARG_TAB      = "tab";
        private static final int    TAB_IMAGES   = 0;
        private static final int    TAB_PROFILES = 1;

        /**
         * Creates a new tab fragment pre-configured for the Images dataset.
         *
         * @return a new {@link AdminTabFragment} with {@code tab=TAB_IMAGES}
         */
        public static AdminTabFragment newImagesTab() {
            AdminTabFragment f = new AdminTabFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_TAB, TAB_IMAGES);
            f.setArguments(args);
            return f;
        }

        /**
         * Creates a new tab fragment pre-configured for the Profiles dataset.
         *
         * @return a new {@link AdminTabFragment} with {@code tab=TAB_PROFILES}
         */
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

                // Re-filter whenever the data OR the search query changes.
                vm.getImages().observe(getViewLifecycleOwner(), events ->
                        applyImageFilter(vm, imageList, adapter, layoutEmpty, rv));
                vm.getSearchQuery().observe(getViewLifecycleOwner(), query ->
                        applyImageFilter(vm, imageList, adapter, layoutEmpty, rv));

            } else {
                List<User> profileList = new ArrayList<>();
                AdminProfileAdapter adapter = new AdminProfileAdapter(profileList, user ->
                        confirmDelete(
                                "Delete this profile?",
                                "The profile will be marked as deleted.",
                                () -> vm.deleteProfile(user.getUid())));
                rv.setAdapter(adapter);

                // Re-filter whenever the data OR the search query changes.
                vm.getProfiles().observe(getViewLifecycleOwner(), users ->
                        applyProfileFilter(vm, profileList, adapter, layoutEmpty, rv));
                vm.getSearchQuery().observe(getViewLifecycleOwner(), query ->
                        applyProfileFilter(vm, profileList, adapter, layoutEmpty, rv));
            }
        }

        /**
         * Filters the full images list from the ViewModel by the current search query and
         * updates the adapter. Matching is case-insensitive against the event title,
         * organizer ID, and organizer email.
         *
         * <p>An empty query shows all events. The empty-state view is shown/hidden based
         * on whether the filtered list is empty.
         *
         * @param vm          the shared ViewModel providing images and the search query
         * @param imageList   the mutable backing list for the RecyclerView adapter
         * @param adapter     the adapter to notify after modifying {@code imageList}
         * @param layoutEmpty the empty-state view to toggle
         * @param rv          the RecyclerView to toggle
         */
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
                        boolean match =
                                (e.getTitle()        != null && e.getTitle().toLowerCase().contains(query))
                                        || (e.getOrganizerId()  != null && e.getOrganizerId().toLowerCase().contains(query))
                                        || (e.getOrganizerEmail()!= null && e.getOrganizerEmail().toLowerCase().contains(query));
                        if (match) imageList.add(e);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            layoutEmpty.setVisibility(imageList.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(imageList.isEmpty() ? View.GONE : View.VISIBLE);
        }

        /**
         * Filters the full profiles list from the ViewModel by the current search query and
         * updates the adapter. Already-deleted users are excluded unconditionally; remaining
         * users are matched case-insensitively against name, email, and role.
         *
         * <p>An empty query shows all active (non-deleted) profiles. The empty-state view
         * is shown/hidden based on whether the filtered list is empty.
         *
         * @param vm          the shared ViewModel providing profiles and the search query
         * @param profileList the mutable backing list for the RecyclerView adapter
         * @param adapter     the adapter to notify after modifying {@code profileList}
         * @param layoutEmpty the empty-state view to toggle
         * @param rv          the RecyclerView to toggle
         */
        private void applyProfileFilter(AdminViewModel vm, List<User> profileList,
                                        AdminProfileAdapter adapter, View layoutEmpty, RecyclerView rv) {
            List<User> source = vm.getProfiles().getValue();
            String query = vm.getSearchQuery().getValue() != null
                    ? vm.getSearchQuery().getValue() : "";
            profileList.clear();
            if (source != null) {
                for (User u : source) {
                    if (u.isDeleted()) continue; // never show deactivated accounts in this list
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

        /**
         * Shows a standard {@link AlertDialog} asking the admin to confirm a destructive action.
         *
         * @param title     the dialog title (e.g. "Delete this profile?")
         * @param message   the explanatory message shown below the title
         * @param onConfirm runnable invoked only if the admin taps "Delete"; not called on cancel
         */
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