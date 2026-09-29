import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const checkoutErrorRate = new Rate('checkout_error_rate');
const optimisticLockRate = new Rate('optimistic_lock_rate');

export const options = {
    stages: [
        { duration: '15s', target: 10 },  // Warm up
        { duration: '30s', target: 30 },  // Moderate concurrency
        { duration: '45s', target: 60 },  // High concurrency (contention on HikariCP pool and stock rows)
        { duration: '15s', target: 0 },   // Cool down
    ],
    thresholds: {
        http_req_duration: ['p(95)<1500'], // 95% of write requests under 1.5s under heavy lock contention
        checkout_error_rate: ['rate<0.05'], // Max 5% 5xx server errors
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

// Generate a random UUID-like string
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

export default function () {
    const sessionId = `k6-session-${__VU}-${generateUUID().substring(0, 8)}`;
    const idempotencyKey = `k6-key-${generateUUID()}`;

    // Step 1: Add variant to cart (Variant ID 1)
    const addPayload = JSON.stringify({
        variantId: 1,
        quantity: 1,
        sessionId: sessionId,
    });

    const addRes = http.post(`${BASE_URL}/cart/items`, addPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    const addSuccess = check(addRes, {
        'add to cart success or out of stock': (r) => r.status === 200 || r.status === 400,
    });

    if (addRes.status !== 200) {
        sleep(0.5);
        return;
    }

    sleep(0.2);

    // Step 2: Concurrently Checkout with Idempotency-Key
    const checkoutPayload = JSON.stringify({
        sessionId: sessionId,
        customerName: `K6 User ${__VU}`,
        customerPhone: '0912345678',
        shippingAddress: '123 Đường Test, Quận 1',
        city: 'TP. Hồ Chí Minh',
        district: 'Quận 1',
        ward: 'Phường Bến Nghé',
        paymentMethod: 'BANK',
        idempotencyKey: idempotencyKey,
        notes: 'K6 Stress Test Order',
    });

    const checkoutRes = http.post(`${BASE_URL}/orders/checkout`, checkoutPayload, {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
    });

    const checkoutSuccess = check(checkoutRes, {
        'checkout status is 200 or business handled': (r) => {
            // 200: Order created successfully
            // 400: Out of stock (legitimate concurrency rejection)
            // 409: Conflict retry handled
            return r.status === 200 || r.status === 400 || r.status === 409;
        },
        'not internal 500 server error': (r) => r.status !== 500,
    });

    checkoutErrorRate.add(!checkoutSuccess);
    if (checkoutRes.status === 409) {
        optimisticLockRate.add(1);
    }

    sleep(0.5);
}
