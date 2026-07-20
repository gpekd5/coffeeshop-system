import http from 'k6/http';
import { check, fail, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const vus = Number(__ENV.VUS || '10');
const duration = __ENV.DURATION || '1m';
const userCount = Number(__ENV.USER_COUNT || String(vus));
const menuId = Number(__ENV.MENU_ID || '9101');
const quantity = Number(__ENV.ORDER_QUANTITY || '1');
const pointAmount = Number(__ENV.POINT_AMOUNT || '1000000');
const password = __ENV.PERF_USER_PASSWORD || 'Password123!';
const runId = __ENV.RUN_ID || `${Date.now()}`;

export const options = {
  vus,
  duration,
  thresholds: {
    order_api_failed: ['rate<0.01'],
    order_api_duration: ['p(95)<2000'],
  },
};

const orderApiDuration = new Trend('order_api_duration', true);
const orderApiFailed = new Rate('order_api_failed');

export function setup() {
  const users = [];

  for (let i = 1; i <= userCount; i += 1) {
    const email = `perf-${runId}-${i}@example.com`;

    postJson('/api/v1/auth/signup', {
      email,
      password,
      name: `성능테스트사용자${i}`,
    });

    const loginResponse = postJson('/api/v1/auth/login', {
      email,
      password,
    });
    const accessToken = loginResponse.json('data.accessToken');

    if (!accessToken) {
      fail(`로그인 응답에서 accessToken을 찾을 수 없습니다. email=${email}`);
    }

    postJson(
      '/api/v1/points/charge',
      { amount: pointAmount },
      accessToken
    );

    users.push({
      email,
      accessToken,
    });
  }

  return { users };
}

export default function (data) {
  const user = data.users[(__VU - 1) % data.users.length];

  group('prepare cart', () => {
    http.del(`${baseUrl}/api/v1/cart/items`, null, authParams(user.accessToken));
    postJson(
      '/api/v1/cart/items',
      {
        menuId,
        quantity,
      },
      user.accessToken
    );
  });

  const orderResponse = http.post(
    `${baseUrl}/api/v1/orders`,
    null,
    {
      headers: {
        Authorization: `Bearer ${user.accessToken}`,
        'Idempotency-Key': uuidV4(),
      },
    }
  );
  const orderSucceeded = check(orderResponse, {
    'order status is 200': (response) => response.status === 200,
    'order response success true': (response) => response.json('success') === true,
  });

  orderApiDuration.add(orderResponse.timings.duration);
  orderApiFailed.add(!orderSucceeded);
}

function postJson(path, body, accessToken = null) {
  const response = http.post(
    `${baseUrl}${path}`,
    JSON.stringify(body),
    jsonParams(accessToken)
  );

  if (response.status >= 400) {
    fail(
      `요청이 실패했습니다. path=${path}, status=${response.status}, body=${response.body}`
    );
  }

  return response;
}

function jsonParams(accessToken = null) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  return { headers };
}

function authParams(accessToken) {
  return {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  };
}

function uuidV4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16);
    const value = char === 'x' ? random : (random & 0x3) | 0x8;

    return value.toString(16);
  });
}
