import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080/api/v1';
const rates = (__ENV.CATALOG_RATES || '10,25,50,100').split(',').map(Number);
const seconds = Number(__ENV.CATALOG_STAGE_SECONDS || 60);
const errors = new Rate('error_rate');
const keywords = ['kem', 'serum', 'son', 'duong', 'toner'];

if (![...rates, seconds].every((n) => Number.isSafeInteger(n) && n > 0)) {
    throw new Error('Catalog rates and stage duration must be positive integers');
}

const warmupSeconds = 10;
const drainSeconds = 6;
const scenarios = {};
const thresholds = {};
function addStage(name, rate, duration, startTime) {
    scenarios[name] = {
        executor: 'constant-arrival-rate',
        rate,
        timeUnit: '1s',
        duration: duration + 's',
        startTime: startTime + 's',
        gracefulStop: '5s',
        preAllocatedVUs: Math.max(5, Math.ceil(rate / 2)),
        maxVUs: Math.max(10, rate * 2),
    };
}

addStage('warmup', Math.min(5, rates[0]), warmupSeconds, 0);
for (const [index, rate] of rates.entries()) {
    const name = 'catalog_' + (index + 1);
    addStage(name, rate, seconds, warmupSeconds + drainSeconds + index * (seconds + drainSeconds));
    thresholds['http_req_duration{scenario:' + name + '}'] = ['p(95)<300', 'p(99)<500'];
    thresholds['error_rate{scenario:' + name + '}'] = ['rate<0.01'];
    thresholds['dropped_iterations{scenario:' + name + '}'] = ['count==0'];
}

export const options = { scenarios, thresholds, maxRedirects: 0 };

function responseData(response) {
    try {
        return response.json('data');
    } catch {
        return null;
    }
}

export function setup() {
    const list = http.get(baseUrl + '/products?page=0&size=30&sort=createdAt,desc');
    const products = responseData(list)?.content;
    if (list.status !== 200 || !Array.isArray(products) || !products.some((product) => product.id != null)) {
        throw new Error('Populate the catalog and verify BASE_URL before starting the benchmark');
    }
    return products.filter((product) => product.id != null).map((product) => product.id);
}

export default function (productIds) {
    const iteration = exec.scenario.iterationInTest;
    // One request per iteration, split 50% list, 30% search and 20% detail.
    const bucket = iteration % 10;
    let endpoint;
    let path;
    if (bucket < 5) {
        endpoint = 'list';
        path = '/products?page=0&size=10&sort=createdAt,desc';
    } else if (bucket < 8) {
        endpoint = 'search';
        const keyword = keywords[(Math.floor(iteration / 10) * 3 + bucket - 5) % keywords.length];
        path = '/products/search?keyword=' + encodeURIComponent(keyword) + '&page=0&size=10';
    } else {
        endpoint = 'detail';
        const detailIndex = Math.floor(iteration / 10) * 2 + bucket - 8;
        path = '/products/' + productIds[detailIndex % productIds.length];
    }

    const response = http.get(baseUrl + path, {
        tags: { name: 'catalog_' + endpoint, endpoint },
        timeout: '5s',
    });
    const data = responseData(response);
    const success = check(response, {
        'catalog response has expected data': (r) => r.status === 200 &&
            (endpoint === 'detail' ? data != null && String(data.id) === path.split('/').pop() :
                Array.isArray(data?.content)),
    });
    errors.add(!success, { endpoint });
}
