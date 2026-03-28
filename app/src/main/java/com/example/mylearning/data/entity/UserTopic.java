package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;

// Junction table linking a user to their selected topics
@Entity(
        tableName = "user_topics",
        primaryKeys = {"userId", "topicId"},
        foreignKeys = {
                @ForeignKey(entity = User.class, parentColumns = "id", childColumns = "userId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Topic.class, parentColumns = "id", childColumns = "topicId", onDelete = ForeignKey.CASCADE)
        }
)
public class UserTopic {
    public int userId;
    public int topicId;
}