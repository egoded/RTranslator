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

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import nie.translator.rtranslator.BuildConfig;
import nie.translator.rtranslator.tools.UpdateChecker;

import androidx.core.splashscreen.SplashScreen;


public class LoadingActivity extends GeneralActivity {
    private Handler mainHandler;
    private boolean isVisible = false;
    private Global global;
    private boolean showingError = false;
    private boolean updateRequired = false;

    private TextView tvStatus;
    private Button btnRetry;

    public LoadingActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String previousActivity = getIntent().getStringExtra("activity");
        SplashScreen splashScreen = null;
        if (previousActivity == null || !previousActivity.equals("download")) {
            splashScreen = SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        if (splashScreen == null) {
            setTheme(R.style.Theme_Speech);
        }
        setContentView(R.layout.activity_loading);
        mainHandler = new Handler(Looper.getMainLooper());

        tvStatus = findViewById(R.id.tvUpdateStatus);
        btnRetry = findViewById(R.id.btnRetryUpdate);

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> checkForUpdates());
        }

        if (splashScreen != null) {
            splashScreen.setKeepOnScreenCondition(() -> !showingError);
        }
    }

    public void onResume() {
        super.onResume();
        isVisible = true;
        global = (Global) getApplication();

        if (updateRequired) {
            setStatusText("Необходимо установить обновление", Color.RED);
            showRetryButton(true);
        } else {
            checkForUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
    }

    private void checkForUpdates() {
        showRetryButton(false);
        setStatusText("Проверка обновлений...", Color.parseColor("#FF9800"));

        UpdateChecker checker = new UpdateChecker(this);
        checker.checkForUpdate(new UpdateChecker.UpdateCheckListener() {
            @Override
            public void onUpdateAvailable(int serverVersionCode, String serverVersionName) {
                if (!isVisible) return;
                showingError = true;
                updateRequired = true;
                setStatusText("Доступна версия " + serverVersionCode + "\nСкачивание...", Color.parseColor("#1976D2"));
                Log.i("LoadingActivity", "Автообновление: " + BuildConfig.VERSION_CODE + " → " + serverVersionCode);
                downloadAndInstall(checker);
            }

            @Override
            public void onNoUpdate() {
                updateRequired = false;
                proceedAfterUpdateCheck();
            }

            @Override
            public void onError(String error) {
                showingError = true;
                setStatusText("Сервер обновлений недоступен\n" + error, Color.RED);
                showRetryButton(true);
            }
        });
    }

    private void downloadAndInstall(UpdateChecker checker) {
        checker.downloadApk(new UpdateChecker.DownloadListener() {
            @Override
            public void onProgress(int percent) {
                setStatusText("Скачивание: " + percent + "%", Color.parseColor("#1976D2"));
            }

            @Override
            public void onDownloadComplete(java.io.File apkFile) {
                Log.i("LoadingActivity", "APK скачан: " + apkFile.length() / 1024 + " КБ");
                setStatusText("Установка...", Color.parseColor("#1976D2"));
                checker.installApk(apkFile);
            }

            @Override
            public void onDownloadError(String error) {
                Log.e("LoadingActivity", "Ошибка скачивания: " + error);
                setStatusText("Ошибка скачивания: " + error, Color.RED);
                showRetryButton(true);
            }
        });
    }

    private void proceedAfterUpdateCheck() {
        // Проверяем наличие моделей
        if (global.areModelsDownloaded()) {
            // Модели есть — на экран перевода
            Intent intent = new Intent(this, TranslateActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            // Моделей нет — на экран скачивания
            Intent intent = new Intent(this, ModelDownloadActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
    }

    private void setStatusText(String text, int color) {
        if (tvStatus != null) {
            tvStatus.setText(text);
            tvStatus.setTextColor(color);
        }
    }

    private void showRetryButton(boolean show) {
        if (btnRetry != null) {
            btnRetry.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
