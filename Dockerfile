FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gzip nodejs npm python3 python3-bs4 \
    && npm install -g @openai/codex \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --create-home --uid 10001 app
WORKDIR /app
COPY build/libs/*.jar /app/news-wiki.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENV DATA_DIR=/app/data
ENV CODEX_HOME=/home/app/.codex
ENV HOME=/home/app
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
