import http from 'k6/http';
import { check, sleep } from 'k6';

// Configuration
const BASE_URL = 'http://localhost:8080';
const VENDOR_SLUG = 'test-vendor';

export let options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '2m', target: 50 },    // Stay at 50 users
    { duration: '30s', target: 0 },    // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    http_req_failed: ['rate<0.05'],   // Error rate should be below 5%
  },
};

export default function () {
  // Test 1: Create order
  const orderPayload = JSON.stringify({
    vendorSlug: VENDOR_SLUG,
    amount: Math.floor(Math.random() * 1000) + 10,
    customerPhone: `9${Math.floor(Math.random() * 1000000000)}`,
  });

  const orderParams = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const orderRes = http.post(`${BASE_URL}/api/orders/create`, orderPayload, orderParams);
  
  check(orderRes, {
    'order creation status 200': (r) => r.status === 200,
    'order creation has orderCode': (r) => JSON.parse(r.body).orderCode !== undefined,
  });

  const orderCode = JSON.parse(orderRes.body).orderCode;

  // Test 2: Check order status
  sleep(1); // Wait 1 second between requests
  
  const statusRes = http.get(`${BASE_URL}/api/orders/${orderCode}/status`);
  
  check(statusRes, {
    'status check status 200': (r) => r.status === 200,
    'status check has status': (r) => JSON.parse(r.body).status !== undefined,
  });

  // Test 3: Simulate payment (webhook simulation)
  sleep(2);
  
  const webhookPayload = JSON.stringify({
    event_id: `evt_${__VU}_${Date.now()}`,
    order_code: orderCode,
    amount: 100,
    status: 'success',
    gateway_ref: `ref_${__VU}_${Date.now()}`,
  });

  const webhookRes = http.post(`${BASE_URL}/api/webhook/simulate-payment`, webhookPayload, orderParams);
  
  check(webhookRes, {
    'webhook simulation status 200': (r) => r.status === 200,
  });

  sleep(Math.random() * 2); // Random sleep between iterations
}

export function handleSummary(data) {
  // Custom summary output
  console.log('\n=== Load Test Summary ===');
  console.log(`Total requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Failed requests: ${data.metrics.http_req_failed.values.count}`);
  console.log(`Failure rate: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log(`P95 latency: ${data.metrics.http_req_duration.values['p(95)']}ms`);
  console.log(`P99 latency: ${data.metrics.http_req_duration.values['p(99)']}ms`);
  console.log('========================\n');
}
