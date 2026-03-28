package com.example.mylearning;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.data.entity.UserTopic;
import com.example.mylearning.databinding.ActivityInterestsBinding;
import com.example.mylearning.network.GeminiClient;
import com.example.mylearning.network.GeminiRequest;
import com.example.mylearning.network.GeminiResponse;
import com.example.mylearning.util.SessionManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InterestsActivity extends AppCompatActivity {

    private ActivityInterestsBinding binding;
    private AppDatabase db;
    private SessionManager session;

    private List<Topic> allTopics = new ArrayList<>();
    private final List<Topic> selectedTopics = new ArrayList<>();
    private int userId;

    private static final int MAX_SELECTIONS = 10;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInterestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        session = new SessionManager(this);

        userId = getIntent().getIntExtra("userId", session.getUserId());

        loadTopics();

        searchRunnable = () -> renderTopics(
                binding.editSearch.getText().toString().trim().toLowerCase());

        // Live filter on type
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 300);
                // Clear AI feedback when user starts typing again
                hideFeedback();
            }
            public void afterTextChanged(Editable s) {}
        });

        // Done/Enter on keyboard — trigger AI validation if no results found
        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String query = binding.editSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    handleDonePressed(query);
                }
                return true;
            }
            return false;
        });

        binding.btnContinue.setOnClickListener(v -> saveAndContinue());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
    }

    private void loadTopics() {
        AsyncTask.execute(() -> {
            allTopics = db.topicDao().getAllTopics();

            // Clear before loading to prevent duplicates on re-entry
            selectedTopics.clear();
            List<Topic> existing = db.topicDao().getTopicsForUser(userId);
            selectedTopics.addAll(existing);

            runOnUiThread(() -> {
                renderTopics("");
                updateSelectedChips();
                updateCounter();
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
            });
        });
    }

    // Called when user presses Done on keyboard
    private void handleDonePressed(String query) {
        // Check if any existing topics match the query
        boolean hasMatch = allTopics.stream()
                .anyMatch(t -> t.name.toLowerCase().contains(query.toLowerCase())
                        || t.category.toLowerCase().contains(query.toLowerCase()));

        if (hasMatch) {
            // Results already showing — nothing to do
            return;
        }

        // Check topic limit before calling Gemini
        if (selectedTopics.size() >= MAX_SELECTIONS) {
            showFeedback("Topic limit reached (10). Remove a topic before adding a new one.", false);
            return;
        }

        // No match found — ask Gemini to validate as educational topic
        validateNewTopic(query);
    }

    // Calls Gemini to check if the typed text is a valid educational subject
    private void validateNewTopic(String topicName) {
        showFeedback("Checking '" + topicName + "'…", true);

        String prompt = "Is '" + topicName + "' a formal academic or professional subject taught at school, "
                + "college or university level with established curriculum, textbooks or qualifications?\n"
                + "Examples of VALID subjects: Calculus, Criminal Law, Organic Chemistry, Welding, Building, Machine Learning.\n"
                + "Examples of INVALID subjects: Sky Diving, Cooking, Gaming, Surfing, Yoga.\n"
                + "A subject is only valid if it has dedicated university courses and academic literature.\n"
                + "Respond ONLY with valid JSON, no markdown:\n"
                + "{\"valid\": true or false, \"category\": \"category name if valid, else null\"}\n"
                + "Use broad academic categories like: Science, Mathematics, Law, Medicine, Engineering, "
                + "Business, History, Literature, Technology, Arts, Social Sciences, Computer Science.";

        GeminiClient.getInstance().generateContent(
                BuildConfig.GEMINI_API_KEY,
                new GeminiRequest(prompt)
        ).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().getResponseText() : null;

                if (text == null) {
                    showFeedback("Could not validate topic. Please try again.", false);
                    return;
                }

                parseValidationResponse(text, topicName);
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                showFeedback("Could not validate topic. Please check your connection.", false);
            }
        });
    }

    private void parseValidationResponse(String json, String topicName) {
        try {
            // Strip markdown fences if Gemini adds them
            json = json.replaceAll("```json", "").replaceAll("```", "").trim();
            JSONObject obj = new JSONObject(json);
            boolean valid = obj.getBoolean("valid");
            String category = obj.isNull("category") ? "General" : obj.getString("category");

            if (!valid) {
                showFeedback("'" + topicName + "' doesn't appear to be an educational subject.", false);
                return;
            }

            // Valid topic — persist globally and auto-select for this user
            addNewTopicGlobally(topicName, category);

        } catch (Exception e) {
            showFeedback("Could not validate topic. Please try again.", false);
        }
    }

    // Inserts new topic into Room globally and auto-selects it for this user
    private void addNewTopicGlobally(String topicName, String category) {
        AsyncTask.execute(() -> {
            // Capitalise first letter of topic name
            String name = topicName.substring(0, 1).toUpperCase() + topicName.substring(1);

            Topic newTopic = new Topic();
            newTopic.name = name;
            newTopic.category = category;

            // insertAll uses IGNORE conflict — safe if topic already exists
            List<Topic> toInsert = new ArrayList<>();
            toInsert.add(newTopic);
            db.topicDao().insertAll(toInsert);

            // Reload all topics to get the new topic's generated ID
            allTopics = db.topicDao().getAllTopics();

            // Find the newly inserted topic by name
            Topic inserted = allTopics.stream()
                    .filter(t -> t.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);

            if (inserted != null && selectedTopics.stream().noneMatch(t -> t.id == inserted.id)) {
                selectedTopics.add(inserted);

                // Persist the user's selection
                UserTopic ut = new UserTopic();
                ut.userId = userId;
                ut.topicId = inserted.id;
                db.topicDao().insertUserTopic(ut);
            }

            runOnUiThread(() -> {
                binding.editSearch.setText("");
                renderTopics("");
                updateSelectedChips();
                updateCounter();
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
                showFeedback("'" + name + "' added to " + category + " and selected!", true);
            });
        });
    }

    private void showFeedback(String message, boolean isPositive) {
        runOnUiThread(() -> {
            binding.tvAiFeedback.setText(message);
            binding.tvAiFeedback.setTextColor(ContextCompat.getColor(this,
                    isPositive ? R.color.accent_green : R.color.accent_red));
            binding.tvAiFeedback.setVisibility(View.VISIBLE);
        });
    }

    private void hideFeedback() {
        binding.tvAiFeedback.setVisibility(View.GONE);
    }

    private void renderTopics(String query) {
        binding.containerTopics.removeAllViews();

        Map<String, List<Topic>> grouped = new LinkedHashMap<>();
        for (Topic t : allTopics) {
            if (query.isEmpty() || t.name.toLowerCase().contains(query) || t.category.toLowerCase().contains(query)) {
                grouped.computeIfAbsent(t.category, k -> new ArrayList<>()).add(t);
            }
        }

        for (Map.Entry<String, List<Topic>> entry : grouped.entrySet()) {
            TextView header = new TextView(this);
            header.setText(entry.getKey());
            header.setTextSize(13f);
            header.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            header.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.setMargins(0, 24, 0, 8);
            header.setLayoutParams(headerParams);
            binding.containerTopics.addView(header);

            ChipGroup group = new ChipGroup(this);
            group.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            for (Topic topic : entry.getValue()) {
                group.addView(buildChip(topic));
            }
            binding.containerTopics.addView(group);
        }
    }

    private Chip buildChip(Topic topic) {
        boolean isSelected = selectedTopics.stream().anyMatch(t -> t.id == topic.id);

        Chip chip = new Chip(this);
        chip.setText(topic.name);
        chip.setCheckable(true);
        chip.setChecked(isSelected);
        applyChipStyle(chip, isSelected);

        chip.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && selectedTopics.size() >= MAX_SELECTIONS) {
                chip.setChecked(false);
                showFeedback("Topic limit reached (10). Remove a topic to add another.", false);
                return;
            }
            if (checked) {
                selectedTopics.add(topic);
            } else {
                selectedTopics.removeIf(t -> t.id == topic.id);
            }
            applyChipStyle(chip, checked);
            updateSelectedChips();
            updateCounter();
            binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
        });

        return chip;
    }

    private void applyChipStyle(Chip chip, boolean selected) {
        chip.setChipBackgroundColorResource(selected ? R.color.chip_selected : R.color.chip_unselected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.text_on_primary : R.color.text_primary));
        chip.setChipStrokeColorResource(selected ? R.color.chip_selected : R.color.divider);
        chip.setChipStrokeWidth(2f);
    }

    private void updateSelectedChips() {
        binding.chipGroupSelected.removeAllViews();

        if (selectedTopics.isEmpty()) {
            binding.labelYourPicks.setVisibility(View.GONE);
            return;
        }

        binding.labelYourPicks.setVisibility(View.VISIBLE);
        for (Topic topic : selectedTopics) {
            Chip chip = new Chip(this);
            chip.setText(topic.name);
            chip.setCloseIconVisible(true);
            chip.setChipBackgroundColorResource(R.color.primary_light);
            chip.setTextColor(ContextCompat.getColor(this, R.color.primary));

            chip.setOnCloseIconClickListener(v -> {
                selectedTopics.removeIf(t -> t.id == topic.id);
                updateSelectedChips();
                updateCounter();
                renderTopics(binding.editSearch.getText().toString().trim().toLowerCase());
                binding.btnContinue.setEnabled(!selectedTopics.isEmpty());
            });
            binding.chipGroupSelected.addView(chip);
        }
    }

    private void updateCounter() {
        binding.tvCounter.setText(selectedTopics.size() + " / " + MAX_SELECTIONS + " selected");
    }

    private void saveAndContinue() {
        binding.btnContinue.setEnabled(false);

        AsyncTask.execute(() -> {
            db.topicDao().clearUserTopics(userId);
            for (Topic t : selectedTopics) {
                UserTopic ut = new UserTopic();
                ut.userId = userId;
                ut.topicId = t.id;
                db.topicDao().insertUserTopic(ut);
            }

            if (!session.isLoggedIn()) {
                User user = db.userDao().findById(userId);
                if (user != null) {
                    session.saveSession(user.id, user.username);
                }
            }

            runOnUiThread(() -> {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}