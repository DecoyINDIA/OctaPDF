import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test for OctaPDF (QA-03)
// Simulates mixed traffic across core PDF operations: health check, merge, compress, rotate

export const options = {
  stages: [
    { duration: '30s', target: 5 },   // Ramp-up to 5 concurrent VUs
    { duration: '1m', target: 15 },   // Sustain expected peak load
    { duration: '30s', target: 25 },  // Stress test at 3x peak load
    { duration: '30s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'], // 95% of requests should complete within 3s
    http_req_failed: ['rate<0.02'],     // Error rate should stay under 2%
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

// Minimal valid single-page PDF in base64
const SAMPLE_PDF_BASE64 = 
  'JVBERi0xLjQKJcOkw7zDtsOfCjEgMCBvYmoKPDwKL1R5cGUgL0NhdGFsb2cKL1BhZ2VzIDIgMCBS' +
  'Cj4+CmVuZG9iagoyIDAgb2JqCjw8Ci9UeXBlIC9QYWdlcwovS2lkcyBbMyAwIFJdCi9Db3VudCAx' +
  'Cj4+CmVuZG9iagozIDAgb2JqCjw8Ci9UeXBlIC9QYWdlCi9QYXJlbnQgMiAwIFIKL01lZGlhQm94' +
  'IFswIDAgNjEyIDc5Ml0KL1Jlc291cmNlcyA8PAo+PgovQ29udGVudHMgNCAwIFIKPj4KZW5kb2Jq' +
  'CjQgMCBvYmoKPDwKL0xlbmd0aCA0NAo+PgpzdHJlYW0KQVQgL0YxIDEyIFRmIDcyIDcxMiBUZCAo' +
  'T2N0YVBERSBMb2FkIFRlc3QpIFRqIEVUCmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDUKMDAwMDAw' +
  'MDAwMCA2NTUzNSBmIAowMDAwMDAwMDE1IDAwMDAwIG4gCjAwMDAwMDAwNjggMDAwMDAgbiAKMDAw' +
  'MDAwMDEyNSAwMDAwMCBuIAowMDAwMDAwMjIwIDAwMDAwIG4gCnRyYWlsZXIKPDwKL1NpemUgNQov' +
  'Um9vdCAxIDAgUgo+PgpzdGFydHhyZWYKMzE0CiUlRU9GCg==';

export default function () {
  const binaryPdf = http.file(SAMPLE_PDF_BASE64, 'sample.pdf', 'application/pdf');

  // Scenario 1: Healthcheck
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health returns 200': (r) => r.status === 200,
  });

  // Scenario 2: Home page load
  const homeRes = http.get(`${BASE_URL}/`);
  check(homeRes, {
    'home returns 200': (r) => r.status === 200,
    'home contains OctaPDF': (r) => r.body.includes('OctaPDF'),
  });

  // Scenario 3: Rotate PDF
  const rotateData = {
    fileInput: binaryPdf,
    angle: '90',
  };
  const rotateRes = http.post(`${BASE_URL}/api/v1/general/rotate-pdf`, rotateData);
  check(rotateRes, {
    'rotate returns 200': (r) => r.status === 200,
  });

  // Scenario 4: Merge PDFs (submitting two copies)
  const mergeData = {
    fileInput: [binaryPdf, binaryPdf],
  };
  const mergeRes = http.post(`${BASE_URL}/api/v1/general/merge-pdfs`, mergeData);
  check(mergeRes, {
    'merge returns 200': (r) => r.status === 200,
  });

  sleep(1);
}
