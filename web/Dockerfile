# Production image for the TwistMeet web app (Next.js). Multi-stage, built on `output:
# "standalone"` (next.config.mjs) for a minimal runtime with no package manager or source tree.
# Build context is this `web/` directory (see docker-compose.production.yml and DEPLOYMENT.md).
#
# IMPORTANT build-time vs. run-time env vars: Next.js inlines every NEXT_PUBLIC_* variable into
# the client JavaScript bundle at `next build` time — it is NOT something a container can change
# at `docker run` time. NEXT_PUBLIC_API_BASE_URL (and NEXT_PUBLIC_APP_NAME) must therefore be
# passed as build ARGs, and this image must be rebuilt for each distinct public API origin (e.g.
# a separate staging vs. production build). This is a real constraint of static client bundling,
# not an oversight — see DEPLOYMENT.md for the recommended alternative (a reverse proxy that
# makes the API reachable at a path on the same origin, so the value never has to change).

# ---- Dependencies stage ----
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---- Build stage ----
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_APP_NAME
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME}
# Telemetry is off by default for this project's CI; keep the production image quiet too.
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---- Runtime stage ----
FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Non-root runtime user.
RUN addgroup -S twistmeet && adduser -S twistmeet -G twistmeet

COPY --from=build /app/public ./public
COPY --from=build --chown=twistmeet:twistmeet /app/.next/standalone ./
COPY --from=build --chown=twistmeet:twistmeet /app/.next/static ./.next/static

USER twistmeet
ENV PORT=3000
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:3000/ >/dev/null 2>&1 || exit 1

CMD ["node", "server.js"]
