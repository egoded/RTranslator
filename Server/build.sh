#!/bin/bash

# Сборка RTranslator
# Использование: ./build.sh [env]
# env: dev (по умолчанию) | stage | prod

ENV="${1:-dev}"
VALID_ENVS=("dev" "stage" "prod")

if [[ ! " ${VALID_ENVS[@]} " =~ " ${ENV} " ]]; then
  echo "Ошибка: неизвестная среда '$ENV'. Допустимые: dev, stage, prod"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
VERSIONS_FILE="$SCRIPT_DIR/versions.json"
APK_DIR="$SCRIPT_DIR/apk/$ENV"
BUILD_GRADLE="$PROJECT_DIR/app/build.gradle"

echo "=== Сборка RTranslator ($ENV) ==="

# Читаем текущую версию
CURRENT_VERSION=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data['$ENV']['versionCode'])
")

if [ "$ENV" == "dev" ]; then
  # dev: инкремент versionCode
  NEW_VERSION=$((CURRENT_VERSION + 1))
  python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
data['dev']['versionCode'] = $NEW_VERSION
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
"
  echo "versionCode: $CURRENT_VERSION -> $NEW_VERSION"

elif [ "$ENV" == "stage" ]; then
  # stage: берёт versionCode из dev
  NEW_VERSION=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data['dev']['versionCode'])
")
  python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
data['stage']['versionCode'] = $NEW_VERSION
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
"
  echo "versionCode: $CURRENT_VERSION -> $NEW_VERSION (из dev)"

elif [ "$ENV" == "prod" ]; then
  # prod: берёт versionCode из stage
  NEW_VERSION=$(python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
print(data['stage']['versionCode'])
")
  python3 -c "
import json
with open('$VERSIONS_FILE') as f:
    data = json.load(f)
data['prod']['versionCode'] = $NEW_VERSION
with open('$VERSIONS_FILE', 'w') as f:
    json.dump(data, f, indent=2)
"
  echo "versionCode: $CURRENT_VERSION -> $NEW_VERSION (из stage)"
fi

# Обновляем versionCode в build.gradle
sed -i "s/versionCode [0-9]*/versionCode $NEW_VERSION/" "$BUILD_GRADLE"
echo "build.gradle обновлён: versionCode = $NEW_VERSION"

# Создаём папку для APK
mkdir -p "$APK_DIR"

# Сборка APK через Gradle
FLAVOR_CAP="$(tr '[:lower:]' '[:upper:]' <<< ${ENV:0:1})${ENV:1}"
echo "Запуск Gradle: assemble${FLAVOR_CAP}Debug..."
cd "$PROJECT_DIR" && ./gradlew assemble${FLAVOR_CAP}Debug

if [ $? -eq 0 ]; then
  APK_PATH="$PROJECT_DIR/app/build/outputs/apk/$ENV/debug/app-${ENV}-debug.apk"
  if [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$APK_DIR/app.apk"
    echo "APK скопирован: $APK_DIR/app.apk"
  else
    echo "Ошибка: APK не найден по пути $APK_PATH"
    exit 1
  fi
else
  echo "Ошибка сборки Gradle"
  exit 1
fi

echo "=== Сборка завершена ($ENV, versionCode=$NEW_VERSION) ==="
