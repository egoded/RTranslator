#!/bin/bash
# Сборка APK для окружения (dev / stage / prod)
# Версионирование (промоушен): dev +1, stage ← dev, prod ← stage
# Git-теги: dev-{N}, stage-{N} — prod собирается из кода тега stage
#
# Использование: bash Server/build.sh [env]
#   bash Server/build.sh          # по умолчанию dev
#   bash Server/build.sh stage    # собрать stage (версия от dev)
#   bash Server/build.sh prod     # собрать prod (код и версия от stage)

cd "$(dirname "$0")/.."

export JAVA_HOME=/home/u_kadykovd_ubunturdp/.local/tools/jdk-17.0.2
export ANDROID_HOME=/home/u_kadykovd_ubunturdp/.local/tools/android-sdk

ENV="${1:-dev}"
VERSIONS_FILE="Server/versions.json"

# Проверяем валидность окружения
if [[ "$ENV" != "dev" && "$ENV" != "stage" && "$ENV" != "prod" ]]; then
    echo "Ошибка: неизвестное окружение '$ENV'. Допустимые: dev, stage, prod"
    exit 1
fi

# Проверяем наличие versions.json
if [ ! -f "$VERSIONS_FILE" ]; then
    echo "Ошибка: файл $VERSIONS_FILE не найден"
    exit 1
fi

# Определяем versionCode по модели промоушена
if [ "$ENV" == "dev" ]; then
    OLD_VERSION_CODE=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data.get('dev', {}).get('versionCode', 0))
")
    NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))
    echo "Окружение: dev (инкремент)"
    echo "versionCode: $OLD_VERSION_CODE → $NEW_VERSION_CODE"
elif [ "$ENV" == "stage" ]; then
    NEW_VERSION_CODE=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data.get('dev', {}).get('versionCode', 0))
")
    OLD_VERSION_CODE=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data.get('stage', {}).get('versionCode', 0))
")
    echo "Окружение: stage (промоушен из dev)"
    echo "versionCode: $OLD_VERSION_CODE → $NEW_VERSION_CODE (dev=$NEW_VERSION_CODE)"
elif [ "$ENV" == "prod" ]; then
    NEW_VERSION_CODE=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data.get('stage', {}).get('versionCode', 0))
")
    OLD_VERSION_CODE=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data.get('prod', {}).get('versionCode', 0))
")
    STAGE_TAG="stage-$NEW_VERSION_CODE"
    if ! git rev-parse "$STAGE_TAG" >/dev/null 2>&1; then
        echo "Ошибка: тег $STAGE_TAG не найден. Сначала соберите stage."
        exit 1
    fi
    echo "Окружение: prod (промоушен из stage)"
    echo "versionCode: $OLD_VERSION_CODE → $NEW_VERSION_CODE (stage=$NEW_VERSION_CODE)"
    echo "Код из тега: $STAGE_TAG ($(git log -1 --format='%h %s' $STAGE_TAG))"
fi

# Формируем имя задачи Gradle
TASK_NAME="assemble$(echo "$ENV" | sed 's/./\U&/')Debug"

# --- Сборка prod: checkout на тег stage ---
if [ "$ENV" == "prod" ]; then
    CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

    # Сохраняем незакоммиченные изменения
    STASHED=false
    if ! git diff --quiet || ! git diff --cached --quiet; then
        git stash push -m "build-prod-temp"
        STASHED=true
    fi

    # Переключаемся на тег stage
    git checkout "$STAGE_TAG"

    # Обновляем versionCode в build.gradle
    sed -i "s/versionCode [0-9]*/versionCode $NEW_VERSION_CODE/" app/build.gradle

    echo "Запуск: ./gradlew $TASK_NAME"
    ./gradlew "$TASK_NAME"
    BUILD_RESULT=$?

    # Восстанавливаем build.gradle и возвращаемся
    git checkout -- app/build.gradle
    git checkout "$CURRENT_BRANCH"
    if [ "$STASHED" = true ]; then
        git stash pop
    fi

    if [ $BUILD_RESULT -eq 0 ]; then
        python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
data['prod']['versionCode'] = $NEW_VERSION_CODE
data['prod']['changelog'] = 'Build $(date '+%Y-%m-%d %H:%M')'
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(data, f, indent=4, ensure_ascii=False)
"
        # Копируем APK
        APK_DIR="Server/apk/prod"
        mkdir -p "\$APK_DIR"
        APK_PATH="app/build/outputs/apk/prod/debug/app-prod-debug.apk"
        if [ -f "\$APK_PATH" ]; then
            cp "\$APK_PATH" "\$APK_DIR/RTranslator_prod.apk"
            echo "APK скопирован: \$APK_DIR/RTranslator_prod.apk"
        fi
        git tag "prod-$NEW_VERSION_CODE"
        echo "APK собран (prod), versions.json обновлён (versionCode=$NEW_VERSION_CODE)"
        echo "Тег создан: prod-$NEW_VERSION_CODE"
    else
        echo "Ошибка сборки!"
        exit 1
    fi
    exit 0
fi

# --- Сборка dev / stage: из текущего кода ---

# Обновляем versionCode в build.gradle
sed -i "s/versionCode [0-9]*/versionCode $NEW_VERSION_CODE/" app/build.gradle

echo "Запуск: ./gradlew $TASK_NAME"
./gradlew "$TASK_NAME"

if [ $? -eq 0 ]; then
    python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
data['$ENV']['versionCode'] = $NEW_VERSION_CODE
data['$ENV']['changelog'] = 'Build $(date '+%Y-%m-%d %H:%M')'
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(data, f, indent=4, ensure_ascii=False)
"
    # Копируем APK
    APK_DIR="Server/apk/$ENV"
    mkdir -p "$APK_DIR"
    APK_PATH="app/build/outputs/apk/$ENV/debug/app-${ENV}-debug.apk"
    if [ -f "$APK_PATH" ]; then
        cp "$APK_PATH" "$APK_DIR/RTranslator_${ENV}.apk"
        echo "APK скопирован: $APK_DIR/RTranslator_${ENV}.apk"
    fi

    # Создаём git-тег
    git tag "${ENV}-${NEW_VERSION_CODE}"
    echo "APK собран ($ENV), versions.json обновлён (versionCode=$NEW_VERSION_CODE)"
    echo "Тег создан: ${ENV}-${NEW_VERSION_CODE}"
else
    echo "Ошибка сборки!"
    git checkout -- app/build.gradle
    exit 1
fi
