ARG BASE_REGISTRY=dockerproxy.net/library

FROM ${BASE_REGISTRY}/node:22-bookworm-slim AS node-dependencies

WORKDIR /build/spider
COPY runtime/vendor/Spider_XHS-master/package.json runtime/vendor/Spider_XHS-master/package-lock.json ./
RUN npm ci --omit=dev

FROM ${BASE_REGISTRY}/python:3.13-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    NODE_ENV=production \
    XHS_SIDECAR_HOST=0.0.0.0 \
    XHS_SIDECAR_PORT=18081 \
    SPIDER_XHS_ROOT=/opt/spider \
    SPIDER_XHS_PYTHON=/usr/local/bin/python \
    XHS_SIDECAR_DATA_DIR=/data/jobs

RUN apt-get update \
    && apt-get install --no-install-recommends -y libgl1 libglib2.0-0 libstdc++6 tini \
    && rm -rf /var/lib/apt/lists/*

COPY --from=node-dependencies /usr/local/bin/node /usr/local/bin/node
RUN node --version

WORKDIR /app
COPY runtime/vendor/Spider_XHS-master/requirements.txt /tmp/spider-requirements.txt
RUN sed -i '/^opencv-python$/d' /tmp/spider-requirements.txt \
    && python -m pip install --no-cache-dir --timeout 120 --retries 10 -r /tmp/spider-requirements.txt \
    && rm /tmp/spider-requirements.txt

COPY pyproject.toml ./
COPY xhs_sidecar ./xhs_sidecar
RUN python -m pip install --no-cache-dir --timeout 120 --retries 10 .

COPY runtime/vendor/Spider_XHS-master /opt/spider
COPY --from=node-dependencies /build/spider/node_modules /opt/spider/node_modules

RUN useradd --create-home --uid 10001 sidecar \
    && mkdir -p /data/jobs \
    && chown -R sidecar:sidecar /data/jobs

USER sidecar
EXPOSE 18081

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:18081/health', timeout=2).read()"]

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["python", "-m", "xhs_sidecar.server"]
