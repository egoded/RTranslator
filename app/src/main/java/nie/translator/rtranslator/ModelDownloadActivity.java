package nie.translator.rtranslator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ModelDownloadActivity extends AppCompatActivity {

    private static final String TAG = "ModelDownload";
    private static final String UPDATE_SERVER_URL = "http://10.0.12.102:3001";
    private static final int PERMISSION_REQUEST = 200;

    private TextView tvStatus, tvProgress, tvFileInfo;
    private ProgressBar progressTotal;
    private Button btnRetry;

    private volatile boolean downloading = false;
    private volatile boolean cancelled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_download);

        tvStatus = findViewById(R.id.tvStatus);
        tvProgress = findViewById(R.id.tvProgress);
        tvFileInfo = findViewById(R.id.tvFileInfo);
        progressTotal = findViewById(R.id.progressTotal);
        btnRetry = findViewById(R.id.btnRetry);

        btnRetry.setOnClickListener(v -> startDownload());

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                tvStatus.setText("Нужен доступ к хранилищу");
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                return;
            }
        }
        startDownload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                startDownload();
            } else {
                tvStatus.setText("Без доступа к хранилищу модели не скачать");
                btnRetry.setVisibility(View.VISIBLE);
                btnRetry.setText("Дать доступ");
                btnRetry.setOnClickListener(v -> checkPermissionsAndStart());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload();
        } else {
            tvStatus.setText("Без доступа к хранилищу модели не скачать");
            btnRetry.setVisibility(View.VISIBLE);
        }
    }

    private File getModelsDir() throws Exception {
        // Пробуем общее хранилище (сохраняется при обновлении APK)
        File dir = new File(Environment.getExternalStorageDirectory(), "RTranslator/models");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            Log.i(TAG, "mkdirs " + dir.getAbsolutePath() + " = " + created);
        }
        if (dir.exists() && dir.canWrite()) {
            return dir;
        }

        // Fallback: внутреннее хранилище приложения
        Log.w(TAG, "Внешнее хранилище недоступно, используем внутреннее");
        File fallback = new File(getFilesDir(), "models");
        if (!fallback.exists()) {
            fallback.mkdirs();
        }
        if (!fallback.exists() || !fallback.canWrite()) {
            throw new Exception("Не удалось создать папку для моделей");
        }
        return fallback;
    }

    private void startDownload() {
        if (downloading) return;
        downloading = true;
        cancelled = false;
        btnRetry.setVisibility(View.GONE);
        tvStatus.setText("Получение списка моделей...");

        new Thread(() -> {
            try {
                // Получаем список моделей с сервера
                List<String[]> models = fetchModelList();
                if (models.isEmpty()) {
                    showError("Список моделей пуст");
                    return;
                }

                File modelsDir = getModelsDir();
                int totalFiles = models.size();

                // Считаем общий размер
                long totalBytes = 0;
                for (String[] m : models) totalBytes += Long.parseLong(m[1]);

                // Считаем уже скачанное
                long downloadedSoFar = 0;
                int completedFiles = 0;
                for (String[] m : models) {
                    File f = new File(modelsDir, m[0]);
                    long expected = Long.parseLong(m[1]);
                    if (f.exists() && f.length() == expected) {
                        downloadedSoFar += expected;
                        completedFiles++;
                    }
                }

                // Все скачаны
                if (completedFiles == totalFiles) {
                    downloading = false;
                    goToTranslate();
                    return;
                }

                for (int i = 0; i < totalFiles; i++) {
                    if (cancelled) break;

                    String fileName = models.get(i)[0];
                    long expectedSize = Long.parseLong(models.get(i)[1]);
                    File targetFile = new File(modelsDir, fileName);
                    File tmpFile = new File(modelsDir, fileName + ".tmp");

                    // Пропускаем если файл полностью скачан
                    if (targetFile.exists() && targetFile.length() == expectedSize) {
                        int num = i + 1;
                        runOnUiThread(() -> tvFileInfo.setText(fileName + " — уже скачан"));
                        continue;
                    }

                    int fileNum = i + 1;
                    final long totalBytesF = totalBytes;
                    runOnUiThread(() -> {
                        tvStatus.setText("Скачивание " + fileNum + " из " + totalFiles);
                        tvFileInfo.setText(fileName);
                    });

                    // Скачиваем файл с докачкой
                    String urlStr = UPDATE_SERVER_URL + "/models/" + fileName;
                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);

                    // Докачка через Range
                    long resumeFrom = 0;
                    if (tmpFile.exists() && tmpFile.length() > 0) {
                        resumeFrom = tmpFile.length();
                        conn.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
                    }

                    int responseCode = conn.getResponseCode();
                    FileOutputStream fos;

                    if (responseCode == 206) {
                        // Докачка
                        fos = new FileOutputStream(tmpFile, true);
                        downloadedSoFar += resumeFrom;
                    } else if (responseCode == 200) {
                        // С начала
                        fos = new FileOutputStream(tmpFile, false);
                    } else {
                        conn.disconnect();
                        throw new Exception("HTTP " + responseCode + " для " + fileName);
                    }

                    long contentLength = conn.getContentLengthLong();
                    InputStream input = conn.getInputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long fileDownloaded = (responseCode == 206) ? resumeFrom : 0;

                    while ((bytesRead = input.read(buffer)) != -1) {
                        if (cancelled) break;
                        fos.write(buffer, 0, bytesRead);
                        fileDownloaded += bytesRead;
                        downloadedSoFar += bytesRead;

                        final long dl = downloadedSoFar;
                        final long fd = fileDownloaded;
                        runOnUiThread(() -> {
                            int pct = (int) (dl * 100 / totalBytesF);
                            progressTotal.setProgress(pct);
                            tvProgress.setText(pct + "%");
                            tvFileInfo.setText(fileName + "  " + formatSize(fd) + " / " + formatSize(expectedSize));
                        });
                    }

                    fos.close();
                    input.close();
                    conn.disconnect();

                    if (!cancelled) {
                        if (targetFile.exists()) targetFile.delete();
                        tmpFile.renameTo(targetFile);
                        completedFiles++;
                        Log.i(TAG, "Скачан: " + fileName + " (" + formatSize(targetFile.length()) + ")");
                    }
                }

                downloading = false;

                if (!cancelled) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Все модели загружены!");
                        tvProgress.setText("100%");
                        progressTotal.setProgress(100);
                    });
                    goToTranslate();
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка", e);
                showError(e.getMessage());
            }
        }).start();
    }

    private List<String[]> fetchModelList() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(UPDATE_SERVER_URL + "/models").openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("Сервер вернул " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        JSONArray arr = new JSONArray(sb.toString());
        List<String[]> models = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            models.add(new String[]{obj.getString("name"), String.valueOf(obj.getLong("size"))});
        }
        return models;
    }

    private void showError(String error) {
        downloading = false;
        runOnUiThread(() -> {
            tvStatus.setText("Ошибка: " + error);
            btnRetry.setVisibility(View.VISIBLE);
            btnRetry.setText("Повторить");
            btnRetry.setOnClickListener(v -> startDownload());
        });
    }

    private void goToTranslate() {
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        runOnUiThread(() -> {
            Intent intent = new Intent(ModelDownloadActivity.this, TranslateActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " КБ";
        return (bytes / (1024 * 1024)) + " МБ";
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }
}
