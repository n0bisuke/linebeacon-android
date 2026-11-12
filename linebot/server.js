'use strict';

/**
 * LINE Messaging API webhook receiver for LINE Simple Beacon testing.
 *
 * 受け取ったWebhookイベントのうち type: "beacon" のものをログに表示する。
 * LINE_CHANNEL_ACCESS_TOKEN を設定すると、検知をLINEのトークに返信する。
 *
 * 依存ライブラリなし (Node.js の組み込みモジュールのみ)。
 *
 * 設定は server/.env または環境変数で渡す(トークン類はコードに書かないこと):
 *   LINE_CHANNEL_SECRET        Messaging APIチャネルのChannel secret(署名検証)
 *   LINE_CHANNEL_ACCESS_TOKEN  (任意) 返信メッセージ送信用
 *   PORT / WEBHOOK_PATH        (任意) 既定は 65135 / /webhook
 */

const http = require('http');
const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

loadDotEnv(path.join(__dirname, '.env'));

// 既定ポートは 0xFE6F (Service UUID値) = 65135。3000は衝突しやすいので使わない。
const PORT = Number(process.env.PORT || '65135');
const WEBHOOK_PATH = process.env.WEBHOOK_PATH || '/webhook';
const CHANNEL_SECRET = process.env.LINE_CHANNEL_SECRET || '';
const CHANNEL_ACCESS_TOKEN = process.env.LINE_CHANNEL_ACCESS_TOKEN || '';

const MAX_BODY_BYTES = 1024 * 1024; // LINEのWebhookは数KB程度。安全側の上限。

function loadDotEnv(file) {
  let text;
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch {
    return; // .env が無くても環境変数だけで動く
  }
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    const value = m[2].replace(/^["']|["']$/g, '');
    if (process.env[m[1]] === undefined) process.env[m[1]] = value;
  }
}

/** x-line-signature ヘッダの検証 (HMAC-SHA256, base64)。 */
function verifySignature(rawBody, signatureHeader) {
  if (!CHANNEL_SECRET) return true; // 未設定なら検証スキップ(ログに警告)
  if (!signatureHeader) return false;
  const expected = crypto
    .createHmac('sha256', CHANNEL_SECRET)
    .update(rawBody)
    .digest('base64');
  const a = Buffer.from(expected);
  const b = Buffer.from(signatureHeader);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

// ---- beaconイベントの整形ログ ----

/**
 * dmのHEXを表示用に整形する。UTF-8テキストとして安全に復元できる場合のみ
 * 解釈結果を併記する (例: "0x584f4f41 (= ほげ)"). バイナリならHEXのまま。
 */
function formatDm(dm) {
  if (dm === undefined) return '(なし)';
  const bytes = Buffer.from(dm, 'hex');
  if (bytes.length === 0 || bytes.toString('hex') !== dm.toLowerCase()) {
    return `0x${dm}`; // HEXとして解釈できない/空の入力はそのまま
  }
  const text = bytes.toString('utf8');
  const lossless =
    Buffer.from(text, 'utf8').equals(bytes) &&
    !text.includes('�') &&
    !/[\u0000-\u001f]/.test(text);
  return lossless ? `0x${dm}(= "${text}")` : `0x${dm}`;
}

function describeBeaconEvent(event) {
  const b = event.beacon || {};
  const dm = formatDm(b.dm);
  const when = new Date().toLocaleString('ja-JP', { hour12: false });
  const who =
    event.source && event.source.userId
      ? `userId=${event.source.userId.slice(0, 8)}…`
      : 'userId=(unknown)';
  return [
    '',
    '━━━ 📡 ビーコン検知! ━━━',
    `  時刻   : ${when}`,
    `  種別   : ${b.type || '(unknown)'}   ${b.sc ? `(sc=${b.sc})` : ''}`,
    `  HWID   : ${b.hwid || '(unknown)'}`,
    `  dm     : ${dm}`,
    `  検知元 : ${who}`,
    '━━━━━━━━━━━━━━━━━━━',
  ].join('\n');
}

/** 指定テキストをreplyToken宛てに返信する(トークン未設定なら何もしない)。 */
function replyText(replyToken, text) {
  if (!CHANNEL_ACCESS_TOKEN) return;
  if (!replyToken || replyToken === '00000000000000000000000000000000') return; // 検証用ダミー
  const body = JSON.stringify({
    replyToken,
    messages: [{ type: 'text', text }],
  });
  const req = https.request(
    {
      hostname: 'api.line.me',
      path: '/v2/bot/message/reply',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${CHANNEL_ACCESS_TOKEN}`,
        'Content-Length': Buffer.byteLength(body),
      },
    },
    (res) => {
      if (res.statusCode !== 200) {
        console.warn(`[reply] LINE API returned ${res.statusCode}`);
      }
      res.resume(); // drain
    },
  );
  req.on('error', (err) => console.warn(`[reply] 送信失敗: ${err.message}`));
  req.end(body);
}

// ---- HTTPサーバー ----

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/') {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end(`LINE Beacon webhook server\nPOST ${WEBHOOK_PATH} で待機中\n`);
    return;
  }

  if (req.method === 'POST' && req.url === WEBHOOK_PATH) {
    const chunks = [];
    let size = 0;
    let aborted = false;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        aborted = true;
        res.writeHead(413).end();
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      if (aborted) return;
      const rawBody = Buffer.concat(chunks);

      if (!verifySignature(rawBody, req.headers['x-line-signature'])) {
        console.warn('[webhook] 署名検証に失敗したリクエストを拒否しました');
        res.writeHead(403).end();
        return;
      }

      let payload;
      try {
        payload = JSON.parse(rawBody.toString('utf8'));
      } catch {
        console.warn('[webhook] JSONとして解釈できないリクエストを破棄しました');
        res.writeHead(400).end();
        return;
      }

      // 200は必ず先に返す(LINE側は応答が遅いとリトライするため)
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('{}');

      for (const event of payload.events || []) {
        if (event.type === 'beacon') {
          console.log(describeBeaconEvent(event));
          const b = event.beacon || {};
          replyText(
            event.replyToken,
            `📡 ビーコン検知!\nイベント: ${b.type}\nHWID: ${b.hwid||'?'}\ndm: ${formatDm(b.dm)}`,
          );
        } else if (event.type === 'message' && event.message && event.message.type === 'text') {
          // オウム返し: 届いたテキストをそのまま返す(LINE bot動作確認用)
          console.log(`[message] オウム返し: ${event.message.text}`);
          replyText(event.replyToken, event.message.text);
        } else {
          console.log(`[webhook] 他イベント: type=${event.type}`);
        }
      }
    });
    req.on('error', () => { /* クライアント切断などは無視 */ });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('Not Found\n');
});

server.listen(PORT, () => {
  console.log(`LINE Beacon webhook server listening on :${PORT}${WEBHOOK_PATH}`);
  console.log(
    CHANNEL_SECRET
      ? '署名検証: 有効 (LINE_CHANNEL_SECRET を使用)'
      : '署名検証: 無効 (LINE_CHANNEL_SECRET 未設定 — 本番では必ず設定すること)',
  );
  console.log(
    CHANNEL_ACCESS_TOKEN
      ? '返信: 有効 (検知をLINEトークに返信します)'
      : '返信: 無効 (LINE_CHANNEL_ACCESS_TOKEN 未設定 — ログ確認のみ)',
  );
});
