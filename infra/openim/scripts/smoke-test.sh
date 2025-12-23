#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:10008}"
AREA_CODE="${AREA_CODE:-+237}"
PHONE1="${PHONE1:-690000001}"
PHONE2="${PHONE2:-690000002}"
NICK1="${NICK1:-test1}"
NICK2="${NICK2:-test2}"
PASSWORD="${PASSWORD:-Test1234}"
VERIFY_CODE="${VERIFY_CODE:-666666}"
PLATFORM="${PLATFORM:-5}"

PASS_HASH="$(printf '%s' "$PASSWORD" | md5sum | awk '{print $1}')"

json_extract() {
  local key="$1"
  sed -n "s/.*\"${key}\":\"\\([^\"]*\\)\".*/\\1/p"
}

register_user() {
  local phone="$1"
  local nick="$2"
  local opid="$3"

  curl -s -X POST "${BASE_URL}/account/register" \
    -H 'Content-Type: application/json' \
    -H "operationID: ${opid}" \
    -d "{\"verifyCode\":\"${VERIFY_CODE}\",\"autoLogin\":true,\"user\":{\"nickname\":\"${nick}\",\"faceURL\":\"\",\"areaCode\":\"${AREA_CODE}\",\"phoneNumber\":\"${phone}\",\"password\":\"${PASS_HASH}\",\"email\":\"\"},\"platform\":${PLATFORM}}"
}

login_user() {
  local phone="$1"
  local opid="$2"

  curl -s -X POST "${BASE_URL}/account/login" \
    -H 'Content-Type: application/json' \
    -H "operationID: ${opid}" \
    -d "{\"areaCode\":\"${AREA_CODE}\",\"phoneNumber\":\"${phone}\",\"password\":\"${PASS_HASH}\",\"platform\":${PLATFORM}}"
}

get_err_code() {
  sed -n 's/.*"errCode":\([0-9-]*\).*/\1/p'
}

echo "OpenIM smoke test"
echo "BASE_URL=${BASE_URL}"
echo "PHONE1=${AREA_CODE} ${PHONE1} (nick: ${NICK1})"
echo "PHONE2=${AREA_CODE} ${PHONE2} (nick: ${NICK2})"

resp1="$(register_user "${PHONE1}" "${NICK1}" "op-smoke-1")"
code1="$(printf '%s' "$resp1" | get_err_code)"
if [ "${code1}" != "0" ]; then
  resp1="$(login_user "${PHONE1}" "op-smoke-1-login")"
fi
user1="$(printf '%s' "$resp1" | json_extract userID)"
token1="$(printf '%s' "$resp1" | json_extract chatToken)"

resp2="$(register_user "${PHONE2}" "${NICK2}" "op-smoke-2")"
code2="$(printf '%s' "$resp2" | get_err_code)"
if [ "${code2}" != "0" ]; then
  resp2="$(login_user "${PHONE2}" "op-smoke-2-login")"
fi
user2="$(printf '%s' "$resp2" | json_extract userID)"

echo "user1=${user1}"
echo "user2=${user2}"

if [ -n "${token1}" ]; then
  curl -s -X POST "${BASE_URL}/user/find/full" \
    -H 'Content-Type: application/json' \
    -H "operationID: op-smoke-find" \
    -H "token: ${token1}" \
    -d "{\"userIDs\":[\"${user1}\",\"${user2}\"]}"
  echo
else
  echo "Missing chatToken for user1; cannot call /user/find/full."
fi
