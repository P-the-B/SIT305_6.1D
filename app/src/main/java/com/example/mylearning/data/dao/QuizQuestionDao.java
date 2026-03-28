package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.entity.QuizQuestion;

import java.util.List;

@Dao
public interface QuizQuestionDao {

    @Insert
    void insertAll(List<QuizQuestion> questions);

    // Results screen — all questions for a completed attempt
    @Query("SELECT * FROM quiz_questions WHERE attemptId = :attemptId")
    List<QuizQuestion> getQuestionsForAttempt(int attemptId);

    // Lesson screen — starred questions for a topic across all attempts
    @Query("SELECT * FROM quiz_questions WHERE topicId = :topicId AND isStarred = 1")
    List<QuizQuestion> getStarredForTopic(int topicId);

    // Star / unstar toggle from results or lesson screen
    @Query("UPDATE quiz_questions SET isStarred = :starred WHERE id = :questionId")
    void setStarred(int questionId, boolean starred);
}