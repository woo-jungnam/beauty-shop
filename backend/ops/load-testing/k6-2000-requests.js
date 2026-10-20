import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('error_rate');
const successCounter = new Counter('successful_requests');
const cachedRequestsDuration = new Trend('cached_requests_duration');
const searchRequestsDuration = new Trend('search_requests_duration');
const mediaRequestsDuration = new Trend('media_requests_duration');

// 2,000 exact iterations across 20 concurrent virtual users
export const options = {
    scenarios: {
        exact_2000_requests: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 2000,
            maxDuration: '3m',
        },
    },
    thresholds: {
        http_req_duration: ['p(90)<300', 'p(95)<500', 'p(99)<1000'],
        error_rate: ['rate<0.01'], // Tỷ lệ lỗi dưới 1%
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost/api/v1';
const HOST_URL = __ENV.HOST_URL || 'http://localhost';

const SEARCH_KEYWORDS = ['kem', 'serum', 'son', 'duong', 'toner', 'mat na', 'chong nang', 'tay trang'];

export default function () {
    // Mô phỏng 20 khách hàng độc lập từ Cloudflare với IP riêng biệt
    const clientHeaders = {
        'CF-Connecting-IP': `103.21.244.${(__VU % 250) + 1}`,
        'X-Forwarded-For': `103.21.244.${(__VU % 250) + 1}`,
        'User-Agent': `Mozilla/5.0 (k6-load-test-vu-${__VU})`,
    };

    const rand = Math.random();

    if (rand < 0.40) {
        // 1. Duyệt sản phẩm phân trang (Redis Cache)
        const page = Math.floor(Math.random() * 3);
        const res = http.get(`${BASE_URL}/products?page=${page}&size=10&sort=createdAt,desc`, {
            headers: clientHeaders,
            tags: { name: 'GetProductsList' },
        });
        const passed = check(res, {
            'status is 200': (r) => r.status === 200,
            'response has content': (r) => {
                try {
                    const json = JSON.parse(r.body);
                    return json.data && (json.data.content || Array.isArray(json.data));
                } catch (e) {
                    return false;
                }
            },
        });
        errorRate.add(!passed);
        if (passed) successCounter.add(1);
        cachedRequestsDuration.add(res.timings.duration);

    } else if (rand < 0.70) {
        // 2. Tìm kiếm sản phẩm bằng keyword
        const kw = SEARCH_KEYWORDS[Math.floor(Math.random() * SEARCH_KEYWORDS.length)];
        const res = http.get(`${BASE_URL}/products/search?keyword=${encodeURIComponent(kw)}&page=0&size=10`, {
            headers: clientHeaders,
            tags: { name: 'SearchProducts' },
        });
        const passed = check(res, {
            'status is 200': (r) => r.status === 200,
            'search returned json': (r) => r.status === 200 && r.body.length > 0,
        });
        errorRate.add(!passed);
        if (passed) successCounter.add(1);
        searchRequestsDuration.add(res.timings.duration);

    } else if (rand < 0.85) {
        // 3. Đọc danh mục, thương hiệu, policy hệ thống
        const subRand = Math.random();
        let endpoint = `${BASE_URL}/categories`;
        if (subRand < 0.33) endpoint = `${BASE_URL}/brands?size=20`;
        else if (subRand < 0.66) endpoint = `${BASE_URL}/system-configs/public`;

        const res = http.get(endpoint, {
            headers: clientHeaders,
            tags: { name: 'GetCatalogsAndConfigs' },
        });
        const passed = check(res, {
            'status is 200': (r) => r.status === 200,
        });
        errorRate.add(!passed);
        if (passed) successCounter.add(1);

    } else {
        // 4. Tải file tĩnh media qua Nginx (kiểm tra Zero-copy & Cache-Control headers)
        const res = http.get(`${HOST_URL}/uploads/nonexistent-cache-test.png`, {
            headers: clientHeaders,
            tags: { name: 'MediaUploadStatic' },
        });
        // 404 là hợp lệ vì file test không tồn tại nhưng Nginx xử lý trực tiếp
        const passed = check(res, {
            'nginx handled static route (200 or 404)': (r) => r.status === 200 || r.status === 404,
            'has security headers': (r) => r.headers['X-Content-Type-Options'] !== undefined || r.headers['Cache-Control'] !== undefined,
        });
        errorRate.add(!passed);
        if (passed) successCounter.add(1);
        mediaRequestsDuration.add(res.timings.duration);
    }

    // Pacing nhỏ giữa các thao tác (10-30ms) mô phỏng luồng người dùng lướt web liên tục
    sleep(0.01 + Math.random() * 0.02);
}
