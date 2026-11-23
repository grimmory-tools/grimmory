FROM eclipse-temurin:25-jre@sha256:4ba7de2d5d53f8e2faad1f04e08b96719bd085c4b65fad0182b9f1a2cf94d259
COPY backend.jar /opt/grimmory/backend.jar
RUN chmod 0644 /opt/grimmory/backend.jar
ENTRYPOINT ["/bin/sh", "-c", "exec java --enable-preview -Xmx256m -Dloader.main=org.booklore.service.metadata.writer.NasEpubPrototype -cp /opt/grimmory/backend.jar org.springframework.boot.loader.launch.PropertiesLauncher < /request.json"]
