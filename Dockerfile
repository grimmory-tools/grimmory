FROM node:24-alpine AS frontend-build

WORKDIR /workspace/booklore-ui

COPY booklore-ui/package.json booklore-ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY booklore-ui/ ./
RUN npm run build --configuration=production

FROM gradle:9.3.1-jdk25-alpine AS backend-build

WORKDIR /workspace/booklore-api

COPY booklore-api/gradlew booklore-api/gradlew.bat booklore-api/build.gradle booklore-api/settings.gradle ./
COPY booklore-api/gradle ./gradle
RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies

COPY booklore-api/ ./
COPY --from=frontend-build /workspace/booklore-ui/dist/booklore/browser /tmp/frontend-dist

RUN mkdir -p build/resources/main/static && \
    cp -r /tmp/frontend-dist/. build/resources/main/static/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon bootJar

RUN set -eux; \
    jar_path="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' | head -n 1)"; \
    cp "$jar_path" /workspace/booklore-api/app.jar

FROM alpine:3.22 AS unrar-build

ARG UNRAR_VERSION=7.2.4
ARG UNRAR_SHA256=b02e571a33af7711cd803080500370dc1d28eea82b2032480819d27462ad8b31

RUN apk add --no-cache build-base curl tar

WORKDIR /tmp

RUN curl -fsSL "https://www.rarlab.com/rar/unrarsrc-${UNRAR_VERSION}.tar.gz" -o unrarsrc.tar.gz && \
    echo "${UNRAR_SHA256}  unrarsrc.tar.gz" | sha256sum -c - && \
    tar -xzf unrarsrc.tar.gz && \
    make -C unrar

FROM eclipse-temurin:25-jre-alpine

ARG APP_VERSION=development
ARG APP_REVISION=unknown

LABEL org.opencontainers.image.title="Grimmory" \
      org.opencontainers.image.description="Grimmory: a self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo and KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/grimmory-tools/grimmory" \
      org.opencontainers.image.url="https://github.com/grimmory-tools/grimmory" \
      org.opencontainers.image.documentation="https://grimmory.org/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre-alpine"

ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:+UseCompactObjectHeaders -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV APP_VERSION=${APP_VERSION} \
    APP_REVISION=${APP_REVISION}

RUN apk update && apk add --no-cache su-exec libstdc++ libgcc && \
    mkdir -p /bookdrop

COPY packaging/docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
COPY --from=unrar-build /tmp/unrar/unrar /usr/local/bin/unrar
COPY --from=backend-build /workspace/booklore-api/app.jar /app/app.jar

ARG BOOKLORE_PORT=6060
EXPOSE ${BOOKLORE_PORT}

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
