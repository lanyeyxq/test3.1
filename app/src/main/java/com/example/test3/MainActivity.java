package com.example.test3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.util.Set;
import java.util.HashSet;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private MqttClientManager mqttClientManager;
    private SharedPreferences sharedPreferences;
    private EditText etSubscribeTopic, etPublishTopic, etMessage;
    private Button btnConnect, btnDisconnect, btnSubscribe, btnPublish, btnSettings;
    private Set<String> subscribedTopics = new HashSet<>();
    private TextView tvLogContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        // 初始化控件
        initViews();

        // 初始化MQTT客户端
        mqttClientManager = null;

        // 设置按钮点击事件
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // 连接按钮点击事件
        btnConnect.setOnClickListener(v -> connectToMqtt());

        // 断开连接按钮点击事件
        btnDisconnect.setOnClickListener(v -> disconnectFromMqtt());

        // 订阅按钮点击事件
        btnSubscribe.setOnClickListener(v -> subscribeToTopic());

        // 发布按钮点击事件
        btnPublish.setOnClickListener(v -> publishMessage());
    }

    // 初始化控件
    private void initViews() {
        btnSettings = findViewById(R.id.btn_settings);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnSubscribe = findViewById(R.id.btn_subscribe);
        btnPublish = findViewById(R.id.btn_publish);
        etSubscribeTopic = findViewById(R.id.et_subscribe_topic);
        etPublishTopic = findViewById(R.id.et_publish_topic);
        etMessage = findViewById(R.id.et_message);
        tvLogContent = findViewById(R.id.tv_log_content);

        // 从SharedPreferences加载保存的值
        etSubscribeTopic.setText(sharedPreferences.getString("subscribe_topic", "test/topic"));
        etPublishTopic.setText(sharedPreferences.getString("publish_topic", "test/topic"));
        etMessage.setText(sharedPreferences.getString("message", "Hello MQTT!"));
    }

    // 断开MQTT连接
    private void disconnectFromMqtt() {
        if (mqttClientManager != null) {
            mqttClientManager.disconnect(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    mqttClientManager = null;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    mqttClientManager = null;
                }
            });
        }
    }

    // 订阅主题
    private void subscribeToTopic() {
        if (mqttClientManager == null || !mqttClientManager.isConnected()) {
            Toast.makeText(this, "请先连接到MQTT服务器", Toast.LENGTH_SHORT).show();
            return;
        }

        String topic = etSubscribeTopic.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(this, "请输入订阅主题", Toast.LENGTH_SHORT).show();
            return;
        }

        // 清除之前的订阅
        for (String t : subscribedTopics) {
            mqttClientManager.unsubscribe(t);
        }
        subscribedTopics.clear();

        mqttClientManager.subscribe(topic, 1);
    }

    // 发布消息
    private void publishMessage() {
        if (mqttClientManager == null || !mqttClientManager.isConnected()) {
            Toast.makeText(this, "请先连接到MQTT服务器", Toast.LENGTH_SHORT).show();
            return;
        }

        String topic = etPublishTopic.getText().toString().trim();
        String message = etMessage.getText().toString().trim();

        if (topic.isEmpty()) {
            Toast.makeText(this, "请输入发布主题", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.isEmpty()) {
            Toast.makeText(this, "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

        addLog("发布消息到 " + topic + ": " + message);
        mqttClientManager.publish(topic, message, 1, false);
    }

    // 连接到MQTT服务器
    private void connectToMqtt() {
        final String server = sharedPreferences.getString("server", "tcp://mqtt.eclipseprojects.io:1883");
        final String baseClientId = sharedPreferences.getString("clientId", "");
        
        // 确保每次连接尝试使用唯一的客户端ID
        final String clientId = baseClientId.isEmpty() ? 
            "android-client-" + System.currentTimeMillis() : 
            baseClientId + "-" + System.currentTimeMillis();

        try {
            // 确保先清理旧的客户端实例
            if (mqttClientManager != null) {
                Runnable onDestroyComplete = new Runnable() {
                    @Override
                    public void run() {
                        createNewMqttClient(server, clientId);
                    }
                };
                mqttClientManager.disconnect(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        onDestroyComplete.run();
                    }
                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        onDestroyComplete.run();
                    }
                });
            } else {
                createNewMqttClient(server, clientId);
            }
        } catch (Exception e) {
            addLog("连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createNewMqttClient(String server, String clientId) {
        try {
            // 创建新的MQTT客户端实例
            mqttClientManager = new MqttClientManager(this, server, clientId, new MqttClientManager.MqttClientCallback() {
                @Override
                public void onConnected() {
                    runOnUiThread(() -> {
                        addLog("连接成功");
                        btnConnect.setEnabled(false);
                        btnDisconnect.setEnabled(true);
                        btnSubscribe.setEnabled(true);
                        btnPublish.setEnabled(true);
                    });
                }

                @Override
                public void onConnectionFailed(String error) {
                    runOnUiThread(() -> {
                        addLog("连接失败: " + error);
                        btnConnect.setEnabled(true);
                        btnDisconnect.setEnabled(false);
                        btnSubscribe.setEnabled(false);
                        btnPublish.setEnabled(false);
                        // 连接失败后清理资源
                        if (mqttClientManager != null) {
                            mqttClientManager.disconnect(new IMqttActionListener() {
                                @Override
                                public void onSuccess(IMqttToken asyncActionToken) {
                                    createNewMqttClient(server, clientId);
                                }
                                @Override
                                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                    createNewMqttClient(server, clientId);
                                }
                            });
                        }
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            addLog("已断开连接");
                            btnConnect.setEnabled(true);
                            btnDisconnect.setEnabled(false);
                            btnSubscribe.setEnabled(false);
                            btnPublish.setEnabled(false);
                        }
                    });
                }

                @Override
                public void onConnectionLost(Throwable cause) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            String errorMessage = (cause != null) ? cause.getMessage() : "连接已断开";
                            addLog("连接丢失: " + errorMessage);
                            btnConnect.setEnabled(true);
                            btnDisconnect.setEnabled(false);
                            btnSubscribe.setEnabled(false);
                            btnPublish.setEnabled(false);
                        }
                    });
                }

                @Override
                public void onMessageReceived(String topic, String message) {
                    runOnUiThread(() -> addLog("收到消息 - 主题: " + topic + ", 内容: " + message));
                }

                @Override
                public void onMessagePublished(String topic) {
                    runOnUiThread(() -> addLog("消息已发布到: " + topic));
                }

                @Override
                public void onSubscribeSuccess(String topic) {
                    runOnUiThread(() -> {
                        addLog("订阅成功: " + topic);
                        subscribedTopics.add(topic);
                    });
                }

                @Override
                public void onSubscribeFailed(String topic, String error) {
                    runOnUiThread(() -> addLog("订阅失败 " + topic + ": " + error));
                }
            });
            mqttClientManager.connect();
            addLog("正在连接到: " + server);
        } catch (Exception e) {
            addLog("连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("subscribe_topic", etSubscribeTopic.getText().toString());
        editor.putString("publish_topic", etPublishTopic.getText().toString());
        editor.putString("message", etMessage.getText().toString());
        editor.apply();
    }

    // 添加日志
    private void addLog(String message) {
        runOnUiThread(() -> {
            String currentLog = tvLogContent.getText().toString();
            String newLog = currentLog + "\n" + message;
            tvLogContent.setText(newLog);
            // 滚动到底部
            tvLogContent.scrollTo(0, tvLogContent.getBottom());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClientManager != null) {
            mqttClientManager.disconnect(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    mqttClientManager = null;
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    mqttClientManager = null;
                }
            });
            mqttClientManager = null;
        }
    }

}
