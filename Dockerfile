# Stage 1: Build the Angular app
FROM docker.io/library/node:22-alpine AS angular-build

WORKDIR /angular-app

COPY ./booklore-ui/package.json ./booklore-ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm config set registry https://registry.npmjs.org/ \
    && npm install --force --legacy-peer-deps


COPY ./booklore-ui /angular-app/

RUN npm run build --configuration=production

# Stage 2: Build the Spring Boot app with Gradle
FROM docker.io/library/gradle:9.3.1-jdk25-alpine AS springboot-build

WORKDIR /springboot-app

# Copy only build files first to cache dependencies
COPY ./booklore-api/build.gradle ./booklore-api/settings.gradle /springboot-app/

# Download dependencies (cached layer)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY ./booklore-api/src /springboot-app/src

# Copy Angular dist into Spring Boot static resources so it's embedded in the JAR
COPY --from=angular-build /angular-app/dist/booklore/browser /springboot-app/src/main/resources/static

# Inject version into application.yaml using yq
ARG APP_VERSION
RUN apk add --no-cache yq && \
    yq eval '.app.version = strenv(APP_VERSION)' -i /springboot-app/src/main/resources/application.yaml

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon --parallel

# Stage 3: Final image
FROM docker.io/library/eclipse-temurin:25-jre-alpine

ARG APP_VERSION
ARG APP_REVISION

# Set OCI labels
LABEL org.opencontainers.image.title="BookLore" \
      org.opencontainers.image.description="BookLore: A self-hosted, multi-user digital library with smart shelves, auto metadata, Kobo & KOReader sync, BookDrop imports, OPDS support, and a built-in reader for EPUB, PDF, and comics." \
      org.opencontainers.image.source="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.url="https://github.com/booklore-app/booklore" \
      org.opencontainers.image.documentation="https://booklore.org/docs/getting-started" \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre-alpine"

ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+UseContainerSupport -XX:+UseCompactObjectHeaders -XX:MaxRAMPercentage=75.0"

RUN apk update && apk add --no-cache su-exec

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
COPY --from=springboot-build /springboot-app/build/libs/booklore-api-0.0.1-SNAPSHOT.jar /app/app.jar

ARG BOOKLORE_PORT=6060
EXPOSE ${BOOKLORE_PORT}

ENTRYPOINT ["entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
