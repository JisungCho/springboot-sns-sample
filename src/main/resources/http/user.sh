#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=========================================="
echo "1. 회원가입 성공 (POST /api/v1/users/signup)"
echo "=========================================="
curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password123"}'

echo ""
echo "=========================================="
echo "2. username 중복 회원가입 (409 Conflict)"
echo "=========================================="
curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user1", "password": "password456"}'

echo ""
echo "=========================================="
echo "3. 유효성 검사 실패 - username 공백 (400 Bad Request)"
echo "=========================================="
curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "", "password": "password123"}'

echo ""
echo "=========================================="
echo "4. 유효성 검사 실패 - password 공백 (400 Bad Request)"
echo "=========================================="
curl -s -X POST "$BASE_URL/api/v1/users/signup" \
  -H "Content-Type: application/json" \
  -d '{"username": "user2", "password": ""}'

echo ""
