import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses(200, 202, 409, 429));

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            stages: [
                { duration: '10s', target: 50 },
                { duration: '10s', target: 100 },
                { duration: '10s', target: 200 },
            ],
        },
    },
    thresholds: {
        checks: ['rate==1.0'],

        order_unexpected: ['count==0'],

        order_400_unknown: ['count==0'],
        order_409_unknown: ['count==0'],
        order_429_unknown: ['count==0'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BROADCAST_ID = Number(__ENV.BROADCAST_NUM || 1);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

const enterSuccess = new Counter('enter_success');
const enterFailed = new Counter('enter_failed');
const enterTokenMissing = new Counter('enter_token_missing');

const orderTotal = new Counter('order_total');
const order202 = new Counter('order_202');

const order400QuantityError = new Counter('order_400_quantity_error');
const order400RequiredError = new Counter('order_400_required_error');
const order400Unknown = new Counter('order_400_unknown');

const order409OutOfStock = new Counter('order_409_out_of_stock');
const order409Duplicate = new Counter('order_409_duplicate');
const order409Unknown = new Counter('order_409_unknown');

const order429QueueFull = new Counter('order_429_queue_full');
const order429RateLimit = new Counter('order_429_rate_limit');
const order429Unknown = new Counter('order_429_unknown');

const orderUnexpected = new Counter('order_unexpected');

function safeJson(res, path) {
    try {
        return res.json(path);
    } catch (e) {
        return undefined;
    }
}

function getErrorInfo(res) {
    const error = safeJson(res, 'error');
    const message = safeJson(res, 'message');

    return {
        error: error === undefined || error === null ? '' : String(error),
        message: message === undefined || message === null ? '' : String(message),
    };
}

function errorText(res) {
    const info = getErrorInfo(res);
    return `${info.error} ${info.message}`.toLowerCase();
}

function debugError(res) {
    if (__ENV.DEBUG_ERRORS === 'true') {
        console.log(`status=${res.status}, body=${res.body}`);
    }
}

function countOrderResult(orderRes) {
    orderTotal.add(1);

    if (orderRes.status === 202) {
        order202.add(1);
        return;
    }

    const text = errorText(orderRes);

    if (orderRes.status === 400) {
        if (text.includes('quantity')) {
            order400QuantityError.add(1);
        } else if (
            text.includes('broadcastid') ||
            text.includes('broadcast') ||
            text.includes('productid') ||
            text.includes('product') ||
            text.includes('userid') ||
            text.includes('user id') ||
            text.includes('required')
        ) {
            order400RequiredError.add(1);
        } else {
            order400Unknown.add(1);
            debugError(orderRes);
        }

        return;
    }

    if (orderRes.status === 409) {
        if (
            text.includes('sold out') ||
            text.includes('out of stock') ||
            text.includes('out_of_stock') ||
            text.includes('stock')
        ) {
            order409OutOfStock.add(1);
        } else if (
            text.includes('duplicate') ||
            text.includes('duplicated') ||
            text.includes('idempotency')
        ) {
            order409Duplicate.add(1);
        } else {
            order409Unknown.add(1);
            debugError(orderRes);
        }

        return;
    }

    if (orderRes.status === 429) {
        if (
            text.includes('queue') ||
            text.includes('queue is full')
        ) {
            order429QueueFull.add(1);
        } else if (
            text.includes('rate') ||
            text.includes('limit') ||
            text.includes('too many')
        ) {
            order429RateLimit.add(1);
        } else {
            order429Unknown.add(1);
            debugError(orderRes);
        }

        return;
    }

    orderUnexpected.add(1);
    debugError(orderRes);
}

export default function () {
    const userId = Math.floor(Math.random() * 1000000) + 1;

    const enterRes = http.post(
        `${BASE_URL}/api/live/${BROADCAST_ID}/enter`,
        JSON.stringify({ userId }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                api: 'enter',
            },
        }
    );

    const entered = check(enterRes, {
        'enter waiting room success': (r) => r.status === 200,
        'return success': (r) => safeJson(r, 'success') === true,
        'waiting token is not empty': (r) => {
            const token = safeJson(r, 'data.waitingToken');
            return typeof token === 'string' && token.length > 0;
        },
    });

    if (!entered) {
        enterFailed.add(1);

        const token = safeJson(enterRes, 'data.waitingToken');
        if (!token) {
            enterTokenMissing.add(1);
        }

        debugError(enterRes);
        sleep(SLEEP_SECONDS);
        return;
    }

    enterSuccess.add(1);

    const token = enterRes.json('data.waitingToken');

    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify({
            broadcastId: BROADCAST_ID,
            productId: PRODUCT_ID,
            userId,
            quantity: 1,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': String(userId),
                'X-Waiting-Token': token,
                'Idempotency-Key': `${userId}-${Date.now()}-${Math.random()}`,
            },
            tags: {
                api: 'order',
            },
        }
    );

    countOrderResult(orderRes);

    check(orderRes, {
        'order accepted or controlled': (r) => [202, 409, 429].includes(r.status),
    });

    sleep(SLEEP_SECONDS);
}