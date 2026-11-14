#!/bin/bash

BASE_URL="http://localhost:8080"
COOKIE_USER1="cookies_user1.txt"
COOKIE_USER2="cookies_user2.txt"

REDIS_CLI="docker exec -i sns-session-redis redis-cli"
VIEW_COUNT_KEY_PREFIX="post:view:"
DIRTY_SET_KEY="post:view:dirty_set"

echo "=========================================="
echo "0. 회원가입 - user1, user2 (이미 있으면 409)"
echo "=========================================="
USER1_JSON=$(curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password123"}')
echo "$USER1_JSON"

USER2_JSON=$(curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user2", "password": "password123"}')
echo "$USER2_JSON"

echo ""
echo "=========================================="
echo "1. user1 로그인 (POST /api/v1/login)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/login" \
  -c "$COOKIE_USER1" \
  -d "username=user1&password=password123"

echo ""
echo "=========================================="
echo "2. user2 로그인 (POST /api/v1/login)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/login" \
  -c "$COOKIE_USER2" \
  -d "username=user2&password=password123"

echo ""
echo "=========================================="
echo "3. user1 게시글 작성 (POST /api/v1/posts, 201)"
echo "=========================================="
# 주의: Git Bash(MSYS)에서는 한글을 -d '...' 인자로 넘기면 argv가 ANSI(CP949)로
# 변환되어 UTF-8 JSON이 깨지고 서버가 400(Invalid UTF-8)을 반환한다.
# 그래서 본문은 표준입력(heredoc)으로 넘긴다.
POST_JSON=$(curl -s -X POST "$BASE_URL/api/v1/posts" \
  -b "$COOKIE_USER1" \
  -H "Content-Type: application/json" \
  --data-binary @- <<'JSON'
{"content": "조회수 테스트 게시글입니다."}
JSON
)
echo "$POST_JSON"

POST_ID=$(echo "$POST_JSON" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
POST_ID=${POST_ID:-1}
echo "POST_ID=$POST_ID"

echo ""
echo "=========================================="
echo "4. 게시글 단건 조회 (GET /api/v1/posts/{postId}, 200)"
echo "     조회 API는 viewCount를 올리지 않는다. 아래 POST /views 로만 집계된다."
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "5. 조회수 1회 증가 (POST /api/v1/posts/{postId}/views, 204)"
echo "     Redis: INCR post:view:{postId} / SADD post:view:dirty_set {postId}"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/views" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "6. Redis에 쌓인 값 확인 (DB view_count는 아직 0)"
echo "=========================================="
echo "GET ${VIEW_COUNT_KEY_PREFIX}${POST_ID}"
$REDIS_CLI GET "${VIEW_COUNT_KEY_PREFIX}${POST_ID}"
echo "SMEMBERS ${DIRTY_SET_KEY}"
$REDIS_CLI SMEMBERS "$DIRTY_SET_KEY"

echo ""
echo "=========================================="
echo "7. 조회수 3회 더 증가 (Redis에 4회 누적)"
echo "=========================================="
for i in 1 2 3; do
  curl -s -o /dev/null -w "%{http_code} " -X POST "$BASE_URL/api/v1/posts/$POST_ID/views" \
    -b "$COOKIE_USER2"
done
echo ""
echo "GET ${VIEW_COUNT_KEY_PREFIX}${POST_ID}"
$REDIS_CLI GET "${VIEW_COUNT_KEY_PREFIX}${POST_ID}"

echo ""
echo "=========================================="
echo "8. 스케줄러(매분 0초) 반영 대기 후 DB view_count 확인 (200, viewCount=4)"
echo "     반영되면 post:view:{postId} 키와 dirty_set 멤버가 사라진다."
echo "=========================================="
echo "최대 65초 대기..."
for i in $(seq 1 65); do
  DIRTY=$($REDIS_CLI SISMEMBER "$DIRTY_SET_KEY" "$POST_ID")
  if [ "$DIRTY" = "0" ]; then
    echo "dirty_set에서 제거됨 (${i}초 경과)"
    break
  fi
  sleep 1
done
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER2"

echo ""
echo "GET ${VIEW_COUNT_KEY_PREFIX}${POST_ID} (반영 후에는 nil)"
$REDIS_CLI GET "${VIEW_COUNT_KEY_PREFIX}${POST_ID}"

echo ""
echo "=========================================="
echo "9. 존재하지 않는 게시글의 조회수 증가 (404 Not Found)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/999999/views" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "10. 인증 없이 조회수 증가 (401/403)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/views"

echo ""
rm -f "$COOKIE_USER1" "$COOKIE_USER2"
