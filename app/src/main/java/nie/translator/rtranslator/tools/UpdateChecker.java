package nie.translator.rtranslator.tools;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import nie.translator.rtranslator.BuildConfig;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UpdateCheckListener {
        void onUpdateAvailable(int serverVersionCode, String serverVersionName);
        void onNoUpdate();
        void onError(String error);
    }

    public interface DownloadListener {
        void onDownloadComplete(File apkFile);
        void onDownloadError(String error);
    }

    public UpdateChecker(Context context) {
        this.context = context;
    }

    public void checkForUpdate(UpdateCheckListener listener) {
        new Thread(() -> {
            try {
                String serverUrl = BuildConfig.UPDATE_SERVER_URL;
                String env = BuildConfig.BUILD_ENV;
                String urlStr = serverUrl + "/" + env + "/version";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    mainHandler.post(() -> listener.onError("Сервер вернул код " + responseCode));
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

    public void downloadAndInstall(DownloadListener listener) {
        String serverUrl = BuildConfig.UPDATE_SERVER_URL;
        String env = BuildConfig.BUILD_ENV;
        String downloadUrl = serverUrl + "/" + env + "/download";
        String fileName = "RTranslator-update.apk";

        // Удаляем старый файл если есть
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File apkFile = new File(dir, fileName);
        if (apkFile.exists()) {
            apkFile.delete();
        }

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle("Обновление RTranslator");
        request.setDescription("Загрузка новой версии...");
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        long downloadId = downloadManager.enqueue(request);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    context.unregisterReceiver(this);
                    File file = new File(dir, fileName);
                    if (file.exists()) {
                        mainHandler.post(() -> listener.onDownloadComplete(file));
                        installApk(file);
                    } else {
                        mainHandler.post(() -> listener.onDownloadError("Файл не найден после загрузки"));
                    }
                }
            }
        };

        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED);
    }

    public void installApk(File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".update.provider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
