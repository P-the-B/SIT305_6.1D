package com.example.mylearning.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String username;
    public String email;
    public String passwordHash;   // SHA-256 hash, never plain text
    public String phone;
    public String securityQuestion;
    public String securityAnswerHash; // hashed same as password
}