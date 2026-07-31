ARG BASE_REGISTRY=dockerproxy.net/library

FROM ${BASE_REGISTRY}/node:22-bookworm-slim

ARG DEBIAN_MIRROR=mirrors.tuna.tsinghua.edu.cn

ENV NODE_ENV=production \
    BROWSER_MCP_HOST=0.0.0.0 \
    BROWSER_MCP_PORT=3333 \
    CHROME_HEADLESS=true \
    CHROME_USER_DATA_DIR=/data/chrome-profile \
    BROWSER_SCREENSHOT_DIR=/data/screenshots \
    CHROME_DEVTOOLS_MCP_NO_USAGE_STATISTICS=1 \
    CHROME_DEVTOOLS_MCP_NO_UPDATE_CHECKS=1

RUN sed -i "s|deb.debian.org|${DEBIAN_MIRROR}|g" /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install --no-install-recommends --fix-missing -y chromium ca-certificates tini fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev

COPY server.js tool-result.js ./

RUN useradd --create-home --uid 10001 sidecar \
    && mkdir -p /data/chrome-profile /data/screenshots \
    && chown -R sidecar:sidecar /data

USER sidecar
EXPOSE 3333

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD ["node", "-e", "fetch('http://127.0.0.1:3333/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "server.js"]
