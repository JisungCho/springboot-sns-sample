# 게시글 조회수 Redis 집계 — 주니어 개발자를 위한 해설

이 프로젝트의 코드를 "왜 이렇게 짰는지" 이해할 수 있도록
개념 설명 + 예시 + 비유로 정리한 노트.

> 대상 코드: `springboot-sns-sample`
> `domain/post/PostViewCountService.java`, `domain/post/PostViewCountFlushScheduler.java`,
> `controller/PostViewController.java`, `domain/post/PostRepository#incrementViewCountBy`

## 0. 먼저 결론부터

**"조회 요청마다 DB를 UPDATE하지 말고, Redis 카운터에 +1만 하고, 1분에 한 번 모아서 DB에 더하자"**

이걸 업계 용어로 두 가지로 부른다. 용어를 알아두면 검색도 되고 대화도 된다.

| 용어 | 뜻 | 이 코드에서 |
|---|---|---|
| **Write-behind (지연 쓰기)** | 원본 저장소(DB)에 바로 쓰지 않고, 중간 저장소에 먼저 모았다가 나중에 몰아서 반영 | Redis에 먼저 쌓고 1분마다 DB 반영 |
| **배치 집계 (aggregation)** | N번의 개별 UPDATE를 1번의 `+N` UPDATE로 합침 | `+1`을 1,000번 → `+1000`을 1번 |

---

## 1. 출발점: 기존 코드는 뭐가 문제였나

기존에는 글을 한 번 볼 때마다 이 SQL이 나갔다.

```sql
UPDATE posts SET view_count = view_count + 1 WHERE id = 1;
```

"한 줄인데 뭐가 문제지?" 싶지만, **인기 글**에서 이 한 줄이 병목이 된다.

### 1-1. DB는 UPDATE 하는 동안 그 행을 잠근다

이걸 **행 잠금(row lock)** 이라고 한다. 인기 글에 1초에 1,000번 조회가 오면 이렇게 된다.

```
요청 1 → id=1 행 잠금 → +1 → 커밋 → 잠금 해제
요청 2 → (대기...) → 잠금 획득 → +1 → 커밋 → 잠금 해제
요청 3 → (대기...)
...
요청 1000 → (한참 뒤에야 처리)
```

전부 **줄을 서서 순서대로** 처리된다. 같은 행 하나 때문에 1,000개 요청이 직렬화되는 것.
이 현상을 **핫스팟(hot spot)**, **잠금 경합(lock contention)** 이라고 부른다.

### 1-2. 서버를 늘려도 안 되는 이유 (여기가 중요)

> **비유**: 편의점 계산대가 1개인데 손님이 1,000명이다.
> 점원을 3명으로 늘려도(서버 증설), **손님이 같은 계산대 하나만 고집하면** 아무 소용없다.

서버를 10대 띄워도 **모두 같은 DB의 같은 행**을 UPDATE한다. 즉 **DB 행이 병목**이므로
애플리케이션 서버 증설로는 해결되지 않는다. 이게 "스케일 아웃으로 안 풀리는 문제"의 대표 예시다.

---

## 2. 그래서 Redis를 쓴다 — Redis가 왜 괜찮은가

### 2-1. Redis는 단일 스레드다

Redis는 명령을 **한 번에 하나씩** 처리한다. 그래서 `INCR` 같은 명령에는
개발자가 락을 걸 필요가 없다. **명령 자체가 원자적**이다.

```
INCR post:view:1   ← 100개 스레드가 동시에 호출해도 정확히 +100
```

DB처럼 "락 잡고 → 풀고" 하는 절차가 없다.

### 2-2. 메모리 연산이라 빠르다

DB는 디스크(SSD)에 쓰고, 인덱스도 갱신하고, WAL도 남긴다.
Redis는 **메모리에서 값 하나 바꾸고 끝**난다. 비교할 수 없이 가볍다.

### 2-3. 정리

| | DB UPDATE | Redis INCR |
|---|---|---|
| 잠금 | 행 잠금 필요 | 불필요 (단일 스레드) |
| 저장 위치 | 디스크 | 메모리 |
| 속도 | 상대적으로 느림 | 매우 빠름 |
| 영속성 | 강함 | 약함 (설정에 따라 유실 가능) |

**"빠른 대신 무엇을 잃었는가"**: 영속성이다. 이건 5절에서 다시 다룬다.

---

## 3. 등장인물: Redis 키 2개

```java
private static final String VIEW_COUNT_KEY_PREFIX = "post:view:";
private static final String DIRTY_SET_KEY = "post:view:dirty_set";
```

| Redis 키 | 타입 | 값 예시 | 역할 |
|---|---|---|---|
| `post:view:42` | String | `"137"` | 42번 글의 **아직 DB에 반영 안 된** 조회수 |
| `post:view:dirty_set` | Set | `{"42", "7", "91"}` | **DB에 반영해야 할 글 ID 목록** |
| `posts.view_count` | DB 컬럼 | `1380` | 실제(최종) 값 |

용어 하나: **dirty**는 "원본과 달라진(더러워진)" 상태를 뜻한다.
`dirty_set` = **"손댔으니 나중에 DB에 반영해야 할 목록"** 이다.

### 3-1. 자주 나오는 질문: "dirty_set 없이 `post:view:*` 전체를 뒤지면 안 되나?"

두 가지 이유로 안 된다.

**① `KEYS post:view:*`는 운영에서 금지 명령이다.**
`KEYS`는 모든 키를 훑는다(시간복잡도 O(N)). 키가 수백만 개인 순간 **Redis 전체가 멈춘다.**
`SCAN`으로 바꿔도 여전히 O(N)이고, 1분마다 수백만 개를 훑는 배치는 감당이 안 된다.

**② Set은 "글 개수"에 비례한다.**
42번 글이 1,000번 조회돼도 `SADD`는 **처음 1번만** 추가한다(나머지는 무시).
즉 `dirty_set` 크기 = **"이번 주기에 반영할 글의 수"** 다. 조회수 총합이 아니다.
인기 글이 100만 번 조회돼도 원소는 여전히 1개다. **이게 성능의 핵심 포인트**다.

> 참고: `SMEMBERS`도 원소가 아주 많으면 O(N)이다. dirty_set이 커질 수 있는 구조라면
> `SSCAN`으로 나눠 읽거나 `SPOP`을 쓴다.

---

## 4. 조회 1번이 일어날 때 — `increaseViewCount`

```java
public void increaseViewCount(Long postId) {
    if (!postRepository.existsById(postId)) {
        throw new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다.");
    }
    redisTemplate.opsForValue().increment(viewCountKey(postId));
    redisTemplate.opsForSet().add(DIRTY_SET_KEY, String.valueOf(postId));
}
```

### 4-1. `existsById`는 왜 DB를 한 번 더 읽나?

없는 글 ID(`999999`)로 요청이 오면 `post:view:999999` 같은 **쓰레기 키**가 Redis에 쌓인다.
1분마다 flush 할 때 `UPDATE ... WHERE id=999999`는 0건이지만, `dirty_set`에는 계속 남아
**매분 헛일**을 한다. 그래서 **입구에서 막는다**.

비용은 SELECT 1회다. 트래픽이 더 커지면 이 존재 확인도 캐시로 빼는 게 다음 최적화 단계다.

### 4-2. `increment` = Redis `INCR`

```java
redisTemplate.opsForValue().increment("post:view:42");
```

- 키가 없으면 **0에서 시작해 1**을 반환한다.
- 원자적이라 동시 100 요청이면 정확히 100이 된다.
- 반환값은 증가 후의 값이다.

### 4-3. `add` = Redis `SADD`

```java
redisTemplate.opsForSet().add("post:view:dirty_set", "42");
```

이미 있으면 **아무 일도 안 한다.** 몇 번 호출해도 결과가 같다.
이런 성질을 **멱등(idempotent)** 하다고 말한다. 재시도하기 안전한 연산이다.

### 4-4. 솔직히 말하면 이 두 줄은 원자적이지 않다

```
INCR 성공 → (여기서 서버가 죽음) → SADD 실행 안 됨
```

이러면 조회수는 Redis에 있는데 `dirty_set`에 없어서 **영원히 DB에 반영되지 않는다.**

완벽하게 하려면 **Lua 스크립트**로 두 명령을 한 덩어리로 실행한다.

```lua
-- 개념 예시 (지금 코드에는 아직 없음)
redis.call('INCR', KEYS[1])       -- post:view:{id}
redis.call('SADD', KEYS[2], ARGV[1])  -- dirty_set
```

Redis는 Lua 스크립트 전체를 **하나의 원자적 단위**로 실행한다.
지금은 크래시 확률이 극히 낮아 감수한 트레이드오프다.

> **주니어에서 시니어로 가는 지점**: "이 코드가 완벽하지 않다"를 **아는 것**.
> 완벽한 코드를 짜는 게 아니라, 어디가 얼마나 위험한지 알고 선택하는 것.

---

## 5. `flush()` — 한 주기 해부

```java
@Transactional
public int flush() {
    Set<String> dirtyPostIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
    if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
        return 0;
    }

    Map<Long, Long> claimedCounts = new LinkedHashMap<>();
    Set<String> claimedPostIds = new LinkedHashSet<>();
    try {
        claim(dirtyPostIds, claimedPostIds, claimedCounts);
        claimedCounts.forEach((postId, delta) -> postRepository.incrementViewCountBy(postId, delta));
    } catch (RuntimeException e) {
        restore(claimedPostIds, claimedCounts);   // Redis에 되돌린다
        throw e;
    }
    return claimedCounts.size();
}
```

네 단계로 나눠 본다.

### 5-1. 스냅샷 — `SMEMBERS`

`dirty_set`의 **그 순간 목록을 복사**한다. 이 시점 이후에 새로 추가된 postId는
**다음 주기**가 처리한다. flush는 "지금 이 스냅샷"만 책임진다.

### 5-2. 선점(claim) — 왜 `SREM`을 하나?

서버가 3대면 스케줄러도 3개 돈다. 그냥 처리하면 **같은 글 조회수를 3번 더한다.**

Redis `SREM`은 **"내가 실제로 지웠으면 1, 이미 없으면 0"** 을 반환한다.

```java
Long removed = redisTemplate.opsForSet().remove(DIRTY_SET_KEY, rawPostId);
if (removed == null || removed == 0) {
    continue;   // 다른 인스턴스가 먼저 가져갔다
}
```

```
인스턴스 A: SREM dirty_set "42" → 1  ← 내가 처리한다
인스턴스 B: SREM dirty_set "42" → 0  ← 다른 놈이 가져감, 스킵
```

> **비유**: 탕비실 공용 장부에 "이거 내가 한다"를 적는다. 적는 행위 자체가 원자적이면
> 두 사람이 동시에 적을 수 없다. 이게 **분산 락 없이 동작하는 try-lock** 이다.

### 5-3. 읽고 지우기 — `GETDEL`

```java
long delta = parseCount(redisTemplate.opsForValue().getAndDelete(viewCountKey(postId)));
```

`GET` 하고 `DEL`을 따로 하면 이렇게 된다.

| 시각 | flush 스레드 | 조회 요청 스레드 | `post:view:42` |
|---|---|---|---|
| t1 | `GET` → **100** | | 100 |
| t2 | | `INCR` → 101 | **101** |
| t3 | `DEL` | | **(삭제)** |
| t4 | DB에 +100 | | |

t2에서 들어온 **1건이 유실**된다. 유저는 글을 봤는데 조회수가 안 올라간 거다.

`GETDEL`(Redis 6.2+)은 **읽기와 삭제를 원자적으로** 한다.
그 순간 값을 가져가고, 이후 `INCR`은 새 키(0부터)에서 시작한다.

> **핵심 사고방식**: "내가 가져간 값에 대해서만 정확히 책임진다."

### 5-4. DB 반영 — `+ :delta`

```java
@Query("update Post p set p.viewCount = p.viewCount + :delta, " +
       "p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
int incrementViewCountBy(@Param("postId") Long postId, @Param("delta") long delta);
```

**절대 하면 안 되는 코드**:

```java
// ❌ 엔티티를 읽어서 더하고 저장 (Lost Update 발생)
Post post = postRepository.findById(id).orElseThrow();
post.setViewCount(post.getViewCount() + delta);
```

왜 위험한지:

```
A: 읽기 viewCount=100
B: 읽기 viewCount=100
A: 저장 100+5 = 105
B: 저장 100+3 = 103   ← A의 +5가 사라짐!
```

이걸 **갱신 손실(Lost Update)** 이라고 한다. 반면
`SET view_count = view_count + :delta` 는 **DB가 내부에서 계산**하므로 원자적이다.

이 프로젝트의 `likeCount`, `replyCount`, `repostCount`도 **모두 같은 이유로 이 방식**이다.
"카운터는 절대 읽고-수정-쓰기 하지 않는다"를 규칙으로 기억해두자.

---

## 6. 왜 그릇이 2개일까 (가장 헷갈리는 부분)

```java
Map<Long, Long> claimedCounts = new LinkedHashMap<>();   // 반영할 값
Set<String> claimedPostIds = new LinkedHashSet<>();      // 빼앗은 목록
```

| 변수 | 담는 것 | 쓰임 |
|---|---|---|
| `claimedCounts` | `postId → 증가분` | **DB에 실제로 더할 값** |
| `claimedPostIds` | `SREM`으로 빼앗은 **원본 문자열 전부** | 실패 시 **dirty_set에 되돌릴 목록** |

**두 집합이 일치하지 않는 경우가 있다.**

```java
claimedPostIds.add(rawPostId);                                  // 빼앗았으니 무조건 기록
long delta = parseCount(...getAndDelete(viewCountKey(postId)));
if (delta > 0) {
    claimedCounts.put(postId, delta);                           // 0이면 여기 안 들어감
}
```

`SREM`은 성공했는데 `GETDEL` 결과가 `null`이거나 `0`이면
→ `claimedCounts`에는 **안 들어가고**, `claimedPostIds`에는 **들어간다**.

만약 `claimedCounts`만 있다면, 실패 시 복구에서 **"빼앗았지만 값이 0이었던 글"** 을 놓친다.
그 글은 `dirty_set`에서 영구히 사라져 이후 조회수가 DB에 반영되지 않는다.

> **정리**: `claimedCounts` = **할 일**, `claimedPostIds` = **내가 건드린 것 전부(되돌릴 목록)**.
> 실패 복구는 "할 일"이 아니라 "건드린 것"을 기준으로 해야 안전하다.

---

## 7. 실패하면? — `@Transactional`의 함정

```java
@Transactional
public int flush() { ... }
```

이 코드를 보고 **"실패하면 Redis도 롤백되겠지"** 라고 생각하기 쉽다. **아니다.**

- `@Transactional`은 **Spring이 관리하는 JDBC 트랜잭션 1개**만 롤백한다 → **DB만** 되돌아간다.
- Redis는 이 트랜잭션에 **참여하지 않는다**. `SREM`·`GETDEL`은 이미 실행됐고 되돌아가지 않는다.

그래서 **직접 코드로 되돌려야** 한다. 이걸 **보상(compensation)** 이라고 부른다.

```java
private void restore(Set<String> claimedPostIds, Map<Long, Long> claimedCounts) {
    claimedPostIds.forEach(rawPostId -> {
        try {
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, rawPostId);
        } catch (RuntimeException e) {
            log.error("게시글 {}을(를) dirty_set에 되돌리지 못했습니다.", rawPostId, e);
        }
    });
    claimedCounts.forEach((postId, delta) -> {
        try {
            redisTemplate.opsForValue().increment(viewCountKey(postId), delta);
        } catch (RuntimeException e) {
            log.error("게시글 {} 조회수 {}건을 Redis에 되돌리지 못했습니다.", postId, delta, e);
        }
    });
}
```

### 7-1. 용어: 분산 트랜잭션

DB와 Redis처럼 **서로 다른 저장소에 걸친 일관성**을 다루는 문제를 **분산 트랜잭션**이라고 한다.
2PC(2단계 커밋) 같은 무거운 방법도 있지만, 보통은 **"되돌리는 코드 + 재시도"** 로 해결한다.
Saga 패턴도 같은 계열이다.

### 7-2. 복구 코드가 실패하면? 그냥 로그를 남긴다

`restore()` 안에서도 try-catch를 쓰고 **값과 함께 `log.error`** 를 남긴다.
`delta`까지 로그에 남기는 이유: 나중에 수동 복구할 때 **얼마를 되돌려야 하는지** 알아야 하기 때문이다.

> **실무 감각**: 복구 코드는 "복구하다 또 실패할 수 있다"를 전제로 짠다.
> 그리고 복구 불가능한 상황을 **반드시 로그로 남겨 나중에 손으로 고칠 수 있게** 한다.

---

## 8. 이 설계가 보장하는 것 / 못 하는 것

| 상황 | 결과 |
|---|---|
| 정상 | 정확히 1회 반영 ✅ |
| DB 실패 → restore 성공 | 다음 주기에 재시도, 값 보존 ✅ |
| DB 커밋 성공 → restore 실패 | **중복 반영 가능** ⚠️ |
| restore 자체 실패 | **유실 가능** ⚠️ |
| `INCR` 후 `SADD` 전 프로세스 종료 | **유실 가능** ⚠️ |
| Redis 자체가 날아감 | 최대 1주기분 **유실 가능** ⚠️ |

**"거의 정확, 아주 드물게 어긋남"** 이다. 이걸 업계 용어로
**최종적 일관성(Eventual Consistency)** 이라고 한다. "지금은 아니지만 결국 맞는다."

### 8-1. 그럼 이렇게 짜도 되는 건가? — 데이터 종류에 따라 다르다

| 데이터 | 이 방식? |
|---|---|
| 조회수, 좋아요 수 | ✅ OK. 1건 틀려도 서비스에 영향 없음 |
| 팔로워 수(표시용) | ✅ 대체로 OK |
| **은행 잔액** | ❌ 절대 안 됨 |
| **재고 수량** | ❌ 절대 안 됨 (초과 판매 = 금전 손실) |
| **결제 금액** | ❌ 절대 안 됨 |

> **설계의 첫 질문은 항상 이것**: "이 데이터가 1건 틀리면 무슨 일이 생기는가?"
> 이 질문에 답하기 전에 코드를 쓰기 시작하면 안 된다.

---

## 9. 스케줄러 — `@Scheduled` 함정 4개

```java
@Scheduled(cron = "${app.post-view.flush-cron:0 * * * * *}")
public void flushViewCounts() {
    int flushedPostCount = postViewCountService.flush();
    if (flushedPostCount > 0) {
        log.info("게시글 조회수 {}건을 DB에 반영했습니다.", flushedPostCount);
    }
}
```

### 함정 ① `@EnableScheduling`이 없으면 아무 일도 안 일어난다

`@Scheduled`만 붙여놓고 "왜 안 돌지?" 하는 경우가 정말 많다.
이 프로젝트는 `config/SchedulingConfig.java`에 `@EnableScheduling`이 있다.

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

### 함정 ② Spring cron은 6필드다

```
초 분 시 일 월 요일
0  *  *  *  *  *      ← 매분 0초
```

리눅스 crontab은 **5필드**(분 시 일 월 요일)라서 헷갈린다.
5필드로 쓰면 "매초"가 아니라 **파싱 에러**가 난다.

### 함정 ③ 기본 스케줄러 스레드는 1개다

이전 실행이 안 끝났으면 다음 실행은 **겹치지 않고 대기**한다.
그래서 flush가 오래 걸리면 주기가 밀린다. 동시 실행이 필요하면
`ThreadPoolTaskScheduler`를 등록해야 한다 (지금은 일부러 1개).

### 함정 ④ `fixedRate` vs `fixedDelay`

| 옵션 | 기준 |
|---|---|
| `fixedRate` | **시작 시각** 기준 간격. 작업이 오래 걸리면 겹칠 수 있음 |
| `fixedDelay` | **끝난 시각** 기준 간격. 항상 작업 사이에 간격이 생김 |

### 보너스: 주기를 설정값으로 뺐다

```yaml
app:
  post-view:
    flush-cron: ${POST_VIEW_FLUSH_CRON:0 * * * * *}
```

테스트에서 1초마다 돌리거나, 운영에서 부하를 보고 조정할 때
**재배포 없이** 바꿀 수 있다. "주기·임계치·키 이름은 하드코딩하지 않는다"는 원칙.

---

## 10. 자주 하는 오해 Q&A

**Q. Redis에 쌓이는 값이 계속 커지면 int 오버플로 나지 않나?**
`view_count`는 `long`이다. 그리고 flush 때마다 `GETDEL`로 0으로 리셋되므로
Redis에 쌓이는 값은 **1주기 동안의 증가분**뿐이다.

**Q. 글을 삭제하면 `post:view:{id}`는 어떻게 되나?**
flush 할 때 `UPDATE ... WHERE id=:postId`가 0건이 되고, `dirty_set`에서는 이미 제거됐다.
Redis 키는 `GETDEL`로 지워졌으므로 남지 않는다. 즉 **자동 정리**된다.

**Q. flush 도중에 `dirty_set`에 새로 추가된 글은?**
스냅샷 이후 추가분이라 이번 주기에서는 처리되지 않는다. 다음 주기가 처리한다. 정상 동작이다.

**Q. `@Transactional`이 있으니 flush 전체가 원자적인가?**
**DB 안에서만** 그렇다. Redis는 원자적이지 않고, 보상 코드로 되돌린다. (7절)

**Q. 그냥 조회수 조회 API에서 DB 안 읽고 Redis 값만 주면 안 되나?**
가능하지만 `DB값 + Redis델타`를 합산해야 정확하다. Redis만 보면 **1주기 이전 값**이 빠진다.

**Q. 왜 `SREM`을 먼저 하고 `GETDEL`을 나중에 하나? 반대면?**
`GETDEL` 먼저 하면, 그 사이 다른 인스턴스가 `SREM`에 성공해 처리 대상이 되는데
값은 이미 내가 가져간 상태가 되어 **유실**된다. "선점 → 값 확보" 순서가 안전하다.

---

## 11. 직접 확인해보는 실습

`src/main/resources/http/view.sh`가 이 흐름을 그대로 따라간다.

```bash
# 1) 조회수 1회 증가
curl -s -i -X POST "http://localhost:8080/api/v1/posts/1/views" -b cookies_user2.txt

# 2) Redis 확인 (DB는 아직 그대로)
docker exec -i sns-session-redis redis-cli GET post:view:1
docker exec -i sns-session-redis redis-cli SMEMBERS post:view:dirty_set

# 3) 매분 0초 지나면 자동 반영 → Redis 키 사라짐 + DB view_count 증가
docker exec -i sns-session-redis redis-cli GET post:view:1   # (nil)
```

스스로 해볼 질문:

1. 3번 연속 호출하면 `post:view:1`은 얼마인가? `dirty_set` 원소는 몇 개인가? (왜?)
2. flush 후 `GET post:view:1`이 `nil`인 이유는? (`GETDEL`)
3. 서버를 2대 띄우고 동시에 flush하면 조회수가 2배가 되는가? (→ `SREM` 선점)
4. `POST_VIEW_FLUSH_CRON=*/10 * * * * *` 로 바꾸면 반영 주기가 어떻게 변하나?

---

## 12. 요약 카드

```
[조회 요청]  INCR post:view:{id}     ← 원자적, 빠름
             SADD post:view:dirty_set {id}  ← 멱등

[1분마다]    SMEMBERS dirty_set      ← 스냅샷
             SREM (성공한 것만)      ← 다중 인스턴스 중복 방지
             GETDEL post:view:{id}   ← 원자적 읽기+삭제
             UPDATE ... + :delta     ← DB 내부 계산, Lost Update 방지
             실패 시 → restore()     ← Redis는 롤백 안 되므로 직접 복구
```

**얻은 것**: 응답이 DB 락에 안 묶임 / DB 쓰기 1,000회 → 1회
**포기한 것**: 최대 1분 지연 / 극히 드문 유실·중복 / 복구 코드 관리 부담

---

## 13. 설계할 때 스스로에게 던질 질문 (체크리스트)

- [ ] 이 데이터의 **정확도 요구사항**은? (정확히 1회 / 최대 1회 / 대략)
- [ ] 이 쓰기가 **핫스팟**(같은 행/같은 키)을 만들지 않는가?
- [ ] 원자적이어야 하는 지점을 찾았나? (`INCR`, `SET x = x + n`, `SREM`, `GETDEL`)
- [ ] **읽고-수정-쓰기**를 코드에서 하고 있지 않은가? (Lost Update)
- [ ] 여러 저장소에 썼다면, **롤백되지 않는 쪽**을 어떻게 되돌릴 것인가?
- [ ] 여러 인스턴스가 동시에 돌아도 **중복 처리**되지 않는가?
- [ ] 실패 시 **유실**과 **중복** 중 무엇을 허용할 것인가? 로그에 추적 정보가 있나?
- [ ] 사용자에게 **지연**이 보이는가? 보여줄 필요가 있는가?
- [ ] 주기·임계치·키 이름이 하드코딩되어 있지 않은가?
