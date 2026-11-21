# OctaPDF Production Deployment & Hardening Guide

This document describes how to deploy OctaPDF in a production environment with defense-in-depth network isolation and security controls.

---

## 1. Outbound Network Isolation / Egress Lockdown (SEC-02)

### Rationale
PDF processors, document conversion engines (LibreOffice, WeasyPrint), and OCR pipelines parse complex untrusted file formats. Even with strict application-level SSRF mitigations (SEC-01), blocking container network egress at the infrastructure layer guarantees that no outbound network connections can be initiated from the container, neutralizing SSRF, remote resource leakage, and command injection callbacks.

Container startup scripts (`scripts/init.sh`, `scripts/init-without-ocr.sh`) have been decoupled from package managers (`apk add`) and remote downloads. All fonts, dependencies, and OCR languages are baked into the container image or mounted via local volumes.

---

### Egress Lockdown Options

#### Option A: Docker Compose with Internal Network (Recommended for Docker)
Run OctaPDF on an isolated internal network (`internal: true`) with no default gateway or internet route. An Nginx or Caddy reverse proxy joins both the public and internal networks to forward user requests to OctaPDF.

```yaml
version: '3.8'

services:
  reverse-proxy:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro
    networks:
      - frontend
      - octapdf-internal
    restart: unless-stopped

  octapdf:
    image: ghcr.io/decoyindia/octapdf:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SECURITY_ENABLELOGIN=false
    volumes:
      - octapdf-configs:/configs
      - octapdf-logs:/logs
    networks:
      - octapdf-internal
    restart: unless-stopped

networks:
  frontend:
    driver: bridge
  octapdf-internal:
    driver: bridge
    internal: true # Disables default gateway / prevents internet egress

volumes:
  octapdf-configs:
  octapdf-logs:
```

#### Option B: Kubernetes NetworkPolicy
In Kubernetes, apply a `NetworkPolicy` to the OctaPDF pods allowing only cluster DNS egress (if needed for internal service discovery) and ingress from the ingress controller:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: octapdf-network-isolation
  namespace: octapdf
spec:
  podSelector:
    matchLabels:
      app: octapdf
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
  egress:
    # Allow DNS queries only (kube-dns / CoreDNS)
    - to:
        - namespaceSelector: {}
          podSelector:
            matchLabels:
              k8s-app: kube-dns
      ports:
        - protocol: UDP
          port: 53
        - protocol: TCP
          port: 53
    # Deny all other outbound internet / intranet traffic
```

#### Option C: Host Firewall (iptables / nftables)
For standalone container engines on Linux hosts:

```bash
# Block all outbound connections originating from the container bridge except established responses
iptables -I DOCKER-USER -i br-octapdf ! -o br-octapdf -j DROP
```

#### Option D: Container PaaS (Railway, Render, Fly.io) — Platform Egress Decision (D4)
On container PaaS platforms, Docker custom bridge networks (`internal: true`) and Kubernetes `NetworkPolicy` are not supported by the platform layer. If deploying to one of these platforms:
- **Risk Acceptance:** Outbound egress risk is explicitly accepted **only while risky external-fetching conversion endpoints (`html-to-pdf`, `markdown-to-pdf`, `file-to-pdf`) remain disabled** via `endpoints.toRemove` in `application-prod.properties`.
- **Pre-enablement Requirement:** Before enabling HTML/Markdown/Office conversion tools on PaaS hosts, deploy a dedicated egress filtering gateway or migrate to VPS/Kubernetes infrastructure with strict firewall rules (Option A/B/C) to enforce SEC-02 SSRF defense-in-depth.

---

### Verification
Verify that outbound network access is blocked from inside the running container:

```bash
docker exec -it <container_name_or_id> curl -m 5 https://example.com
```

**Expected output:**
```
curl: (28) Failed to connect to example.com port 443: Connection timed out
# OR
curl: (7) Failed to connect to example.com: Network unreachable
```

---

## 2. Container Hardening & Least Privilege (SEC-11)

In production, run the OctaPDF container with least-privilege security flags enabled:

1. **Non-Root Execution:** The Java and unoserver processes run as unprivileged user `stirlingpdfuser` (UID 1000, GID 1000). Startup scripts automatically drop root privileges using `su-exec`.
2. **Read-Only Root Filesystem:** Enable `read_only: true`. Only designated directories (`/configs`, `/logs`, `/pipeline`, and `/tmp`) should be writable.
3. **Drop Linux Capabilities:** Run with `cap_drop: [ "ALL" ]` to prevent any privileged kernel interactions.
4. **Prevent Privilege Escalation:** Enforce `security_opt: [ "no-new-privileges:true" ]`.
5. **Capped In-Memory TempFS:** Mount `/tmp` as `tmpfs` with a strict size limit (`/tmp:size=1G,mode=1777`) to prevent disk exhaustion attacks.

See `docker-compose.prod.yml` for a complete reference configuration.

---

## 3. Production Spring Profile (`prod`)

Activate the production profile by setting:
```bash
SPRING_PROFILES_ACTIVE=prod
```

This enforces:
- Generic error messages with correlation IDs; suppression of stack traces and internal Java class names (SEC-03).
- Strict security response headers (CSP, HSTS, X-Content-Type-Options, etc., SEC-08).
- Multi-tier IP rate limiting (SEC-04).
- Safe upload and processing thresholds: 100MB max file size, 250MB request size, 1,000 max pages, 5-minute async timeout (SEC-05).
- Disabled internal metrics and management endpoints (SEC-06).

---

## 4. Reverse Proxy Configuration Guidelines

A reverse proxy (Nginx, Cloudflare, Traefik, or Caddy) must sit in front of OctaPDF to terminate TLS and enforce transport security:

1. **Client Body Size Limit:** Match `spring.servlet.multipart.max-request-size` (250M):
   ```nginx
   client_max_body_size 250M;
   ```
2. **Timeouts:** Align proxy read and send timeouts with `spring.mvc.async.request-timeout` (300s):
   ```nginx
   proxy_read_timeout 300s;
   proxy_connect_timeout 60s;
   proxy_send_timeout 300s;
   ```
3. **Forwarded Headers:** Pass trusted client IP addresses via `X-Forwarded-For` and `X-Forwarded-Proto`:
   ```nginx
   proxy_set_header X-Real-IP $remote_addr;
   proxy_set_header X-Forwarded-For $remote_addr;
   proxy_set_header X-Forwarded-Proto $scheme;
   ```
   > **Important Security Requirement:** The reverse proxy **must overwrite** (using `$remote_addr`), **not append to** (do not use `$proxy_add_x_forwarded_for`), the `X-Forwarded-For` header. Appending allows an untrusted client to inject arbitrary spoofed IP addresses and bypass rate limiting.
   > In `settings.yml` (under `security.rateLimit.trustedProxies`) or via the environment variable `SECURITY_RATELIMIT_TRUSTEDPROXIES`, configure the trusted reverse proxy IP address or CIDR range (e.g. `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, or `127.0.0.1/32`). Only requests originating directly from these trusted IP/CIDR ranges will have client IP addresses resolved from `X-Forwarded-For`. All other requests use the real socket connection address.

---

## 5. Resource Allocation Recommendations

- **CPU:** Minimum 2 vCPUs recommended (scales with concurrent conversion/OCR requests).
- **RAM:** Minimum 4 GB RAM.
- **JVM Memory Options:**
  ```bash
  JAVA_BASE_OPTS="-XX:+UnlockExperimentalVMOptions -XX:MaxRAMPercentage=50 -XX:+UseG1GC"
  ```
  Leaving 50% of container memory headroom ensures LibreOffice, Python unoserver, and Tesseract processes do not trigger OOM kills.


---

## 6. Secrets Management (OPS-07)

All credentials and cryptographic keys must be sourced from the platform's secret store (or Kubernetes Secrets) as environment variables rather than embedded in configuration files:

| Environment Variable | Description | Default / Requirement |
| -------------------- | ----------- | --------------------- |
| `SECURITY_INITIALLOGIN_USERNAME` | Initial administrator username | Required if login enabled; no default in `prod` (SEC-07) |
| `SECURITY_INITIALLOGIN_PASSWORD` | Initial administrator password | Required if login enabled; refuses startup in `prod` if absent (SEC-07) |
| `AUTOMATICALLYGENERATED_KEY` | Application encryption & CSRF secret key | Randomly generated UUID at startup if absent; never `example` in production |
| `AUTOMATICALLYGENERATED_UUID` | Unique instance identifier | Randomly generated UUID at startup if absent |
| `SPRING_MAIL_PASSWORD` | SMTP service password | Required only if email notifications enabled |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_*_CLIENT_SECRET` | OAuth2 / OIDC client secrets | Required only if OAuth2 enabled |
| `POSTGRES_PASSWORD` | External database password | Required only if PostgreSQL data source enabled |


---

## 7. Scaling and Rollback Procedures (OPS-08)

### Horizontal Scaling
When login is disabled (`SECURITY_ENABLELOGIN=false`), OctaPDF is completely stateless. Requests can be load-balanced across multiple container replicas without requiring sticky sessions.
- In Docker Compose: `docker-compose -f docker-compose.prod.yml up --scale octapdf=3 -d` (Docker's internal DNS automatically round-robins requests sent to service name `octapdf` across all active replicas on `octapdf-internal`).
- In Kubernetes: configure a HorizontalPodAutoscaler (HPA) targeting CPU utilization at 70%.

### Rapid Rollback
If a deployment exhibits critical failures:
1. Revert to the prior Git SHA image tag published to GHCR:
   ```bash
   ghcr.io/decoyindia/octapdf:<previous-commit-sha>
   ```
2. Redeploy with the previous tag. In stateless mode (`SECURITY_ENABLELOGIN=false`), rollbacks take effect immediately with zero persistent state or database rollback requirements. When running with an external database, verify that any schema migrations are backward-compatible before initiating deployment.
3. Consult `docs/RUNBOOK.md` for comprehensive incident handling and troubleshooting procedures.
