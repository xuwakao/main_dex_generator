package com.wakao;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import okhttp3.OkHttpClient;

public class FakeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OkHttpClient.Builder.class.getAnnotations();
    }
}
