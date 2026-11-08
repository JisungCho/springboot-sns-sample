# Follow / FollowCount 서비스 계획

SNS에서 유저 간 팔로우와, 프로필용 카운트를 분리한다.

- **Follow**: 누가 누구를 팔로우하는지 (관계)
- **FollowCount**: 유저별 팔로워/팔로이 수 (조회용 집계)

카운트는 `Follow`를 매번 `COUNT`하지 않고, 팔로우/언팔로우 시 `FollowCount`를 같이 갱신한다.

## 패키지

기존 규칙대로 도메인별로 둔다.

```
domain/follow/
  Follow.java
  FollowRepository.java
  FollowService.java
  FollowException.java

domain/followcount/
  FollowCount.java
  FollowCountRepository.java
  FollowCountService.java
```

컨트롤러는 `FollowController`, DTO는 `controller/dto`의 record.

엔티티는 모두 `BaseEntity`를 상속한다. `createdAt`, `updatedAt`, `deletedAt`은 공통 필드이므로 도메인 엔티티에 다시 두지 않는다.

## Follow

엔티티: `id`, `followerId`, `followeeId` (`BaseEntity` 상속)  
DB FK는 두지 않는다. `(followerId, followeeId)`는 유니크.

서비스:

| 동작 | 설명 |
|------|------|
| follow | 로그인 유저 → 대상 유저. 자기 자신/중복/없는 유저는 예외 |
| unfollow | 관계 없으면 예외 |
| 팔로이 목록 | 내가 팔로우하는 유저 |
| 팔로워 목록 | 나를 팔로우하는 유저 |

팔로우/언팔로우는 관계 저장과 카운트 갱신을 한 트랜잭션으로 처리한다. 카운트는 엔티티를 읽어 더하고 저장하지 않고, `UPDATE ... SET count = count + 1` 형태의 원자적 갱신으로 동시 요청의 갱신 손실을 막는다.

## FollowCount

엔티티: `userId`(유니크), `followersCount`, `followeesCount` (`BaseEntity` 상속)  
회원가입 시 0,0으로 만들고, 이후에는 Follow 서비스가 증감만 호출한다.

- 팔로우: 팔로워 `followeesCount + 1`, 대상 `followersCount + 1`
- 언팔로우: 각각 `- 1` (0 미만 방지)
- 증감은 DB에서 원자적으로 수행한다. 조회 후 메모리에서 `+1` 하고 save 하면 동시 팔로우 시 한쪽 증가가 덮어쓰인다.

프로필 조회는 `FollowCount`만 읽는다.

## API (초안)

인증 필요. 로그인 유저는 세션 쿠키에서 식별한다. 대상 유저만 경로에 둔다.  
`src/main/resources/http/follow.sh`에 curl을 둔다.

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/follow/{followeeId}` | 팔로우 |
| DELETE | `/api/v1/follow/{followeeId}` | 언팔로우 |
| GET | `/api/v1/followers` | 내 팔로워 목록 |
| GET | `/api/v1/followees` | 내 팔로이 목록 |
| GET | `/api/v1/follow-count` | 내 팔로워/팔로이 수 |

## 구현 순서

1. `Follow`, `FollowCount` 엔티티(`BaseEntity` 상속)/리포지토리
2. `FollowCountService` (생성, 증감, 조회)
3. `FollowService` (팔로우/언팔로우 + 카운트 갱신, 목록)
4. 컨트롤러/DTO, `FollowException` 핸들러 등록
5. 회원가입 시 FollowCount 초기화
6. `follow.sh` + 테스트
