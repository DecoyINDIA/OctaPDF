# XolverLabs PDF — Production Launch Roadmap

Audit date: 2026-11-20. Baseline commit: `1d775c25` ("Brand the PDF editor as XolverLabs PDF"), forked from Stirling-PDF **v0.46.2**.

This file is the work order for the implementation agent. Each task has an ID, a priority, the files involved, the steps, and acceptance criteria. Do the tasks in phase order unless a task says otherwise.

---

## 1. Where things stand

| Area | State | Launch-ready? |
|---|---|---|
| Build | `./gradlew build` passes; 309 unit tests, 0 failures | Yes |
| Branding | Core names changed (app name, Docker labels, installers, OpenAPI). About 25 templates/JS files and 40 translation files still say "Stirling" | No |
| Third-party data flows | Stirling tracking pixel always loads; Stirling's PostHog key is hard-coded; update checks, survey and "Go Pro" links point to Stirling | No (privacy blocker) |
| Legal | Startup code puts Stirling's Terms and Privacy URLs back into settings whenever they are blank | No |
| Security | Base version has unpatched public CVEs (SSRF). Stack traces are returned to clients. No rate limiting when login is off. 2 GB upload limit. `/api/v1/info` metrics are public | No |
| Deployment | Dockerfile pulls from Alpine `edge`/`testing`, installs packages at container start, runs two processes with `&`. No health check | No |
| CI/CD | Upstream workflows (Stirling image names, SwaggerHub, PR demos, SonarQube) | No |
| Observability, backups, runbooks | None set up | No |

**Pending work:** 8 owner decisions, then 53 tasks: 33 P0 (launch blockers), 14 P1 (before or right after launch), 6 P2 (later). Rough size: **3–5 weeks** for an agent working with a human reviewer, plus human-only items (legal text, domain, hosting account, logo).

### Findings that drive the plan (with evidence)

1. **Known unpatched vulnerabilities.** v0.46.2 is affected by CVE-2025-55150 (HTML→PDF, `/api/v1/convert/html/pdf`, GHSA-xw8v-9mfm-g2pm) and CVE-2025-55151 (SSRF through the LibreOffice/unoconvert file→PDF path, GHSA-76hv-h7g2-xfv3, upstream fix commit `7d6b7087`). Both are fixed upstream in 1.1.0. CVE-2025-46568 was fixed in 0.45.0, so this base already has that fix.
2. **Tracking pixel cannot be turned off.** `src/main/resources/templates/fragments/common.html:44-50` loads `https://pixel.stirlingpdf.com/a.png` with machine type, version, license and login state. The opt-out is broken: `AppConfig.disablePixel()` (`src/main/java/stirling/software/SPDF/config/AppConfig.java:201-204`) calls `Boolean.getBoolean(env.getProperty("DISABLE_PIXEL"))`. That reads a *JVM system property named "true"*, so it always returns `false` and the pixel always loads.
3. **Stirling's PostHog key is embedded** in `application.properties` (`posthog.api.key=phc_fiR65…`) and in `common.html:146`. The backend `PostHogService` / `MetricsAggregatorService` send events to it.
4. **Legal URLs are put back.** `InitialSetup.initLegalUrls()` (`src/main/java/stirling/software/SPDF/config/InitialSetup.java:72-87`) writes `https://www.stirlingpdf.com/terms` and `/privacy-policy` into settings when they are blank. That undoes the blanking in the branding commit, so your footer links to another company's legal pages.
5. **Errors leak internals.** `application.properties` sets `server.error.include-stacktrace=always`, `include-exception=true` and `include-message=always`.
6. **No abuse protection for anonymous use.** `SecurityConfiguration.filterChain` adds `IPRateLimitingFilter` only inside `if (loginEnabledValue)`. With `enableLogin: false` (the default), nothing limits requests. Uploads are allowed up to 2000 MB (`spring.servlet.multipart.max-file-size`), and `fileUploadLimit` is empty (no limit).
7. **Default admin password.** `InitialSecuritySetup` creates `admin` / `stirling` when no initial login is configured and login is enabled.
8. **Container is not reproducible.** The `Dockerfile` adds Alpine `edge/main`, `edge/community` and `edge/testing` repositories and runs `apk upgrade -a`. `scripts/init.sh` runs `apk add` at container start when `TESSERACT_LANGS` is set. `CMD` starts Java in the background with `&` and unoserver in the foreground, so a Java crash is not detected.

---

## 2. Decisions the owner must make first (Phase 0 — human)

### Recorded Owner Decisions (D1–D8 Sign-off)

- **D1 (Product Scope):** **(c) Both.** Launch as a hosted web application paired with an official hardened Docker image (`ghcr.io/decoyindia/octapdf:latest`). Desktop standalone bundles remain deferred to P2.
- **D2 (Codebase Strategy):** **(a) Stay on v0.46.2.** Backport critical CVE fixes, maintain 100% MIT license compliance, and apply defense-in-depth isolation (read-only filesystem, network lockdown, and disabling unsafe converters).
- **D3 (User Accounts):** **(a) Anonymous mode only (`enableLogin: false`).** Zero user credential storage, zero session database persistence, minimizing attack surface and compliance scope.
- **D4 (Hosting & Egress Control):** Deploy on VPS / Dedicated Linux VM or Kubernetes cluster with Docker `internal: true` bridge network and Kubernetes `NetworkPolicy` enforcing zero external egress. If hosted on container PaaS platforms without kernel-level egress filtering (Railway, Render, Fly.io), the residual egress risk is explicitly accepted **only** because risky external-fetching converter endpoints (`html-to-pdf`, `markdown-to-pdf`, `file-to-pdf`) remain disabled in production (`endpoints.toRemove`).
- **D5 (Brand Assets):** OctaPDF wordmark and brand identity; clean SVG assets, neutral modern color scheme; third-party tracking pixels and upsells removed.
- **D6 (Legal Entity & Compliance):** Published by XolverLabs / OctaPDF Contributors under the MIT License with original Stirling Tools attribution preserved. Static legal templates provided for Terms, Privacy, Cookies, and Accessibility.
- **D7 (Localization):** Default to `en-GB`. All active user-facing strings and English locale files scrubbed of unauthorized upstream naming.
- **D8 (Tool Curation):** Ship a curated core subset of PDF tools (Merge, Split, Rotate, Compress, Watermark, OCR). Risky SSRF-vulnerable converters (HTML/Markdown/Office to PDF) remain disabled in production until sandboxed wrappers and content-type detection are verified.

| ID | Decision | Options | Status / Resolution |
|---|---|---|---|
| D1 | Product Scope | Hosted web vs Docker image vs Desktop | Resolved: Hosted web + Docker image |
| D2 | Codebase Strategy | Stay on v0.46.2 vs Rebase upstream | Resolved: Stay on v0.46.2 + security backports |
| D3 | Authentication | Anonymous only vs Account login | Resolved: Anonymous only (`enableLogin: false`) |
| D4 | Hosting Infrastructure | VPS/K8s vs PaaS (Railway/Render/Fly.io) | Resolved: VPS/K8s egress lockdown; PaaS risk accepted with converters disabled |
| D5 | Brand Identity | Assets, icons, styling | Resolved: OctaPDF branding complete |
| D6 | Legal & Policies | Attribution, terms, privacy | Resolved: MIT attribution preserved, static policies added |
| D7 | Localization Scope | Single locale vs All locales | Resolved: `en-GB` default |
| D8 | Tool Enablement | All tools vs Curated safe tools | Resolved: Curated safe tools (risky converters disabled) |

---

## 3. Rules for the implementation agent

- Follow `AGENTS.md`: 4-space Java, 2-space HTML/JS, LF line endings, `./gradlew spotlessApply` before commit, `./gradlew build` must pass before every commit. Do not commit if the build cannot run.
- One task = one focused commit (or one PR), with the task ID in the commit subject, e.g. `SEC-03: hide stack traces in error responses`.
- Never put secrets in the repo. Production values come from environment variables (`SECURITY_INITIALLOGIN_PASSWORD`, etc.).
- Add or update a unit test for every behaviour change in Java code. Security fixes need a regression test that fails before the fix.
- Keep the MIT `LICENSE` and the Stirling Tools copyright notice. MIT requires it. Removing brand *names* from the UI is fine; removing the license notice is not.
- When a step says "verify", write down what you checked in the PR description.

---

## 4. Phase 1 — Security hardening (P0)

### SEC-01 · Backport fixes for CVE-2025-55150 and CVE-2025-55151 · P0 · L
**Files:** `utils/FileToPdf.java`, `utils/CustomHtmlSanitizer.java`, `controller/api/converters/ConvertHtmlToPDF.java`, `controller/api/converters/ConvertMarkdownToPdf.java`, `controller/api/converters/ConvertOfficeController.java`, plus any code that calls `weasyprint` or `unoconvert`.
**Steps:**
1. Read advisories GHSA-xw8v-9mfm-g2pm and GHSA-76hv-h7g2-xfv3 and upstream commit `7d6b7087` in Stirling-Tools/Stirling-PDF. Port the fix logic, adapted to this code layout.
2. HTML/Markdown → PDF (WeasyPrint): block every external resource fetch. Allow only `data:` URIs and files inside the per-request temp directory. If the WeasyPrint CLI cannot do this, call WeasyPrint through a small Python wrapper script with a custom `url_fetcher` that rejects other schemes and hosts.
3. Office/HTML → PDF (LibreOffice/unoconvert): stop LibreOffice from loading linked external content (links, OLE, remote images) for uploaded documents. Reject or strip HTML inputs that reference `http(s)://`, `file://` or `ftp://` before they reach LibreOffice, or route HTML through path 2 only.
4. Keep `system.enableUrlToPDF: false` and add a startup warning if anyone turns it on.
**Acceptance:**
- New tests: an HTML file containing `<img src="http://169.254.169.254/latest/meta-data/">`, `<link href="file:///etc/passwd">`, `<iframe src="http://127.0.0.1:8080/api/v1/info/status">` converts without any outbound request and without file contents in the output (assert with a local mock HTTP server that records zero hits).
- Same test for a `.docx`/`.odt` with an externally linked image.
- `./gradlew build` passes.

### SEC-02 · Block outbound network from the container · P0 · M
**Why:** SSRF bugs keep turning up in PDF converters. Blocking egress at the network layer makes the next one harmless.
**Steps:**
1. Move the runtime `apk add` out of `scripts/init.sh` (OPS-02 bakes all OCR languages into the image), so the container needs no internet at start.
2. Deploy with outbound traffic denied, except DNS if the platform requires it. Use the host's private networking or firewall, a Kubernetes `NetworkPolicy`, or Docker `--network` with no default route plus a reverse proxy in front.
3. Document the egress rule in `docs/DEPLOYMENT.md` (OPS-08).
**Acceptance:** from inside the running container, `curl -m 5 https://example.com` fails. All in-scope tools still work (QA-02).

### SEC-03 · Stop leaking stack traces and exception details · P0 · S
**Files:** `src/main/resources/application.properties`, `templates/error.html`, `utils/ErrorUtils.java`, global exception handlers.
**Steps:** set `server.error.include-stacktrace=never`, `include-exception=false`, `include-message=never` (or `on_param` only in a dev profile). Make sure custom error pages and JSON error bodies show a generic message plus a request/correlation ID, and that the full trace is logged server-side.
**Acceptance:** a request that triggers an exception (e.g. a corrupt PDF to `/api/v1/general/merge-pdfs`) returns no Java class names or stack frames in HTML or JSON. Unit/integration test covers it.

### SEC-04 · Rate limiting and abuse controls for anonymous users · P0 · M
**Files:** `config/security/SecurityConfiguration.java`, `config/security/IPRateLimitingFilter.java`, `RateLimitResetScheduler.java`, `model/ApplicationProperties.java`, `settings.yml.template`.
**Steps:**
1. Register `IPRateLimitingFilter` whether or not login is enabled. Read limits from settings (e.g. `security.rateLimit.requestsPerMinute`, `security.rateLimit.heavyRequestsPerMinute`).
2. Give heavy endpoints (OCR, LibreOffice conversions, compress, PDF/A, HTML→PDF) a separate, lower limit.
3. Use the client IP from `X-Forwarded-For` only when the request comes from a trusted proxy. `server.forward-headers-strategy=NATIVE` is already set; document the trusted proxy setup.
4. Return HTTP 429 with a `Retry-After` header and a friendly UI message.
5. Also limit at the reverse proxy / CDN (OPS-03).
**Acceptance:** tests show the 61st request in a minute from one IP gets 429 with login disabled; spoofed `X-Forwarded-For` from an untrusted source does not bypass the limit.

### SEC-05 · Size, page and time limits · P0 · M
**Files:** `application.properties`, `settings.yml.template`, `service/CustomPDFDocumentFactory.java`, `utils/ProcessExecutor.java`.
**Steps:**
1. Set `system.fileUploadLimit` (e.g. `100MB`) and lower `spring.servlet.multipart.max-file-size` / `max-request-size` to match (e.g. 100MB / 250MB).
2. Add a max page count per document (e.g. 1,000) and a max file count per request; reject with HTTP 413/400 and a clear message.
3. Make sure PDFBox loads large files with a memory limit / temp-file mode (no unlimited heap use). Add a guard against decompression bombs (very large images, deeply nested objects).
4. Lower `processExecutor.timeoutMinutes` (LibreOffice 30 → e.g. 3, others accordingly) and set `sessionLimit` values that match the instance's CPU count.
5. Lower `spring.mvc.async.request-timeout` (currently 20 minutes) to e.g. 5 minutes.
**Acceptance:** tests for oversized file, too many pages, and a process that exceeds its timeout (killed, temp files cleaned, user gets an error).

### SEC-06 · Turn off endpoints that should not be public · P0 · S
**Files:** production settings (`settings.yml` / env), `metrics` section, `endpoints.toRemove` / `groupsToRemove`.
**Steps:**
1. `metrics.enabled: false`, or require admin auth for `/api/v1/info/**` (`controller/web/MetricsController.java`).
2. Disable pipeline and folder-scanning features for the public site (`/pipeline`, `PipelineDirectoryProcessor`). They are built for single-tenant servers.
3. Keep `enableUrlToPDF: false`, `customHTMLFiles: false`, `disableSanitize: false`, Google Drive off, H2 console off.
4. Decide on Swagger UI (`/index.html`, `/v1/api-docs`): off for launch unless you offer a public API.
5. Until SEC-01 and SEC-02 are verified, remove `html-to-pdf`, `markdown-to-pdf` and `file-to-pdf` (LibreOffice group) through `endpoints.toRemove`.
**Acceptance:** each disabled path returns 404/403 in an integration test or `testing/test_disabledEndpoints.sh` run.

### SEC-07 · No default admin password · P0 · S
**Files:** `config/security/InitialSecuritySetup.java`.
**Steps:** when login is enabled and no `SECURITY_INITIALLOGIN_USERNAME` / `SECURITY_INITIALLOGIN_PASSWORD` is set, refuse to start in the `prod` profile (clear log message), instead of creating `admin` / `stirling`. Keep forcing a password change on first login.
**Acceptance:** unit test for both branches. (Not reachable while D3 = anonymous, but must be fixed before anyone enables login.)

### SEC-08 · Security headers · P0 · S
**Files:** `SecurityConfiguration.java` (both login and no-login paths), or a servlet filter.
**Steps:** add `Content-Security-Policy` (start with `default-src 'self'; img-src 'self' data: blob:; worker-src 'self' blob:; object-src 'none'; frame-ancestors 'none'`, then loosen only as needed by pdf.js and inline scripts, preferring nonces), `Strict-Transport-Security` (via proxy or app), `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`.
**Acceptance:** every page loads with no CSP violations in the browser console across QA-02. securityheaders.com (or ZAP, QA-04) rates it A.

### SEC-09 · Temp file and session cleanup · P0 · M
**Steps:**
1. Audit every controller/util that creates temp files or dirs (`Files.createTemp*`, LibreOffice profile dirs, unzip dirs) and make sure each is deleted in a `finally` block.
2. Add a scheduled janitor that deletes anything older than N minutes (e.g. 30) under the app's temp directory, as a safety net.
3. Mount the temp directory as `tmpfs` with a size cap in the container (OPS-02).
**Acceptance:** after running QA-02 and a load test (QA-03), the temp directory is empty within N minutes. The privacy policy statement "files are deleted after processing" is true.

### SEC-10 · Dependency and image hygiene · P0 · M
**Steps:**
1. Bump Spring Boot (`springBootVersion = "3.4.5"` in `build.gradle`) to the newest supported patch release, plus PDFBox, BouncyCastle, commonmark, jsoup/OWASP sanitizer, and front-end libraries in `static/js/thirdParty` (pdf.js, bootstrap, etc.) to patched versions.
2. Run OWASP Dependency-Check (or `gradle dependencyCheckAnalyze`) and Trivy on the built image; fix or document every High/Critical.
3. Enable Dependabot for Gradle, GitHub Actions and Docker.
**Acceptance:** zero unaddressed Critical/High findings; report attached to the PR.

### SEC-11 · Container runs locked down · P1 · S
**Steps:** run the Java process as the non-root user (verify `init-without-ocr.sh` drops privileges with `su-exec`), read-only root filesystem with writable `tmpfs`/volume mounts only where needed, drop Linux capabilities, `no-new-privileges`.
**Acceptance:** `docker inspect` / platform config shows these settings; app works in QA-02.

### SEC-12 · Responsible disclosure · P1 · S
Rewrite `SECURITY.md` with your own contact address and response process; add `/.well-known/security.txt`.

---

## 5. Phase 2 — Remove third-party tracking and finish de-branding (P0)

### BR-01 · Remove the Stirling tracking pixel · P0 · S
**Files:** `templates/fragments/common.html:43-50`, `config/AppConfig.java:201-204`.
**Steps:** delete the `<img … pixel.stirlingpdf.com …>` element. Delete the `disablePixel` bean, or if you keep it for some reason, fix the bug (`Boolean.parseBoolean(env.getProperty("DISABLE_PIXEL", "false"))`).
**Acceptance:** page load in a browser makes no request to any `stirlingpdf.com` host (check DevTools network tab); test asserts the template contains no `pixel.` URL.

### BR-02 · Remove or replace PostHog analytics · P0 · M
**Files:** `application.properties` (`posthog.api.key`, `posthog.host`), `config/PostHogConfig.java`, `config/PostHogLoggerImpl.java`, `service/PostHogService.java`, `service/MetricsAggregatorService.java`, `controller/api/pipeline/PipelineController.java`, `PipelineDirectoryProcessor.java`, `templates/fragments/common.html` (PostHog snippet near lines 90-160), analytics consent modal in `templates/home.html:~165-185`, `build.gradle` (`com.posthog.java:posthog`), `EE/KeygenLicenseVerifier.java` (imports PostHog's shaded JSON classes — switch to Jackson).
**Steps:** pick one:
- (Recommended) Remove PostHog completely, including the dependency and the consent modal. If you want product analytics later, add a privacy-friendly tool you control, behind a config flag that defaults off.
- Or replace the key and host with your own PostHog project, read from environment variables (`POSTHOG_API_KEY`, `POSTHOG_HOST`), default off, and show the consent prompt only when enabled.
**Acceptance:** `grep -r "phc_" src/` returns nothing. No requests to `posthog.com` with analytics off. Build passes.

### BR-03 · Own legal pages; stop injecting Stirling's · P0 · S
**Files:** `config/InitialSetup.java:72-87`, `settings.yml.template` `legal:` block, `templates/fragments/footer.html`.
**Steps:** delete the default-URL logic in `initLegalUrls()` (blank must stay blank). Add static pages (e.g. `static/terms.html`, `static/privacy.html`, `static/cookies.html`) or point settings at your own URLs once LEG-01 provides the text.
**Acceptance:** fresh start with an empty `configs/` folder → footer shows your links or none; `grep -r "stirlingpdf.com/terms\|stirlingpdf.com/privacy" src/main` returns nothing. Unit test for `initLegalUrls()`.

### BR-04 · Update checks point to your repo or are off · P0 · S
**Files:** `static/js/githubVersion.js:20,38`, `templates/releases.html:113-114`, `config/AppUpdateService.java`, `templates/home.html:85`, `templates/fragments/navbar.html:19`.
**Steps:** for the hosted site, turn update checks off (users can't update a website). Remove the release-notes page or point it at `DecoyINDIA/OctaPDF`.
**Acceptance:** no requests to `api.github.com/repos/Stirling-Tools` on any page.

### BR-05 · Remove upstream upsell, survey and license-server calls · P0 · M
**Files:** `templates/fragments/navbar.html:174` ("Go Pro" → `stirlingpdf.com/pricing`), `templates/home.html:~125-160` (survey modal, Calendly link, `stirlingpdf.info` link), `EE/` package (`KeygenLicenseVerifier`, `LicenseKeyChecker`, `EEAppConfig`), `config/EnterpriseEndpointFilter.java`.
**Steps:** remove the pro link and survey modal. Make sure no call to `api.keygen.sh` happens at startup or on a schedule when no license key is set. Decide whether to keep the EE gating code (dormant) or remove it; if you remove it, keep features that are useful to you enabled by config instead of license.
**Acceptance:** startup log and network capture show no outbound call to `keygen.sh`, `stirlingpdf.com`, `stirlingpdf.info` or `calendly.com`.

### BR-06 · Replace visible "Stirling" text · P0 · M
**Files (non-translation):** `static/manifest.json`, `static/site.webmanifest`, `templates/fragments/common.html:19-20`, `templates/login.html:100`, `templates/error.html:21`, `templates/fragments/errorBanner.html:22`, `templates/fragments/errorBannerPerPage.html:41`, `templates/security/add-watermark.html:45`, `templates/security/remove-watermark.html:26`, `templates/misc/ocr-pdf.html:223`, `templates/pipeline.html:79,86` (docs links), `settings.yml.template` ASCII banner (lines 1-11) and SAML examples, `static/css/footer.css` class name (optional).
**Steps:**
- (a) Replace product names with `XolverLabs PDF` or the `${@appName}` bean. Point GitHub-issue buttons at your support channel or your repo's issues. Replace `docs.stirlingpdf.com` links with your own docs, or remove them.
- (b) Translations: per D7, either run a scripted replacement of the product name in all `messages_*.properties` (needs owner approval because AGENTS.md restricts this), or set `ui.languages: ["en_GB"]` for launch and fix `messages_en_GB.properties` (18 occurrences) only.
- Internal identifiers such as `window.stirlingPDF`, the Java package `stirling.software.SPDF` and the jar name are not user-visible; leave them for now (see P2-03).
**Acceptance:** a script in `testing/` (e.g. `check_branding.sh`) fails the build or CI if user-facing templates, JS strings, manifests or enabled locale files contain `Stirling` (ignoring `stirling.software`, `window.stirlingPDF` and the LICENSE/attribution page).

### BR-07 · Replace logos and icons · P0 · S (needs D5)
**Files:** The OctaPDF lockup and platform icons live in the static assets directory. See the [logo guidelines](OctaPDF-Logo-Guidelines.pdf) for construction and export sizes.
**Acceptance:** visual check on desktop and mobile; installers use the new `favicon.ico` / `.icns`.

### BR-08 · Attribution and third-party notices page · P0 · S
Keep `LICENSE`. Add a short "Based on Stirling-PDF (MIT)" credit and a link to the existing `static/3rdPartyLicenses.json` from an About/Licenses page.
**Acceptance:** page reachable from the footer.

---

## 6. Phase 3 — Production configuration and infrastructure (P0)

### OPS-01 · `prod` Spring profile · P0 · S
Create `application-prod.properties`: logging at INFO/WARN, error settings from SEC-03, `server.servlet.session.cookie.secure=true`, `same-site=lax`, `system.googlevisibility: true` if you want search engines to index the site, limits from SEC-05. Activate with `SPRING_PROFILES_ACTIVE=prod` in the image.
**Acceptance:** app starts with the profile; config dump (in a test) shows the values.

### OPS-02 · Production Dockerfile · P0 · M
**Steps:**
1. Use a pinned stable Alpine (or Debian slim) and remove the `edge/*` and `@testing` repositories. Find stable sources for `py3-pillow`, `py3-pdf2image` (pip into the venv is fine) and pin the pip packages (`unoserver`, `weasyprint`) to exact versions.
2. Bake in every OCR language you support; remove the runtime `apk add` from `scripts/init.sh` (needed for SEC-02).
3. Run Java and unoserver under a proper supervisor (e.g. `tini` + a small script that exits if either process dies, or `s6-overlay`), so the platform restarts the container on failure.
4. Add a `HEALTHCHECK` that calls a health endpoint (OPS-05).
5. Rename the OS user/group from `stirlingpdfuser` (optional, cosmetic).
6. Build multi-arch (amd64, arm64) if your host needs it.
**Acceptance:** image builds twice from scratch with identical package versions; container starts with no internet; killing the Java process makes the container exit.

### OPS-03 · Hosting, domain, TLS and reverse proxy · P0 · M (needs D4)
**Steps:** provision staging and production environments. Put a reverse proxy/CDN in front (TLS, HTTP→HTTPS redirect, HSTS, request body limit matching SEC-05, request timeout, IP rate limit, WAF rules if available). Point the domain. Set environment variables for secrets.
**Acceptance:** `https://<domain>` serves the app; SSL Labs grade A; staging and prod are separate.

### OPS-04 · Sizing and concurrency · P0 · S
Start with ≥2 vCPU / 4 GB RAM per instance. Set `JAVA_BASE_OPTS` `MaxRAMPercentage` to leave room for LibreOffice, Tesseract and Python (e.g. 50%). Match `processExecutor.sessionLimit` to CPU count. Use the load test (QA-03) to set autoscaling thresholds. The app is stateless when login is off, so horizontal scaling works without sticky sessions.

### OPS-05 · Health checks, logging, monitoring and alerts · P0 · M
**Steps:**
1. Expose only `/actuator/health` (liveness/readiness) publicly or to the platform; keep other actuator endpoints off or internal.
2. Log in JSON to stdout (adjust `logback.xml`). Never log file contents or file names from users at INFO level. Set log retention (e.g. 14–30 days).
3. External uptime monitor on the home page and one cheap tool endpoint.
4. Error tracking (e.g. Sentry) with PII scrubbing, or alerts on error-log rate.
5. Alerts: instance down, 5xx rate, p95 latency, memory > 85%, disk/tmpfs > 80%.
**Acceptance:** a forced failure in staging triggers an alert to the owner.

### OPS-06 · Data persistence and backups · P1 · S
With login off, the app stores only `configs/settings.yml` and the H2 file (`./configs/stirling-pdf-DB-…`). Generate `settings.yml` from environment variables at deploy time so instances are disposable. If accounts are enabled later (D3), switch to managed PostgreSQL (`system.datasource.*`) with daily backups and a tested restore.

### OPS-07 · Secrets management · P0 · S
All secrets (OAuth client secrets, mail password, analytics keys, initial admin password) come from the platform's secret store as environment variables. `AutomaticallyGenerated.key` must be unique per environment and persisted, not `example`.

### OPS-08 · Runbooks and deployment docs · P1 · S
Write `docs/DEPLOYMENT.md` (build, env vars, egress rule, scaling, rollback) and `docs/RUNBOOK.md` (restart, high memory, LibreOffice stuck, disk full, incident contact).

---

## 7. Phase 4 — CI/CD (P0/P1)

### CI-01 · Prune upstream workflows · P0 · S
**Files:** `.github/workflows/`.
Keep and adapt: `build.yml`, `pre_commit.yml`, `dependency-review.yml`, `check_properties.yml`, `scorecards.yml`. Remove or disable: `PR-Demo-Comment-with-react.yml`, `PR-Demo-cleanup.yml`, `testdriver.yml`, `sonarqube.yml` (unless you set up your own SonarCloud), `swagger.yml` (SwaggerHub upload), `sync_files.yml`, `licenses-update.yml` (or keep if it works without upstream secrets), `releaseArtifacts.yml` and `multiOSReleases.yml` (until desktop distribution, P2-01), `auto-labeler.yml`, `manage-label.yml`, `stale.yml` as you prefer. Also remove `swaggerhubUpload` from `build.gradle` if unused.
**Acceptance:** every remaining workflow passes on a test PR with no missing-secret failures.

### CI-02 · Own image build and publish · P0 · S
Rewrite `push-docker.yml` to publish `ghcr.io/decoyindia/octapdf` (not `s-pdf` / `stirling-pdf`), tagged with the git SHA and version. Generate an SBOM and run Trivy (SEC-10) in the same job; fail on Critical.

### CI-03 · Deploy pipeline · P1 · M
Merge to `main` → build → deploy to staging → smoke tests (QA-01 subset) → manual approval → deploy to production. One-click rollback to the previous image tag.

### CI-04 · Branding guard in CI · P1 · S
Run the BR-06 `check_branding.sh` script on every PR.

---

## 8. Phase 5 — Testing and QA (P0/P1)

### QA-01 · Integration tests against the built image · P0 · M
Run `testing/test.sh` and the Cucumber suite in `testing/cucumber` against the production image in CI. Fix or delete tests that depend on upstream infrastructure.

### QA-02 · Feature regression matrix · P0 · M (needs D8)
For each tool you ship: upload a small, a large and a malformed file; check output opens correctly in at least two PDF readers. Record results in `docs/QA_MATRIX.md`. Disable (via `endpoints.toRemove`) any tool that fails and can't be fixed before launch.

### QA-03 · Load test · P0 · M
k6 or Gatling script: mixed traffic (merge, compress, OCR, office→PDF) at expected peak × 3. Record p95 latency, error rate, memory. Use results for OPS-04.

### QA-04 · Security testing · P0 · M
OWASP ZAP baseline + active scan against staging; the SSRF test set from SEC-01 against staging (with egress blocked and unblocked); upload fuzzing with malformed PDFs. Fix all High findings.

### QA-05 · Browser, mobile and accessibility · P1 · M
Latest Chrome, Firefox, Safari, Edge; iOS Safari and Android Chrome; keyboard navigation and screen-reader smoke test on the home page and three most-used tools. Publish an accessibility statement (`legal.accessibilityStatement`).

### QA-06 · Unit test coverage for changed code · P1 · S
JaCoCo is configured (`build/jacoco`). Require tests on every file changed by SEC-*/BR-* tasks.

---

## 9. Phase 6 — Legal and compliance (human-led, P0)

### LEG-01 · Policies · P0 (human; agent can draft)
Terms of Service, Privacy Policy (state exactly what is collected, that uploaded files are processed in memory/temp storage and deleted after processing, log retention period, any analytics), Cookie Policy (if any non-essential cookies), Impressum (if you target Germany/Austria). Have a lawyer review. Agent task: publish the final texts as static pages (BR-03).

### LEG-02 · Open-source license obligations · P0 (human + agent)
- MIT (Stirling-PDF): keep the copyright notice (BR-08).
- The Docker image bundles GPL/LGPL/MPL software (e.g. poppler-utils, LibreOffice, qpdf, Tesseract). Running them as a hosted service is generally fine; if you **distribute** the image or desktop installers, you take on source-availability duties. Have counsel confirm.
- Regenerate `3rdPartyLicenses.json` after SEC-10 dependency bumps; check against `allowed-licenses.json`.

### LEG-03 · Trademark · P0 (human)
Clear "XolverLabs PDF" for your markets. Make sure no "Stirling" mark remains in user-facing UI (BR-06).

### LEG-04 · Abuse and takedown process · P1 (human)
A contact address for abuse/legal requests, shown in the footer or Terms.

---

## 10. Phase 7 — Launch readiness (P1)

| ID | Task | Acceptance |
|---|---|---|
| LCH-01 | Home page copy: replace `ui.homeDescription`, page titles and meta descriptions; Open Graph tags and share image | Link previews show your brand |
| LCH-02 | `robots.txt` and `sitemap.xml` (`system.googlevisibility: true`) | Search Console verifies the sitemap |
| LCH-03 | Support channel (email or form) linked from footer and error pages (replaces upstream GitHub-issue buttons) | Test message received |
| LCH-04 | Status page (can be the uptime monitor's public page) | Link in footer |
| LCH-05 | Go-live checklist run on production: all P0 tasks closed, QA-02/03/04 reports attached, backups/alerts tested, rollback rehearsed once | Owner signs off |

---

## 11. Phase 8 — After launch (P2)

| ID | Task |
|---|---|
| P2-01 | Desktop installers (if D1 includes them): enable code signing for Windows (`launch4j`/`jpackage`) and macOS signing + notarization (`macSign = false` in `build.gradle` today); point `winUpdateUrl` at your releases; rename `lauch4jConfig.xml` typo only if build references are updated too |
| P2-02 | Upstream security tracking: watch Stirling-PDF security advisories weekly and backport fixes; re-evaluate the rebase decision (D2) each quarter |
| P2-03 | Internal rename (optional): `rootProject.name = 'Stirling-PDF'` in `settings.gradle`, jar name `Stirling-PDF-<ver>.jar` (also used by `jpackage`, Dockerfiles), Java package `stirling.software.SPDF`, H2 file name, `window.stirlingPDF`. Large mechanical change; do it in one dedicated PR |
| P2-04 | User accounts (if D3 changes): enable login, OAuth, PostgreSQL, email verification, account deletion (privacy-law requirement), per-user rate limits |
| P2-05 | Gradle 9 readiness: fix deprecated features reported by the build |
| P2-06 | Remove unused Dockerfile variants (`Dockerfile.fat`, `Dockerfile.ultra-lite`, `Dockerfile.dev`) or bring them up to the OPS-02 standard |

---

## 12. Suggested order and sizing

| Week | Work |
|---|---|
| 0 | Owner answers D1–D8; starts LEG-01, LEG-03, D4 accounts, D5 assets |
| 1 | BR-01, BR-02, BR-03, BR-04, BR-05, SEC-03, SEC-06, SEC-07, OPS-01, CI-01 (quick wins, removes privacy blockers) |
| 2 | SEC-01, SEC-04, SEC-05, SEC-08, BR-06, BR-07, BR-08 |
| 3 | SEC-02, SEC-09, SEC-10, OPS-02, OPS-03, OPS-05, OPS-07, CI-02 |
| 4 | QA-01 to QA-04, fixes from QA, OPS-04, LEG pages published |
| 5 | P1 items, LCH-01 to LCH-05, go-live |

Sizes: S ≈ under half a day, M ≈ 1–2 days, L ≈ 3–5 days of agent work, plus human review time.
