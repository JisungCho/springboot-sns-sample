#!/bin/bash

BASE_URL="http://localhost:8080"
COOKIE_USER1="cookies_user1.txt"
COOKIE_USER2="cookies_user2.txt"

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

USER1_ID=$(echo "$USER1_JSON" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
USER2_ID=$(echo "$USER2_JSON" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
USER1_ID=${USER1_ID:-1}
USER2_ID=${USER2_ID:-2}

echo ""
echo "NOTE: id는 회원가입 JSON에서 grep/cut로 파싱합니다. 409면 placeholder(1, 2)를 사용합니다. USER1_ID=$USER1_ID USER2_ID=$USER2_ID"

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
{"content": "첫 번째 게시글입니다."}
JSON
)
echo "$POST_JSON"

POST_ID=$(echo "$POST_JSON" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
POST_ID=${POST_ID:-1}
echo "POST_ID=$POST_ID"

echo ""
echo "=========================================="
echo "4. 게시글 단건 조회 (GET /api/v1/posts/{postId}, 200, viewCount +1)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "5. 본문 없이 게시글 작성 (400 Bad Request)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts" \
  -b "$COOKIE_USER1" \
  -H "Content-Type: application/json" \
  -d '{"content": "   "}'

echo ""
echo "=========================================="
echo "6. user2가 답글 작성 (POST /api/v1/posts/{postId}/replies, 201)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/replies" \
  -b "$COOKIE_USER2" \
  -H "Content-Type: application/json" \
  --data-binary @- <<'JSON'
{"content": "답글입니다."}
JSON

echo ""
echo "=========================================="
echo "7. 답글 목록 (GET /api/v1/posts/{postId}/replies, 200, 오래된 순)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID/replies" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "8. user2가 인용글 작성 (POST /api/v1/posts/{postId}/quotes, 201)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/quotes" \
  -b "$COOKIE_USER2" \
  -H "Content-Type: application/json" \
  --data-binary @- <<'JSON'
{"content": "인용글입니다."}
JSON

echo ""
echo "=========================================="
echo "9. 이 글을 인용한 글 목록 (GET /api/v1/posts/{postId}/quotes, 200, 최신순)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID/quotes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "10. user2가 리포스트 (POST /api/v1/posts/{postId}/reposts, 201)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/reposts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "11. 중복 리포스트 (409 Conflict)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/reposts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "12. 리포스트한 글 목록 (GET /api/v1/posts/{postId}/reposts, 200)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID/reposts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "13. user2가 리포스트 취소 (DELETE /api/v1/posts/{postId}/reposts, 204)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID/reposts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "14. 리포스트하지 않은 글 취소 (404 Not Found)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID/reposts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "15. user2가 user1의 게시글 삭제 시도 (403 Forbidden)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "16. 존재하지 않는 게시글 조회 (404 Not Found)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/999999" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "17. user1이 게시글 삭제 (DELETE /api/v1/posts/{postId}, 204)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "18. 삭제된 게시글 조회 (404 Not Found)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "19. 인증 없이 게시글 작성 (401/403)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  --data-binary @- <<'JSON'
{"content": "비로그인 작성 시도"}
JSON

echo ""
rm -f "$COOKIE_USER1" "$COOKIE_USER2"
