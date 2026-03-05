package com.example.abacus_app;

import android.content.Intent;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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

    private ImageButton btnBack;
    private ImageView   imgAvatar;
    private TextView    tvDisplayedName;
    private TextView    tvDisplayedEmail;
    private EditText    etName;
    private EditText    etEmail;
    private EditText    etPhone;
    private TextView    tvNameError;
    private TextView    tvEmailError;
    private Button      btnSave;
    private Button      btnDelete;
    private Button      btnLinkAccount;
    private TextView    tvGuestBanner;
    private View        dangerDivider;
    private TextView    labelDanger;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_profile_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        btnBack          = view.findViewById(R.id.btnBack);
        imgAvatar        = view.findViewById(R.id.imgAvatar);
        tvDisplayedName  = view.findViewById(R.id.tvDisplayedName);
        tvDisplayedEmail = view.findViewById(R.id.tvDisplayedEmail);
        etName           = view.findViewById(R.id.etName);
        etEmail          = view.findViewById(R.id.etEmail);
        etPhone          = view.findViewById(R.id.etPhone);
        tvNameError      = view.findViewById(R.id.tvNameError);
        tvEmailError     = view.findViewById(R.id.tvEmailError);
        btnSave          = view.findViewById(R.id.btnSaveProfile);
        btnDelete        = view.findViewById(R.id.btnDeleteProfile);
        btnLinkAccount   = view.findViewById(R.id.btnLinkAccount);
        tvGuestBanner    = view.findViewById(R.id.tvGuestBanner);
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

        // ── Observe LiveData ──────────────────────────────────────────────

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
            else       showLoggedInUI();
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


        btnSave.setOnClickListener(v -> viewModel.saveProfile());

        btnDelete.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete Profile")
                        .setMessage("Are you sure? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteProfile())
                        .setNegativeButton("Cancel", null)
                        .show());

        btnLinkAccount.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LoginActivity.class)));

        viewModel.loadProfile();
    }

    private void showGuestUI() {
        tvGuestBanner.setVisibility(View.VISIBLE);
        btnLinkAccount.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.GONE);
        btnDelete.setVisibility(View.GONE);
        dangerDivider.setVisibility(View.GONE);
        labelDanger.setVisibility(View.GONE);
        etName.setEnabled(false);
        etEmail.setEnabled(false);
        etPhone.setEnabled(false);
    }

    private void showLoggedInUI() {
        tvGuestBanner.setVisibility(View.GONE);
        btnLinkAccount.setVisibility(View.GONE);
        btnSave.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        dangerDivider.setVisibility(View.VISIBLE);
        labelDanger.setVisibility(View.VISIBLE);
        etName.setEnabled(true);
        etEmail.setEnabled(true);
        etPhone.setEnabled(true);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}