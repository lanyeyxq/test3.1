package com.example.test3;

import android.content.Context;
import android.util.Log;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttClientManager {
    private static final String TAG = "MqttClientManager";
    private MqttAndroidClient mqttAndroidClient;
    private MqttConnectOptions mqttConnectOptions;
    private MqttClientCallback callback;

    public interface MqttClientCallback {
        void onConnected();
        void onDisconnected();
        void onConnectionLost(Throwable cause);
        void onMessageReceived(String topic, String message);
        void onSubscribeSuccess(String topic);
        void onSubscribeFailed(String topic, String error);
        void onMessagePublished(String topic);
        void onConnectionFailed(String error);
    }

    public MqttClientManager(Context context, String serverUri, String clientId, MqttClientCallback callback) {
        this.callback = callback;
        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                String errorMessage = (cause != null) ? cause.getMessage() : "Unknown error";
                Log.e(TAG, "Connection lost: " + errorMessage);
                if (callback != null) {
                    callback.onConnectionLost(cause);
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Message arrived: " + topic + " - " + new String(message.getPayload()));
                if (MqttClientManager.this.callback != null) {
                    MqttClientManager.this.callback.onMessageReceived(topic, new String(message.getPayload()));
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                String[] topics = token.getTopics();
                if (topics != null && topics.length > 0 && MqttClientManager.this.callback != null) {
                    MqttClientManager.this.callback.onMessagePublished(topics[0]);
                }
            }
        });

        // 配置连接选项
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setConnectionTimeout(10);
        mqttConnectOptions.setKeepAliveInterval(60);
    }

    // 连接到MQTT服务器
    public void connect() {
        if (mqttAndroidClient != null && !mqttAndroidClient.isConnected()) {
            try {
                IMqttToken token = mqttAndroidClient.connect(mqttConnectOptions);
                token.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Connected successfully");
                                if (callback != null) {
                                    callback.onConnected();
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        final String errorMessage = exception.getMessage();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(TAG, "Connection failed", exception);
                                if (callback != null) {
                                    callback.onConnectionFailed(errorMessage);
                                }
                            }
                        });
                    }
                });
            }
        }
    }

    // 断开连接
    public void disconnect(IMqttActionListener listener) {
        if (mqttAndroidClient != null) {
            try {
                if (mqttAndroidClient.isConnected()) {
                    IMqttToken token = mqttAndroidClient.disconnect();
                    token.setActionCallback(new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d(TAG, "Disconnected successfully during destroy");
                            cleanupResources();
                            if (listener != null) {
                                listener.onSuccess(asyncActionToken);
                            }
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.e(TAG, "Disconnection failed during destroy", exception);
                            cleanupResources();
                            if (listener != null) {
                                listener.onFailure(asyncActionToken, exception);
                            }
                        }
                    });
                } else {
                    cleanupResources();
                    if (listener != null) {
                        listener.onSuccess(null);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during disconnect: " + e.getMessage(), e);
                cleanupResources();
                if (listener != null) {
                    listener.onFailure(null, e);
                }
            }
        } else {
            if (listener != null) {
                listener.onSuccess(null);
            }
        }
        callback = null;
    }

    private void cleanupResources() {
        if (mqttAndroidClient != null) {
            try {
                mqttAndroidClient.unregisterResources();
                mqttAndroidClient.close();
            } catch (MqttException e) {
                Log.e(TAG, "Error cleaning up resources", e);
            }
            mqttAndroidClient = null;
        }
    }

    // 检查客户端是否已连接
    public boolean isConnected() {
        return mqttAndroidClient != null && mqttAndroidClient.isConnected();
    }

    // 发布消息
    public void publish(String topic, String message, int qos, boolean retained) {
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "Cannot publish - client not connected");
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.getBytes());
            mqttMessage.setQos(qos);
            mqttMessage.setRetained(retained);
            mqttAndroidClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            Log.e(TAG, "Error publishing message: " + e.getMessage(), e);
        }
    }

    // 订阅主题
    public void subscribe(String topic, int qos) {
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "Cannot subscribe - client not connected");
            if (callback != null) {
                callback.onSubscribeFailed(topic, "Client not connected");
            }
            return;
        }

        try {
            IMqttToken token = mqttAndroidClient.subscribe(topic, qos);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribed to topic: " + topic);
                    if (callback != null) {
                        callback.onSubscribeSuccess(topic);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    String errorMessage = exception != null ? exception.getMessage() : "Unknown error";
                    Log.e(TAG, "Subscription failed for topic " + topic + ": " + errorMessage);
                    if (callback != null) {
                        callback.onSubscribeFailed(topic, errorMessage);
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to topic " + topic + ": " + e.getMessage(), e);
            if (callback != null) {
                callback.onSubscribeFailed(topic, e.getMessage());
            }
        }
    }

    // 取消订阅主题
    public void unsubscribe(String topic) {
        if (mqttAndroidClient == null || !mqttAndroidClient.isConnected()) {
            Log.e(TAG, "Cannot unsubscribe - client not connected");
            return;
        }

        try {
            IMqttToken token = mqttAndroidClient.unsubscribe(topic);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Unsubscribed from topic: " + topic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Unsubscription failed for topic " + topic + ": " + exception.getMessage(), exception);
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Error unsubscribing from topic " + topic + ": " + e.getMessage(), e);
        }
    }
}

