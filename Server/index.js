const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3001;
const VERSIONS_FILE = path.join(__dirname, 'versions.json');
const SCAN_LOG_FILE = path.join(__dirname, 'scan.log');
const MODELS_DIR = path.join(__dirname, 'models');
const VALID_ENVS = ['dev', 'stage', 'prod'];

function getVersions() {
    try {
        const data = fs.readFileSync(VERSIONS_FILE, 'utf8');
        return JSON.parse(data);
    } catch (e) {
        return {};
    }
}

function getApkPath(env) {
    return path.join(__dirname, 'apk', env, `RTranslator_${env}.apk`);
}

const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const pathname = url.pathname;

    console.log(`[${new Date().toLocaleTimeString()}] ${req.method} ${pathname}`);

    // GET /:env/version — текущая версия для окружения
    const versionMatch = pathname.match(/^\/(dev|stage|prod)\/version$/);
    if (versionMatch && req.method === 'GET') {
        const env = versionMatch[1];
        const versions = getVersions();
        const version = versions[env] || { versionCode: 0, versionName: "0.0" };
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(version));
        return;
    }

    // GET /:env/download — скачать APK для окружения
    const downloadMatch = pathname.match(/^\/(dev|stage|prod)\/download$/);
    if (downloadMatch && req.method === 'GET') {
        const env = downloadMatch[1];
        const apkPath = getApkPath(env);
        if (!fs.existsSync(apkPath)) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: `APK не найден для окружения ${env}` }));
            return;
        }

        const stat = fs.statSync(apkPath);
        res.writeHead(200, {
            'Content-Type': 'application/vnd.android.package-archive',
            'Content-Length': stat.size,
            'Content-Disposition': `attachment; filename="RTranslator_${env}.apk"`
        });
        fs.createReadStream(apkPath).pipe(res);
        return;
    }

    // GET /models — список моделей с размерами
    if (pathname === '/models' && req.method === 'GET') {
        if (!fs.existsSync(MODELS_DIR)) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify([]));
            return;
        }
        const files = fs.readdirSync(MODELS_DIR).filter(f => f.endsWith('.onnx'));
        const models = files.map(f => ({
            name: f,
            size: fs.statSync(path.join(MODELS_DIR, f)).size
        }));
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(models));
        return;
    }

    // GET /models/:filename — скачать модель (с поддержкой Range для докачки)
    const modelMatch = pathname.match(/^\/models\/(.+)$/);
    if (modelMatch && req.method === 'GET') {
        const filename = modelMatch[1];
        const filePath = path.join(MODELS_DIR, filename);
        if (!fs.existsSync(filePath)) {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: `Модель ${filename} не найдена` }));
            return;
        }

        const stat = fs.statSync(filePath);
        const totalSize = stat.size;
        const rangeHeader = req.headers['range'];

        if (rangeHeader) {
            const match = rangeHeader.match(/bytes=(\d+)-(\d*)/);
            if (match) {
                const start = parseInt(match[1], 10);
                const end = match[2] ? parseInt(match[2], 10) : totalSize - 1;
                const chunkSize = end - start + 1;
                res.writeHead(206, {
                    'Content-Type': 'application/octet-stream',
                    'Content-Range': `bytes ${start}-${end}/${totalSize}`,
                    'Accept-Ranges': 'bytes',
                    'Content-Length': chunkSize,
                    'Content-Disposition': `attachment; filename="${filename}"`
                });
                fs.createReadStream(filePath, { start, end }).pipe(res);
                return;
            }
        }

        res.writeHead(200, {
            'Content-Type': 'application/octet-stream',
            'Accept-Ranges': 'bytes',
            'Content-Length': totalSize,
            'Content-Disposition': `attachment; filename="${filename}"`
        });
        fs.createReadStream(filePath).pipe(res);
        return;
    }

    // POST /log — приём логов
    if (pathname === '/log' && req.method === 'POST') {
        let body = '';
        const MAX_BODY = 1024 * 1024;
        req.on('data', chunk => {
            body += chunk;
            if (body.length > MAX_BODY) {
                res.writeHead(413, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: 'Body too large' }));
                req.destroy();
                return;
            }
        });
        req.on('end', () => {
            const timestamp = new Date().toISOString();
            const line = `[${timestamp}] ${body}\n`;
            fs.appendFileSync(SCAN_LOG_FILE, line);
            console.log(`LOG: ${body}`);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'ok' }));
        });
        return;
    }

    // GET /log — просмотр логов
    if (pathname === '/log' && req.method === 'GET') {
        if (!fs.existsSync(SCAN_LOG_FILE)) {
            res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Логов пока нет');
            return;
        }
        const data = fs.readFileSync(SCAN_LOG_FILE, 'utf8');
        res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end(data);
        return;
    }

    // GET / — статус сервера
    if (pathname === '/' && req.method === 'GET') {
        const versions = getVersions();
        const status = {};
        for (const env of VALID_ENVS) {
            status[env] = {
                version: versions[env] || null,
                apkAvailable: fs.existsSync(getApkPath(env))
            };
        }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'OK', environments: status }));
        return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Сервер обновлений RTranslator запущен: http://0.0.0.0:${PORT}`);
    console.log(`  /:env/version  — текущая версия (dev|stage|prod)`);
    console.log(`  /:env/download — скачать APK`);
    console.log(`  /models/:file  — скачать модель`);
    console.log(`  /log           — логи (POST/GET)`);
});
