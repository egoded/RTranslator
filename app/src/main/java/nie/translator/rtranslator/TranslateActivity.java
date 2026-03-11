package nie.translator.rtranslator;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class TranslateActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 100;

    private EditText editInput, editOutput;
    private Spinner spinnerFrom, spinnerTo;
    private TextToSpeech tts;

    // Поддерживаемые языки
    private final String[] langNames = {"Русский", "English", "中文", "Español", "Français", "Deutsch", "日本語", "한국어", "العربية", "Türkçe"};
    private final String[] langCodes = {"ru", "en", "zh", "es", "fr", "de", "ja", "ko", "ar", "tr"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        editInput = findViewById(R.id.editInput);
        editOutput = findViewById(R.id.editOutput);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        ImageButton btnMic = findViewById(R.id.btnMic);
        Button btnTranslate = findViewById(R.id.btnTranslate);
        ImageButton btnSwap = findViewById(R.id.btnSwapLangs);
        ImageButton btnSpeak = findViewById(R.id.btnSpeak);
        ImageButton btnCopy = findViewById(R.id.btnCopy);

        // Спиннеры языков
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, langNames);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);
        spinnerFrom.setSelection(0); // Русский
        spinnerTo.setSelection(1);   // English

        // Микрофон — встроенный Android SpeechRecognizer
        btnMic.setOnClickListener(v -> startSpeechRecognition());

        // Перевод — пока заглушка (модели не подключены)
        btnTranslate.setOnClickListener(v -> {
            String text = editInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Введите текст", Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: подключить Translator когда модели будут включены
            editOutput.setText("[Перевод будет доступен после подключения моделей]\n\nВведено: " + text);
        });

        // Поменять языки местами
        btnSwap.setOnClickListener(v -> {
            int fromPos = spinnerFrom.getSelectedItemPosition();
            int toPos = spinnerTo.getSelectedItemPosition();
            spinnerFrom.setSelection(toPos);
            spinnerTo.setSelection(fromPos);
            // Поменять тексты
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
