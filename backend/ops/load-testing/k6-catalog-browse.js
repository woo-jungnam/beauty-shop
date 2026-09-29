import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('error_rate');

// Test configuration: Simulating ramp-up traffic to measure cache efficiency and read throughput
export const options = {
    stages: [
        { duration: '30s', target: 20 },  // Ramp up to 20 virtual users
        { duration: '1m', target: 50 },   // Increase to 50 virtual users
        { duration: '1m', target: 100 },  // Peak load at 100 concurrent users
        { duration: '30s', target: 0 },   // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<300', 'p(99)<500'], // 95% of requests should be below 300ms
        error_rate: ['rate<0.01'],                      // Less than 1% errors
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export default function () {
    // 1. Get products list (paginated, cached in Redis)
    const listRes = http.get(`${BASE_URL}/products?page=0&size=10&sort=createdAt,desc`);
    const listSuccess = check(listRes, {
        'products list status is 200': (r) => r.status === 200,
        'has products content': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.content && Array.isArray(body.data.content);
            } catch (e) {
                return false;
            }
        },
    });
    errorRate.add(!listSuccess);

    sleep(1);

    // 2. Search products
    const searchKeywords = ['kem', 'serum', 'son', 'duong', 'toner'];
    const keyword = searchKeywords[Math.floor(Math.random() * searchKeywords.length)];
    const searchRes = http.get(`${BASE_URL}/products/search?keyword=${keyword}&page=0&size=10`);
    const searchSuccess = check(searchRes, {
        'search status is 200': (r) => r.status === 200,
    });
    errorRate.add(!searchSuccess);

    sleep(1);

    // 3. View detail of product with id 1
    const detailRes = http.get(`${BASE_URL}/products/1`);
    const detailSuccess = check(detailRes, {
        'product detail status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    errorRate.add(!detailSuccess);

    sleep(1);
}
