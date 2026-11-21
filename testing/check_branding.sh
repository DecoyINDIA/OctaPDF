#!/usr/bin/env bash
set -euo pipefail

echo "Checking for unauthorized user-facing 'Stirling' branding..."

ERRORS=0

# 1. Check manifests
if grep -i "stirling" src/main/resources/static/*.json src/main/resources/static/*.webmanifest 2>/dev/null; then
    echo "ERROR: Found 'Stirling' in static manifests."
    ERRORS=$((ERRORS + 1))
fi

# 2. Check enabled locale file (messages_en_GB.properties)
if grep -i "stirling" src/main/resources/messages_en_GB.properties 2>/dev/null; then
    echo "ERROR: Found 'Stirling' in messages_en_GB.properties."
    ERRORS=$((ERRORS + 1))
fi

# 3. Check HTML templates (ignoring internal identifiers, license, and attribution)
while IFS= read -r match; do
    if [ -n "$match" ]; then
        echo "ERROR: Found 'Stirling' in user-facing template: $match"
        ERRORS=$((ERRORS + 1))
    fi
done < <(grep -rnI "Stirling" src/main/resources/templates/ 2>/dev/null \
    | grep -v "window\.stirlingPDF" \
    | grep -v "StirlingPDFLabel" \
    | grep -v "license" \
    | grep -v "attribution" \
    | grep -v -E "templates/about\.html:.*(Copyright.*Stirling Tools|Stirling-PDF)" || true)

# 4. Check settings.yml.template
if grep -i "stirling" src/main/resources/settings.yml.template 2>/dev/null; then
    echo "ERROR: Found 'Stirling' in settings.yml.template."
    ERRORS=$((ERRORS + 1))
fi

if [ "$ERRORS" -gt 0 ]; then
    echo "Branding check failed with $ERRORS error(s)."
    exit 1
fi

echo "Branding check passed: no unauthorized 'Stirling' branding found."
exit 0
