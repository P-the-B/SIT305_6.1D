package com.example.mylearning;

import com.example.mylearning.BuildConfig;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.databinding.ActivityLessonBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.ToolbarUtil;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LessonActivity extends AppCompatActivity {

    private ActivityLessonBinding binding;
    private AppDatabase db;
    private int topicId;
    private String topicName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLessonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, "Short Topic Lesson", true);

        db = AppDatabase.getInstance(this);
        topicId = getIntent().getIntExtra("topicId", -1);
        topicName = getIntent().getStringExtra("topicName");

        binding.tvTopicName.setText(topicName);

        binding.btnStartQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("topicId", topicId);
            intent.putExtra("topicName", topicName);
            startActivity(intent);
            finish();
        });

        loadLesson();
    }

    private void loadLesson() {
        AsyncTask.execute(() -> {
            List<QuizQuestion> starred = db.quizQuestionDao().getStarredForTopic(topicId);
            runOnUiThread(() -> {
                if (!starred.isEmpty()) {
                    showStarredMode(starred);
                } else {
                    showSummaryMode();
                }
            });
        });
    }

    // User has starred questions — show them as expandable cards with AI explanations
    private void showStarredMode(List<QuizQuestion> starred) {
        binding.tvModeLabel.setText("Based on your saved questions");
        binding.containerStarred.setVisibility(View.VISIBLE);

        for (QuizQuestion q : starred) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_starred_question, binding.containerStarred, false);

            TextView tvQuestion = item.findViewById(R.id.tvQuestion);
            LinearLayout rowQuestion = item.findViewById(R.id.rowQuestion);
            LinearLayout containerExplanation = item.findViewById(R.id.containerExplanation);

            tvQuestion.setText(q.questionText);

            // Tap to expand and load explanation
            rowQuestion.setOnClickListener(v -> {
                if (containerExplanation.getVisibility() == View.VISIBLE) {
                    containerExplanation.setVisibility(View.GONE);
                } else {
                    containerExplanation.setVisibility(View.VISIBLE);
                    // Only fetch if not already loaded
                    TextView tvExp = item.findViewById(R.id.tvExplanation);
                    if (tvExp.getVisibility() != View.VISIBLE) {
                        fetchStarredExplanation(item, q);
                    }
                }
            });

            binding.containerStarred.addView(item);
        }
    }

    // No starred questions — auto-generate a topic summary
    private void showSummaryMode() {
        binding.tvModeLabel.setText("General topic summary");
        binding.tvLoading.setVisibility(View.VISIBLE);

        String prompt = "Give me a concise 2-minute study summary of: " + topicName + ".\n"
                + "Format as exactly 5 bullet points starting with •\n"
                + "Each bullet should cover a key concept. Keep language clear and educational.\n"
                + "No introduction, no conclusion — just the 5 bullets.";

        GeminiClient.getInstance().generateContent(
                BuildConfig.GEMINI_API_KEY,
                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                runOnUiThread(() -> {
                    binding.tvLoading.setVisibility(View.GONE);
                    if (text != null) {
                        binding.tvSummary.setText(text);
                        binding.tvSummary.setVisibility(View.VISIBLE);
                    } else {
                        showSummaryError();
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    binding.tvLoading.setVisibility(View.GONE);
                    showSummaryError();
                });
            }
        });
    }

    // Fetches a bullet-point explanation for a single starred question
    private void fetchStarredExplanation(View item, QuizQuestion q) {
        TextView tvLoading = item.findViewById(R.id.tvLoading);
        TextView tvExplanation = item.findViewById(R.id.tvExplanation);
        TextView tvError = item.findViewById(R.id.tvError);

        tvLoading.setVisibility(View.VISIBLE);
        tvExplanation.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);

        String prompt = "In 3 concise bullet points starting with •, explain the key concept behind this question:\n"
                + q.questionText + "\n"
                + "The correct answer is: " + q.correctAnswer + "\n"
                + "Keep it simple, clear, and educational.";

        GeminiClient.getInstance().generateContent(
                BuildConfig.GEMINI_API_KEY,
                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    if (text != null) {
                        tvExplanation.setText(text);
                        tvExplanation.setVisibility(View.VISIBLE);
                    } else {
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    tvLoading.setVisibility(View.GONE);
                    tvError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showSummaryError() {
        binding.containerError.setVisibility(View.VISIBLE);
        binding.btnRetry.setOnClickListener(v -> {
            binding.containerError.setVisibility(View.GONE);
            showSummaryMode();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}