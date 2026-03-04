package com.example.abacus_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class test_SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_splashpage);

        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(test_SplashActivity.this, test_LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
