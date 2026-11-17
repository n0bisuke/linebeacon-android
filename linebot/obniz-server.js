
import http from "node:http";
import { createHmac, timingSafeEqual } from "node:crypto";
import Obniz from "obniz";

const {
  OBNIZ_ID,
  LINE_BEACON_HWID,
  LINE_CHANNEL_SECRET,
  LINE_CHANNEL_ACCESS_TOKEN,
  PORT = "65135",
  WEBHOOK_PATH = "/webhook",
  MASK_HWID = "1",
} = process.env;

// ---------- 設定チェック ----------

if (!OBNIZ_ID ||
    !/^[0-9a-f]{10}$/i.test(LINE_BEACON_HWID ?? "") ||
    !LINE_CHANNEL_SECRET ||
    !LINE_CHANNEL_ACCESS_TOKEN) {
  throw new Error("環境変数を確認してください");
}

const port = Number(PORT);

if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error("PORTが不正です");
}

const maskHwid = (hwid) => {
  if (typeof hwid !== "string" || MASK_HWID === "0") {
    return hwid;
  }
  if (hwid.length < 4) return "****";
  return hwid.slice(0, 2) + "******" + hwid.slice(-2);
};

// ---------- LINE Webhook署名検証 ----------

function verifySignature(rawBody, signature) {
  if (typeof signature !== "string") return false;

  const expected = createHmac("sha256", LINE_CHANNEL_SECRET)
    .update(rawBody)
    .digest();

  const received = Buffer.from(signature, "base64");

  return expected.length === received.length &&
    timingSafeEqual(expected, received);
}

// ---------- LINE Reply API ----------

async function replyText(replyToken, text) {
  if (!replyToken ||
      replyToken === "00000000000000000000000000000000") {
    return;
  }

  const response = await fetch(
    "https://api.line.me/v2/bot/message/reply",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${LINE_CHANNEL_ACCESS_TOKEN}`,
      },
      body: JSON.stringify({
        replyToken,
        messages: [{
          type: "text",
          text,
        }],
      }),
      signal: AbortSignal.timeout(10000),
    }
  );

  if (!response.ok) {
    throw new Error(
      `Reply API ${response.status}: ${await response.text()}`
    );
  }

  console.log("[LINE] 返信成功");
}

// ---------- Beaconイベント処理 ----------

async function handleEvent(event) {
  if (event.mode === "standby") return;

  if (event.type === "beacon") {
    const beacon = event.beacon ?? {};

    console.log("\n===== Beacon検知 =====");
    console.log("イベント:", beacon.type);
    console.log("HWID:", maskHwid(beacon.hwid));
    console.log("dm:", beacon.dm ?? "(なし)");
    console.log("ユーザー:", event.source?.userId?.slice(0, 8));
    console.log("======================\n");

    await replyText(
      event.replyToken,
      [
        "Beaconを検知しました！",
        `イベント: ${beacon.type}`,
        `HWID: ${maskHwid(beacon.hwid)}`,
        `dm: ${beacon.dm ?? "(なし)"}`,
      ].join("\n")
    );

  } else if (
    event.type === "message" &&
    event.message?.type === "text"
  ) {
    const text = event.message.text;

    console.log("[LINE] 受信:", text);

    // 動作確認用のオウム返し
    await replyText(event.replyToken, text);

  } else {
    console.log("[LINE] イベント:", event.type);
  }
}

// ---------- Webhookサーバー ----------

const MAX_BODY_BYTES = 1024 * 1024;

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/") {
    res.writeHead(200, {
      "Content-Type": "text/plain; charset=utf-8",
    });
    res.end("obniz LINE Beacon Server\n");
    return;
  }

  if (req.method !== "POST" || req.url !== WEBHOOK_PATH) {
    res.writeHead(404).end("Not Found");
    return;
  }

  try {
    const chunks = [];
    let total = 0;

    for await (const chunk of req) {
      total += chunk.length;

      if (total > MAX_BODY_BYTES) {
        res.writeHead(413).end("Payload Too Large");
        return;
      }

      chunks.push(chunk);
    }

    const rawBody = Buffer.concat(chunks);

    if (!verifySignature(
      rawBody,
      req.headers["x-line-signature"]
    )) {
      console.warn("[Webhook] 署名検証失敗");
      res.writeHead(401).end("Unauthorized");
      return;
    }

    const payload = JSON.parse(rawBody.toString("utf8"));

    console.log(
      `[Webhook] 受信: ${payload.events?.length ?? 0}件`
    );

    // LINEに先に200を返す
    res.writeHead(200, {
      "Content-Type": "application/json",
    });
    res.end("{}");

    // Webhookイベントを非同期処理
    for (const event of payload.events ?? []) {
      handleEvent(event).catch((error) => {
        console.error("[LINE] 処理エラー:", error.message);
      });
    }

  } catch (error) {
    console.error("[Webhook] エラー:", error.message);

    if (!res.headersSent) {
      res.writeHead(400).end("Bad Request");
    }
  }
});

server.listen(port, () => {
  console.log(`Webhook: http://localhost:${port}${WEBHOOK_PATH}`);
});

// ---------- obniz BLE Beacon ----------

const obniz = new Obniz(OBNIZ_ID);

function display(text) {
  obniz.display.clear();
  obniz.display.print(text);
}

obniz.onconnect = async () => {
  try {
    console.log("[obniz] 接続成功");
    display("Connected");

    await obniz.ble.initWait();

    console.log("[obniz] BLE初期化成功");
    display("BLE Ready");

    const hwidBytes = LINE_BEACON_HWID
      .match(/.{2}/g)
      .map((hex) => parseInt(hex, 16));

    // LINE Simple Beacon Advertising Data
    const advData = [
      0x02, 0x01, 0x06,        // Flags
      0x03, 0x03, 0x6F, 0xFE,  // Service UUID: FE6F
      0x0B, 0x16, 0x6F, 0xFE,  // Service Data
      0x02,                    // Frame Type
      ...hwidBytes,            // HWID (5 bytes)
      0x7F,                    // Measured TxPower
      0x00,                    // Device Message
    ];

    obniz.ble.advertisement.setAdvDataRaw(advData);
    await obniz.ble.advertisement.start();

    display("Beacon ON");

    console.log("[obniz] Beacon送信開始");
    console.log("[obniz] HWID:", maskHwid(LINE_BEACON_HWID));

  } catch (error) {
    console.error("[obniz] エラー:", error);
    display("ERROR");
  }
};

obniz.onclose = () => {
  console.log("[obniz] 切断");
};

process.on("SIGINT", async () => {
  console.log("\n終了します");

  server.close();

  try {
    if (obniz.connectionState === "connected") {
      await obniz.ble.advertisement.end();
      display("Beacon OFF");
    }
    obniz.close();
  } catch (error) {
    console.error("[obniz] 終了エラー:", error.message);
  }

  process.exit(0);
});
