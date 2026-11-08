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
echo "NOTE: followee id는 회원가입 JSON에서 grep/cut로 파싱합니다. 409면 placeholder(1, 2)를 사용합니다. USER1_ID=$USER1_ID USER2_ID=$USER2_ID"

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
echo "3. user1이 user2를 팔로우 (POST /api/v1/follow/{followeeId}, 201)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/follow/$USER2_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "4. 중복 팔로우 (409 Conflict)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/follow/$USER2_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "5. 자기 자신 팔로우 (400 Bad Request)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/follow/$USER1_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "6. 존재하지 않는 사용자 팔로우 (404 Not Found)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/follow/999999" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "7. user2의 팔로워 목록 (GET /api/v1/followers, 200)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/followers" \
  -b "$COOKIE_USER2"

echo ""
echo "=========================================="
echo "8. user1의 팔로이 목록 (GET /api/v1/followees, 200)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/followees" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "9. user1 팔로우 카운트 (GET /api/v1/follow-count, 200)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/follow-count" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "10. user1이 user2 언팔로우 (DELETE /api/v1/follow/{followeeId}, 204)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/follow/$USER2_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "11. 존재하지 않는 팔로우 관계 언팔로우 (404 Not Found)"
echo "=========================================="
curl -s -i -X DELETE "$BASE_URL/api/v1/follow/$USER2_ID" \
  -b "$COOKIE_USER1"

echo ""
echo "=========================================="
echo "12. 인증 없이 팔로우 (401/403)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/follow/$USER2_ID"

echo ""
rm -f "$COOKIE_USER1" "$COOKIE_USER2"
