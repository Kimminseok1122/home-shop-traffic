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

## 10. 개선 과정

```text
1. 현재 코드로 몇명까지 버틸 수 있을지 부하테스트 실행
 - (1)
```