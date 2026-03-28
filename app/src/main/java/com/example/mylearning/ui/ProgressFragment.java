package com.example.mylearning.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mylearning.LessonActivity;
import com.example.mylearning.QuizActivity;
import com.example.mylearning.R;
import com.example.mylearning.data.AppDatabase;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.UserTopic;
import com.example.mylearning.databinding.FragmentProgressBinding;
import com.example.mylearning.util.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProgressFragment extends Fragment {

    private FragmentProgressBinding binding;
    private AppDatabase db;
    private SessionManager session;

    private List<TopicStat> stats = new ArrayList<>();
    private boolean sortByScore = false;

    private static final int MAX_TOPICS = 10;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.getInstance(requireContext());
        session = new SessionManager(requireContext());

        binding.btnSort.setOnClickListener(v -> {
            sortByScore = !sortByScore;
            renderStats();
        });

        loadStats();
    }

    private void loadStats() {
        AsyncTask.execute(() -> {
            List<Integer> attemptedIds = db.quizAttemptDao().getAttemptedTopicIds(session.getUserId());
            List<TopicStat> loaded = new ArrayList<>();

            for (int topicId : attemptedIds) {
                Topic t = db.topicDao().getTopicById(topicId);
                if (t == null) continue;
                int attempts = db.quizAttemptDao().getAttemptCount(session.getUserId(), topicId);
                int accuracy = db.quizAttemptDao().getAccuracyForTopic(session.getUserId(), topicId);
                loaded.add(new TopicStat(t, accuracy, attempts));
            }

            requireActivity().runOnUiThread(() -> {
                stats = loaded;
                if (stats.isEmpty()) {
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    binding.containerProgress.setVisibility(View.GONE);
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                    binding.containerProgress.setVisibility(View.VISIBLE);
                    renderStats();
                }
            });
        });
    }

    private void renderStats() {
        binding.containerProgress.removeAllViews();

        List<TopicStat> sorted = new ArrayList<>(stats);
        if (sortByScore) {
            sorted.sort(Comparator.comparingInt(s -> s.accuracy));
        } else {
            sorted.sort((a, b) -> a.topic.name.compareTo(b.topic.name));
        }

        TopicStat weakest = stats.stream()
                .min(Comparator.comparingInt(s -> s.accuracy))
                .orElse(null);

        for (TopicStat stat : sorted) {
            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_progress_card, binding.containerProgress, false);

            TextView tvName = card.findViewById(R.id.tvTopicName);
            TextView tvAttempts = card.findViewById(R.id.tvAttempts);
            TextView tvAccuracy = card.findViewById(R.id.tvAccuracy);
            TextView tvFocus = card.findViewById(R.id.tvFocusBadge);
            MaterialCardView cardView = card.findViewById(R.id.card);

            tvName.setText(stat.topic.name);
            tvAttempts.setText(stat.attempts + (stat.attempts == 1 ? " attempt" : " attempts"));
            tvAccuracy.setText(stat.accuracy + "%");

            if (stat.accuracy >= 70) {
                tvAccuracy.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_good_text));
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.score_good));
            } else if (stat.accuracy >= 50) {
                tvAccuracy.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_amber_text));
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.score_amber));
            } else {
                tvAccuracy.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_poor_text));
                cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.score_poor));
            }

            if (weakest != null && stat.topic.id == weakest.topic.id) {
                tvFocus.setVisibility(View.VISIBLE);
            }

            card.setOnClickListener(v -> handleTopicTap(stat.topic));
            binding.containerProgress.addView(card);
        }
    }

    private void handleTopicTap(Topic topic) {
        AsyncTask.execute(() -> {
            boolean isSelected = db.topicDao().isTopicSelected(session.getUserId(), topic.id) > 0;

            if (!isSelected) {
                int currentCount = db.topicDao().getUserTopicCount(session.getUserId());
                if (currentCount >= MAX_TOPICS) {
                    requireActivity().runOnUiThread(() -> {
                        // Show limit warning after sheet is dismissed
                        BottomSheetDialog sheet = showBottomSheet(topic);
                        sheet.setOnDismissListener(d ->
                                Snackbar.make(requireActivity().findViewById(android.R.id.content),
                                        "Topic limit reached (10). Remove a topic first to re-add this one.",
                                        Snackbar.LENGTH_LONG).show());
                    });
                } else {
                    // Re-add topic to user interests
                    UserTopic ut = new UserTopic();
                    ut.userId = session.getUserId();
                    ut.topicId = topic.id;
                    db.topicDao().insertUserTopic(ut);

                    requireActivity().runOnUiThread(() -> {
                        // Show confirmation after sheet is dismissed
                        BottomSheetDialog sheet = showBottomSheet(topic);
                        sheet.setOnDismissListener(d ->
                                Snackbar.make(requireActivity().findViewById(android.R.id.content),
                                        topic.name + " added back to your interests.",
                                        Snackbar.LENGTH_SHORT).show());
                    });
                }
            } else {
                requireActivity().runOnUiThread(() -> showBottomSheet(topic));
            }
        });
    }

    // Returns the dialog so caller can attach dismiss listener for Snackbar timing
    private BottomSheetDialog showBottomSheet(Topic topic) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_topic_options, null);

        ((TextView) sheetView.findViewById(R.id.tvTopicName)).setText(topic.name);
        ((TextView) sheetView.findViewById(R.id.tvTopicCategory)).setText(topic.category);

        sheetView.findViewById(R.id.btnStartQuiz).setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(requireContext(), QuizActivity.class);
            intent.putExtra("topicId", topic.id);
            intent.putExtra("topicName", topic.name);
            startActivity(intent);
        });

        sheetView.findViewById(R.id.btnShortLesson).setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(requireContext(), LessonActivity.class);
            intent.putExtra("topicId", topic.id);
            intent.putExtra("topicName", topic.name);
            startActivity(intent);
        });

        sheet.setContentView(sheetView);
        sheet.show();
        return sheet;
    }

    private static class TopicStat {
        Topic topic;
        int accuracy;
        int attempts;

        TopicStat(Topic topic, int accuracy, int attempts) {
            this.topic = topic;
            this.accuracy = accuracy;
            this.attempts = attempts;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}