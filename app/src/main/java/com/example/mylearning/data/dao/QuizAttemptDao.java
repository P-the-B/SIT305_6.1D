package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.entity.QuizAttempt;

import java.util.List;

@Dao
public interface QuizAttemptDao {

    @Insert
    long insert(QuizAttempt attempt);

    @Query("SELECT * FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId ORDER BY timestamp DESC")
    List<QuizAttempt> getAttemptsForTopic(int userId, int topicId);

    @Query("SELECT SUM(score) * 100 / SUM(totalQuestions) FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId")
    int getAccuracyForTopic(int userId, int topicId);

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE userId = :userId AND topicId = :topicId")
    int getAttemptCount(int userId, int topicId);

    @Query("UPDATE quiz_attempts SET score = :score, totalQuestions = :total WHERE id = :attemptId")
    void updateScoreAndTotal(int attemptId, int score, int total);

    // All topic IDs the user has ever attempted — used by progress screen regardless of current interests
    @Query("SELECT DISTINCT topicId FROM quiz_attempts WHERE userId = :userId")
    List<Integer> getAttemptedTopicIds(int userId);
}