# Main stage
FROM alpine:3.21.3@sha256:a8560b36e8b8210634f77d9f7f9efd7ffa463e380b75e2e74aff4511df3ef88c

# Copy necessary files
COPY scripts /scripts
COPY pipeline /pipeline
COPY src/main/resources/static/fonts/*.ttf /usr/share/fonts/opentype/noto/
COPY build/libs/*.jar app.jar

ARG VERSION_TAG

LABEL org.opencontainers.image.title="OctaPDF"
LABEL org.opencontainers.image.description="A powerful locally hosted web-based PDF manipulation tool supporting 50+ operations including merging, splitting, conversion, OCR, watermarking, and more."
LABEL org.opencontainers.image.source="https://github.com/DecoyINDIA/OctaPDF"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.vendor="XolverLabs"
LABEL org.opencontainers.image.url="https://github.com/DecoyINDIA/OctaPDF"
LABEL org.opencontainers.image.documentation="https://github.com/DecoyINDIA/OctaPDF#readme"
LABEL maintainer="XolverLabs (DecoyINDIA)"
LABEL org.opencontainers.image.authors="XolverLabs (DecoyINDIA)"
LABEL org.opencontainers.image.version="${VERSION_TAG}"
LABEL org.opencontainers.image.keywords="PDF, manipulation, merge, split, convert, OCR, watermark"

# Set Environment Variables
ENV DOCKER_ENABLE_SECURITY=false \
    SPRING_PROFILES_ACTIVE=prod \
    VERSION_TAG=$VERSION_TAG \
    JAVA_BASE_OPTS="-XX:+UnlockExperimentalVMOptions -XX:MaxRAMPercentage=50 -XX:InitiatingHeapOccupancyPercent=20 -XX:+G1PeriodicGCInvokesConcurrent -XX:G1PeriodicGCInterval=10000 -XX:+UseStringDeduplication -XX:G1PeriodicGCSystemLoadThreshold=70" \
    JAVA_CUSTOM_OPTS="" \
    HOME=/home/stirlingpdfuser \
    PUID=1000 \
    PGID=1000 \
    UMASK=022 \
    TESSDATA_PREFIX=/usr/share/tessdata \
    PYTHONPATH=/usr/lib/libreoffice/program:/opt/venv/lib/python3.12/site-packages \
    UNO_PATH=/usr/lib/libreoffice/program \
    URE_BOOTSTRAP=file:///usr/lib/libreoffice/program/fundamentalrc \
    PATH=$PATH:/opt/venv/bin

# Packages & dependencies from stable Alpine 3.21 repositories (OPS-02)
RUN apk upgrade --no-cache -a && \
    apk add --no-cache \
    ca-certificates \
    tzdata \
    tini \
    bash \
    curl \
    qpdf \
    shadow \
    su-exec \
    openssl \
    openssl-dev \
    openjdk21-jre \
    # Doc conversion
    gcompat \
    libc6-compat \
    libreoffice \
    # pdftohtml
    poppler-utils \
    # OCR data baked in (OPS-02 / SEC-02)
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-chi_sim \
    tesseract-ocr-data-chi_tra \
    tesseract-ocr-data-deu \
    tesseract-ocr-data-fra \
    tesseract-ocr-data-por \
    tesseract-ocr-data-spa \
    tesseract-ocr-data-ita \
    tesseract-ocr-data-rus \
    tesseract-ocr-data-jpn \
    tesseract-ocr-data-kor \
    tesseract-ocr-data-hin \
    tesseract-ocr-data-ara \
    # Fonts
    font-terminus font-dejavu font-noto font-noto-cjk font-awesome font-noto-extra font-liberation \
    # Python & runtime
    python3 \
    py3-pip \
    py3-pillow && \
    python3 -m venv /opt/venv && \
    /opt/venv/bin/pip install --upgrade pip && \
    /opt/venv/bin/pip install --no-cache-dir unoserver==3.7 weasyprint==64.1 pdf2image==1.17.0 pillow==11.1.0 && \
    ln -s /usr/lib/libreoffice/program/uno.py /opt/venv/lib/python3.12/site-packages/ && \
    ln -s /usr/lib/libreoffice/program/unohelper.py /opt/venv/lib/python3.12/site-packages/ && \
    ln -s /usr/lib/libreoffice/program /opt/venv/lib/python3.12/site-packages/LibreOffice && \
    mkdir -p $HOME /configs /logs /customFiles /pipeline/watchedFolders /pipeline/finishedFolders /usr/share/tessdata && \
    fc-cache -f -v && \
    chmod +x /scripts/* && \
    chmod +x /scripts/init.sh && \
    chmod +x /scripts/supervise.sh && \
    # User permissions with explicit UID/GID 1000:1000 matching compose user
    addgroup -g 1000 -S stirlingpdfgroup && \
    adduser -u 1000 -S -G stirlingpdfgroup -h /home/stirlingpdfuser stirlingpdfuser && \
    chown -R stirlingpdfuser:stirlingpdfgroup $HOME /scripts /usr/share/fonts/opentype/noto /configs /logs /customFiles /pipeline /usr/share/tessdata && \
    chown stirlingpdfuser:stirlingpdfgroup /app.jar

EXPOSE 8080/tcp

# Healthcheck endpoint (OPS-02, OPS-05)
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f -s http://localhost:8080/actuator/health | grep -q 'UP' || exit 1

# Process supervisor (OPS-02)
ENTRYPOINT ["tini", "--", "/scripts/init.sh"]
CMD ["/scripts/supervise.sh"]
