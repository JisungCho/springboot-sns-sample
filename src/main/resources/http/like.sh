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
{"content": "좋아요 테스트 게시글입니다."}
JSON
)
echo "$POST_JSON"

POST_ID=$(echo "$POST_JSON" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
POST_ID=${POST_ID:-1}
echo "POST_ID=$POST_ID"

echo ""
echo "=========================================="
echo "4. user2가 좋아요 (POST /api/v1/posts/{postId}/likes, 201, likeCount=1)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/likes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "5. 중복 좋아요 (409 Conflict)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/likes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "6. 게시글 조회로 likeCount 확인 (GET /api/v1/posts/{postId}, 200, likeCount=1)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "7. user1도 좋아요 (201, likeCount=2)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/likes" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "8. user2가 좋아요한 글 목록 (GET /api/v1/liked-posts, 200)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/liked-posts" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "9. user2가 좋아요 취소 (DELETE /api/v1/posts/{postId}/likes, 204)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID/likes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "10. 게시글 조회로 likeCount 확인 (200, likeCount=1)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/posts/$POST_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "11. 좋아요하지 않은 글 취소 (404 Not Found)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/posts/$POST_ID/likes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "12. 존재하지 않는 게시글에 좋아요 (404 Not Found)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/999999/likes" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "13. 인증 없이 좋아요 (401/403)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/posts/$POST_ID/likes"

echo ""
rm -f "$COOKIE_USER1" "$COOKIE_USER2"
