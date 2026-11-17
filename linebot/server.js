
/**
 * LINE Messaging API webhook receiver for LINE Simple Beacon testing.
 *
 * ES Modules版 / 外部依存ライブラリなし。
 *
 * 機能:
 * - Beaconイベントのログ表示とLINE返信
 * - テキストメッセージのオウム返し
 * - GET /apk によるAndroid APK配信
 * - Webhook署名検証
 * - HWIDマスク表示
 *
 * 環境変数:
 *   LINE_CHANNEL_SECRET
 *   LINE_CHANNEL_ACCESS_TOKEN (任意)
 *   PORT / WEBHOOK_PATH (任意)
 *   MASK_HWID (任意)
 *   APK_FILE / APK_PATH (任意)
 */

import http from "node:http";
import https from "node:https";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// ---------- 設定 ----------

// 0xFE6F = 65135
const PORT = Number(process.env.PORT || "65135");
const WEBHOOK_PATH = process.env.WEBHOOK_PATH || "/webhook";

const CHANNEL_SECRET = process.env.LINE_CHANNEL_SECRET || "";
const CHANNEL_ACCESS_TOKEN =
  process.env.LINE_CHANNEL_ACCESS_TOKEN || "";

const MASK_HWID = String(process.env.MASK_HWID || "1") !== "0";

const APK_FILE =
  process.env.APK_FILE ||
  path.join(
    __dirname,
    "..",
    "android-app",
    "dist",
    "app-debug.apk"
  );

const APK_PATH = process.env.APK_PATH || "/apk";

const MAX_BODY_BYTES = 1024 * 1024;

// ---------- HWIDマスク ----------

function maskHwid(hwid) {
  if (!MASK_HWID || typeof hwid !== "string" || hwid.length < 4) {
    return hwid;
  }

  return (
    hwid.slice(0, 2) +
    "•".repeat(Math.max(hwid.length - 4, 2)) +
    hwid.slice(-2)
  );
}

// ---------- 署名検証 ----------

function verifySignature(rawBody, signatureHeader) {
  if (!CHANNEL_SECRET) return true;
  if (!signatureHeader) return false;

  const expected = crypto
    .createHmac("sha256", CHANNEL_SECRET)
    .update(rawBody)
    .digest("base64");

  const a = Buffer.from(expected);
  const b = Buffer.from(signatureHeader);

  return (
    a.length === b.length &&
    crypto.timingSafeEqual(a, b)
  );
}

// ---------- Device Message ----------

function formatDm(dm) {
  if (dm === undefined) return "(なし)";

  const bytes = Buffer.from(dm, "hex");

  if (
    bytes.length === 0 ||
    bytes.toString("hex") !== dm.toLowerCase()
  ) {
    return `0x${dm}`;
  }

  const text = bytes.toString("utf8");

  const lossless =
    Buffer.from(text, "utf8").equals(bytes) &&
    !text.includes("�") &&
    !/[\u0000-\u001f]/.test(text);

  return lossless
    ? `0x${dm}(= "${text}")`
    : `0x${dm}`;
}

// ---------- Beaconイベント整形 ----------

function describeBeaconEvent(event) {
  const b = event.beacon || {};
  const dm = formatDm(b.dm);

  const when = new Date().toLocaleString("ja-JP", {
    hour12: false,
  });

  const who = event.source?.userId
    ? `userId=${event.source.userId.slice(0, 8)}…`
    : "userId=(unknown)";

  return [
    "",
    "━━━ 📡 ビーコン検知! ━━━",
    `  時刻   : ${when}`,
    `  種別   : ${b.type || "(unknown)"}   ${
      b.sc ? `(sc=${b.sc})` : ""
    }`,
    `  HWID   : ${maskHwid(b.hwid) || "(unknown)"}`,
    `  dm     : ${dm}`,
    `  検知元 : ${who}`,
    "━━━━━━━━━━━━━━━━━━━",
  ].join("\n");
}

// ---------- LINE Reply API ----------

function replyText(replyToken, text) {
  if (!CHANNEL_ACCESS_TOKEN) return;

  if (
    !replyToken ||
    replyToken === "00000000000000000000000000000000"
  ) {
    return;
  }

  const body = JSON.stringify({
    replyToken,
    messages: [
      {
        type: "text",
        text,
      },
    ],
  });

  const req = https.request(
    {
      hostname: "api.line.me",
      path: "/v2/bot/message/reply",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${CHANNEL_ACCESS_TOKEN}`,
        "Content-Length": Buffer.byteLength(body),
      },
    },
    (res) => {
      if (res.statusCode !== 200) {
        console.warn(
          `[reply] LINE API returned ${res.statusCode}`
        );
      }

      res.resume();
    }
  );

  req.on("error", (err) => {
    console.warn(`[reply] 送信失敗: ${err.message}`);
  });

  req.end(body);
}

// ---------- HTTPサーバー ----------

const server = http.createServer((req, res) => {

  // GET /
  if (req.method === "GET" && req.url === "/") {
    res.writeHead(200, {
      "Content-Type": "text/plain; charset=utf-8",
    });

    res.end(
      `LINE Beacon webhook server\n` +
      `POST ${WEBHOOK_PATH} で待機中\n` +
      `GET ${APK_PATH} でAPKを配信中\n`
    );

    return;
  }

  // GET /apk
  if (req.method === "GET" && req.url === APK_PATH) {
    fs.stat(APK_FILE, (err, stat) => {
      if (err || !stat.isFile()) {
        res.writeHead(404, {
          "Content-Type": "text/plain; charset=utf-8",
        });

        res.end(
          "APKが見つかりません: " +
          "android-app/dist/app-debug.apk を" +
          "ビルドして配置してください\n"
        );

        return;
      }

      res.writeHead(200, {
        "Content-Type":
          "application/vnd.android.package-archive",
        "Content-Length": stat.size,
        "Content-Disposition":
          'attachment; filename="app-debug.apk"',
      });

      fs.createReadStream(APK_FILE)
        .on("error", () => res.destroy())
        .pipe(res);
    });

    return;
  }

  // POST /webhook
  if (
    req.method === "POST" &&
    req.url === WEBHOOK_PATH
  ) {
    const chunks = [];
    let size = 0;
    let aborted = false;

    req.on("data", (chunk) => {
      size += chunk.length;

      if (size > MAX_BODY_BYTES) {
        aborted = true;
        res.writeHead(413).end();
        req.destroy();
        return;
      }

      chunks.push(chunk);
    });

    req.on("end", () => {
      if (aborted) return;

      const rawBody = Buffer.concat(chunks);

      if (
        !verifySignature(
          rawBody,
          req.headers["x-line-signature"]
        )
      ) {
        console.warn(
          "[webhook] 署名検証に失敗したリクエストを拒否しました"
        );
        res.writeHead(403).end();
        return;
      }

      let payload;

      try {
        payload = JSON.parse(rawBody.toString("utf8"));
      } catch {
        console.warn(
          "[webhook] JSONとして解釈できないリクエストを破棄しました"
        );
        res.writeHead(400).end();
        return;
      }

      // LINEへ先に200を返す
      res.writeHead(200, {
        "Content-Type": "application/json",
      });
      res.end("{}");

      // APK配信URL
      const apkUrl =
        `https://${req.headers.host || `localhost:${PORT}`}` +
        APK_PATH;

      for (const event of payload.events || []) {

        // LINE Beacon
        if (event.type === "beacon") {
          console.log(describeBeaconEvent(event));

          const b = event.beacon || {};

          replyText(
            event.replyToken,
            `📡 ビーコン検知!\n` +
            `イベント: ${b.type}\n` +
            `HWID: ${maskHwid(b.hwid) || "?"}\n` +
            `dm: ${formatDm(b.dm)}`
          );

        // テキストメッセージ
        } else if (
          event.type === "message" &&
          event.message?.type === "text"
        ) {
          const text = event.message.text.trim();

          if (text.toLowerCase() === "apk") {
            console.log(
              `[message] APK URLを返信: ${apkUrl}`
            );

            replyText(
              event.replyToken,
              `⬇️ アプリのダウンロード\n` +
              `${apkUrl}\n\n` +
              `URLを開いてAPKをダウンロード後、` +
              `インストールしてください` +
              `(「提供元不明アプリ」の許可が必要です)`
            );

          } else {
            console.log(
              `[message] オウム返し: ${text}`
            );

            replyText(event.replyToken, text);
          }

        } else {
          console.log(
            `[webhook] 他イベント: type=${event.type}`
          );
        }
      }
    });

    req.on("error", () => {
      // クライアント切断など
    });

    return;
  }

  // その他
  res.writeHead(404, {
    "Content-Type": "text/plain; charset=utf-8",
  });
  res.end("Not Found\n");
});

// ---------- サーバー起動 ----------

server.listen(PORT, () => {
  console.log(
    `LINE Beacon webhook server listening on ` +
    `:${PORT}${WEBHOOK_PATH}`
  );

  console.log(
    CHANNEL_SECRET
      ? "署名検証: 有効 (LINE_CHANNEL_SECRET を使用)"
      : "署名検証: 無効 (LINE_CHANNEL_SECRET 未設定)"
  );

  console.log(
    CHANNEL_ACCESS_TOKEN
      ? "返信: 有効 (検知をLINEトークに返信します)"
      : "返信: 無効 (ログ確認のみ)"
  );
});
