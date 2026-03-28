package com.example.mylearning;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.databinding.ActivityForgotPasswordBinding;
import com.example.mylearning.util.HashUtil;
import com.example.mylearning.util.ToolbarUtil;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AppDatabase db;

    // Tracks which step the user is on
    private enum Step { FIND_ACCOUNT, VERIFY_ANSWER, RESET_PASSWORD }
    private Step currentStep = Step.FIND_ACCOUNT;
    private User foundUser; // held in memory between steps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAction.setOnClickListener(v -> handleAction());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Reset Password", true);
    }

    private void handleAction() {
        switch (currentStep) {
            case FIND_ACCOUNT: findAccount(); break;
            case VERIFY_ANSWER: verifyAnswer(); break;
            case RESET_PASSWORD: resetPassword(); break;
        }
    }

    // Step 1 — look up account by email
    private void findAccount() {
        binding.layoutEmail.setError(null);
        String email = binding.editEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.layoutEmail.setError("Enter your email");
            return;
        }

        setLoading(true);
        AsyncTask.execute(() -> {
            User user = db.userDao().findByEmail(email);
            runOnUiThread(() -> {
                setLoading(false);
                if (user == null) {
                    binding.layoutEmail.setError("No account found for this email");
                } else {
                    foundUser = user;
                    showSecurityQuestion(user.securityQuestion);
                }
            });
        });
    }

    // Reveal security question and advance to step 2
    private void showSecurityQuestion(String question) {
        binding.labelSecurityQuestion.setText(question);
        binding.labelSecurityQuestion.setVisibility(View.VISIBLE);
        binding.layoutSecurityAnswer.setVisibility(View.VISIBLE);
        binding.editEmail.setEnabled(false); // lock email field
        binding.btnAction.setText("Verify Answer");
        currentStep = Step.VERIFY_ANSWER;
    }

    // Step 2 — check security answer hash
    private void verifyAnswer() {
        binding.layoutSecurityAnswer.setError(null);
        String answer = binding.editSecurityAnswer.getText().toString().trim();

        if (TextUtils.isEmpty(answer)) {
            binding.layoutSecurityAnswer.setError("Enter your answer");
            return;
        }

        String hash = HashUtil.sha256(answer.toLowerCase());
        if (!hash.equals(foundUser.securityAnswerHash)) {
            binding.layoutSecurityAnswer.setError("Incorrect answer");
            return;
        }

        // Answer correct — show new password fields
        binding.layoutNewPassword.setVisibility(View.VISIBLE);
        binding.layoutConfirmNewPassword.setVisibility(View.VISIBLE);
        binding.layoutSecurityAnswer.setEnabled(false);
        binding.btnAction.setText("Reset Password");
        currentStep = Step.RESET_PASSWORD;
    }

    // Step 3 — save new password hash
    private void resetPassword() {
        binding.layoutNewPassword.setError(null);
        binding.layoutConfirmNewPassword.setError(null);

        String newPass = binding.editNewPassword.getText().toString();
        String confirm = binding.editConfirmNewPassword.getText().toString();

        if (newPass.length() < 3) {
            binding.layoutNewPassword.setError("At least 3 characters");
            return;
        }
        if (!newPass.equals(confirm)) {
            binding.layoutConfirmNewPassword.setError("Passwords do not match");
            return;
        }

        setLoading(true);
        String newHash = HashUtil.sha256(newPass);

        AsyncTask.execute(() -> {
            db.userDao().updatePassword(foundUser.id, newHash);
            runOnUiThread(() -> {
                setLoading(false);
                // Go back to login — password reset done
                finish();
            });
        });
    }

    private void setLoading(boolean loading) {
        binding.btnAction.setEnabled(!loading);
        if (loading) binding.btnAction.setText("Please wait…");
    }
}