# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Install Tesseract + language data
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-rus \
        tesseract-ocr-deu \
        tesseract-ocr-fra \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/image_translate-1.0.0.jar app.jar

ENV TESSDATA_PATH=/usr/share/tessdata
ENV LIBRETRANSLATE_URL=http://libretranslate:5000

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Dtessdata.path=${TESSDATA_PATH}", \
  "-Dlibretranslate.url=${LIBRETRANSLATE_URL}", \
  "-jar", "app.jar"]
