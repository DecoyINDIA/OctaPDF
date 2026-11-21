# OctaPDF QA Feature Matrix & Test Validation

This matrix documents the functional testing, regression status, and security compliance for OctaPDF tools and endpoints. All results are verified against existing automated test suites or explicitly marked as "Not run".

---

## 1. Core Tooling Validation Matrix

| Tool / Endpoint | Small File (<1MB) | Large File (50–100MB) | Corrupt / Malformed File | In-Scope Status | Verified In / Notes |
| --------------- | ----------------- | --------------------- | ------------------------ | --------------- | ------------------- |
| **Merge PDFs** (`/api/v1/general/merge-pdfs`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `RearrangePagesPDFControllerTest`, `RateLimitingIntegrationTest` |
| **Split PDFs** (`/api/v1/general/split-pages`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `RearrangePagesPDFControllerTest`, maxPageCount: 1000 |
| **Compress PDF** (`/api/v1/general/compress-pdf`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `PdfUtilsTest`, uses qpdf CLI |
| **Rotate PDF** (`/api/v1/general/rotate-pdf`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `RotationControllerTest`, `RateLimitingIntegrationTest` |
| **Watermark** (`/api/v1/security/add-watermark`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `PdfUtilsTest`, branded as OctaPDF |
| **Remove Watermark** (`/api/v1/security/remove-watermark`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `PdfUtilsTest`, branded as OctaPDF |
| **OCR PDF** (`/api/v1/misc/ocr-pdf`) | :white_check_mark: Pass | :hourglass: Not run | :white_check_mark: HTTP 400 | Enabled | `RateLimitingIntegrationTest`, baked-in Tesseract languages |
| **Convert Office to PDF** (`/api/v1/convert/file/pdf`) | :x: Disabled | :x: Disabled | :x: Disabled | Disabled | `endpoints.toRemove` in prod; disabled for defense-in-depth |
| **HTML to PDF** (`/api/v1/convert/html/pdf`) | :x: Disabled | :x: Disabled | :x: Disabled | Disabled | `endpoints.toRemove` in prod; disabled for SSRF defense-in-depth |
| **Markdown to PDF** (`/api/v1/convert/markdown/pdf`) | :x: Disabled | :x: Disabled | :x: Disabled | Disabled | `endpoints.toRemove` in prod; disabled for SSRF defense-in-depth |
| **Pipeline** (`/api/v1/pipeline/*`) | :x: Disabled | :x: Disabled | :x: Disabled | Disabled | `endpoints.toRemove` in prod; watched folders disabled |

---

## 2. Security Controls Verification

| Security Control | Test Requirement | Result | Verified In |
| ---------------- | ---------------- | ------ | ----------- |
| **Health Check Endpoint** | `/actuator/health` returns 200 with UP status in prod profile | :white_check_mark: Pass | `HealthEndpointIntegrationTest`, `ProductionServerControlsIntegrationTest` |
| **Actuator Protection** | Sensitive actuator endpoints (`/actuator/env`, `/actuator/prometheus`, `/actuator/metrics`) hidden (404/403) | :white_check_mark: Pass | `HealthEndpointIntegrationTest`, `ProductionServerControlsIntegrationTest` |
| **Rate Limiting (FIX-01/02)** | 60 req/min general, 10 req/min heavy; forged `X-Forwarded-For` rejected; trusted proxy CIDR support; no double-counting | :white_check_mark: Pass | `RateLimitingIntegrationTest`, `RateLimitingTest`, `ProductionServerControlsIntegrationTest` |
| **Security Headers (FIX-12)** | Single source of truth in `SecurityHeadersFilter`; HSTS, X-Content-Type-Options: nosniff, X-Frame-Options: DENY, Referrer-Policy, clean CSP without Google links | :white_check_mark: Pass | `SecurityHeadersTest`, `ProductionServerControlsIntegrationTest` |
| **Stack Trace Suppression** | Errors never reveal Java classes, frames, or internal file paths | :white_check_mark: Pass | `ProductionServerControlsIntegrationTest`, `ErrorUtilsTest` |
| **Resource & Page Caps** | Files > 100MB rejected, requests > 250MB rejected, max pages capped | :white_check_mark: Pass | `ProductionServerControlsIntegrationTest`, `SizeAndPageLimitTest` |
| **Non-Root & Read-Only Rootfs (FIX-05)** | Container runs as explicit unprivileged UID/GID 1000:1000 with read-only rootfs; writable tmpfs for HOME and /tmp | :white_check_mark: Pass | `ContainerLockdownTest`, `ProductionDockerfileTest` |
| **Outbound Egress Lockdown** | Zero outbound network egress at container layer (`internal: true`); no runtime package downloads | :white_check_mark: Pass | `ContainerLockdownTest`, `scripts/init.sh` |
| **Branding Guard (FIX-04)** | Zero unauthorized Stirling branding in user-facing templates and static files; MIT credit preserved | :white_check_mark: Pass | `BrandingCheckTest`, `testing/check_branding.sh` |
| **Temp File Janitor** | Temp files and unzipped working directories automatically reaped after 30 minutes | :white_check_mark: Pass | `TempFileJanitorServiceTest` |
| **Process Supervision** | Container supervisor monitors Java and unoserver, terminates if either process exits | :white_check_mark: Pass | `ProductionDockerfileTest`, `scripts/supervise.sh` |
| **SSRF Neutralization** | External HTTP/file/ftp links blocked; risky conversion tools disabled in prod | :white_check_mark: Pass | `CustomHtmlSanitizerTest`, `ProductionServerControlsIntegrationTest` |
| **Pipeline Vulnerability Scan (FIX-06)** | Trivy scanner pinned to commit SHA; scans local image before publishing to GHCR | :white_check_mark: Pass | `CIWorkflowsTest`, `.github/workflows/push-docker.yml` |
| **Load Testing (QA-03)** | 50 concurrent virtual users sustained load | :hourglass: Not run | `testing/load_test_k6.js` (scheduled for pre-launch staging) |
| **DAST Security Baseline (QA-04)** | OWASP ZAP baseline scan against staging deployment | :hourglass: Not run | Scheduled for pre-launch staging environment |
