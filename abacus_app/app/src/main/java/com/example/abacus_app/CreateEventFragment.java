package com.example.abacus_app;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
public class CreateEventFragment extends Fragment {

    private CreateEventViewModel viewModel;
    private EditText etTitle, etDescription, etLimit;
    private Button btnSetStart, btnSetEnd, btnUpload, btnCreate;
    private MaterialSwitch switchGeo;
    private CheckBox cbLimit;
    private ImageView ivPoster;
    
    private Timestamp startTimestamp, endTimestamp;
    private Uri posterUri;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    posterUri = uri;
                    ivPoster.setImageURI(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organizer_create_fragment, container, false);
        
        viewModel = new ViewModelProvider(this).get(CreateEventViewModel.class);
        
        etTitle = view.findViewById(R.id.et_event_title);
        etDescription = view.findViewById(R.id.et_event_description);
        etLimit = view.findViewById(R.id.et_waitlist_limit);
        btnSetStart = view.findViewById(R.id.btn_set_start);
        btnSetEnd = view.findViewById(R.id.btn_set_end);
        btnUpload = view.findViewById(R.id.btn_upload_poster);
        btnCreate = view.findViewById(R.id.btn_create_event);
        switchGeo = view.findViewById(R.id.switch_geo);
        cbLimit = view.findViewById(R.id.cb_limit_waitlist);
        ivPoster = view.findViewById(R.id.iv_poster_preview);

        cbLimit.setOnCheckedChangeListener((v, isChecked) -> etLimit.setEnabled(isChecked));
        btnSetStart.setOnClickListener(v -> showDateTimePicker(true));
        btnSetEnd.setOnClickListener(v -> showDateTimePicker(false));
        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnCreate.setOnClickListener(v -> createEvent());

        observeViewModel();

        return view;
    }

    private void observeViewModel() {
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
        String desc = etDescription.getText().toString().trim();
        
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

        // Replace "ORGANIZER_ID" with actual ID if available, or fetch it from preferences/auth
        Event event = new Event(null, title, desc, "ORGANIZER_ID", startTimestamp, endTimestamp, limit, switchGeo.isChecked());
        viewModel.createEvent(event, posterUri);
    }
}
