package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "topics", indices = {@Index(value = "name", unique = true)})
public class Topic {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String category;
}