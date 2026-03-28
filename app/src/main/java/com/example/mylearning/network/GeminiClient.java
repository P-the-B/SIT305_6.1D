package com.example.mylearning.network;

import com.example.mylearning.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/";
    private static GeminiApi instance;

    public static GeminiApi getInstance() {
        if (instance == null) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)  // fail fast if no connection
                    .readTimeout(30, TimeUnit.SECONDS)     // Gemini can be slow — 30s is reasonable
                    .writeTimeout(15, TimeUnit.SECONDS);

            // Only log in debug builds — keeps prompts and API key out of production logcat
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                clientBuilder.addInterceptor(logging);
            }

            instance = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(clientBuilder.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GeminiApi.class);
        }
        return instance;
    }
}