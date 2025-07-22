package com.example.test3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private EditText etServer;
    private EditText etClientId;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化控件
        etServer = findViewById(R.id.et_server);
        etClientId = findViewById(R.id.et_client_id);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnBack = findViewById(R.id.btn_back);

        // 获取SharedPreferences实例
        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        // 加载保存的设置
        loadSavedSettings();

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> saveSettings());

        // 返回按钮点击事件
        btnBack.setOnClickListener(v -> finish());
    }

    // 加载保存的设置
    private void loadSavedSettings() {
        String server = sharedPreferences.getString("server", "tcp://mqtt.eclipseprojects.io:1883");
        String clientId = sharedPreferences.getString("clientId", "");
        etServer.setText(server);
        etClientId.setText(clientId);
    }

    // 保存设置到SharedPreferences
    private void saveSettings() {
        String server = etServer.getText().toString().trim();
        String clientId = etClientId.getText().toString().trim();

        if (server.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("server", server);
        editor.putString("clientId", clientId);
        editor.apply();

        Toast.makeText(this, "设置保存成功", Toast.LENGTH_SHORT).show();
        finish();
    }
}