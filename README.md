# 수강 신청 시스템 (BE-A)

## 프로젝트 개요

크리에이터(강사)가 강의를 개설하고, 클래스메이트(수강생)가 신청/결제/취소하는 수강 신청 시스템입니다.


### 도메인 모델

```
courses 1 ──── * enrollments
courses 1 ──── * waitlists
```

| 테이블 | 주요 필드 |
|---|---|
| `courses` | id, creator_id, title, capacity, status(DRAFT/OPEN/CLOSED), enrolled_count |
| `enrollments` | id, user_id, course_id, status(PENDING/CONFIRMED/CANCELLED), confirmed_at |
| `waitlists` | id, user_id, course_id, created_at |

### DB 스키마

```sql
CREATE TABLE courses (
    id             BIGSERIAL PRIMARY KEY,
    creator_id     BIGINT         NOT NULL,
    title          VARCHAR(255)   NOT NULL,
    description    TEXT,
    price          NUMERIC        NOT NULL,
    capacity       INT            NOT NULL,
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    enrolled_count INT            NOT NULL DEFAULT 0
);

CREATE TABLE enrollments (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    course_id    BIGINT       NOT NULL REFERENCES courses(id),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL
);

CREATE TABLE waitlists (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    course_id  BIGINT    NOT NULL REFERENCES courses(id),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (course_id, user_id)
);
```

---

## 기술 스택

- **Language**: Java 17
- **Framework**: Spring Boot 3.5.14
- **ORM**: Spring Data JPA (Hibernate)
- **DB**: PostgreSQL 16
- **Build**: Gradle
- **Test**: JUnit 5, Mockito, Testcontainers

---

## 실행 방법

### 사전 요구사항
- Docker

### 애플리케이션 실행 (Docker)

PostgreSQL + Spring Boot 앱을 한 번에 실행합니다.

실행 위치 : 루트 디렉토리

```bash
docker-compose up --build
```

`http://localhost:8080` 으로 접근할 수 있습니다.

### 테스트 실행

Testcontainers가 PostgreSQL 컨테이너를 자동으로 실행합니다. Docker와 Java 17이 필요합니다.

```bash
./gradlew test
```

### API 명세

#### 자세한 API 명세는 실행 후 Swagger UI에서 확인할 수 있습니다: `http://localhost:8080/swagger-ui.html`

대부분의 요청에 `X-User-Id: {userId}` 헤더가 필요합니다.

단, `GET /api/courses`, `GET /api/courses/{id}`는 헤더 없이 호출 가능합니다.

**응답 포맷**

```json
// 성공
{ "data": { ... }, "error": null }

// 실패
{ "data": null, "error": { "code": "에러코드", "message": "설명" } }
```

**강의 API**

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| POST | /api/courses | 강의 등록 | 누구나 |
| GET | /api/courses | 강의 목록 조회 (?status=OPEN) | 누구나 |
| GET | /api/courses/{id} | 강의 상세 조회 | 누구나 |
| PATCH | /api/courses/{id}/status | 강의 상태 변경 | 크리에이터 본인 |

**수강 신청 API**

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| POST | /api/enrollments | 수강 신청 (자리 있을 때) | 수강생 (크리에이터 본인 제외) |
| POST | /api/enrollments/{id}/confirm | 결제 확정 | 신청자 본인 |
| POST | /api/enrollments/{id}/cancel | 수강 취소 | 신청자 본인 |
| GET | /api/enrollments/me | 내 수강 신청 목록 | 본인 |
| GET | /api/courses/{id}/enrollments | 수강생 목록 | 크리에이터 본인 |

**대기열 API**

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| POST | /api/courses/{id}/waitlist | 대기열 등록 (자리 없을 때) | 수강생 |
| GET | /api/courses/{id}/waitlist/me | 내 대기 순번 조회 | 본인 |
| DELETE | /api/courses/{id}/waitlist/me | 대기열 이탈 | 본인 |

**대기열 흐름**

```
[일반 수강 신청]

1. POST /api/enrollments
   ├── 자리 있음 (CONFIRMED + PENDING < capacity) → enrollment 생성 (status: PENDING)
   └── 자리 없음 → 409 COURSE_FULL

2. POST /api/enrollments/{id}/confirm
   └── PENDING → CONFIRMED, enrolledCount 증가


[정원 초과 시 대기열 등록]

1. POST /api/courses/{id}/waitlist
   ├── 자리 있으면 → 400 COURSE_NOT_FULL (수강 신청 API 사용 안내)
   └── 자리 없으면 → waitlists 테이블에 등록, 현재 대기 순번 반환

2. GET /api/courses/{id}/waitlist/me
   └── 현재 대기 순번 확인 (created_at ASC 기준)

3. 다른 수강생이 취소하면 → waitlists에서 1번(가장 오래된) 자동 PENDING 승격
   └── ※ 알림 미구현: GET /api/enrollments/me 폴링으로 PENDING 여부 직접 확인

4. PENDING 확인 후 → POST /api/enrollments/{id}/confirm 으로 결제 확정


[취소]

- PENDING 취소: POST /api/enrollments/{id}/cancel → CANCELLED, waitlists 1번 PENDING 승격
- CONFIRMED 취소: 취소 가능 기간(confirmedAt 기준 7일) 이내일 때만 허용
                  → CANCELLED, enrolledCount 감소, waitlists 1번 PENDING 승격
```

**에러 코드**

| 코드 | HTTP | 설명 |
|---|---|---|
| `COURSE_NOT_FOUND` | 404 | 강의를 찾을 수 없음 |
| `ENROLLMENT_NOT_FOUND` | 404 | 수강 신청을 찾을 수 없음 |
| `COURSE_NOT_OPEN` | 400 | OPEN 상태가 아닌 강의에 신청 |
| `COURSE_FULL` | 409 | 정원 초과 |
| `ALREADY_ENROLLED` | 409 | 중복 신청 |
| `INVALID_STATUS_TRANSITION` | 400 | 잘못된 상태 전이 |
| `CANCEL_PERIOD_EXPIRED` | 409 | 취소 가능 기간(7일) 초과 |
| `CREATOR_CANNOT_ENROLL` | 400 | 크리에이터가 본인 강의에 신청 |
| `UNAUTHORIZED` | 403 | 권한 없음 |
| `MISSING_HEADER` | 400 | X-User-Id 헤더 누락 |
| `INVALID_INPUT` | 400 | 입력값 유효성 오류 |

---

## 요구사항 해석 및 가정

| 항목 | 해석 / 가정 |
|---|---|
| 도메인 클래스명 | `Class`는 Java 예약어이므로 `Course`로 명명, API path는 `/api/courses` 유지 |
| 정원 집계 기준 | `enrolledCount`는 CONFIRMED 기준으로만 증가. 단, 신규 신청 시에는 CONFIRMED + PENDING < capacity 여야 수락하여 중복 신청으로 인한 오버부킹을 방지 |
| 사용자 식별 | 별도 인증 서버 없이 `X-User-Id` 헤더 값을 신뢰하여 사용자 식별. 크리에이터/수강생 역할 구분 없이 컨텍스트(강의 creatorId와 요청자 비교)로 권한 판단 |
| 결제 처리 | 외부 PG 연동 없이 `POST /enrollments/{id}/confirm` 호출 자체를 결제 완료로 간주 |
| 크리에이터 수강 신청 | 요구사항에 명시되지 않았으나, 자신이 개설한 강의에 본인이 수강 신청하는 것은 비정상적 흐름으로 판단하여 차단 (`CREATOR_CANNOT_ENROLL`) |
| users 테이블 | 별도 사용자 테이블 없음. userId는 헤더에서 전달되는 Long 값으로만 관리 |
| 대기열 승격 방식 | 취소 발생 시 대기열 1번을 동일 트랜잭션 내에서 자동으로 PENDING 승격. 알림 시스템 미구현으로 승격 사실은 `GET /api/enrollments/me` 폴링으로 확인 |

---

## 설계 결정과 이유

### 정원 관리 기준
`enrolledCount`는 CONFIRMED 기준으로만 증가합니다. 단, 신규 신청 시에는 CONFIRMED + PENDING < capacity 조건으로 수락 여부를 판단합니다.

CONFIRMED만 집계하는 이유: 결제 없이 신청만 한 PENDING이 자리를 영구 독점하지 않도록, 먼저 결제(confirm)한 순서대로 정원이 확정됩니다.

### 동시성 제어 — Pessimistic Lock
enroll, confirm, cancel, joinWaitlist 시점에 `SELECT FOR UPDATE`로 Course 행에 락을 획득합니다.
수강 신청은 마지막 자리를 두고 다수가 동시에 경합하는 구조이기 때문에, 낙관적 락(재시도) 대신 비관적 락(직렬화)을 선택해 데이터 정합성을 보장합니다.

**낙관적 락을 선택하지 않은 이유**

낙관적 락은 충돌 시 예외 후 재시도 방식입니다. 충돌이 드문 상황에서는 유리하지만, 수강 신청처럼 마지막 자리에 수백 명이 동시에 경합하는 구조에서는 재시도가 폭증해 오히려 성능이 나빠집니다.

**트레이드오프**

비관적 락은 정합성을 보장하는 대신 confirm 처리가 직렬화됩니다. 동시 요청이 많을수록 대기 시간이 늘어나고, 대기 중인 요청도 DB 커넥션을 점유하기 때문에 트래픽이 극단적으로 몰리는 경우 커넥션 풀 고갈로 이어질 수 있습니다. 이 경우 confirm 요청을 큐에 적재하고 워커가 순차 처리하는 방식으로 전환하면 커넥션 낭비 없이 더 많은 요청을 수용할 수 있습니다. 다만 클라이언트에게 즉시 성공/실패 대신 비동기 응답을 제공해야 하는 UX 변화가 따릅니다.

### 대기열(Waitlist)
정원이 초과된 강의에 신청하면 별도 `waitlists` 테이블에 등록됩니다. CONFIRMED/PENDING 수강생이 취소하면 `waitlists`에서 `created_at ASC` 기준 가장 오래된 대기자를 꺼내 자동으로 PENDING 승격합니다. 승격된 사용자가 `GET /api/enrollments/me`에서 PENDING 상태를 확인한 뒤 confirm API를 호출해 수강을 확정합니다.

Enrollment의 PENDING이 "결제 대기"와 "대기열 줄서기"를 동시에 의미하는 혼재를 방지하기 위해 waitlists 테이블을 분리했습니다. 별도 인프라 없이 DB 기반으로 순서를 관리하며, `(course_id, user_id)` UNIQUE 제약으로 중복 등록을 방지합니다.

### 비즈니스 로직 위치
상태 전이 규칙과 검증 로직을 서비스가 아닌 엔티티(`Course`, `Enrollment`) 안에 위치시켰습니다.

엔티티가 자신의 규칙을 알고 있어, 규칙 변경 시 엔티티만 수정하면 됩니다.

```java
// 서비스는 단순히 지시만
enrollment.cancel();

// 엔티티가 규칙을 알고 검증 + 상태 변경
public void cancel() {
    if (this.status == CANCELLED) throw new BusinessException(INVALID_STATUS_TRANSITION);
    if (this.status == CONFIRMED) validateCancelPeriod();
    this.status = CANCELLED;
    this.cancelledAt = LocalDateTime.now();
}
```

### 테스트 전략

| 종류 | 대상 | 도구 |
|---|---|---|
| 단위 테스트 | 엔티티 도메인 로직 | JUnit 5 |
| 단위 테스트 | 서비스 레이어 | JUnit 5, Mockito |
| 통합 테스트 | 전체 API 흐름 | MockMvc, Testcontainers |
| 동시성 테스트 | enroll 동시 요청 | CountDownLatch, Testcontainers |

H2 대신 Testcontainers(PostgreSQL)를 사용한 이유: H2는 Pessimistic Lock 동작이 PostgreSQL과 달라 동시성 테스트의 신뢰도가 낮아지기 때문입니다.

enroll 및 confirm 시점 모두 `SELECT FOR UPDATE`로 Course 행에 락을 획득합니다. 동시에 수백 명이 마지막 자리에 요청하더라도 DB가 요청을 하나씩 순서대로 처리합니다. 락을 획득한 트랜잭션이 정원을 확인하고 처리한 뒤 커밋하면, 그 다음 트랜잭션이 락을 획득해 다시 정원을 확인합니다. 이 구조 덕분에 정원을 초과하는 신청·확정은 발생하지 않습니다.

---

## 미구현 / 제약사항

### 동시성 제어 — 다중 서버 환경 한계

현재 비관적 락(`SELECT FOR UPDATE`)은 DB 레벨에서 동작하므로 단일 서버 환경에서는 정합성을 보장합니다. 그러나 서버 대수가 늘어날수록 DB 커넥션을 점유한 채 대기하는 요청이 증가해 커넥션 풀 고갈로 이어질 수 있습니다.

**개선 방향**: 

Redis 분산 락(Redisson 등)을 도입하면 DB 부하 없이 락을 관리할 수 있습니다. 다만 `@Transactional`과 분산 락을 함께 사용할 경우 락 해제 시점이 트랜잭션 커밋보다 앞서는 문제가 발생할 수 있어, AOP 순서를 락이 트랜잭션을 완전히 포함하도록 조정해야 합니다. 현재 단계에서는 단일 서버를 전제로 비관적 락으로 충분하다고 판단해 오버 엔지니어링을 지양했습니다.

### 대기열 — DB 기반 순서 관리의 한계

현재 대기 순서는 `waitlists` 테이블의 `created_at ASC` 정렬로 관리합니다. 별도 인프라 없이 구현이 단순하다는 장점이 있지만, 대기자 조회나 순번 계산마다 DB I/O가 발생합니다. 인메모리 자료구조가 접근 속도 면에서 빠르지만, JVM 힙에 올리면 서버 재시작 시 데이터가 소멸하고 다중 서버 환경에서는 서버마다 큐 상태가 달라지는 문제가 생깁니다.

**개선 방향**: 

Redis `LPUSH / BRPOP`을 활용하면 인메모리 속도를 유지하면서 분산 환경에서도 단일 큐를 공유할 수 있고, O(1)로 대기자를 처리할 수 있습니다.

### 대기열 — 알림 및 선착순 보장

취소 발생 시 대기열 1번이 자동으로 PENDING 승격되지만, 승격 사실을 알리는 기능이 없습니다. 승격된 사용자는 `GET /api/enrollments/me`를 직접 폴링해야만 본인이 승격됐음을 알 수 있습니다. 선착순 자체는 보장됩니다(자동 승격은 `created_at ASC` 기준이며, confirm은 본인 enrollment만 가능). 단, 승격된 사용자가 confirm을 하지 않으면 해당 자리는 PENDING 상태로 남고 다음 대기자에게 넘어가지 않습니다.

**개선 방향**: 

취소 이벤트 발생 시 `ApplicationEvent` 또는 Kafka로 이벤트를 발행하고 별도 리스너가 알림을 처리합니다. 응답 기한(예: 24시간) 초과 시 PENDING을 자동 취소하고 다음 대기자에게 기회를 넘기는 스케줄러(`@Scheduled`)가 필요합니다.

### 결제 시스템

실제 PG(Payment Gateway) 연동 없음. `confirm` API 호출 자체를 결제 완료로 간주합니다.

### 인증 / 인가

JWT, OAuth 등 실제 인증 메커니즘 없음. `X-User-Id` 헤더를 신뢰하는 단순 구조로, 프로덕션 환경에서는 API 게이트웨이 또는 인터셉터 레벨의 인증이 필요합니다.

### 기타

- **강의 삭제**: DRAFT 상태 강의의 삭제 또는 소프트 딜리트 API 미구현
- **사용자 관리**: users 테이블 없음. 크리에이터/수강생 역할 구분 없이 `X-User-Id` 헤더 값으로만 처리

---

## AI 활용 범위

Claude Code를 활용하여 개발을 진행했습니다.

- **설계 논의**: 도메인 모델, 정원 관리 기준, 동시성 제어 전략(비관적 락 vs 낙관적 락 vs 큐) 등 설계 결정 시 트레이드오프를 함께 검토
- **코드 구현**: 엔티티, 서비스, 컨트롤러, 테스트 코드 작성
- **코드 리뷰**: 비즈니스 로직 누락, 엣지 케이스, 테스트 커버리지 검토
- **버그 수정**: Testcontainers 컨텍스트 캐싱 문제, 단위 테스트 Mock 불일치 등

각 설계 결정은 AI가 제시한 옵션을 바탕으로 직접 판단하고 선택했습니다. 코드 역시 동작 원리를 이해하고 검증한 후 채택했습니다.
