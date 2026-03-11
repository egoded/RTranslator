package nie.translator.rtranslator.tools;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import nie.translator.rtranslator.BuildConfig;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_SERVER_URL = "http://10.0.12.102:3001";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpdateCheckListener {
        void onUpdateAvailable(int serverVersionCode, String serverVersionName);
        void onNoUpdate();
        void onError(String error);
    }

    public interface DownloadListener {
        void onProgress(int percent);
        void onDownloadComplete(File apkFile);
        void onDownloadError(String error);
    }

    public UpdateChecker(Context context) {
        this.context = context;
    }

    public void checkForUpdate(UpdateCheckListener listener) {
        new Thread(() -> {
            try {
                String updatePath = BuildConfig.UPDATE_PATH;
                String urlStr = UPDATE_SERVER_URL + "/" + updatePath + "/version";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    conn.disconnect();
                    mainHandler.post(() -> listener.onError("HTTP " + responseCode));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(response.toString());
                int serverVersionCode = json.getInt("versionCode");
                String serverVersionName = json.optString("versionName", "");

                int localVersionCode = BuildConfig.VERSION_CODE;

                if (serverVersionCode > localVersionCode) {
                    mainHandler.post(() -> listener.onUpdateAvailable(serverVersionCode, serverVersionName));
                } else {
                    mainHandler.post(() -> listener.onNoUpdate());
                }

            } catch (Exception e) {
                Log.e(TAG, "Ошибка проверки обновлений", e);
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    public void downloadApk(DownloadListener listener) {
        new Thread(() -> {
            try {
                String updatePath = BuildConfig.UPDATE_PATH;
                String urlStr = UPDATE_SERVER_URL + "/" + updatePath + "/download";

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    conn.disconnect();
                    mainHandler.post(() -> listener.onDownloadError("HTTP " + responseCode));
                    return;
                }

                File apkFile = new File(context.getCacheDir(), "update.apk");
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long total = 0;
                    int contentLength = conn.getContentLength();
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                        total += bytesRead;
                        if (contentLength > 0) {
                            int pct = (int) (total * 100 / contentLength);
                            mainHandler.post(() -> listener.onProgress(pct));
                        }
                    }
                } finally {
                    output.close();
                    input.close();
                }
                conn.disconnect();
                mainHandler.post(() -> listener.onDownloadComplete(apkFile));

            } catch (Exception e) {
                Log.e(TAG, "Ошибка скачивания APK", e);
                mainHandler.post(() -> listener.onDownloadError(e.getMessage()));
            }
        }).start();
    }

    public void installApk(File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
