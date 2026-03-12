package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;

/**
 * UI Controller for the event creation screen.
 * Owner: Himesh
 */
public class OrganizerCreateFragment extends Fragment {

    private CreateEventViewModel viewModel;
    private EditText etTitle, etDescription, etLimit, etPosterUrl;
    private Button btnSetStart, btnSetEnd, btnCreate;
    private MaterialSwitch switchGeo;
    private CheckBox cbLimit;

    private Timestamp startTimestamp, endTimestamp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organizer_create_fragment, container, false);

        viewModel     = new ViewModelProvider(this).get(CreateEventViewModel.class);
        etTitle       = view.findViewById(R.id.et_event_title);
        etDescription = view.findViewById(R.id.et_event_description);
        etLimit       = view.findViewById(R.id.et_waitlist_limit);
        etPosterUrl   = view.findViewById(R.id.et_poster_url);
        btnSetStart   = view.findViewById(R.id.btn_set_start);
        btnSetEnd     = view.findViewById(R.id.btn_set_end);
        btnCreate     = view.findViewById(R.id.btn_create_event);
        switchGeo     = view.findViewById(R.id.switch_geo);
        cbLimit       = view.findViewById(R.id.cb_limit_waitlist);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
            }
        });

        cbLimit.setOnCheckedChangeListener((v, isChecked) -> etLimit.setEnabled(isChecked));
        btnSetStart.setOnClickListener(v -> showDateTimePicker(true));
        btnSetEnd.setOnClickListener(v -> showDateTimePicker(false));
        btnCreate.setOnClickListener(v -> createEvent());

        observeViewModel();
        return view;
    }

    private void observeViewModel() {
        viewModel.getIsSaving().observe(getViewLifecycleOwner(), saving -> {
            btnCreate.setEnabled(!saving);
            btnCreate.setText(saving ? "Creating…" : "Create Event");
        });

        viewModel.getEventCreated().observe(getViewLifecycleOwner(), created -> {
            if (created) {
                Toast.makeText(getContext(), "Event Created!", Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showHome();
                }
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
        });
    }

    private void showDateTimePicker(boolean isStart) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker().build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(12)
                    .setMinute(0)
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selection);
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                calendar.set(Calendar.MINUTE, timePicker.getMinute());

                Timestamp ts = new Timestamp(new Date(calendar.getTimeInMillis()));
                if (isStart) {
                    startTimestamp = ts;
                    btnSetStart.setText("Start: " + calendar.getTime().toString());
                } else {
                    endTimestamp = ts;
                    btnSetEnd.setText("End: " + calendar.getTime().toString());
                }
            });
            timePicker.show(getParentFragmentManager(), "TIME_PICKER");
        });
        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void createEvent() {
        String title = etTitle.getText().toString().trim();
        String desc  = etDescription.getText().toString().trim();

        if (title.isEmpty() || startTimestamp == null || endTimestamp == null) {
            Toast.makeText(getContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer limit = null;
        if (cbLimit.isChecked()) {
            try {
                limit = Integer.parseInt(etLimit.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid limit", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Use real UUID instead of hardcoded string
        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        String organizerId = local.getUUIDSync();
        if (organizerId == null) organizerId = "ORGANIZER_ID";

        Event event = new Event(null, title, desc, organizerId, startTimestamp, endTimestamp,
                limit, switchGeo.isChecked());
        String posterUrl = etPosterUrl.getText().toString().trim();
        viewModel.createEvent(event, posterUrl);
    }
}