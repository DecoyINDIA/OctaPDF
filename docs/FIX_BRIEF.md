# OctaPDF — Fix Brief (post-review)

Baseline: branch `ccr-45026858-9n8f0z` at `89fdf27`. Review date: 2026-11-21.
Build status at review: `./gradlew build` passes (381 tests, 0 failures). The first attempts failed only on Maven Central HTTP 429; retry.
Method: code reading plus a live run of the jar with `SPRING_PROFILES_ACTIVE=prod`.

Same rules as `PRODUCTION_ROADMAP.md` section 3: one task = one commit with the task ID in the subject, `./gradlew spotlessApply` and `./gradlew build` before each commit, and a test for every behaviour change. **New rule: a test that only reads a file and checks for a string does not count as a regression test.** Each fix below needs a test that runs the real filter chain or server and fails before the fix.

Status key: **CONFIRMED** = reproduced on the running app. **CODE-READ** = found in code, not yet run (needs a container run to confirm).

---

## Phase A — Fix first (small, all CONFIRMED, P0)

### FIX-01 · Rate limit can be bypassed with a forged X-Forwarded-For · P0 · S · CONFIRMED
**Evidence:** 70 requests to `/api/v1/general/rotate-pdf`, each with a different `X-Forwarded-For`, returned 70 × 415 and 0 × 429. Same client without the header was limited.
**Cause:** `application.properties:11` sets `server.forward-headers-strategy=NATIVE`. Jetty then rewrites `getRemoteAddr()` from the leftmost `X-Forwarded-For` value sent by any caller, before `IPRateLimitingFilter.getClientIp()` and its `trustedProxies` check run. The check is dead code.
**Files:** `src/main/resources/application.properties`, `config/security/IPRateLimitingFilter.java`, `model/ApplicationProperties.java` (`rateLimit.trustedProxies`), `docs/DEPLOYMENT.md`.
**Steps:**
1. Set `server.forward-headers-strategy=NONE` (also remove it from any prod/env override).
2. In the filter, read the real socket address, then resolve the client IP only if that address is inside a trusted proxy range. Walk `X-Forwarded-For` from the right and take the first address that is not a trusted proxy.
3. Make `trustedProxies` accept CIDR ranges, not just exact strings.
4. Document in `DEPLOYMENT.md` that the reverse proxy must overwrite, not append to, the header, and which CIDR to configure.
**Acceptance:** an integration test (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) sends requests with varying forged `X-Forwarded-For` from an untrusted source and gets 429 after the limit. A second test from a trusted-proxy address gets per-client limits.

### FIX-02 · Every request is counted twice, halving the limits · P0 · S · CONFIRMED (cause likely)
**Evidence:** `/about` returned 30 × 200 then 40 × 429 against a configured 60/min. Heavy tools would be limited at about 5/min, not 10.
**Likely cause:** `IPRateLimitingFilter` is a `@Bean` implementing `jakarta.servlet.Filter`, so Spring Boot auto-registers it as a servlet filter, and `SecurityConfiguration.java:190` (login on) and `:338` (anonymous) add it to the security chain as well. Verify by logging the filter invocation count per request.
**Files:** `config/security/SecurityConfiguration.java`, `config/security/IPRateLimitingFilter.java`.
**Steps:** either register a `FilterRegistrationBean<IPRateLimitingFilter>` with `setEnabled(false)`, or make the filter extend `OncePerRequestFilter` (guarantees one run per request). Keep the filter in the security chain only.
**Acceptance:** an integration test shows request number 61 gets 429 and request 60 does not; the heavy limit is exactly 10.
**Do together with FIX-01** (same files, same test class).

### FIX-03 · Container health check always fails · P0 · S · CONFIRMED
**Evidence:** `/actuator/health` returns 404 with the prod profile.
**Cause:** `config/EnterpriseEndpointFilter.java` returns 404 for any URI containing `/actuator/` when not running Pro. The `Dockerfile` `HEALTHCHECK`, the compose healthcheck and any platform probe all hit this path.
**Files:** `config/EnterpriseEndpointFilter.java`, `config/security/SecurityConfiguration.java:252` (already permits `/actuator/health`).
**Steps:** limit the 404 to `/actuator/prometheus` (or to everything except `/actuator/health` and `/actuator/health/**`). Confirm `management.endpoints.web.exposure.include=health` still hides all other actuator endpoints.
**Acceptance:** an integration test with the prod profile expects 200 and `UP` on `/actuator/health`, and 404/403 on `/actuator/env`, `/actuator/prometheus` and `/actuator/metrics`.

### FIX-04 · CI branding check fails on `main` · P0 · S · CONFIRMED
**Evidence:** `bash testing/check_branding.sh` exits non-zero: "Found 'Stirling' in user-facing template: templates/about.html:24" (the required MIT copyright line).
**Files:** `testing/check_branding.sh`, `.github/workflows/build.yml:51`.
**Steps:** exclude the copyright/attribution line in `about.html` (by marker comment or path), keeping the MIT credit. Do not remove the credit.
**Acceptance:** the script exits 0 on the current tree and still exits 1 if "Stirling" is added to any other template. `build.yml` passes.

---

## Phase B — Container and pipeline (P0, CODE-READ: verify by running the image)

Before fixing, build the image and run it exactly as `docker-compose.prod.yml` does. Record what actually fails. If the sandbox has no working Docker daemon, run this in CI.

### FIX-05 · Prod compose setup likely does not start or work · P0 · M · CODE-READ
**Suspected problems:**
1. `user: "1000:1000"` but the image creates `stirlingpdfuser` with a system uid (about 100). `scripts/init-without-ocr.sh` only does `chown`/`su-exec` as root, so as uid 1000 those steps silently fail and `/configs` and `/logs` volumes may not be writable.
2. `read_only: true` with `HOME=/home/stirlingpdfuser` not writable at runtime: LibreOffice cannot create its profile, unoserver exits, and the supervisor then stops the container (restart loop).
3. `scripts/init.sh` runs `mkdir -p /usr/share/tessdata` and `cp` into it: not possible on a read-only filesystem or as a non-root user, so OCR has no language data.
4. The `reverse-proxy` service uses the default nginx image with no config in the repo (`DEPLOYMENT.md:31` mounts `./nginx.conf`, which does not exist). It serves the nginx welcome page and does no proxying, TLS, body limit or rate limit.
**Steps:**
1. Make the image create the user with a fixed uid/gid matching the compose `user:`. Do the tessdata copy at build time, and point `TESSDATA_PREFIX` at an existing directory.
2. Mount a `tmpfs` for `$HOME` (and `/tmp`) and set LibreOffice `-env:UserInstallation` to a path under it.
3. Add `nginx.conf` (or the platform's equivalent): proxy to `octapdf:8080`, TLS, HTTP→HTTPS redirect, `client_max_body_size 100m`, timeouts, `proxy_set_header X-Forwarded-For $remote_addr` (overwrite, not append: pairs with FIX-01).
4. Reconcile the two healthchecks (Dockerfile and compose) so both use the same endpoint.
**Acceptance:** a CI job builds the image, starts it with `docker-compose.prod.yml`, waits for healthy, then: converts a small `.docx` to PDF, OCRs a small image PDF, merges two PDFs, and confirms `curl -m 5 https://example.com` fails from inside the container.

### FIX-06 · Image is published before it is scanned; actions are unpinned · P0 · S · CODE-READ
**Files:** `.github/workflows/push-docker.yml` (steps "Build and Push" then "Run Trivy"), `.github/workflows/security-scan.yml`, `Dockerfile`.
**Problems:**
1. `push: true` runs before Trivy, so a Critical finding fails the job after `:latest` is already published.
2. `aquasecurity/trivy-action@master` is unpinned in both workflows (violates SEC-10).
3. `apk upgrade -a` plus unpinned apk packages make two builds differ (fails the OPS-02 acceptance test).
**Steps:** build and load the image first (or push to a staging tag only), scan, then promote to `:latest`/version tags only if the scan passes. Pin Trivy to a commit SHA. Pin the Alpine package versions or accept and document the drift with a weekly scheduled rebuild.
**Acceptance:** a deliberately vulnerable test image fails the workflow before any tag is pushed.

### FIX-07 · Repository and image names are inconsistent · P1 · S
16 references to `DecoyINDIA/pdf-editor-project`, 5 to `DecoyINDIA/OctaPDF`, one to `ghcr.io/decoyindia/xolverlabs-pdf`. The git remote is `DecoyINDIA/OctaPDF`. Fix the image labels in `Dockerfile`, `githubVersion.js`, `templates/error.html`, `errorBanner.html`, `SECURITY.md`, docs, and `exampleYmlFiles/` to the canonical repo; decide whether to keep or delete the example compose files that reference the upstream image.
**Acceptance:** `grep -r "pdf-editor-project\|xolverlabs-pdf" --exclude-dir=build --exclude-dir=.git .` returns nothing (or only intentional history in the roadmap).

---

## Phase C — SSRF protection that works (P0 if the tools are enabled; otherwise keep them disabled)

Currently `html-to-pdf`, `markdown-to-pdf` and `file-to-pdf` are removed in prod (live check: `/api/v1/convert/file/pdf` returned 403), so this is not exploitable today. It becomes exploitable as soon as anyone re-enables them. **Recommendation: keep them disabled for launch** and do Phase C before enabling.

### FIX-08 · The safe WeasyPrint wrapper is never called · P0 (if enabled) · M · CODE-READ
**Evidence:** `scripts/weasyprint_safe.py` exists, but `ConvertHtmlToPDF.java:61`, `ConvertMarkdownToPdf.java:81`, `FileToPdf.java:51` and `ConvertWebsiteToPDF.java:73` all execute `runtimePathConfig.getWeasyPrintPath()` directly. Protection rests only on the HTML sanitizer, which can be turned off with `system.disableSanitize`.
**Wrapper bugs to fix as well:** `abs_path.startswith(allowed_dir)` is a string-prefix check (`/tmp/a` matches `/tmp/ab`); use `os.path.realpath` and `commonpath`. It also lets any relative path through.
**Files:** `utils/FileToPdf.java`, `ConvertHtmlToPDF.java`, `ConvertMarkdownToPdf.java`, `config/RuntimePathConfig.java`, `scripts/weasyprint_safe.py`, `Dockerfile`.
**Steps:** point the weasyprint path at the wrapper (run with `/opt/venv/bin/python`), fix the path check, and block `..` in sanitizer image paths (`CustomHtmlSanitizer.SAFE_IMAGE_URI` currently allows `../x`).
**Acceptance:** an integration test that runs the real converter (skipped when weasyprint is absent) on HTML containing `http://`, `file:///etc/passwd`, `../` and an iframe, against a local mock HTTP server, asserts zero hits and no file content in the PDF text. The test must fail when the wrapper is bypassed.

### FIX-09 · Office sanitizer is chosen by file extension · P0 (if enabled) · M · CODE-READ
**Evidence:** `OfficeDocumentSanitizer.sanitizeOfficeDocument()` branches on `.docx/.docm/.dotx/.dotm/.pptx/.xlsx`, `.odt/.ods/.odp/.odg`, `.rtf`, `.html`. `ConvertOfficeController.isValidFileExtension` accepts any 2–4 character extension, and LibreOffice detects the format from content. A `.docx` renamed `.doc`, `.fodt`, `.xls`, `.ppt` and so on skips cleaning.
**Steps:** detect the real format from content (ZIP with `[Content_Types].xml`, ZIP with `mimetype`, RTF header, XML/HTML sniff). Reject unknown formats rather than passing them through. Also disable LibreOffice link updating and external content loading in its configuration.
**Acceptance:** test with an OOXML file carrying an external relationship under the renamed extensions `.doc`, `.xls`, `.ppt`, and an ODF flat file `.fodt`; assert it is either sanitized or rejected, with zero outbound hits from a real LibreOffice run.

---

## Phase D — Evidence and documentation (P0 for launch sign-off)

### FIX-10 · QA matrix claims results that do not exist · P0 · M
**Evidence:** `docs/QA_MATRIX.md` cites `SafeUrlValidatorTest`, `SafeConversionFilterTest`, `ProductionProfileErrorTest`, `IPRateLimitingFilterTest`: none exist (`find src/test` returns nothing). It marks Office→PDF "Enabled / Pass" while prod disables it, says compression "Uses qpdf / Ghostscript" (Ghostscript is not installed), and lists "Pass" for large-file cases that were never run. No load-test or ZAP results exist.
**Steps:** replace the matrix with measured results only; mark everything not run as "Not run". Run QA-02 against the running prod-profile container, QA-03 with `testing/load_test_k6.js`, and QA-04 (ZAP baseline) against staging. Record p95 latency, error rate and peak memory.
**Acceptance:** every "Pass" row links to a test name that exists or to a saved log/report.

### FIX-11 · Replace string-matching tests with behaviour tests · P1 · M
Many new tests (for example `ContainerLockdownTest`, `NetworkLockdownCheckTest`, `ProductionDockerfileTest`, `SecretsManagementTest`) assert only that a file contains a text. They passed while FIX-01 to FIX-03 were broken. Keep them as cheap guards, but add at least one running-server integration test per control: rate limit, health, headers, error masking, disabled endpoints, size limit.
**Acceptance:** an integration test class exercises each control through HTTP on the prod profile.

### FIX-12 · CSP and header cleanup · P1 · S
The CSP policy string is defined twice (`SecurityConfiguration.java` and `SecurityHeadersFilter.CSP_POLICY`) and will drift; keep one. Drop `https://apis.google.com`, `https://accounts.google.com`, `https://www.googleapis.com` (Google Drive is off). Plan the removal of `'unsafe-inline'` and `'unsafe-eval'` (nonces) after QA-02.
**Acceptance:** one source for the policy; no CSP violations in the browser console during QA-02.

### FIX-13 · Record owner decisions · P1 · S
D1–D8 are not recorded at the top of `PRODUCTION_ROADMAP.md`. D4 (hosting) matters: the Docker `internal: true` network and the Kubernetes `NetworkPolicy` in `DEPLOYMENT.md` do not apply on Railway, Render or Fly.io. If the host is one of those, SEC-02 needs a platform-specific egress control, or an explicit decision to accept the risk (acceptable only while FIX-08/09 tools stay disabled).

---

## Order and size

| Order | Tasks | Size |
|---|---|---|
| 1 | FIX-01 + FIX-02 (same files and test), FIX-03, FIX-04 | about 1 day |
| 2 | FIX-05, FIX-06, FIX-07 | 2–3 days |
| 3 | FIX-10, FIX-11, FIX-12, FIX-13 | 2–3 days |
| 4 (only if enabling HTML/Markdown/Office tools) | FIX-08, FIX-09 | 3–5 days |

Still open from the roadmap and not covered here: CI-03 (deploy pipeline), OPS-03 (hosting, TLS, domain), OPS-05 alerting, LEG-01 (legal text and review), QA-05 (browsers and accessibility), LCH-05 (go-live checklist).

## Go-live gate

Do not launch until: FIX-01 to FIX-06 are merged, the prod image passes the CI smoke test in FIX-05, FIX-10 results are real, and the converters stay disabled unless FIX-08/09 are done.
