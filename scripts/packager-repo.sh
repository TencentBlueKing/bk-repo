#!/bin/bash
# 生成bk-repo安装包.
# 收集编译产物, 生成所需的安装包目录, 然后打包.
set -eu
trap "on_ERR;" ERR
on_ERR (){
  local fn=$0 ret=$? lineno=${BASH_LINENO:-$LINENO}
  echo >&2 "ERROR $fn exit with $ret at line $lineno: $(sed -n ${lineno}p $0)."
}

my_path="$(readlink -f "$0")"
my_dir=${my_path%/*}
[ -d "$my_dir" ] || { echo >&2 "ERROR: my_dir is NOT an existed dir: $my_dir."; return 3; }
cmd_collect_ci_ms_name="$my_dir/bk-ci-collect-ms-name.sh"
cmd_repo_slim="$my_dir/bk-repo-slim.sh"

collect_frontend (){
  echo "collect_frontend"
  mkdir -p "$ci_pkg_dir/frontend" "$ci_pkg_dir/support-files/templates"
  cp -a "$ci_bin_frontend_dir/." "$ci_pkg_dir/frontend"
  echo "collect page templates."
  find "$ci_pkg_dir/frontend" -name "frontend#*" -exec mv -v {} "$ci_pkg_dir/support-files/templates" \;
}

collect_backend (){
  echo "collect_backend"
  # 收集fatjar, slim化.
  mkdir -p "$ci_pkg_dir/backend"
  svcs=$(ls "$ci_bin_msjar_dir" | sed -n 's/boot-\(.*\).jar/\1/p')
  for ms in $svcs; do
    "$cmd_repo_slim" "$ms" "$ci_bin_msjar_dir/boot-$ms.jar" "$ci_pkg_dir/backend"
  done
}

collect_dirs (){
  local e=0
  for d in "$@"; do
    if [ -d "$d" ]; then
      cp -r "$ci_code_dir/$d" "$ci_pkg_dir"
    else
      echo "WARING: dir does not exist: $d."
      let ++e
    fi
  done
  return "$e"
}

collect_gateway (){
  cp -r "$ci_code_dir/src/gateway" "$ci_pkg_dir"
}

packager_ci (){
  mkdir -p "$ci_pkg_dir"
  echo "ci_code_dir is $ci_code_dir."
  echo "ci_pkg_dir is $ci_pkg_dir."
  collect_dirs "support-files" "scripts" "src/gateway"
  collect_dirs "docs" || echo "skip docs."  # 可选.
  collect_backend
  collect_frontend
  echo "gen version:"
  echo "$VERSION" | tee "$ci_pkg_dir/VERSION"
  echo "BK_CI_VERSION=\"$VERSION\"" | tee -a "$ci_pkg_dir/scripts/bkenv.properties"
  echo "generate $ci_pkg_path from $ci_pkg_dir."
  (cd "$ci_pkg_dir/.."; tar acf "$ci_pkg_path" "$(basename "$ci_pkg_dir")"; )
  ls -l "$ci_pkg_path"
}

if [ $# -lt 2 ]; then
  echo "Usage: $0 VERSION CI_PKG_PATH CI_CODE_DIR  -- make install package"
  echo " VERSION      version string. example: 1.X.X-desc"
  echo " CI_PKG_PATH  generated install package path."
  echo " CI_CODE_DIR  source code with compiled binaries."
  echo "Example: $0 1.5.0-RELEASE workspace/bkci-slim.tar.gz workspace/bk-ci-v1.5.0"
  echo "ENV:"
  echo " ci_pkg_dir   temp dir contains ci package files, should end with /ci/."
  exit 0
else
  VERSION="$1"
  ci_pkg_path="$(readlink -f "$2")"
  ci_code_dir="${3:-${my_dir%/*}}"  # 默认为本脚本的上层目录.
  ci_pkg_dir="${ci_pkg_dir:-$ci_code_dir/repo}"  # 默认为代码目录下的ci目录.
  # 编译后的目录, 其他目录为code_dir的相对路径, 不提供修改.
  ci_bin_frontend_dir="${ci_bin_frontend_dir:-$ci_code_dir/src/frontend/frontend/}"
  ci_bin_msjar_dir=${ci_bin_msjar_dir:-$ci_code_dir/src/backend/release/}
  packager_ci
fi