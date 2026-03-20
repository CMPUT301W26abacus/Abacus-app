package com.example.abacus_app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * ProfileFragment
 *
 * The profile screen UI. Displays input fields and observes LiveData
 * from ProfileViewModel. Contains no business logic or data access.
 *
 * Ref: US 01.02.01–04
 */
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private StorageRepository storageRepo;

    private ImageButton btnBack;
    private ImageView   imgAvatar;
    private TextView    tvDisplayedName;
    private TextView    tvDisplayedEmail;
    private Chip        chipRole;
    private Button      btnUploadPhoto;
    private EditText    etName;
    private EditText    etEmail;
    private EditText    etPhone;
    private EditText    etBio;
    private EditText    etOrgName;
    private TextView    tvNameError;
    private TextView    tvEmailError;
    private Button      btnSave;
    private Button      btnDelete;
    private Button      btnLogout;
    private Button      btnLinkAccount;
    private Button      btnPreferences;
    private Button      btnAccessibility;
    private View        tvGuestBanner;
    private View        cardAvatar;
    private View        cardFields;
    private View        cardBio;
    private View        cardOrgName;
    private View        cardStats;
    private TextView    tvStatCount1;
    private TextView    tvStatLabel1;
    private TextView    tvStatCount2;
    private TextView    tvStatLabel2;
    private TextView    labelSection;
    private View        dangerDivider;
    private TextView    labelDanger;

    private ActivityResultLauncher<Intent> photoPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri photoUri = result.getData().getData();
                        if (photoUri != null && storageRepo != null) {
                            viewModel.uploadProfilePhoto(photoUri, storageRepo);
                        }
                    }
                });
    }

    /**
     * Inflates and returns the layout for this fragment.
     */
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_profile_fragment, container, false);
    }

    /**
     * Initializes views and observes LiveData from ProfileViewModel.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        storageRepo = new StorageRepository();

        // Bind views
        btnBack          = view.findViewById(R.id.btnBack);
        imgAvatar        = view.findViewById(R.id.imgAvatar);
        tvDisplayedName  = view.findViewById(R.id.tvDisplayedName);
        tvDisplayedEmail = view.findViewById(R.id.tvDisplayedEmail);
        chipRole         = view.findViewById(R.id.chipRole);
        btnUploadPhoto   = view.findViewById(R.id.btnUploadPhoto);
        etName           = view.findViewById(R.id.etName);
        etEmail          = view.findViewById(R.id.etEmail);
        etPhone          = view.findViewById(R.id.etPhone);
        etBio            = view.findViewById(R.id.etBio);
        etOrgName        = view.findViewById(R.id.etOrgName);
        tvNameError      = view.findViewById(R.id.tvNameError);
        tvEmailError     = view.findViewById(R.id.tvEmailError);
        btnSave          = view.findViewById(R.id.btnSaveProfile);
        btnDelete        = view.findViewById(R.id.btnDeleteProfile);
        btnLogout        = view.findViewById(R.id.btnLogout);
        btnLinkAccount   = view.findViewById(R.id.btnLinkAccount);
        btnPreferences   = view.findViewById(R.id.btnPreferences);
        btnAccessibility = view.findViewById(R.id.btnAccessibility);
        tvGuestBanner    = view.findViewById(R.id.tvGuestBanner);
        cardAvatar       = view.findViewById(R.id.cardAvatar);
        cardFields       = view.findViewById(R.id.cardFields);
        cardBio          = view.findViewById(R.id.cardBio);
        cardOrgName      = view.findViewById(R.id.cardOrgName);
        cardStats        = view.findViewById(R.id.cardStats);
        tvStatCount1     = view.findViewById(R.id.tvStatCount1);
        tvStatLabel1     = view.findViewById(R.id.tvStatLabel1);
        tvStatCount2     = view.findViewById(R.id.tvStatCount2);
        tvStatLabel2     = view.findViewById(R.id.tvStatLabel2);
        labelSection     = view.findViewById(R.id.labelSection);
        dangerDivider    = view.findViewById(R.id.dangerDivider);
        labelDanger      = view.findViewById(R.id.labelDanger);

        btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showHome();
            } else if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        boolean isGuest = true;
        if (getActivity() != null) {
            isGuest = getActivity().getIntent().getBooleanExtra("isGuest", true);
        }

        UserLocalDataSource  local  = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remote = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository       repo   = new UserRepository(local, remote);
        viewModel.init(repo, isGuest);


        viewModel.getName().observe(getViewLifecycleOwner(), name -> {
            // Update header display name
            tvDisplayedName.setText((name != null && !name.isEmpty()) ? name : "Your Name");
            // Keep edit field in sync without triggering the watcher loop
            if (!etName.getText().toString().equals(name)) {
                etName.setText(name);
                if (name != null) etName.setSelection(name.length());
            }
        });

        viewModel.getEmail().observe(getViewLifecycleOwner(), email -> {
            tvDisplayedEmail.setText((email != null && !email.isEmpty()) ? email : "");
            if (!etEmail.getText().toString().equals(email)) {
                etEmail.setText(email);
            }
        });

        viewModel.getPhone().observe(getViewLifecycleOwner(), phone -> {
            if (!etPhone.getText().toString().equals(phone)) {
                etPhone.setText(phone);
            }
        });

        viewModel.getNameError().observe(getViewLifecycleOwner(), error -> {
            tvNameError.setVisibility(error != null ? View.VISIBLE : View.GONE);
            tvNameError.setText(error != null ? error : "");
        });

        viewModel.getEmailError().observe(getViewLifecycleOwner(), error -> {
            tvEmailError.setVisibility(error != null ? View.VISIBLE : View.GONE);
            tvEmailError.setText(error != null ? error : "");
        });

        viewModel.getIsSaving().observe(getViewLifecycleOwner(), isSaving -> {
            btnSave.setEnabled(!isSaving);
            btnSave.setText(isSaving ? "Saving…" : "Save changes");
        });

        viewModel.getIsGuest().observe(getViewLifecycleOwner(), guest -> {
            if (guest) showGuestUI();
            else       refreshUIForCurrentRole();
        });

        viewModel.getRole().observe(getViewLifecycleOwner(), role -> {
            Boolean guestNow = viewModel.getIsGuest().getValue();
            if (guestNow == null || guestNow) return;
            refreshUIForCurrentRole();
        });

        viewModel.getBio().observe(getViewLifecycleOwner(), bio -> {
            if (etBio != null && !etBio.getText().toString().equals(bio)) {
                etBio.setText(bio);
            }
        });

        viewModel.getProfilePhotoUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.isEmpty() && imgAvatar != null) {
                Glide.with(this).load(url).circleCrop()
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(imgAvatar);
            }
        });

        viewModel.getOrganizationName().observe(getViewLifecycleOwner(), orgName -> {
            if (etOrgName != null && !etOrgName.getText().toString().equals(orgName)) {
                etOrgName.setText(orgName);
            }
        });

        viewModel.getEventsCreated().observe(getViewLifecycleOwner(), count -> {
            if (tvStatCount1 != null) tvStatCount1.setText(String.valueOf(count));
        });

        viewModel.getTotalRegistrations().observe(getViewLifecycleOwner(), count -> {
            if (tvStatCount2 != null) tvStatCount2.setText(String.valueOf(count));
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearToast();
            }
        });

        viewModel.getProfileDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (deleted != null && deleted) {
                etName.setText("");
                etEmail.setText("");
                etPhone.setText("");
                tvDisplayedName.setText("Your Name");
                tvDisplayedEmail.setText("");
            }
        });


        etName.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                viewModel.setName(s.toString());
            }
        });

        etEmail.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                viewModel.setEmail(s.toString());
            }
        });

        etPhone.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                viewModel.setPhone(s.toString());
            }
        });

        etBio.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                viewModel.setBio(s.toString());
            }
        });

        etOrgName.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                viewModel.setOrganizationName(s.toString());
            }
        });

        btnSave.setOnClickListener(v -> viewModel.saveProfile());

        btnDelete.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Profile")
                        .setMessage("Are you sure? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteProfile())
                        .setNegativeButton("Cancel", null)
                        .show());

        btnLogout.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Log out")
                        .setMessage("Are you sure you want to log out?")
                        .setPositiveButton("Log out", (dialog, which) -> viewModel.logout())
                        .setNegativeButton("Cancel", null)
                        .show());

        btnLinkAccount.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));

        btnUploadPhoto.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK);
            pick.setType("image/*");
            photoPickerLauncher.launch(pick);
        });

        btnPreferences.setOnClickListener(v ->
                Navigation.findNavController(requireView())
                        .navigate(R.id.preferencesFragment));

        btnAccessibility.setOnClickListener(v ->
                Navigation.findNavController(requireView())
                        .navigate(R.id.accessibilityFragment));

        viewModel.loadProfile();
    }

    /**
     * Displays UI elements for a guest user.
     *
     * @implNote This method hides editable fields and buttons, and shows a banner
     *           encouraging the user to link an account.
     */
    private void showGuestUI() {
        cardAvatar.setVisibility(View.GONE);
        labelSection.setVisibility(View.GONE);
        cardFields.setVisibility(View.GONE);
        cardBio.setVisibility(View.GONE);
        cardOrgName.setVisibility(View.GONE);
        cardStats.setVisibility(View.GONE);
        chipRole.setVisibility(View.GONE);
        btnUploadPhoto.setVisibility(View.GONE);
        tvGuestBanner.setVisibility(View.VISIBLE);
        btnLinkAccount.setVisibility(View.VISIBLE);
        btnAccessibility.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.GONE);
        btnLogout.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        btnPreferences.setVisibility(View.GONE);
        dangerDivider.setVisibility(View.GONE);
        labelDanger.setVisibility(View.GONE);
    }

    /**
     * Refreshes the logged-in UI based on current role from the ViewModel.
     */
    private void refreshUIForCurrentRole() {
        String role = viewModel.getRole().getValue();
        boolean isOrganizer = "organizer".equals(role);

        cardAvatar.setVisibility(View.VISIBLE);
        labelSection.setVisibility(View.VISIBLE);
        cardFields.setVisibility(View.VISIBLE);
        cardBio.setVisibility(View.VISIBLE);
        cardStats.setVisibility(View.VISIBLE);
        tvGuestBanner.setVisibility(View.GONE);
        btnLinkAccount.setVisibility(View.GONE);
        btnSave.setVisibility(View.VISIBLE);
        btnLogout.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        btnUploadPhoto.setVisibility(View.VISIBLE);
        btnAccessibility.setVisibility(View.VISIBLE);
        dangerDivider.setVisibility(View.VISIBLE);
        labelDanger.setVisibility(View.VISIBLE);
        chipRole.setVisibility(View.VISIBLE);
        etName.setEnabled(true);
        etEmail.setEnabled(true);
        etPhone.setEnabled(true);

        // Role chip
        chipRole.setText(isOrganizer ? "Organizer" : "Entrant");
        int chipColor = isOrganizer
                ? android.graphics.Color.parseColor("#F97316")
                : android.graphics.Color.parseColor("#3B82F6");
        chipRole.setChipBackgroundColor(
                android.content.res.ColorStateList.valueOf(chipColor));
        chipRole.setTextColor(android.graphics.Color.WHITE);

        // Role-specific cards
        cardOrgName.setVisibility(isOrganizer ? View.VISIBLE : View.GONE);
        btnPreferences.setVisibility(isOrganizer ? View.GONE : View.VISIBLE);

        // Role-specific stats labels
        if (isOrganizer) {
            tvStatLabel1.setText("Events Created");
            tvStatLabel2.setText("Total Registrations");
        } else {
            tvStatLabel1.setText("Events Joined");
            tvStatLabel2.setText("Events Won");
        }
    }

    /**
     * Simple TextWatcher implementation for EditText fields.
     */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}