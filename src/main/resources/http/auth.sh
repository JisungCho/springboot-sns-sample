#!/bin/bash

BASE_URL="http://localhost:8080"
COOKIE_FILE="cookies.txt"

echo "=========================================="
echo "0. 사전 준비 - 회원가입 (이미 있으면 409)"
echo "=========================================="
curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password123"}'

echo ""
echo "=========================================="
echo "1. 로그인 성공 (POST /api/v1/login, 200 OK)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/login" \
  -c "$COOKIE_FILE" \
  -d "username=user1&password=password123"

echo ""
echo "=========================================="
echo "2. 비밀번호 불일치 (401 Unauthorized)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/login" \
  -d "username=user1&password=wrongpassword"

echo ""
echo "=========================================="
echo "3. 존재하지 않는 사용자 (401 Unauthorized)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/api/v1/login" \
  -d "username=nobody&password=password123"

echo ""
echo "=========================================="
echo "4. 로그인 세션으로 인증 필요 API 호출 (쿠키 사용)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/users/me" \
  -b "$COOKIE_FILE"

echo ""
echo "=========================================="
echo "5. 쿠키 없이 인증 필요 API 호출 (401/403)"
echo "=========================================="
curl -s -i -X GET "$BASE_URL/api/v1/users/me"

echo ""
echo "=========================================="
echo "6. 로그아웃 (POST /logout)"
echo "=========================================="
curl -s -i -X POST "$BASE_URL/logout" \
  -b "$COOKIE_FILE"

echo ""
echo "=========================================="
echo "7. 동시 세션 제한 확인 - 같은 계정 3회 로그인 (최대 2개 유지)"
echo "=========================================="
for i in 1 2 3; do
  echo "- ${i}번째 로그인"
  curl -s -o /dev/null -w "  status: %{http_code}\n" -X POST "$BASE_URL/api/v1/login" \
    -c "cookies_$i.txt" \
    -d "username=user1&password=password123"
done

echo ""
echo "- 각 세션으로 인증 필요 API 호출 (GET /)"
echo "  기대: 1번 세션 401(만료), 2번/3번 세션 200"
for i in 1 2 3; do
  echo "  [세션 $i]"
  curl -s -w "\n  status: %{http_code}\n" -X GET "$BASE_URL/" \
    -b "cookies_$i.txt"
done

# Redis에서 직접 확인하려면 (호스트 포트 16379):
#   redis-cli -p 16379 keys "sns:session:sessions:*"
#   redis-cli -p 16379 smembers "sns:session:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:user1"

echo ""
rm -f "$COOKIE_FILE" cookies_1.txt cookies_2.txt cookies_3.txt
