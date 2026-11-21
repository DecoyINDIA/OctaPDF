# TLS Certificates Directory

Place your TLS certificate (`cert.pem`) and private key (`key.pem`) here for the reverse-proxy service in `docker-compose.prod.yml`.

For testing or self-signed certificates:
```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout certs/key.pem -out certs/cert.pem -subj "/CN=localhost"
```
