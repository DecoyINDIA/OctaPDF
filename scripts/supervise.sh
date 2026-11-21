#!/bin/bash

# Process supervisor for OctaPDF container (OPS-02)
# Runs both unoserver and Java application. Exits immediately if either process dies.

echo "[SUPERVISOR] Starting unoserver and OctaPDF..."

# Ensure LibreOffice user installation directory exists in writable tmpfs
mkdir -p /tmp/libreoffice_profile 2>/dev/null || true

/opt/venv/bin/unoserver --port 2003 --interface 127.0.0.1 --libreoffice-options="-env:UserInstallation=file:///tmp/libreoffice_profile" &
UNOSERVER_PID=$!

java -Dfile.encoding=UTF-8 -jar /app.jar &
JAVA_PID=$!

echo "[SUPERVISOR] unoserver started with PID $UNOSERVER_PID, OctaPDF Java started with PID $JAVA_PID"

# Trap termination signals and forward to child processes
trap 'echo "[SUPERVISOR] Caught termination signal. Stopping processes..."; kill -TERM $JAVA_PID $UNOSERVER_PID 2>/dev/null; wait; exit 0' SIGTERM SIGINT

# Monitor both processes continuously
while true; do
    if ! kill -0 "$JAVA_PID" 2>/dev/null; then
        echo "[SUPERVISOR] OctaPDF Java process ($JAVA_PID) exited. Terminating container."
        kill -TERM "$UNOSERVER_PID" 2>/dev/null || true
        wait "$JAVA_PID" 2>/dev/null || true
        exit 1
    fi
    if ! kill -0 "$UNOSERVER_PID" 2>/dev/null; then
        echo "[SUPERVISOR] unoserver process ($UNOSERVER_PID) exited. Terminating container."
        kill -TERM "$JAVA_PID" 2>/dev/null || true
        wait "$UNOSERVER_PID" 2>/dev/null || true
        exit 1
    fi
    sleep 2
done
