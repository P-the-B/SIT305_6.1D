package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "quiz_attempts",
        foreignKeys = @ForeignKey(entity = Topic.class, parentColumns = "id", childColumns = "topicId", onDelete = ForeignKey.CASCADE)
)
public class QuizAttempt {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public int topicId;
    public int score;           // questions correct
    public int totalQuestions;  // questions attempted (handles quit mid-quiz)
    public long timestamp;      // System.currentTimeMillis()
}