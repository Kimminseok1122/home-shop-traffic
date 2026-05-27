import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 1,
    duration: '10s',
};

export default function () {
    const res = http.get('http://localhost:8080/api/products/1');

    check(res, {
        'status is 200': (r) => r.status === 200,
        'success is true': (r) => r.json('success') === true,
    });

    sleep(1);
}