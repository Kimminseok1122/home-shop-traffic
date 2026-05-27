import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 0 },
      ],
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const userId = Math.floor(Math.random() * 1000000) + 1;

  const enterRes = http.post(
    `${BASE_URL}/api/live/1/enter`,
    JSON.stringify({ userId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(enterRes, {
    'entered waiting room': (r) => r.status === 200,
  });

  const token = enterRes.json('data.waitingToken');

  const orderRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      broadcastId: 1,
      productId: 1,
      userId,
      quantity: 1,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
        'X-Waiting-Token': token,
        'Idempotency-Key': `${userId}-${Date.now()}`,
      },
    }
  );

  check(orderRes, {
    'order accepted or controlled': (r) => [202, 409, 429].includes(r.status),
  });

  sleep(1);
}
