# OctaPDF Production Operations Runbook (OPS-08)

This runbook provides on-call engineers and administrators with standard operating procedures for monitoring, diagnosing, and resolving incidents with OctaPDF.

---

## 1. Monitoring & Health Verification

### Liveness and Readiness Probes
OctaPDF exposes a dedicated Spring Boot Actuator health endpoint:
- **Health Endpoint:** `GET /actuator/health`
- **Expected Status:** HTTP 200 `{"status":"UP"}`

```bash
# Internal container check:
docker exec -it octapdf curl -f http://localhost:8080/actuator/health

# External / reverse-proxy check:
curl -i https://<your-domain>/actuator/health
```

### Log Inspection
Logs are written to stdout in ISO-8601 structured format and persisted in `/logs/info.log` (with 14-day rotation):
```bash
# View live container logs:
docker logs -f --tail 100 octapdf

# Inspect rotated logs inside volume:
docker exec -it octapdf tail -f /logs/info.log
```

---

## 2. Common Incident Procedures

### Incident A: LibreOffice / unoconvert Process Stuck
**Symptoms:** Office-to-PDF conversions hang, p95 latency spikes, CPU remains pegged.
**Diagnosis:**
```bash
docker exec -it octapdf ps aux | grep soffice
```
**Resolution:**
1. The process executor enforces a strict 3-minute timeout (`processExecutor.timeoutMinutes.libreOfficeTimeoutMinutes: 3`). Stuck processes are killed automatically.
2. If orphan processes persist, restart the container:
   ```bash
   docker restart octapdf
   ```
3. Process supervisor (`/scripts/supervise.sh`) will cleanly restart both `unoserver` and Java.

---

### Incident B: High Memory Usage / Container OOM Kill
**Symptoms:** Container exits with code 137; orchestrator logs show Out-Of-Memory (OOM).
**Diagnosis:**
- JVM heap vs off-heap memory usage:
  ```bash
  docker stats octapdf
  ```
- Memory is split 50/50 between JVM heap and native processes (LibreOffice, Tesseract OCR, WeasyPrint).
**Resolution:**
1. Ensure the host assigns at least 4 GB RAM (`deploy.resources.limits.memory: 4G`).
2. Verify `JAVA_BASE_OPTS` includes `-XX:MaxRAMPercentage=50`.
3. If processing unusually high-concurrency workloads, increase horizontal replicas rather than setting JVM heap > 70%.

---

### Incident C: Disk or Tmpfs Full (/tmp exhaustion)
**Symptoms:** HTTP 500 errors during PDF manipulation; `IOException: No space left on device`.
**Diagnosis:**
```bash
docker exec -it octapdf df -h /tmp
```
**Resolution:**
1. OctaPDF runs an automated `TempFileJanitorService` every 15 minutes, removing files older than 30 minutes.
2. In emergency scenarios, manually purge aged temp files:
   ```bash
   docker exec -it octapdf find /tmp -type f -mmin +10 -delete
   ```
3. In `docker-compose.prod.yml`, expand tmpfs size if sustained traffic requires it:
   ```yaml
   tmpfs:
     - /tmp:size=2G,mode=1777
   ```

---

### Incident D: Excessive Rate Limiting / 429 Spikes
**Symptoms:** Legitimate users reporting HTTP 429 "Too Many Requests".
**Diagnosis:**
- Check reverse proxy configuration: verify `X-Forwarded-For` is being passed accurately from trusted proxies so all clients don't share the reverse proxy's IP.
- Review rate limit settings in `application-prod.properties` (`security.rateLimit.requestsPerMinute`).
**Resolution:**
- If legitimate load exceeds thresholds, adjust via environment variables:
  ```bash
  SECURITY_RATELIMIT_REQUESTSPERMINUTE=120
  SECURITY_RATELIMIT_HEAVYREQUESTSPERMINUTE=20
  ```

---

## 3. Container Restart & Rolling Deployment

### Docker Compose
```bash
# Graceful restart:
docker-compose -f docker-compose.prod.yml restart octapdf

# Recreate container with updated configuration:
docker-compose -f docker-compose.prod.yml up -d --force-recreate octapdf
```

### Kubernetes
```bash
# Trigger a zero-downtime rolling restart:
kubectl rollout restart deployment/octapdf -n octapdf

# Check rollout status:
kubectl rollout status deployment/octapdf -n octapdf
```

---

## 4. Rollback Procedure

If a newly deployed image introduces critical errors or regressions:
1. Locate the last known good image digest or tag from GHCR:
   ```bash
   # Example: ghcr.io/decoyindia/octapdf:<previous-commit-sha>
   ```
2. Update the image reference in `docker-compose.prod.yml` or Kubernetes deployment:
   ```bash
   kubectl set image deployment/octapdf octapdf=ghcr.io/decoyindia/octapdf:<previous-sha> -n octapdf
   ```
3. Verify `/actuator/health` returns HTTP 200.

---

## 5. Incident Contacts & Escalation

- **Security Incidents (SSRF, Auth, Vulnerability):** `security@octapdf.com`
- **Abuse & Legal Notices:** `abuse@octapdf.com` / `legal@octapdf.com`
- **Operations & Systems:** Engineering On-Call Team
