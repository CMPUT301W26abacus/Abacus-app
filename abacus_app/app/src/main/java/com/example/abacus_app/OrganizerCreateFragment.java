package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * UI Controller for the event creation screen.
 * Owner: Himesh
 */
public class OrganizerCreateFragment extends Fragment {

    private CreateEventViewModel viewModel;
    private EditText etTitle, etDescription, etLimit, etPosterUrl, etEventCapacity;
    private Button btnSetStart, btnSetEnd, btnCreate;
    private MaterialSwitch switchGeo, switchPrivate;
    private CheckBox cbLimit;

    private Timestamp startTimestamp, endTimestamp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organizer_create_fragment, container, false);

        viewModel       = new ViewModelProvider(this).get(CreateEventViewModel.class);
        etTitle         = view.findViewById(R.id.et_event_title);
        etDescription   = view.findViewById(R.id.et_event_description);
        etLimit         = view.findViewById(R.id.et_waitlist_limit);
        etEventCapacity = view.findViewById(R.id.et_event_capacity);
        etPosterUrl     = view.findViewById(R.id.et_poster_url);
        btnSetStart     = view.findViewById(R.id.btn_set_start);
        btnSetEnd       = view.findViewById(R.id.btn_set_end);
        btnCreate       = view.findViewById(R.id.btn_create_event);
        switchGeo       = view.findViewById(R.id.switch_geo);
        switchPrivate   = view.findViewById(R.id.switch_private);
        cbLimit         = view.findViewById(R.id.cb_limit_waitlist);

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

    /**
     * Shows a date picker followed by a time picker to set event timestamps.
     * Fixes the issue where UTC selection from MaterialDatePicker shifted the local date.
     */
    private void showDateTimePicker(boolean isStart) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(12)
                    .setMinute(0)
                    .setTitleText("Select Time")
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                Calendar calendar = Calendar.getInstance();

                // selection is UTC midnight. Extract year/month/day in UTC to avoid timezone shifts.
                Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utcCalendar.setTimeInMillis(selection);

                calendar.set(utcCalendar.get(Calendar.YEAR),
                        utcCalendar.get(Calendar.MONTH),
                        utcCalendar.get(Calendar.DAY_OF_MONTH),
                        timePicker.getHour(),
                        timePicker.getMinute(),
                        0);
                calendar.set(Calendar.MILLISECOND, 0);

                Timestamp ts = new Timestamp(new Date(calendar.getTimeInMillis()));

                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());
                String formatted = sdf.format(calendar.getTime());

                if (isStart) {
                    startTimestamp = ts;
                    btnSetStart.setText("Start: " + formatted);
                } else {
                    endTimestamp = ts;
                    btnSetEnd.setText("End: " + formatted);
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

        if (endTimestamp.compareTo(startTimestamp) <= 0) {
            Toast.makeText(getContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer waitlistLimit = null;
        if (cbLimit.isChecked()) {
            try {
                waitlistLimit = Integer.parseInt(etLimit.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid waitlist limit", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Integer eventCapacity = null;
        String capacityStr = etEventCapacity.getText().toString().trim();
        if (!capacityStr.isEmpty()) {
            try {
                eventCapacity = Integer.parseInt(capacityStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid event capacity", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        String organizerId = local.getUUIDSync();
        if (organizerId == null) organizerId = "ORGANIZER_ID";

        Event event = new Event(null, title, desc, organizerId, startTimestamp, endTimestamp,
                waitlistLimit, eventCapacity, switchGeo.isChecked(), false);
        event.setPrivate(switchPrivate.isChecked());

        String posterUrl = etPosterUrl.getText().toString().trim();
        viewModel.createEvent(event, posterUrl);
    }
}