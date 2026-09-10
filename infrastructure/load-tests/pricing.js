import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8081').replace(/\/$/, '');
const productId = __ENV.PRODUCT_ID || 'NOTEBOOK-001';
const quantity = Number.parseInt(__ENV.QUANTITY || '2', 10);
const profile = __ENV.PROFILE || 'load';

const profiles = {
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '10s',
  },
  load: {
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 200,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '20s', target: 25 },
      { duration: '20s', target: 50 },
      { duration: '10s', target: 0 },
    ],
  },
};

if (!profiles[profile]) {
  throw new Error(`PROFILE inválido: ${profile}. Use smoke ou load.`);
}

if (!Number.isInteger(quantity) || quantity <= 0) {
  throw new Error(`QUANTITY deve ser um inteiro positivo; recebido: ${__ENV.QUANTITY}`);
}

export const options = {
  scenarios: {
    pricing: {
      ...profiles[profile],
      gracefulStop: '10s',
      tags: {
        service: 'pricing',
        profile,
      },
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    dropped_iterations: ['count==0'],
  },
  userAgent: 'orderlab-k6/1.0',
};

export function setup() {
  const health = http.get(`${baseUrl}/actuator/health`, {
    tags: { name: 'GET /actuator/health' },
    timeout: '3s',
  });

  const healthy = check(health, {
    'pricing está disponível antes da carga': (response) => response.status === 200,
  });

  if (!healthy) {
    throw new Error(`Pricing indisponível em ${baseUrl}; status recebido: ${health.status}`);
  }

  return { url: `${baseUrl}/prices/${encodeURIComponent(productId)}?quantity=${quantity}` };
}

export default function (data) {
  const response = http.get(data.url, {
    headers: {
      Accept: 'application/json',
      'X-Load-Test': `k6-${profile}`,
    },
    tags: { name: 'GET /prices/{productId}' },
    timeout: '3s',
  });

  let body;
  try {
    body = response.json();
  } catch (_) {
    body = null;
  }

  check(response, {
    'status é 200': (result) => result.status === 200,
    'resposta é JSON': (result) =>
      (result.headers['Content-Type'] || '').includes('application/json'),
    'produto retornado é o solicitado': () => body?.productId === productId,
    'quantidade retornada é a solicitada': () => body?.quantity === quantity,
    'total calculado é positivo': () => Number(body?.total) > 0,
  });
}
