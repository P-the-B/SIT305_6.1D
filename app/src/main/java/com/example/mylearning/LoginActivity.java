package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.databinding.ActivityLoginBinding;
import com.example.mylearning.util.HashUtil;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.TopicSeeder;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AppDatabase db;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);

        // Skip login if already logged in
        if (session.isLoggedIn()) {
            goToHome();
            return;
        }

        // Seed topics on first launch — IGNORE conflict means safe to call every time
        AsyncTask.execute(() -> db.topicDao().insertAll(TopicSeeder.getTopics()));

        // 'Done' on keyboard submits login
        binding.editPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.linkSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        binding.linkForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    private void attemptLogin() {
        // Clear previous errors
        binding.layoutEmail.setError(null);
        binding.layoutPassword.setError(null);

        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString();

        // Inline validation
        if (TextUtils.isEmpty(email)) {
            binding.layoutEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.layoutPassword.setError("Password is required");
            return;
        }

        setLoading(true);

        String hash = HashUtil.sha256(password);

        // DB query off main thread
        AsyncTask.execute(() -> {
            User user = db.userDao().findByEmailAndPassword(email, hash);
            runOnUiThread(() -> {
                setLoading(false);
                if (user != null) {
                    session.saveSession(user.id, user.username);
                    goToHome();
                } else {
                    // Don't reveal which field is wrong — generic message
                    binding.layoutPassword.setError("Invalid email or password");
                }
            });
        });
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setText(loading ? "Logging in…" : getString(R.string.btn_login));
    }

    private void goToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish(); // remove login from back stack
    }
}