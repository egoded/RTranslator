# RTranslator

Офлайн-переводчик в реальном времени для Android. Форк проекта [niedev/RTranslator](https://github.com/niedev/RTranslator).

## Функционал

### Режим разговора (Conversation)
Основной режим. Два телефона соединяются по Bluetooth, каждый пользователь говорит на своём языке — приложение переводит и озвучивает в реальном времени. Поддерживает Bluetooth-гарнитуры и работу в фоне.

### Режим рации (WalkieTalkie)
Быстрый перевод на одном телефоне — для коротких разговоров (спросить дорогу, поговорить с продавцом). Собеседники говорят по очереди, приложение определяет язык автоматически.

### Текстовый перевод
Классический текстовый переводчик.

## Технологии

| Компонент | Технология |
|-----------|-----------|
| Перевод | [Meta NLLB](https://ai.meta.com/research/no-language-left-behind/) (NLLB-Distilled-600M) |
| Распознавание речи | [OpenAI Whisper](https://openai.com/index/whisper/) (Whisper-Small-244M) |
| Инференс | [OnnxRuntime](https://github.com/microsoft/onnxruntime) |
| Bluetooth | [BluetoothCommunicator](https://github.com/niedev/BluetoothCommunicator) |
| Токенизация | [SentencePiece](https://github.com/google/sentencepiece) |
| Определение языка | [ML Kit](https://developers.google.com/ml-kit/language/identification) |

## Поддерживаемые языки

Арабский, болгарский, каталанский, китайский, хорватский, чешский, датский, нидерландский, английский, финский, французский, галисийский, немецкий, греческий, итальянский, японский, корейский, македонский, польский, португальский, румынский, русский, словацкий, испанский, шведский, тамильский, тайский, турецкий, украинский, урду, вьетнамский.

## Требования

- Android-устройство с **6 ГБ RAM** и более
- При первом запуске загружаются AI-модели (~1.2 ГБ)

## Ссылки

- Оригинальный проект: https://github.com/niedev/RTranslator
- Наш форк: https://github.com/egoded/RTranslator
