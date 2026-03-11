/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator;

import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.util.ArrayList;

import nie.translator.rtranslator.tools.CustomLocale;

// ВРЕМЕННО УПРОЩЕНО для тестирования обновлений
// Оригинальные импорты закомментированы:
// import nie.translator.rtranslator.access.AccessActivity;
// import nie.translator.rtranslator.tools.TTS;
// import nie.translator.rtranslator.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
// import nie.translator.rtranslator.bluetooth.BluetoothCommunicator;
// import nie.translator.rtranslator.bluetooth.Peer;
// import nie.translator.rtranslator.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;
// import nie.translator.rtranslator.voice_translation._text_translation.TranslationFragment;
// import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
// import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
// import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recognizer;
// import nie.translator.rtranslator.voice_translation.neural_networks.voice.Recorder;


public class Global extends Application implements DefaultLifecycleObserver {
    private ArrayList<CustomLocale> languages = new ArrayList<>();
    private CustomLocale language;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private String name = "";
    private boolean isForeground = false;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    // ВРЕМЕННО ОТКЛЮЧЕНО — тяжёлые методы
    /*
    public void initializeTranslator(NeuralNetworkApi.InitListener initListener){ ... }
    public void initializeSpeechRecognizer(NeuralNetworkApi.InitListener initListener){ ... }
    public void initializeBluetoothCommunicator(){ ... }
    public Translator getTranslator() { return null; }
    public Recognizer getSpeechRecognizer() { return null; }
    public void deleteTranslator(){ }
    public void deleteSpeechRecognizer(){ }
    public void getLanguages(...) { ... }
    */

    public boolean isForeground() {
        return isForeground;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStop(owner);
        isForeground = false;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        DefaultLifecycleObserver.super.onStart(owner);
        isForeground = true;
    }

    public interface GetLocalesListListener {
        void onSuccess(ArrayList<CustomLocale> result);
        void onFailure(int[] reasons, long value);
    }

    public interface GetLocaleListener {
        void onSuccess(CustomLocale result);
        void onFailure(int[] reasons, long value);
    }

    public interface GetTwoLocaleListener {
        void onSuccess(CustomLocale language1, CustomLocale language2);
        void onFailure(int[] reasons, long value);
    }

    public String getName() {
        if (name.length() == 0) {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            name = sharedPreferences.getString("name", "user");
        }
        return name;
    }

    public void setName(String savedName) {
        name = savedName;
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("name", savedName);
        editor.apply();
    }

    public abstract static class ResponseListener {
        public void onSuccess() {}
        public void onFailure(int[] reasons, long value) {}
    }

    /**
     * Возвращает общую папку для моделей, доступную всем версиям приложения.
     */
    public File getModelsDir() {
        File modelsDir = new File(Environment.getExternalStorageDirectory(), "RTranslator/models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
        return modelsDir;
    }

    /**
     * Проверяет наличие всех моделей в общей папке.
     */
    public boolean areModelsDownloaded() {
        File modelsDir = getModelsDir();
        String[] requiredModels = {
            "NLLB_cache_initializer.onnx", "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx", "NLLB_encoder.onnx",
            "Whisper_cache_initializer.onnx", "Whisper_cache_initializer_batch.onnx",
            "Whisper_decoder.onnx", "Whisper_detokenizer.onnx",
            "Whisper_encoder.onnx", "Whisper_initializer.onnx"
        };
        for (String model : requiredModels) {
            if (!new File(modelsDir, model).exists()) {
                return false;
            }
        }
        return true;
    }

    public boolean isFirstStart() {
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("firstStart", true);
    }

    public void setFirstStart(boolean firstStart) {
        final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("firstStart", firstStart);
        editor.apply();
    }

    private void createNotificationChannel(){
        String channelID = "service_background_notification";
        String channelName = getResources().getString(R.string.notification_channel_name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    public long getTotalRamSize(){
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo.totalMem / 1000000L;
    }

    public long getAvailableRamSize(){
        ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo.availMem / 1000000L;
    }
}
