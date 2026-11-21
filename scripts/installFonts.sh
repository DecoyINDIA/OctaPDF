#!/bin/bash
# Fonts are baked into the container image during build (OPS-02).
# Runtime apk package installations have been removed to enforce zero outbound internet access (SEC-02).
echo "[INFO] Font packages are baked into the container image; runtime package installation is disabled."
