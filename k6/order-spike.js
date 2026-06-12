import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter} from 'k6/metrics';

// VU 0 -> 200 까지 상승시켰을때
// 입장 API와 주문 API가 밀리진 않는지, 서버 500 에러가 없는지, 재고 소진 409가 섞이진 않는지 backpressure 429는 범위내로 나오는지 체크
http.setResponseCallback(http.expectedStatuses(200, 202, 409));

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '5s', target: 50},
                {duration: '5s', target: 200},
                {duration: '20s', target: 200},
                {duration: '10s', target: 0},
            ],
            gracefulRampDown: '5s',
            gracefulStop: '10s',
            tags: {
                test_type: 'order_spike'
            }
        }
    },
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        'http_req_duration{api:enter}': ['p(95)<500'],
        'http_req_duration{api:order}': ['p(95)<1000'],
        order_5xx: ['count==0'],
        order_unexpected: ['count==0']
    }
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BROADCAST_ID = Number(__ENV.BROADCAST_ID || 1);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const STOCK = Number(__ENV.STOCK || 1000000);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

const enter_200 = new Counter('enter_200');
const enter_failed = new Counter('enter_failed');

const order_202 = new Counter('order_202');
const enter_409 = new Counter('enter_409');
const order_429 = new Counter('order_429');
const order_5xx = new Counter('order_5xx');
const order_unexpected = new Counter('order_unexpected');

export function setup() {
    const resetRes = http.post(`${BASE_URL}/api/admin/products/${PRODUCT_ID}/stock?stock=${STOCK}`);

    check(resetRes, {
        'stock reset success': (r) => r.status === 200
    });
}

export default function () {

    const userId = Math.floor(Math.random() * 1000000000) + 1;

    const enterRes = http.post(
        `${BASE_URL}/api/live/${BROADCAST_ID}/enter`,
        JSON.stringify({
            userId
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            },
            tags: {
                api: 'enter'
            }
        }
    );

    const entered = check(enterRes, {
        'enter success 200': (r) => r.status === 200,
        'enter json success ': (r) => r.json('success') === true,
        'waiting token is existed': (r) => {
            const token = r.json('data.waitingToken');
            return typeof token === 'string' && token.length > 0;
        }
    });

    if(!entered){
        enter_failed.add(1);
        sleep(SLEEP_SECONDS);
        return;
    }

    enter_200.add(1);

    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify(
            {
                broadcastId: BROADCAST_ID,
                userId,
                productId: PRODUCT_ID,
                quantity: 1
            }
        ),
        {
            headers:{
                'Content-Type': 'application/json',
                'X-Waiting-Token': enterRes.json('data.waitingToken'),
                'Idempotency-Key': `${userId}-${Date.now()}-${Math.random()}`
            },
            tags:{
                api: 'order'
            }
        }
    );

    if(orderRes.status === 202){
        order_202.add(1);
    } else if(orderRes.status === 409){
        enter_409.add(1);
    } else if(orderRes.status === 429){
        order_429.add(1);
    } else if(orderRes.status >= 500){
        order_5xx.add(1);
    } else {
        order_unexpected.add(1);
    }

    check(orderRes, {
        'order accepted or rejected': (r) => [202, 409].includes(r.status),
    });

    sleep(SLEEP_SECONDS);
}



