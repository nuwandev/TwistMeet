/** @type {import('next').NextConfig} */
const nextConfig = {
  // Next.js 16 auto-generates AGENTS.md/CLAUDE.md on `next dev` by default; this repo
  // already has its own agent-facing docs (DECISIONS.md, TRACEABILITY.md), so don't.
  agentRules: false,
  // Minimal, self-contained runtime output (.next/standalone) for the production Docker image —
  // no effect on `next dev`. See web/Dockerfile and DEPLOYMENT.md.
  output: "standalone",
};

export default nextConfig;
