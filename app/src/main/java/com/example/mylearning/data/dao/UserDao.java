package com.example.mylearning.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mylearning.data.entity.User;

@Dao
public interface UserDao {

    @Insert
    long insert(User user); // returns new row ID

    // Login lookup — email + hash must both match
    @Query("SELECT * FROM users WHERE email = :email AND passwordHash = :hash LIMIT 1")
    User findByEmailAndPassword(String email, String hash);

    // Forgot password — find by email then verify security answer separately
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("UPDATE users SET passwordHash = :newHash WHERE id = :userId")
    void updatePassword(int userId, String newHash);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(int id);
}