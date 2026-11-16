import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "dotenv";

export interface AgentConfig {
  apiKey: string;
  apiKeyConfigured: boolean;
  model: string;
  systemPrompt: string;
  maxTurns: number;
}
export function readConfig(
  env: Readonly<Record<string, string | undefined>>,
): AgentConfig {
  const apiKey = (env.ANTHROPIC_API_KEY || "").trim();
  const maxTurns = Number(env.AGENT_MAX_TURNS || 1);
  if (!Number.isInteger(maxTurns) || maxTurns < 1 || maxTurns > 10)
    throw new Error("AGENT_MAX_TURNS must be an integer from 1 to 10.");
  return {
    apiKey,
    apiKeyConfigured:
      Boolean(apiKey) && apiKey !== "sk-ant-replace-with-your-key",
    model: env.CLAUDE_MODEL || "",
    systemPrompt: env.AGENT_SYSTEM_PROMPT || "",
    maxTurns,
  };
}
export function loadConfig(): AgentConfig {
  // src/ and dist/ are siblings; neither cwd nor other projects affect this path.
  const path = fileURLToPath(new URL("../.env", import.meta.url));
  const file = existsSync(path) ? parse(readFileSync(path)) : {};
  return readConfig({ ...file, ...process.env });
}
