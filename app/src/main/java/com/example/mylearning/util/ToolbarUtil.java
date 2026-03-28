package com.example.mylearning.util;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.mylearning.R;

public class ToolbarUtil {

    // Call this from every activity after setSupportActionBar()
    public static void setup(AppCompatActivity activity, Toolbar toolbar, String title, boolean showBack) {
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(showBack);
        }

        // White back arrow
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(
                    ContextCompat.getColor(activity, R.color.text_on_primary));
        }

        // Centered title TextView
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(20f);
        titleView.setTextColor(ContextCompat.getColor(activity, R.color.text_on_primary));
        titleView.setTypeface(null, Typeface.BOLD);

        Toolbar.LayoutParams lp = new Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        toolbar.addView(titleView, lp);
    }
}