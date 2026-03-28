package com.example.mylearning.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface GeminiApi {

    // Use a model YOUR key actually supports
    @POST("v1beta/models/gemini-2.5-flash:generateContent")

    Call<GeminiResponse> generateContent(
            @Query("key") String apiKey,
            @Body GeminiRequest request
    );
}