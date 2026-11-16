import {
  createServer,
  type IncomingMessage,
  type ServerResponse,
} from "node:http";
import { existsSync, readFileSync } from "node:fs";
import { parse } from "dotenv";
import { loadConfig } from "./config.js";
import { createAgentService } from "./agent-service.js";
import { createHandlers } from "./handlers.js";
import { docsHtml, openapi } from "./docs.js";

const path = new URL("../.env", import.meta.url);
const env = {
  ...(existsSync(path) ? parse(readFileSync(path)) : {}),
  ...process.env,
};
const port = Number(env.API_PORT || 8700),
  host = env.API_HOST || "127.0.0.1";
const origins = new Set(
  (env.ALLOWED_ORIGINS || "")
    .split(",")
    .map((x) => x.trim())
    .filter(Boolean),
);
const config = loadConfig();
const handlers = createHandlers(config, createAgentService(config));
async function waitForDrain(res: ServerResponse) {
  await new Promise<void>((resolve) => {
    const cleanup = () => {
      res.off("drain", cleanup);
      res.off("close", cleanup);
      resolve();
    };
    res.once("drain", cleanup);
    res.once("close", cleanup);
    if (res.destroyed) cleanup();
  });
}
async function handle(req: IncomingMessage, res: ServerResponse) {
  const origin = req.headers.origin;
  if (origin && !origins.has(origin)) {
    res.writeHead(403, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ detail: "Origin is not allowed." }));
    return;
  }
  if (origin) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    });
    res.end();
    return;
  }
  const route = (req.url || "/").split("?")[0];
  if (req.method === "GET" && (route === "/docs" || route === "/openapi.json")) {
    res.writeHead(200, {"Content-Type":route === "/docs" ? "text/html; charset=utf-8" : "application/json"});
    res.end(route === "/docs" ? docsHtml : JSON.stringify(openapi));
    return;
  }
  if (route === "/api/health" && req.method === "GET") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "ok",
        api_key_configured: config.apiKeyConfigured,
      }),
    );
    return;
  }
  if (
    !["/api/agent/query", "/api/agent/stream"].includes(route) ||
    req.method !== "POST"
  ) {
    res.writeHead(404);
    res.end();
    return;
  }
  const chunks: Buffer[] = [];
  let bytes = 0;
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > 32768) {
      res.writeHead(413, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ detail: "Request body is too large." }));
      return;
    }
    chunks.push(chunk);
  }
  const abort = new AbortController();
  const disconnect = () => {
    if (!res.writableEnded) abort.abort();
  };
  res.on("close", disconnect);
  // CORS has already checked the real Origin; do not forward it as same-origin.
  const request = new Request(`http://127.0.0.1:${port}${route}`, {
    method: "POST",
    headers: { "Content-Type": req.headers["content-type"] || "" },
    body: Buffer.concat(chunks),
    signal: abort.signal,
  });
  const response = await (route.endsWith("/stream")
    ? handlers.stream(request)
    : handlers.query(request));
  res.writeHead(response.status, Object.fromEntries(response.headers));
  res.flushHeaders();
  const reader = response.body?.getReader();
  try {
    if (reader)
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        if (!res.write(value)) await waitForDrain(res);
        if (res.destroyed) break;
      }
    res.end();
  } finally {
    await reader?.cancel().catch(() => {});
    reader?.releaseLock();
    res.off("close", disconnect);
  }
}
const server = createServer((req, res) => {
  void handle(req, res).catch(() => {
    if (!res.headersSent) {
      res.writeHead(500, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          detail: "The server could not process this request.",
        }),
      );
    } else res.end();
  });
});
server.requestTimeout = 200000;
server.listen(port, host, () =>
  console.log(
    `Agent API listening at http://${host}:${port} (key configured: ${config.apiKeyConfigured})`,
  ),
);
