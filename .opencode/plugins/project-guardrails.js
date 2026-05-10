const SAFE_PREFIXES = [
  "ctx ",
  "tools/ai-context-proxy/bin/ctx ",
  "./tools/ai-context-proxy/bin/ctx ",
  "tools/backlog ",
  "./tools/backlog ",
  "python3 tools/backlog.py ",
  "python3 ./tools/backlog.py ",
  "python3 tools/ai-context-proxy/",
  "python3 ./tools/ai-context-proxy/",
]

const RAW_CONTEXT_RE = /(^|[;&|()]\s*)(cat|grep|rg|find|sed)\s+/

export const BannerModProjectGuardrails = async () => {
  return {
    "tool.execute.before": async (input, output) => {
      if (input.tool !== "bash") return

      const command = String(output.args?.command ?? "").trim()
      if (!command) return
      if (SAFE_PREFIXES.some((prefix) => command.startsWith(prefix))) return

      if (command.includes("BANNERMOD_BACKLOG.json") || command.includes("BANNERMOD_BACKLOG.sqlite")) {
        throw new Error(
          "Blocked direct backlog access. Use tools/backlog batch/show/add/validate/stage instead."
        )
      }

      if (RAW_CONTEXT_RE.test(command) || /^(cat|grep|rg|find|sed)(\s|$)/.test(command)) {
        throw new Error(
          "Blocked raw context dump. Use tools/ai-context-proxy/bin/ctx search/file/exact/log instead."
        )
      }
    },
  }
}
