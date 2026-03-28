package com.example.mylearning.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "mylearning_session";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Called on successful login
    public void saveSession(int userId, String username) {
        prefs.edit()
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    // -1 means no active session
    public boolean isLoggedIn() {
        return getUserId() != -1;
    }

    // Called on logout or session clear
    public void clearSession() {
        prefs.edit().clear().apply();
    }
}