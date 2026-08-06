#!/usr/bin/env bash
# bk-repo-assign-global-preview-role.sh
#
# 制品库（bk-repo）「全局预览角色」运维脚本
#   - 该角色为系统预置 system 级角色，绑定后用户对所有资源仅有只读/预览权限
#   - 角色与其它角色互斥：用户被加入该角色时，其它角色会被自动覆盖移除
#   - 该角色不可被删除（后端已做保护）
#
# 用法:
#   bk-repo-assign-global-preview-role.sh role-id
#   bk-repo-assign-global-preview-role.sh add    <user|user1,user2,...>
#   bk-repo-assign-global-preview-role.sh remove <user|user1,user2,...>
#   bk-repo-assign-global-preview-role.sh list
#
# 必要环境变量:
#   BK_REPO_HOST   bk-repo 网关地址，如 https://bkrepo.example.com
#   BK_REPO_AUTH   认证凭据，二选一:
#                    1. "admin:password"          -> 走 Basic
#                    2. "Platform <ak>:<sk>"      -> 走平台账号
#                  注: 若使用平台账号，需附带管理员 Bk-Repo-Uid:
#                    BK_REPO_UID=admin
#
# 示例:
#   export BK_REPO_HOST=https://bkrepo.example.com
#   export BK_REPO_AUTH='admin:xxxxxx'
#   ./bk-repo-assign-global-preview-role.sh add userA,userB
#

set -eu

trap 'on_ERR' ERR
on_ERR() {
  local fn=$0 ret=$? lineno=${BASH_LINENO:-$LINENO}
  echo >&2 "ERROR $fn exit with $ret at line $lineno: $(sed -n "${lineno}"p "$0")."
}

# ---------- 依赖检查 ----------
command -v curl >/dev/null 2>&1 || { echo >&2 "ERROR: curl 未安装"; exit 1; }
command -v jq   >/dev/null 2>&1 || { echo >&2 "ERROR: jq 未安装"; exit 1; }

# ---------- 参数检查 ----------
: "${BK_REPO_HOST:?ERROR: 请先 export BK_REPO_HOST=https://your-bkrepo}"
: "${BK_REPO_AUTH:?ERROR: 请先 export BK_REPO_AUTH (admin:pwd 或 Platform <ak>:<sk>)}"

BK_REPO_HOST="${BK_REPO_HOST%/}"   # 去掉末尾 /
BK_REPO_UID="${BK_REPO_UID:-admin}"

# 构造 curl 鉴权参数数组
AUTH_ARGS=()
if [[ "$BK_REPO_AUTH" == Platform\ * ]]; then
  AUTH_ARGS=(-H "Authorization: ${BK_REPO_AUTH}" -H "Bk-Repo-Uid: ${BK_REPO_UID}")
else
  AUTH_ARGS=(-u "$BK_REPO_AUTH")
fi

# ---------- 公共方法 ----------
# 调用 bk-repo HTTP API，自动附带鉴权头；输出 raw response
api() {
  local method=$1 path=$2; shift 2
  curl -sS -X "$method" "${AUTH_ARGS[@]}" "$@" "${BK_REPO_HOST}${path}"
}

# 校验响应 code==0，并 echo .data
api_data() {
  local resp=$1
  local code
  code=$(echo "$resp" | jq -r '.code // empty')
  if [[ "$code" != "0" ]]; then
    echo >&2 "ERROR: api fail, resp=$resp"
    return 1
  fi
  echo "$resp" | jq -r '.data'
}

# 懒触发并取回全局预览角色 _id（幂等）
get_role_id() {
  local resp
  resp=$(api POST /auth/api/role/create/global/preview)
  api_data "$resp"
}

# 单个用户加入角色
add_one() {
  local uid=$1 rid=$2 resp
  resp=$(api POST "/auth/api/user/role/${uid}/${rid}")
  api_data "$resp" >/dev/null
  echo "  [+] ${uid} -> global_preview (rid=${rid})"
}

# 单个用户移除角色
remove_one() {
  local uid=$1 rid=$2 resp
  resp=$(api DELETE "/auth/api/user/role/${uid}/${rid}")
  api_data "$resp" >/dev/null
  echo "  [-] ${uid} removed from global_preview"
}

# 列出当前全局预览角色的所有成员
list_members() {
  local rid=$1 resp
  resp=$(api GET "/auth/api/role/users/${rid}")
  api_data "$resp" | jq -r '.[] | "  - " + (.userId // .uid // .)'
}

# 把 "a,b,c" 或 "a b c" 拆分成数组
split_users() {
  echo "$1" | tr ',' '\n' | awk 'NF{print $1}'
}

# ---------- 子命令分发 ----------
usage() {
  sed -n '2,30p' "$0"
  exit 1
}

CMD="${1:-}"
case "$CMD" in
  role-id)
    rid=$(get_role_id)
    echo "global_preview role _id = ${rid}"
    ;;

  add)
    [[ $# -ge 2 ]] || usage
    rid=$(get_role_id)
    echo "global_preview role _id = ${rid}"
    echo "覆盖式分配（用户原有的项目级角色将被自动解除）："
    while read -r u; do
      [[ -n "$u" ]] && add_one "$u" "$rid"
    done < <(split_users "$2")
    ;;

  remove)
    [[ $# -ge 2 ]] || usage
    rid=$(get_role_id)
    echo "global_preview role _id = ${rid}"
    while read -r u; do
      [[ -n "$u" ]] && remove_one "$u" "$rid"
    done < <(split_users "$2")
    ;;

  list)
    rid=$(get_role_id)
    echo "global_preview role _id = ${rid}"
    echo "当前成员列表："
    list_members "$rid"
    ;;

  ""|-h|--help)
    usage
    ;;

  *)
    echo "ERROR: 未知子命令: $CMD"
    usage
    ;;
esac
