#!/usr/bin/env bash
# ============================================================
# DevOps 项目残留成员治理 - 端到端功能测试脚本
#
# 覆盖场景（与 stale-member.http 对齐）：
#   T01 PM 调用名单接口      → 200 + code=0 + 数据形态合法
#   T02 普通成员调用名单接口  → 拒绝
#   T03 PM 清理残留用户      → accepted=true 且 4 步全成功
#   T04 30s 内重复清理        → accepted=false (duplicate)
#   T05 PM 清理自己          → accepted=false (cannot clean himself)
#   T06 PM 清理仍是项目成员的人 → accepted=false (still a project member)
#   T07 普通成员清理         → 拒绝
#   T08 (可选) 30s 后再次扫描 → stale 用户已不在名单
#
# 依赖：bash 4+, curl, jq
# 使用：
#   export AUTH_HOST=bkrepo.example.com
#   export PROJECT=demo-project
#   export MANAGER=manager-bob       MANAGER_PWD=xxx
#   export NORMAL=normal-tom         NORMAL_PWD=xxx
#   export STALE=alice
#   bash stale-member-test.sh
#
# 也可以直接在脚本顶部填默认值。
# ============================================================

set -u

# ---------------- 配置 ----------------
AUTH_HOST="${AUTH_HOST:-bkrepo.example.com}"
PROJECT="${PROJECT:-demo-project}"
MANAGER="${MANAGER:-manager-bob}"
MANAGER_PWD="${MANAGER_PWD:-changeme}"
NORMAL="${NORMAL:-normal-tom}"
NORMAL_PWD="${NORMAL_PWD:-changeme}"
STALE="${STALE:-alice}"
WAIT_AFTER_CLEAN_SECONDS="${WAIT_AFTER_CLEAN_SECONDS:-31}"   # 用于 T08 等待 duplicate 锁过期

BASE_URL="http://${AUTH_HOST}/auth/api/devops/project/${PROJECT}/stale-members"

# ---------------- 工具 ----------------
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
PASS=0; FAIL=0
pass() { echo -e "  ${GREEN}✓${NC} $*"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}✗${NC} $*"; FAIL=$((FAIL+1)); }
section() { echo -e "\n${YELLOW}── $* ──${NC}"; }

require() {
    command -v "$1" >/dev/null 2>&1 || { echo "$1 not found, please install it" >&2; exit 1; }
}
require curl
require jq

# 通用 GET（注意：不要把响应输出到 stderr 影响 jq）
http_get() {
    local user=$1 pwd=$2 url=$3
    curl -sS -w "\n%{http_code}" -u "${user}:${pwd}" "${url}"
}
http_post() {
    local user=$1 pwd=$2 url=$3
    curl -sS -X POST -w "\n%{http_code}" -u "${user}:${pwd}" "${url}"
}
http_get_anon() {
    curl -sS -w "\n%{http_code}" "$1"
}

# 拆分响应：最后一行是 status，其余是 body
parse_resp() {
    local raw=$1
    STATUS=$(printf '%s' "$raw" | tail -n1)
    BODY=$(printf '%s' "$raw" | sed '$d')
}

# ---------------- 用例 ----------------
section "T01  PM 列出残留成员名单（正向）"
parse_resp "$(http_get "$MANAGER" "$MANAGER_PWD" "$BASE_URL")"
[[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS body=$BODY"
CODE=$(echo "$BODY" | jq -r '.code // empty')
[[ "$CODE" == "0" ]] && pass "business code = 0" || fail "code=$CODE"
echo "$BODY" | jq -e '.data.members | type == "array"' >/dev/null \
    && pass "members is array" || fail "members shape invalid"
echo "$BODY" | jq -e '.data.stats.candidateCount, .data.stats.confirmedStaleCount, .data.stats.confirmedMemberCount, .data.stats.errorCount | type == "number"' >/dev/null \
    && pass "stats fields present" || fail "stats fields missing"
INITIAL_STALE_COUNT=$(echo "$BODY" | jq '.data.stats.confirmedStaleCount')
echo "  ↳ stats: $(echo "$BODY" | jq -c '.data.stats')"


section "T02  普通成员调用名单接口（鉴权）"
parse_resp "$(http_get "$NORMAL" "$NORMAL_PWD" "$BASE_URL")"
if [[ "$STATUS" == "401" || "$STATUS" == "403" ]]; then
    pass "rejected with HTTP $STATUS"
elif [[ "$STATUS" == "200" ]]; then
    CODE=$(echo "$BODY" | jq -r '.code // empty')
    [[ "$CODE" != "0" && -n "$CODE" ]] && pass "rejected with biz code=$CODE" \
        || fail "expected non-zero code, got code=$CODE body=$BODY"
else
    fail "unexpected status=$STATUS body=$BODY"
fi


section "T07  普通成员清理（鉴权）"
parse_resp "$(http_post "$NORMAL" "$NORMAL_PWD" "${BASE_URL}/${STALE}/clean")"
if [[ "$STATUS" == "401" || "$STATUS" == "403" ]]; then
    pass "rejected with HTTP $STATUS"
elif [[ "$STATUS" == "200" ]]; then
    CODE=$(echo "$BODY" | jq -r '.code // empty')
    [[ "$CODE" != "0" && -n "$CODE" ]] && pass "rejected with biz code=$CODE" \
        || fail "expected non-zero code, got code=$CODE body=$BODY"
else
    fail "unexpected status=$STATUS"
fi


section "T05  PM 清理自己"
parse_resp "$(http_post "$MANAGER" "$MANAGER_PWD" "${BASE_URL}/${MANAGER}/clean")"
[[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS"
ACCEPTED=$(echo "$BODY" | jq -r '.data.accepted // empty')
REASON=$(echo "$BODY" | jq -r '.data.reason // empty')
[[ "$ACCEPTED" == "false" ]] && pass "accepted=false" \
    || fail "expected accepted=false, got $ACCEPTED"
[[ "$REASON" == *"cannot clean himself"* ]] && pass "reason includes 'cannot clean himself'" \
    || fail "reason mismatch: $REASON"


section "T06  PM 清理仍是项目成员的用户"
parse_resp "$(http_post "$MANAGER" "$MANAGER_PWD" "${BASE_URL}/${NORMAL}/clean")"
[[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS"
ACCEPTED=$(echo "$BODY" | jq -r '.data.accepted // empty')
REASON=$(echo "$BODY" | jq -r '.data.reason // empty')
[[ "$ACCEPTED" == "false" ]] && pass "accepted=false" \
    || fail "expected accepted=false"
[[ "$REASON" == *"still a project member"* ]] && pass "reason mentions still a project member" \
    || fail "reason mismatch: $REASON"


section "T03  PM 清理残留用户（正向）"
parse_resp "$(http_post "$MANAGER" "$MANAGER_PWD" "${BASE_URL}/${STALE}/clean")"
[[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS body=$BODY"
ACCEPTED=$(echo "$BODY" | jq -r '.data.accepted // empty')
if [[ "$ACCEPTED" == "true" ]]; then
    pass "accepted=true"
    STEP_NAMES=$(echo "$BODY" | jq -r '.data.steps[].step' | tr '\n' '|')
    EXPECTED='permission.users|user.roles[PROJECT]|user.roles[REPO]|personal_path|'
    [[ "$STEP_NAMES" == "$EXPECTED" ]] && pass "4 steps in correct order" \
        || fail "steps mismatch, got: $STEP_NAMES"
    ALL_OK=$(echo "$BODY" | jq -r '.data.steps | map(.success) | all')
    [[ "$ALL_OK" == "true" ]] && pass "all steps success" \
        || fail "some step failed: $(echo "$BODY" | jq -c '.data.steps')"
else
    REASON=$(echo "$BODY" | jq -r '.data.reason // empty')
    fail "expected accepted=true, got accepted=$ACCEPTED reason=$REASON; please ensure '$STALE' has been removed from bk-ci project '$PROJECT' and is not the only project admin"
fi


section "T04  30s 内重复清理（防抖）"
parse_resp "$(http_post "$MANAGER" "$MANAGER_PWD" "${BASE_URL}/${STALE}/clean")"
[[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS"
ACCEPTED=$(echo "$BODY" | jq -r '.data.accepted // empty')
REASON=$(echo "$BODY" | jq -r '.data.reason // empty')
[[ "$ACCEPTED" == "false" ]] && pass "accepted=false" \
    || fail "expected accepted=false, duplicate guard not triggered"
[[ "$REASON" == *"duplicate"* ]] && pass "reason mentions duplicate" \
    || fail "reason mismatch: $REASON"


section "T08  (可选) 等待 ${WAIT_AFTER_CLEAN_SECONDS}s 后复扫，stale 用户应已离开名单"
if [[ "${SKIP_WAIT:-0}" == "1" ]]; then
    echo "  (SKIP_WAIT=1, skip)"
else
    echo "  sleeping ${WAIT_AFTER_CLEAN_SECONDS}s ..."
    sleep "$WAIT_AFTER_CLEAN_SECONDS"
    parse_resp "$(http_get "$MANAGER" "$MANAGER_PWD" "$BASE_URL")"
    [[ "$STATUS" == "200" ]] && pass "HTTP 200" || fail "HTTP=$STATUS"
    NEW_STALE_COUNT=$(echo "$BODY" | jq '.data.stats.confirmedStaleCount')
    FOUND=$(echo "$BODY" | jq --arg u "$STALE" '.data.members | map(select(.userId == $u)) | length')
    [[ "$FOUND" == "0" ]] && pass "stale user '$STALE' no longer in list" \
        || fail "stale user '$STALE' still appears in list"
    if [[ -n "${INITIAL_STALE_COUNT:-}" ]]; then
        echo "  ↳ confirmedStaleCount: $INITIAL_STALE_COUNT → $NEW_STALE_COUNT"
    fi
fi


# ---------------- 汇总 ----------------
echo
echo "============================================================"
echo -e "PASS: ${GREEN}${PASS}${NC}    FAIL: ${RED}${FAIL}${NC}"
echo "============================================================"
[[ "$FAIL" -eq 0 ]] && exit 0 || exit 1
