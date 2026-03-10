const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

const VERSIONS_FILE = path.join(__dirname, 'versions.json');
const APK_DIR = path.join(__dirname, 'apk');

// Допустимые среды
const ENVS = ['dev', 'stage', 'prod'];

function readVersions() {
  return JSON.parse(fs.readFileSync(VERSIONS_FILE, 'utf8'));
}

function writeVersions(data) {
  fs.writeFileSync(VERSIONS_FILE, JSON.stringify(data, null, 2), 'utf8');
}

// GET /:env/version — получить текущую версию
app.get('/:env/version', (req, res) => {
  const env = req.params.env;
  if (!ENVS.includes(env)) {
    return res.status(400).json({ error: `Неизвестная среда: ${env}` });
  }

  const versions = readVersions();
  res.json(versions[env]);
});

// GET /:env/download — скачать APK
app.get('/:env/download', (req, res) => {
  const env = req.params.env;
  if (!ENVS.includes(env)) {
    return res.status(400).json({ error: `Неизвестная среда: ${env}` });
  }

  const apkPath = path.join(APK_DIR, env, 'app.apk');
  if (!fs.existsSync(apkPath)) {
    return res.status(404).json({ error: 'APK не найден' });
  }

  res.download(apkPath, `RTranslator-${env}.apk`);
});

app.listen(PORT, () => {
  console.log(`Сервер обновлений запущен на порту ${PORT}`);
});
