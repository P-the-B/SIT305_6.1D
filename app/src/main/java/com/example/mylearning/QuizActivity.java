package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.QuizAttempt;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.databinding.ActivityQuizBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.SessionManager;
import com.example.mylearning.util.ToolbarUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QuizActivity extends AppCompatActivity {

    private ActivityQuizBinding binding;
    private AppDatabase db;
    private SessionManager session;

    private int topicId;
    private String topicName;
    private List<QuizQuestion> questions = new ArrayList<>();
    private int currentIndex = 0;
    private long attemptId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);

        topicId = getIntent().getIntExtra("topicId", -1);
        topicName = getIntent().getStringExtra("topicName");

        setSupportActionBar(binding.toolbar);
        ToolbarUtil.setup(this, binding.toolbar, topicName, false);

        showLoading(true);
        fetchQuestionsFromGemini();

        binding.btnNext.setOnClickListener(v -> handleNext());
        binding.btnQuit.setOnClickListener(v -> confirmQuit());
    }

    // Builds a structured prompt and asks Gemini for 3 quiz questions
    private void fetchQuestionsFromGemini() {
        String prompt = "Generate a quiz with exactly 3 multiple choice questions about: " + topicName + ".\n"
                + "Respond ONLY with a valid JSON array, no markdown, no explanation.\n"
                + "Format:\n"
                + "[{\"question\":\"...\",\"optionA\":\"...\",\"optionB\":\"...\",\"optionC\":\"...\",\"optionD\":\"...\",\"answer\":\"A\"}]\n"
                + "answer must be exactly one of: A, B, C, or D.";

        GeminiClient.getInstance().generateContent(
                BuildConfig.GEMINI_API_KEY,
                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String text = response.body().getResponseText();
                    parseAndStartQuiz(text);
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                showError();
            }
        });
    }

    // Parses Gemini JSON response into QuizQuestion objects
    private void parseAndStartQuiz(String json) {
        try {
            // Strip any accidental markdown fences Gemini sometimes adds
            json = json.replaceAll("```json", "").replaceAll("```", "").trim();

            JSONArray array = new JSONArray(json);
            List<QuizQuestion> parsed = new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                QuizQuestion q = new QuizQuestion();
                q.topicId = topicId;
                q.questionText = obj.getString("question");
                q.optionA = obj.getString("optionA");
                q.optionB = obj.getString("optionB");
                q.optionC = obj.getString("optionC");
                q.optionD = obj.getString("optionD");
                q.correctAnswer = obj.getString("answer").toUpperCase();
                q.isStarred = false;
                parsed.add(q);
            }

            if (parsed.isEmpty()) {
                showError();
                return;
            }

            // Create the attempt record now so we have an ID to attach questions to
            AsyncTask.execute(() -> {
                QuizAttempt attempt = new QuizAttempt();
                attempt.userId = session.getUserId();
                attempt.topicId = topicId;
                attempt.score = 0;
                attempt.totalQuestions = 0;
                attempt.timestamp = System.currentTimeMillis();
                attemptId = db.quizAttemptDao().insert(attempt);

                // Assign attemptId to all questions
                for (QuizQuestion q : parsed) q.attemptId = (int) attemptId;

                runOnUiThread(() -> {
                    questions = parsed;
                    showLoading(false);
                    displayQuestion(0);
                });
            });

        } catch (Exception e) {
            showError();
        }
    }

    private void displayQuestion(int index) {
        QuizQuestion q = questions.get(index);

        binding.tvQuestionCounter.setText("Question " + (index + 1) + " / " + questions.size());
        binding.tvQuestion.setText(q.questionText);
        binding.radioA.setText("A.  " + q.optionA);
        binding.radioB.setText("B.  " + q.optionB);
        binding.radioC.setText("C.  " + q.optionC);
        binding.radioD.setText("D.  " + q.optionD);
        binding.radioGroupAnswers.clearCheck();

        // Reflect current star state
        binding.btnStar.setImageResource(q.isStarred ? R.drawable.ic_star : R.drawable.ic_star_outline);

        // Last question changes button label
        binding.btnNext.setText(index == questions.size() - 1 ? R.string.btn_finish : R.string.btn_next);

        binding.btnStar.setOnClickListener(v -> toggleStar(q));
    }

    private void toggleStar(QuizQuestion q) {
        q.isStarred = !q.isStarred;
        binding.btnStar.setImageResource(q.isStarred ? R.drawable.ic_star : R.drawable.ic_star_outline);
        // Persist star state immediately in case of force close
        if (q.id != 0) {
            AsyncTask.execute(() -> db.quizQuestionDao().setStarred(q.id, q.isStarred));
        }
    }

    private void handleNext() {
        // Record the user's answer for current question
        QuizQuestion q = questions.get(currentIndex);
        int checkedId = binding.radioGroupAnswers.getCheckedRadioButtonId();

        if (checkedId == R.id.radioA) q.userAnswer = "A";
        else if (checkedId == R.id.radioB) q.userAnswer = "B";
        else if (checkedId == R.id.radioC) q.userAnswer = "C";
        else if (checkedId == R.id.radioD) q.userAnswer = "D";
        else q.userAnswer = null; // unanswered

        q.isCorrect = q.userAnswer != null && q.userAnswer.equals(q.correctAnswer);

        currentIndex++;
        if (currentIndex < questions.size()) {
            displayQuestion(currentIndex);
        } else {
            finishQuiz(questions.size());
        }
    }

    private void confirmQuit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.quit_dialog_title)
                .setMessage(R.string.quit_dialog_message)
                .setPositiveButton(R.string.btn_quit_confirm, (d, w) -> {
                    // Record the current question's answer before saving
                    recordCurrentAnswer();
                    finishQuiz(currentIndex + 1);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // Captures whatever the user has selected on the current question
    private void recordCurrentAnswer() {
        if (currentIndex >= questions.size()) return;
        QuizQuestion q = questions.get(currentIndex);
        int checkedId = binding.radioGroupAnswers.getCheckedRadioButtonId();
        if (checkedId == R.id.radioA) q.userAnswer = "A";
        else if (checkedId == R.id.radioB) q.userAnswer = "B";
        else if (checkedId == R.id.radioC) q.userAnswer = "C";
        else if (checkedId == R.id.radioD) q.userAnswer = "D";
        else q.userAnswer = null;
        q.isCorrect = q.userAnswer != null && q.userAnswer.equals(q.correctAnswer);
    }

    // Saves all attempted questions and updates the attempt record
    private void finishQuiz(int attempted) {
        List<QuizQuestion> attempted_qs = questions.subList(0, attempted);
        int correct = (int) attempted_qs.stream().filter(q -> q.isCorrect).count();

        AsyncTask.execute(() -> {
            db.quizQuestionDao().insertAll(new ArrayList<>(attempted_qs));

            // Update attempt with final score
            db.quizAttemptDao().updateScoreAndTotal((int) attemptId, correct, attempted);

            runOnUiThread(() -> {
                Intent intent = new Intent(this, ResultsActivity.class);
                intent.putExtra("attemptId", (int) attemptId);
                intent.putExtra("topicId", topicId);
                intent.putExtra("topicName", topicName);
                startActivity(intent);
                finish();
            });
        });
    }

    private void showLoading(boolean loading) {
        binding.tvQuestionCounter.setVisibility(loading ? View.GONE : View.VISIBLE);
        binding.radioGroupAnswers.setVisibility(loading ? View.GONE : View.VISIBLE);
        binding.btnNext.setVisibility(loading ? View.GONE : View.VISIBLE);
        binding.btnQuit.setVisibility(loading ? View.GONE : View.VISIBLE);
        // Reuse question card as loading label
        binding.tvQuestion.setText(loading ? getString(R.string.loading) : "");
    }

    private void showError() {
        runOnUiThread(() -> {
            binding.tvQuestion.setText("Failed to load questions. Please check your connection and try again.");
            showLoading(false);
            binding.btnNext.setVisibility(View.GONE);
        });
    }
}