#!/bin/bash

# Ensure tessdata directory exists and if tessdata-original exists (e.g. from custom volume mount), copy without failing
if [ -d /usr/share/tessdata-original ] && [ -w /usr/share/tessdata ]; then
    echo "Copying original tessdata files without overwriting existing files"
    cp -rn /usr/share/tessdata-original/* /usr/share/tessdata 2>/dev/null || true
fi

if [ -d /usr/share/tesseract-ocr/4.00/tessdata ] && [ -w /usr/share/tessdata ]; then
    cp -rn /usr/share/tesseract-ocr/4.00/tessdata/* /usr/share/tessdata 2>/dev/null || true
fi

if [ -d /usr/share/tesseract-ocr/5/tessdata ] && [ -w /usr/share/tessdata ]; then
    cp -rn /usr/share/tesseract-ocr/5/tessdata/* /usr/share/tessdata 2>/dev/null || true
fi

# OCR languages are baked into the container image during build (SEC-02 / OPS-02).
# Dynamic package installation has been removed to enforce zero outbound internet egress at container startup.
if [[ -n "$TESSERACT_LANGS" ]]; then
  echo "[INFO] TESSERACT_LANGS specified: $TESSERACT_LANGS. Required languages are pre-baked into the image or supplied via /usr/share/tessdata mount."
fi

/scripts/init-without-ocr.sh "$@"
