/** @type {import('next').NextConfig} */
const nextConfig = {
  // Next.js 16 auto-generates AGENTS.md/CLAUDE.md on `next dev` by default; this repo
  // already has its own agent-facing docs (DECISIONS.md, TRACEABILITY.md), so don't.
  agentRules: false,
};

export default nextConfig;
