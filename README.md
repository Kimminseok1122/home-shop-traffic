# Home Shop Traffic - MySQL + Redis Version

면접용 홈쇼핑 트래픽 관리 토이 프로젝트입니다.

이번 버전은 기존 인메모리 저장소를 제거하고 아래처럼 바꿨습니다.

```text
MySQL
  - 상품 조회
  - 재고 차감
  - 주문 저장
  - Idempotency-Key 중복 주문 방지

Redis
  - 대기열 토큰
  - 주문 API 요청 제한
  - 주문 큐

Prometheus / Grafana
  - 주문 접수/확정/거절 수
  - 큐 크기
  - JVM / HTTP 지표
```

---

## 1. 실행 전 확인

Docker가 이미 떠 있으면 그대로 쓰면 됩니다.

```powershell
docker compose ps
```

없으면 프로젝트 루트에서 실행합니다.

```powershell
docker compose up -d
```

---

## 2. 앱 실행

```powershell
.\gradlew.bat bootRun
```

처음 실행하면 Gradle 9.1.0과 의존성을 다운로드합니다.

---

## 3. 확인 API

### Health

```powershell
curl http://localhost:8080/actuator/health
```

### 상품 조회

```powershell
curl http://localhost:8080/api/products/1
```

### 방송 입장 / 대기열 토큰 발급

```powershell
curl -X POST http://localhost:8080/api/live/1/enter `
  -H "Content-Type: application/json" `
  -d '{"userId":1001}'
```

응답의 `data.waitingToken` 값을 복사합니다.

### 주문 요청

```powershell
curl -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1001" `
  -H "X-Waiting-Token: 위에서_받은_토큰" `
  -H "Idempotency-Key: user-1001-order-1" `
  -d '{"broadcastId":1,"productId":1,"userId":1001,"quantity":1}'
```

### 주문 조회

```powershell
curl http://localhost:8080/api/orders/{orderId}
```

### 큐 상태

```powershell
curl http://localhost:8080/api/orders/queue
```

---

## 4. MySQL 확인

```powershell
docker exec -it home-shop-mysql mysql -uhome_shop -phome_shop home_shop
```

```sql
SHOW TABLES;
SELECT * FROM product;
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;
```

---

## 5. Redis 확인

```powershell
docker exec -it home-shop-redis redis-cli
```

```redis
KEYS *
LLEN queue:orders
GET waiting:live:1:seq
ZRANGE waiting:live:1:tokens 0 -1
```

---

## 6. Prometheus / Grafana

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:3000
ID/PW:      admin / admin
```

Prometheus target 확인:

```text
Status → Target health → home-shop-traffic UP
```

---

## 7. 부하 테스트

k6 설치 후:

```powershell
k6 run k6/order-spike.js
```

---

## 8. 면접 설명 포인트

### 재고 동시성

상품 재고 차감은 MySQL에서 조건부 update로 처리합니다.

```sql
UPDATE product
SET stock = stock - ?
WHERE id = ?
  AND stock >= ?;
```

affected row가 1이면 성공, 0이면 품절입니다.
동시 주문이 몰려도 재고가 음수가 되지 않습니다.

### 중복 주문 방지

`orders` 테이블에 `(user_id, idempotency_key)` 유니크 제약을 둡니다.
사용자가 버튼을 여러 번 누르거나 네트워크 재시도가 일어나도 같은 주문이 중복 생성되지 않습니다.

### Redis 대기열

대기열 토큰은 Redis Hash + TTL로 저장하고, 방송별 활성 토큰은 Sorted Set으로 관리합니다.
주문 시 Lua 스크립트로 토큰 검증과 삭제를 한 번에 처리해서 같은 토큰을 두 번 쓸 수 없게 했습니다.

### Redis 요청 제한

주문 API 앞단에서 `Redis INCR + EXPIRE` 방식으로 사용자/IP별 초당 요청 수를 제한합니다.
서버가 여러 대여도 Redis를 공유하므로 요청 제한 기준이 하나로 유지됩니다.

### Redis 주문 큐

주문 요청은 MySQL에 `ACCEPTED`로 저장하고 Redis List에 orderId를 넣습니다.
워커가 큐에서 orderId를 꺼내 주문을 `CONFIRMED`로 바꿉니다.
느린 후처리를 요청 응답과 분리해서 순간 트래픽을 버틸 수 있게 했습니다.

---

## 9. 주의

이 프로젝트는 면접용 토이 프로젝트입니다.
실무라면 다음을 추가하는 게 좋습니다.

```text
1. Flyway 또는 Liquibase로 스키마 관리
2. Redis 큐 대신 Kafka/RabbitMQ/SQS
3. Outbox Pattern
4. 주문 실패 재처리
5. 분산락 또는 더 정교한 재고 선점 정책
6. Grafana 대시보드 json provisioning
```


## Flyway migration

이 버전은 앱 시작 시 Flyway가 DB 스키마와 초기 데이터를 관리합니다.

- `src/main/resources/db/migration/V1__create_base_tables.sql`: 테이블 생성
- `src/main/resources/db/migration/V2__insert_seed_data.sql`: 상품/방송 초기 데이터

Docker Compose는 MySQL/Redis/Prometheus/Grafana 컨테이너를 띄우는 역할만 합니다.
DB 테이블 생성과 기본 데이터 입력은 Docker init SQL이 아니라 Flyway가 담당합니다.

기존 DB를 완전히 새로 만들고 싶으면:

```powershell
docker compose down -v
docker compose up -d
.\gradlew.bat bootRun
```

기존 DB를 유지한 채로 실행하면 `baseline-on-migrate: true`, `baseline-version: 0` 설정으로 기존 스키마에도 Flyway 이력을 붙이고 migration을 적용합니다.

---

## 10. 부하 테스트 절차

```text
1. 검증 목표
- 홈쇼핑 라이브 방송 중 주문 트래픽이 몰릴때 Redis / Mysql / Queue / Rate Limit 이 제대로 동작하는지 검증해본다
2. 검증 대상
 - 상품 조회가 정상인가?
 - 방송 입장 시 redis 대기열 토큰이 발급 되는가?
 - 대기열 토큰 없이 주문하면 막히는가?
 - 정상 토큰으로 주문하면 Mysql에 주문이 저장되고 재고가 줄어드는가?
 - 같은 주문을 반복해도 중복 주문이 막히는가?
 - 트래픽이 몰릴 때 요청 제한 / 품절 / 큐 처리가 정상 동작하는가?
3. 검증 절차
 (1) Smoke Test
    - 목적 : 앱이 가동되어있고, k6스크립트와 API 흐름이 정상인지 확인한다
    - 조건 : VU 1명 , 시간 10 ~ 30초
 (2) Normal Load Test
    - 목적 : 평상시 주문 트래픽에서는 안정적인지 확인
    - 조건 : VU 10 ~ 30명 , 시간 : 1 ~ 2분 , 재고 10000개 (현재 worker의 처리량은 초당 26건정도이기 때문)
 (3) Queue Capacity Test
    - 목적 : 주문 큐가 밀리는 상황을 의도적으로 만들고, Worker가 나중에 따라잡는지 확인
    - 조건 : VU 50명 , 시간 : 1분 , 재고 10000개
 (4) Spike Test
    - 목적 : 홈쇼핑 방송처럼 순간적으로 어떻게 반응하는지 확인
    - 조건 : 10초동안 VU가 50명까지 증가하고 20초동안 200명까지 증가 10초동안 0명으로 감소
 (5) Sold-out Test
    - 목적 : 재고보다 많은 주문이 들어와도 재고가 음수가 안 되는지 확인
    - 조건 : VU 200명 , 시간 30초  , 재고 100개
 (6)  Rate Limit Test
    - 목적 : 같은 사용자가 너무 많이 주문 요청하면 Redis rate limit이 막는지 확인
    - 조건 : userId 고정, VU 50명, 시간 20초
4. 실제 검증 결과
 (1) Smoke Test
  █ TOTAL RESULTS

    checks_total.......: 20      1.977038/s
    checks_succeeded...: 100.00% 20 out of 20
    checks_failed......: 0.00%   0 out of 20

    ✓ status is 200                                                                                                                                                                                                                                                                                                 
    ✓ success is true                                                                                                                                                                                                                                                                                               

    HTTP
    http_req_duration..............: avg=10.05ms min=8.6ms med=9.86ms max=14.12ms p(90)=10.51ms p(95)=12.31ms                                                                                                                                                                                                       
      { expected_response:true }...: avg=10.05ms min=8.6ms med=9.86ms max=14.12ms p(90)=10.51ms p(95)=12.31ms                                                                                                                                                                                                       
    http_req_failed................: 0.00%  0 out of 10
    http_reqs......................: 10     0.988519/s

    EXECUTION
    iteration_duration.............: avg=1.01s   min=1s    med=1.01s  max=1.02s   p(90)=1.01s   p(95)=1.01s                                                                                                                                                                                                         
    iterations.....................: 10     0.988519/s
    vus............................: 1      min=1       max=1
    vus_max........................: 1      min=1       max=1

    NETWORK
    data_received..................: 2.2 kB 220 B/s
    data_sent......................: 840 B  83 B/s
    
    - checks_total : 총 10번의 요청을 했고 요청마다 2개의 검사를 통과했으므로 20개의 검사가 성공
    - http_req_failed : HTTP 요청 10번중 실패 0번
    - http_req_duration : 응답시간 평균 10.05ms, 가장 빠른 요청 8.65ms, 가장 느린 요청 14.12ms, 95% 요청은 12.31ms 안에 끝남
    - http_reqs : 요청 수
    - iterations : default function이 한 번 실행된 횟수다
    - vu : 가상의 사용자 숫자 라는뜻
 (2) Spike Test 
```