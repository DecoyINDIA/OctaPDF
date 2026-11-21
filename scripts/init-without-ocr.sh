#!/bin/bash

export JAVA_TOOL_OPTIONS="${JAVA_BASE_OPTS} ${JAVA_CUSTOM_OPTS}"
echo "running with JAVA_TOOL_OPTIONS ${JAVA_BASE_OPTS} ${JAVA_CUSTOM_OPTS}"

# Update the user and group IDs as per environment variables
if [ ! -z "$PUID" ] && [ "$PUID" != "$(id -u stirlingpdfuser)" ]; then
    usermod -o -u "$PUID" stirlingpdfuser || true
fi

if [ ! -z "$PGID" ] && [ "$PGID" != "$(getent group stirlingpdfgroup | cut -d: -f3)" ]; then
    groupmod -o -g "$PGID" stirlingpdfgroup || true
fi
umask "$UMASK" || true

if [[ "$INSTALL_BOOK_AND_ADVANCED_HTML_OPS" == "true" && "$FAT_DOCKER" != "true" ]]; then
  echo "issue with calibre in current version, feature currently disabled on OctaPDF"
fi

# Security and login functionality are natively compiled into app.jar.
# Runtime downloads have been removed to eliminate external network requirements (SEC-02).

if [[ -n "$LANGS" ]]; then
  echo "[INFO] LANGS specified: $LANGS. Fonts are pre-installed in the container image."
fi

echo "Setting permissions and ownership for necessary directories..."
# Ensure permissions for non-root execution
chown -R stirlingpdfuser:stirlingpdfgroup $HOME /logs /scripts /usr/share/fonts/opentype/noto /configs /customFiles /pipeline /app.jar 2>/dev/null || true
chmod -R 755 /logs /scripts /usr/share/fonts/opentype/noto /configs /customFiles /pipeline /app.jar 2>/dev/null || true

# SEC-11: Ensure container runs locked down as unprivileged user stirlingpdfuser
if command -v su-exec >/dev/null 2>&1 && [ "$(id -u)" = "0" ]; then
    exec su-exec stirlingpdfuser "$@"
else
    exec "$@"
fi
