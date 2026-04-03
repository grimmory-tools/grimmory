FROM --platform=$BUILDPLATFORM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY .yarnrc.yml /workspace/.yarnrc.yml
COPY frontend/package.json frontend/yarn.lock ./

RUN corepack enable
RUN --mount=type=cache,target=/workspace/.yarn/cache \
    corepack yarn install --immutable

COPY frontend/ ./
RUN --mount=type=cache,target=/workspace/.yarn/cache \
    --mount=type=cache,target=/workspace/frontend/.angular/cache \
    CI=1 NG_CLI_ANALYTICS=false corepack yarn build:prod

FROM --platform=$BUILDPLATFORM gradle:9.3.1-jdk25-alpine AS backend-build

WORKDIR /workspace/booklore-api

COPY booklore-api/gradlew booklore-api/gradlew.bat booklore-api/build.gradle.kts booklore-api/settings.gradle.kts ./
COPY booklore-api/gradle ./gradle
RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies

COPY booklore-api/ ./
COPY --from=frontend-build /workspace/frontend/dist/grimmory/browser /tmp/frontend-dist

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon -PfrontendDistDir=/tmp/frontend-dist bootJar

RUN set -eux; \
    jar_path="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)"; \
    cp "$jar_path" /workspace/booklore-api/app.jar

FROM mwader/static-ffmpeg:8.1 AS ffprobe-layer

FROM scratch AS kepubify-layer-amd64

ARG KEPUBIFY_VERSION="4.0.4"
ARG KEPUBIFY_AMD64_CHECKSUM="sha256:37d7628d26c5c906f607f24b36f781f306075e7073a6fe7820a751bb60431fc5"

ADD \
      --checksum="${KEPUBIFY_AMD64_CHECKSUM}" \
      --chmod=755 \
      https://github.com/pgaskin/kepubify/releases/download/v${KEPUBIFY_VERSION}/kepubify-linux-64bit /kepubify

FROM scratch AS kepubify-layer-arm64

ARG KEPUBIFY_VERSION="4.0.4"
ARG KEPUBIFY_ARM64_CHECKSUM="sha256:5a15b8f6f6a96216c69330601bca29638cfee50f7bf48712795cff88ae2d03a3"

ADD \
      --checksum="${KEPUBIFY_ARM64_CHECKSUM}" \
      --chmod=755 \
      https://github.com/pgaskin/kepubify/releases/download/v${KEPUBIFY_VERSION}/kepubify-linux-arm64 /kepubify

FROM kepubify-layer-${TARGETARCH} AS kepubify-layer

FROM eclipse-temurin:25-jre-alpine

ENV JAVA_TOOL_OPTIONS="-XX:+UseShenandoahGC \
    -XX:ShenandoahGCHeuristics=compact \
    -XX:+UseCompactObjectHeaders \
    -XX:MaxRAMPercentage=60.0 \
    -XX:InitialRAMPercentage=8.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:MaxMetaspaceSize=192m \
    -XX:ReservedCodeCacheSize=64m \
    -Xss512k \
    -XX:CICompilerCount=2 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:ShenandoahUncommitDelay=5000 \
    -XX:ShenandoahGuaranteedGCInterval=30000"

RUN apk add --no-cache su-exec libstdc++ libgcc libarchive && \
    mkdir -p /bookdrop

# Manually link `libarchive.so.13` so java and other libraries can see it
RUN ln -s /usr/lib/libarchive.so.13 /usr/lib/libarchive.so

COPY packaging/docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

COPY --from=ffprobe-layer /ffprobe /usr/local/bin/ffprobe
COPY --from=kepubify-layer /kepubify /usr/local/bin/kepubify

COPY --from=backend-build /workspace/booklore-api/app.jar /app/app.jar

ARG APP_VERSION=development
ARG APP_REVISION=unknown

LABEL org.opencontainers.image.title="Grimmory" \
      org.opencontainers.image.description="Grimmory: a self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo and KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/grimmory-tools/grimmory" \
      org.opencontainers.image.url="https://github.com/grimmory-tools/grimmory" \
      org.opencontainers.image.documentation="https://grimmory.org/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="AGPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre-alpine"

ENV APP_VERSION=${APP_VERSION} \
    APP_REVISION=${APP_REVISION}

ARG BOOKLORE_PORT=6060
EXPOSE ${BOOKLORE_PORT}

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
