# syntax=docker/dockerfile:1.7
# ─────────────────────────────────────────────────────────────────────────
# Barbershop frontend (Next.js) — production image, multi-stage:
#   1. deps:   npm ci (cached)
#   2. build:  next build with output "standalone" (NEXT_OUTPUT=standalone)
#   3. runner: only the traced server files + static assets, non-root, healthcheck
#
# NEXT_PUBLIC_* values are inlined into the browser bundle AT BUILD TIME → pass them
# as build args (one image per environment / domain). Runtime-only variables
# (API_URL_INTERNAL) are set on the container.
#
#   docker build -t barbershop-frontend \
#     --build-arg NEXT_PUBLIC_SITE_URL=https://www.example-barber.de \
#     --build-arg NEXT_PUBLIC_API_URL=https://api.example-barber.de frontend
# ─────────────────────────────────────────────────────────────────────────
FROM node:24-alpine3.22 AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

FROM node:24-alpine3.22 AS build
WORKDIR /app
ARG NEXT_PUBLIC_SITE_URL=http://localhost:3000
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ARG NEXT_PUBLIC_TENANT=the-barber-news
ARG NEXT_PUBLIC_GA4_ID=
ARG NEXT_PUBLIC_GOOGLE_ADS_ID=
ARG NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL=
ARG NEXT_PUBLIC_META_PIXEL_ID=
ARG NEXT_PUBLIC_TURNSTILE_SITE_KEY=
# optional, build time only: a backend reachable DURING the build (e.g. with
# `--network host` and a running stack) → pages are prerendered with the real shop data
ARG API_URL_INTERNAL=
ENV API_URL_INTERNAL=${API_URL_INTERNAL}
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL} \
    NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL} \
    NEXT_PUBLIC_TENANT=${NEXT_PUBLIC_TENANT} \
    NEXT_PUBLIC_GA4_ID=${NEXT_PUBLIC_GA4_ID} \
    NEXT_PUBLIC_GOOGLE_ADS_ID=${NEXT_PUBLIC_GOOGLE_ADS_ID} \
    NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL=${NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL} \
    NEXT_PUBLIC_META_PIXEL_ID=${NEXT_PUBLIC_META_PIXEL_ID} \
    NEXT_PUBLIC_TURNSTILE_SITE_KEY=${NEXT_PUBLIC_TURNSTILE_SITE_KEY} \
    NEXT_OUTPUT=standalone \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Without a reachable backend (the usual case in CI) pages that show shop data are
# prerendered with the defaults and refreshed about a minute after start at runtime
# (lib/shop-server.ts, cacheLife "minutes" on failure).
RUN npm run build

FROM node:24-alpine3.22 AS runner
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="barbershop-frontend" \
      org.opencontainers.image.description="Barbershop website (Next.js)" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app \
    && apk upgrade --no-cache \
    # the server needs no package manager at runtime
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx /opt/yarn* /usr/local/bin/yarn*
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
# standalone server (+ traced node_modules and src/assets/fonts), then the static files
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
COPY --from=build --chown=app:app /app/public ./public
USER 10001:10001
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=4 \
    CMD node -e "fetch('http://127.0.0.1:3000/robots.txt').then(r=>process.exit(r.ok?0:1),()=>process.exit(1))"
# exec form: node is PID 1 and gets SIGTERM directly
CMD ["node", "server.js"]
