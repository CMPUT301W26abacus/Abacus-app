package com.example.abacus_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class test_LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_loginpage);

        mAuth = FirebaseAuth.getInstance();

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn   = findViewById(R.id.btnSignIn);
        TextView tvForgot   = findViewById(R.id.tvForgot);
        TextView tvSignUp   = findViewById(R.id.tvSignUp);
        Button  btnSSO      = findViewById(R.id.btnSSO);

        btnSignIn.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        // TODO: navigate to main screen once ready
                        // startActivity(new Intent(this, MainActivity.class));
                        // finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        tvForgot.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, test_RegisterActivity.class));
        });

        btnSSO.setOnClickListener(v -> {
            // TODO: Add SSO logic
            Toast.makeText(this, "SSO tapped", Toast.LENGTH_SHORT).show();
        });
    }
}
