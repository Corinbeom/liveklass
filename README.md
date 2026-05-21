# 수강 신청 시스템 (BE-A)

## 프로젝트 개요

크리에이터(강사)가 강의를 개설하고, 클래스메이트(수강생)가 신청·결제·취소하는 수강 신청 시스템입니다.
핵심 관심사는 상태 전이, 정원 관리, 동시성 제어입니다.

### 도메인 모델

```
courses 1 ──── * enrollments
```

| 테이블 | 주요 필드 |
|---|---|
| `courses` | id, creator_id, title, capacity, status(DRAFT/OPEN/CLOSED), enrolled_count |
| `enrollments` | id, user_id, course_id, status(PENDING/CONFIRMED/CANCELLED), confirmed_at |

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

```bash
docker-compose up --build
```

`http://localhost:8080` 으로 접근할 수 있습니다.

Swagger UI: `http://localhost:8080/swagger-ui.html`

### 테스트 실행

Testcontainers가 PostgreSQL 컨테이너를 자동으로 실행합니다. Docker와 Java 17이 필요합니다.

```bash
./gradlew test
```

### API 명세

모든 요청에 `X-User-Id: {userId}` 헤더가 필요합니다.

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
| POST | /api/enrollments | 수강 신청 | 수강생 (크리에이터 본인 제외) |
| POST | /api/enrollments/{id}/confirm | 결제 확정 | 신청자 본인 |
| POST | /api/enrollments/{id}/cancel | 수강 취소 | 신청자 본인 |
| GET | /api/enrollments/me | 내 수강 신청 목록 | 본인 |
| GET | /api/courses/{id}/waitlist/me | 내 대기 순번 조회 | 본인 |
| GET | /api/courses/{id}/enrollments | 수강생 목록 | 크리에이터 본인 |

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
| 정원 집계 기준 | `enrolledCount`는 CONFIRMED 기준. PENDING은 자리를 점유하지 않음. 신청만 하고 결제하지 않은 사용자가 정원을 독점하는 상황을 방지하기 위함 |
| 사용자 식별 | 별도 인증 서버 없이 `X-User-Id` 헤더 값을 신뢰하여 사용자 식별. 크리에이터/수강생 역할 구분 없이 컨텍스트(강의 creatorId와 요청자 비교)로 권한 판단 |
| 결제 처리 | 외부 PG 연동 없이 `POST /enrollments/{id}/confirm` 호출 자체를 결제 완료로 간주 |
| 크리에이터 수강 신청 | 요구사항에 명시되지 않았으나, 자신이 개설한 강의에 본인이 수강 신청하는 것은 비정상적 흐름으로 판단하여 차단 (`CREATOR_CANNOT_ENROLL`) |
| users 테이블 | 별도 사용자 테이블 없음. userId는 헤더에서 전달되는 Long 값으로만 관리 |
| 대기열 통보 시점 | 취소 발생 시 자동 승격이 아닌, 알림 시스템을 통해 대기자에게 통보하는 방식으로 해석. 대기자가 직접 confirm 호출로 수강 확정 |

---

## 설계 결정과 이유

### 정원 관리 기준
PENDING은 정원을 점유하지 않고, CONFIRMED 기준으로 집계합니다.
결제 없이 신청만 한 사용자가 자리를 독점하는 것을 방지하기 위함입니다.

### 동시성 제어 — Pessimistic Lock
결제 확정(confirm) 시점에 `SELECT FOR UPDATE`로 Course 행에 락을 획득합니다.
수강 신청은 마지막 자리를 두고 다수가 동시에 경합하는 구조이기 때문에, 낙관적 락(재시도) 대신 비관적 락(직렬화)을 선택해 데이터 정합성을 보장합니다.

**낙관적 락을 선택하지 않은 이유**
낙관적 락은 충돌 시 예외 후 재시도 방식입니다. 충돌이 드문 상황에서는 유리하지만, 수강 신청처럼 마지막 자리에 수백 명이 동시에 경합하는 구조에서는 재시도가 폭증해 오히려 성능이 나빠집니다.

**트레이드오프**
비관적 락은 정합성을 보장하는 대신 confirm 처리가 직렬화됩니다. 동시 요청이 많을수록 대기 시간이 늘어나고, 대기 중인 요청도 DB 커넥션을 점유하기 때문에 트래픽이 극단적으로 몰리는 경우 커넥션 풀 고갈로 이어질 수 있습니다. 이 경우 confirm 요청을 큐에 적재하고 워커가 순차 처리하는 방식으로 전환하면 커넥션 낭비 없이 더 많은 요청을 수용할 수 있습니다. 다만 클라이언트에게 즉시 성공/실패 대신 비동기 응답을 제공해야 하는 UX 변화가 따릅니다.

### 대기열(Waitlist)
정원이 초과된 강의에 신청하면 PENDING 상태로 대기열에 등록됩니다. CONFIRMED 수강생이 취소하면 enrolledCount가 감소하고, 알림 시스템을 통해 대기열의 가장 오래된 순서의 사용자에게 자리가 났음을 통보합니다. 통보받은 사용자가 직접 confirm API를 호출해 수강을 확정하며, 미응답 시 다음 대기자에게 기회가 넘어갑니다.

별도의 큐 자료구조 없이 `enrollments` 테이블의 `created_at ASC` 정렬로 대기 순서를 관리합니다.

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
| 동시성 테스트 | confirm 동시 요청 | CountDownLatch, Testcontainers |

H2 대신 Testcontainers(PostgreSQL)를 사용한 이유: H2는 Pessimistic Lock 동작이 PostgreSQL과 달라 동시성 테스트의 신뢰도가 낮아지기 때문입니다.

결제 확정(confirm) 시점에 `SELECT FOR UPDATE`로 Course 행 전체에 락을 걸기 때문에, 동시에 수백 명이 마지막 자리에 요청하더라도 DB가 요청을 하나씩 순서대로 처리합니다. 락을 획득한 트랜잭션이 정원을 확인하고 enrolledCount를 증가시킨 뒤 커밋하면, 그 다음 트랜잭션이 락을 획득해 다시 정원을 확인합니다. 이 구조 덕분에 정원을 초과하는 확정은 발생하지 않습니다.

---

## 미구현 / 제약사항

| 항목 | 내용 |
|---|---|
| 알림 시스템 | 취소 발생 시 대기자에게 자리가 났음을 통보하는 기능 미구현. 실제 서비스라면 이메일·푸시 알림 등 외부 알림 서비스 연동 필요 |
| 결제 시스템 | 실제 PG(Payment Gateway) 연동 없음. confirm API 호출 자체를 결제 완료로 간주 |
| 인증 / 인가 | JWT, OAuth 등 실제 인증 메커니즘 없음. `X-User-Id` 헤더를 신뢰하는 단순 구조로, 프로덕션 환경에서는 게이트웨이 또는 인터셉터 레벨의 인증이 필요 |
| Rate Limiting | 동시 신청 폭주 시 API 레벨의 요청 제한 없음. 비관적 락으로 데이터 정합성은 보장하지만, DB 커넥션 풀 고갈 방지를 위한 Rate Limit 또는 큐 도입 검토 필요 |
| 강의 삭제 | 강의 삭제 API 미구현. DRAFT 상태 강의의 삭제 또는 소프트 딜리트 처리는 요구사항에 없어 제외 |
| 사용자 관리 | users 테이블 없음. 크리에이터/수강생 역할 구분 없이 X-User-Id 헤더 값으로만 처리 |

---

## AI 활용 범위

Claude Code를 활용하여 개발을 진행했습니다.

- **설계 논의**: 도메인 모델, 정원 관리 기준, 동시성 제어 전략(비관적 락 vs 낙관적 락 vs 큐) 등 설계 결정 시 트레이드오프를 함께 검토
- **코드 구현**: 엔티티, 서비스, 컨트롤러, 테스트 코드 작성
- **코드 리뷰**: 비즈니스 로직 누락, 엣지 케이스, 테스트 커버리지 검토
- **버그 수정**: Testcontainers 컨텍스트 캐싱 문제, 단위 테스트 Mock 불일치 등

각 설계 결정(정원 관리 기준, 락 전략, 대기자 승격 방식 등)은 AI가 제시한 옵션을 바탕으로 직접 판단하고 선택했습니다. 코드 역시 동작 원리를 이해하고 검증한 후 채택했습니다.
