package nie.translator.rtranslator;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class TranslateActivity extends AppCompatActivity {

    private static final String TAG = "TranslateActivity";
    private static final int SPEECH_REQUEST_CODE = 100;

    private EditText editInput, editOutput;
    private Spinner spinnerFrom, spinnerTo;
    private Button btnTranslate;
    private ImageButton btnMic;
    private TextView tvModelStatus;
    private ProgressBar progressInit;
    private TextToSpeech tts;

    private Global global;
    private boolean translatorReady = false;

    // Языки NLLB (код языка -> отображаемое имя)
    private final String[] langNames = {"Русский", "English", "中文", "Español", "Français", "Deutsch", "日本語", "한국어", "العربية", "Türkçe"};
    private final String[] langCodes = {"ru", "en", "zh", "es", "fr", "de", "ja", "ko", "ar", "tr"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        global = (Global) getApplication();

        editInput = findViewById(R.id.editInput);
        editOutput = findViewById(R.id.editOutput);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        btnMic = findViewById(R.id.btnMic);
        btnTranslate = findViewById(R.id.btnTranslate);
        ImageButton btnSwap = findViewById(R.id.btnSwapLangs);
        ImageButton btnSpeak = findViewById(R.id.btnSpeak);
        ImageButton btnCopy = findViewById(R.id.btnCopy);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        progressInit = findViewById(R.id.progressInit);

        // Спиннеры языков
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langNames);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);
        spinnerFrom.setSelection(0); // Русский
        spinnerTo.setSelection(1);   // English

        // Кнопки неактивны пока модель не загружена
        btnTranslate.setEnabled(false);
        btnMic.setEnabled(false);

        // Микрофон — встроенный Android SpeechRecognizer
        btnMic.setOnClickListener(v -> startSpeechRecognition());

        // Перевод через NLLB модель
        btnTranslate.setOnClickListener(v -> translateText());

        // Поменять языки местами
        btnSwap.setOnClickListener(v -> {
            int fromPos = spinnerFrom.getSelectedItemPosition();
            int toPos = spinnerTo.getSelectedItemPosition();
            spinnerFrom.setSelection(toPos);
            spinnerTo.setSelection(fromPos);
            String inputText = editInput.getText().toString();
            String outputText = editOutput.getText().toString();
            editInput.setText(outputText);
            editOutput.setText(inputText);
        });

        // Озвучить результат
        btnSpeak.setOnClickListener(v -> {
            String text = editOutput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Нет текста для озвучивания", Toast.LENGTH_SHORT).show();
                return;
            }
            speakText(text, langCodes[spinnerTo.getSelectedItemPosition()]);
        });

        // Копировать результат
        btnCopy.setOnClickListener(v -> {
            String text = editOutput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Нет текста для копирования", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("translation", text));
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
        });

        // TTS
        tts = new TextToSpeech(this, status -> {});

        // Инициализация переводчика
        initTranslator();
    }

    private void initTranslator() {
        if (tvModelStatus != null) {
            tvModelStatus.setVisibility(View.VISIBLE);
            tvModelStatus.setText("Загрузка модели перевода...");
        }
        if (progressInit != null) {
            progressInit.setVisibility(View.VISIBLE);
        }

        global.initializeTranslator(new NeuralNetworkApi.InitListener() {
            @Override
            public void onInitializationFinished() {
                runOnUiThread(() -> {
                    translatorReady = true;
                    btnTranslate.setEnabled(true);
                    btnMic.setEnabled(true);
                    if (tvModelStatus != null) {
                        tvModelStatus.setText("Модель загружена");
                        tvModelStatus.postDelayed(() -> tvModelStatus.setVisibility(View.GONE), 2000);
                    }
                    if (progressInit != null) {
                        progressInit.setVisibility(View.GONE);
                    }
                    Log.i(TAG, "Переводчик инициализирован");
                });
            }

            @Override
            public void onError(int[] reasons, long value) {
                runOnUiThread(() -> {
                    if (tvModelStatus != null) {
                        tvModelStatus.setText("Ошибка загрузки модели");
                    }
                    if (progressInit != null) {
                        progressInit.setVisibility(View.GONE);
                    }
                    Toast.makeText(TranslateActivity.this, "Не удалось загрузить модель перевода", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Ошибка инициализации переводчика");
                });
            }
        });
    }

    private void translateText() {
        String text = editInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите текст", Toast.LENGTH_SHORT).show();
            return;
        }

        Translator translator = global.getTranslator();
        if (translator == null || !translatorReady) {
            Toast.makeText(this, "Модель ещё загружается", Toast.LENGTH_SHORT).show();
            return;
        }

        String fromCode = langCodes[spinnerFrom.getSelectedItemPosition()];
        String toCode = langCodes[spinnerTo.getSelectedItemPosition()];

        if (fromCode.equals(toCode)) {
            editOutput.setText(text);
            return;
        }

        btnTranslate.setEnabled(false);
        editOutput.setText("Перевод...");

        CustomLocale fromLocale = new CustomLocale(fromCode);
        CustomLocale toLocale = new CustomLocale(toCode);

        translator.translate(text, fromLocale, toLocale, 1, false, new Translator.TranslateListener() {
            @Override
            public void onTranslatedText(String textToTranslate, String translatedText, long resultID, boolean isFinal, CustomLocale languageOfText) {
                runOnUiThread(() -> {
                    editOutput.setText(translatedText);
                    if (isFinal) {
                        btnTranslate.setEnabled(true);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                runOnUiThread(() -> {
                    editOutput.setText("");
                    btnTranslate.setEnabled(true);
                    Toast.makeText(TranslateActivity.this, "Ошибка перевода", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCodes[spinnerFrom.getSelectedItemPosition()]);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                editInput.setText(results.get(0));
            }
        }
    }

    private void speakText(String text, String langCode) {
        Locale locale = new Locale(langCode);
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Язык не поддерживается для озвучивания", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
