#!/bin/bash
# ============================================================================
#  appget.sh — 多 Apple ID 管理 + 按优先级下载解密（macOS / Linux 通用）
#
#  账号管理:
#    ./appget.sh account add <邮箱> [--priority N] [--credential 路径]
#                                      添加账号（会走一次登录，之后免密切换）
#    ./appget.sh account login <邮箱>   重新登录并刷新会话快照
#    ./appget.sh account import          把当前已登录的账号加入列表并保存会话
#    ./appget.sh account save [邮箱]     只保存当前会话快照（不重新登录）
#    ./appget.sh account list           查看账号列表（优先级/凭证/会话状态）
#    ./appget.sh account priority <邮箱> <N>   调整优先级（1 最高）
#    ./appget.sh account credential <邮箱> <路径>  绑定/更换该账号的 credential.bin
#    ./appget.sh account remove <邮箱>  删除账号
#    ./appget.sh switch <邮箱>          手动切换当前 ipatool 会话
#
#  下载解密:
#    ./appget.sh get <App名称|bundle id|数字ID>   按优先级逐个账号尝试
#    ./appget.sh search <关键词>                   搜索 App
#    ./appget.sh status                           当前状态
#    ./appget.sh install                          下载/安装 ipatool
#
#  get 选项:
#    --platform iphone|ipad   默认 iphone
#    --version-id <id>        指定版本
#    --keep-encrypted         保留加密包
#    --force, -f              覆盖已存在结果
#    --account <邮箱>          只用指定账号
#
#  平台:
#    macOS  会话快照取自系统钥匙串（security），切换时替换钥匙串条目
#    Linux  强制 ipatool 文件密钥后端，快照为 ~/.ipatool/account 的密文，
#           口令自动生成在 .ipatool-passphrase（0600），无需桌面密钥环
#
#  依赖: ipatool（自动安装）、fairplay-decrypt/.venv、每个账号的 credential.bin
#  详细说明（原理/排障/给 AI 的操作守则）见同目录 README-AI.md
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FPDIR="$(cd "$ROOT/../fairplay-decrypt" 2>/dev/null && pwd || true)"
IPATOOL="${IPATOOL:-$ROOT/bin/ipatool}"
PY="$FPDIR/.venv/bin/python"
DECRYPT_PY="$FPDIR/decrypt.py"
ACCOUNTS_PY="$ROOT/accounts.py"
SESS_DIR="${SESS_DIR:-$ROOT/accounts}"
DL_DIR="${DL_DIR:-$ROOT/downloads}"
OUT_DIR="${OUT_DIR:-$ROOT/decrypted}"
SECURITY="${SECURITY:-security}"
SVC="ipatool-auth.service"
KC_ACCOUNT="account"

if [ -n "${XDG_STATE_HOME:-}" ] && [ "${XDG_STATE_HOME#/}" != "$XDG_STATE_HOME" ]; then
  STATE_DIR="$XDG_STATE_HOME/ipatool"
else
  STATE_DIR="$HOME/.ipatool"
fi
if [ -d "$HOME/.ipatool" ]; then STATE_DIR="$HOME/.ipatool"; fi

if [ -t 1 ]; then
  B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; D=$'\033[90m'; N=$'\033[0m'
else
  B=; G=; Y=; R=; D=; N=
fi
ok()   { printf '%s✔%s %s\n' "$G" "$N" "$*"; }
warn() { printf '%s!%s %s\n' "$Y" "$N" "$*" >&2; }
die()  { printf '%s✘%s %s\n' "$R" "$N" "$*" >&2; exit 1; }
hr()   { printf '%s\n' "${D}────────────────────────────────────────────────────────────${N}"; }
say()  { printf '%s\n' "$*"; }

# ------------------------------------------------------------------ 平台适配
PLAT="$(uname -s)"
case "$PLAT" in
  Darwin) PLAT="mac" ;;
  Linux)  PLAT="linux" ;;
  *)      die "不支持的平台: ${PLAT}（当前支持 macOS 与 Linux）" ;;
esac

file_size() {
  if [ "$PLAT" = "mac" ]; then stat -f%z "$1" 2>/dev/null; else stat -c%s "$1" 2>/dev/null; fi
}
b64d() {
  if [ "$PLAT" = "mac" ]; then base64 -D; else base64 -d; fi
}
sha256_of() {
  if [ "$PLAT" = "mac" ]; then shasum -a 256 "$1" | awk '{print $1}'; else sha256sum "$1" | awk '{print $1}'; fi
}

# Linux: 强制 ipatool 使用文件密钥后端（~/.ipatool/account），
# 与桌面密钥环解耦，服务器/桌面行为一致；口令由 --keychain-passphrase 提供。
KPASS="${IPATOOL_KEYCHAIN_PASSPHRASE:-}"
KPASS_FILE="$ROOT/.ipatool-passphrase"

prepare_linux_keyring() {
  [ "$PLAT" = "linux" ] || return 0
  if [ -z "$KPASS" ] && [ -s "$KPASS_FILE" ]; then KPASS="$(cat "$KPASS_FILE")"; fi
  if [ -z "$KPASS" ]; then
    KPASS="$(LC_ALL=C tr -dc 'a-f0-9' < /dev/urandom 2>/dev/null | head -c 48 || true)"
    [ -n "$KPASS" ] || KPASS="appget-fallback-passphrase"
    ( umask 077; printf '%s' "$KPASS" > "$KPASS_FILE" )
    chmod 600 "$KPASS_FILE" 2>/dev/null || true
    printf '%s提示:%s 已生成 ipatool 密钥环口令并保存到 %s（0600）；可用 IPATOOL_KEYCHAIN_PASSPHRASE 覆盖\n' "$Y" "$N" "$KPASS_FILE"
  fi
  return 0
}

# 统一调用入口：Linux 追加 --keychain-passphrase，并屏蔽 DBus 以固定文件后端
ipa() {
  if [ "$PLAT" = "linux" ]; then
    DBUS_SESSION_BUS_ADDRESS="unix:path=/nonexistent-appget" "$IPATOOL" "$@" --keychain-passphrase "$KPASS"
  else
    "$IPATOOL" "$@"
  fi
}

usage() {
  awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "${BASH_SOURCE[0]}"
  exit 0
}

cfg() { "$PY" "$ACCOUNTS_PY" "$@"; }

# ------------------------------------------------------------------ 基础工具
slug() { printf '%s' "$1" | sed 's/[^A-Za-z0-9._@+-]/_/g'; }

# ------------------------------------------------------------------ ipatool
ensure_ipatool() {
  [ -x "$IPATOOL" ] && return 0
  if command -v ipatool >/dev/null 2>&1; then IPATOOL="$(command -v ipatool)"; return 0; fi
  install_ipatool
}

install_ipatool() {
  local ver="${IPATOOL_VERSION:-v2.7.0}" arch name os_part base tmp found
  case "$(uname -m)" in
    arm64|aarch64) arch="arm64" ;;
    x86_64|amd64)  arch="amd64" ;;
    *) die "不支持的 CPU 架构: $(uname -m)" ;;
  esac
  if [ "$PLAT" = "mac" ]; then os_part="macos"; else os_part="linux"; fi
  name="ipatool-${ver#v}-${os_part}-${arch}.tar.gz"
  tmp="$ROOT/tmpdl"
  mkdir -p "$tmp" "$ROOT/bin"
  printf '%s下载 ipatool %s (%s-%s)%s\n' "$B" "$ver" "$os_part" "$arch" "$N"

  for base in \
      "https://gh-proxy.com/https://github.com" \
      "https://ghfast.top/https://github.com" \
      "https://github.com"; do
    if curl -fsSL --connect-timeout 10 -m 240 -o "$tmp/$name" \
         "$base/majd/ipatool/releases/download/${ver}/${name}"; then
      if curl -fsSL --connect-timeout 10 -m 60 -o "$tmp/$name.sha256" \
           "$base/majd/ipatool/releases/download/${ver}/${name}.sha256sum" 2>/dev/null; then
        if [ "$(awk '{print $1}' "$tmp/$name.sha256")" != "$(sha256_of "$tmp/$name")" ]; then
          warn "校验失败，换下一个源"; continue
        fi
      fi
      rm -f "$tmp/$name.sha256" "$tmp/$name.sha256sum"
      tar -xzf "$tmp/$name" -C "$tmp" || { warn "解压失败"; continue; }
      found="$(find "$tmp" -type f -name "ipatool-${ver#v}-${os_part}-${arch}" 2>/dev/null | head -1)"
      [ -n "$found" ] || { warn "包内找不到 ipatool"; continue; }
      cp "$found" "$IPATOOL" && chmod +x "$IPATOOL" || { warn "安装失败"; continue; }
      "$IPATOOL" --version >/dev/null 2>&1 || { warn "安装的二进制无法运行，换下一个源"; continue; }
      [ "$PLAT" = "mac" ] && xattr -d com.apple.quarantine "$IPATOOL" 2>/dev/null
      ok "ipatool 已安装: $IPATOOL"
      return 0
    fi
    warn "源不可用: $base"
  done
  die "ipatool 下载失败；可手动下载 $name 放到 $ROOT/bin/ipatool"
}

ensure_env() {
  [ -n "$FPDIR" ] && [ -d "$FPDIR" ] || die "找不到 fairplay-decrypt 目录"
  [ -x "$PY" ] || die "找不到 Python 环境: $PY"
  [ -f "$DECRYPT_PY" ] || die "找不到 $DECRYPT_PY"
  ensure_ipatool
  prepare_linux_keyring
  mkdir -p "$SESS_DIR" "$DL_DIR" "$OUT_DIR"
  chmod 700 "$SESS_DIR" 2>/dev/null || true
}

current_email() {
  ipa auth info --format json 2>/dev/null \
    | sed -n 's/.*"email":"\([^"]*\)".*/\1/p' | head -1
}

sess_file() { printf '%s/%s.session' "$SESS_DIR" "$(slug "$1")"; }
cook_file() { printf '%s/%s.cookies' "$SESS_DIR" "$(slug "$1")"; }

# ------------------------------------------------------------------ 会话快照
# macOS: 读取钥匙串条目；Linux: 读取 ipatool 文件后端的加密凭据文件
kc_read_to() {
  case "$PLAT" in
    mac)
      "$SECURITY" find-generic-password -s "$SVC" -a "$KC_ACCOUNT" -w > "$1" 2>/dev/null || return 1
      [ -s "$1" ] || return 1 ;;
    linux)
      [ -f "$STATE_DIR/account" ] || return 1
      cp "$STATE_DIR/account" "$1" 2>/dev/null || return 1
      [ -s "$1" ] || return 1 ;;
  esac
  return 0
}

kc_write_from() {
  case "$PLAT" in
    mac)
      local blob
      blob="$(cat "$1" 2>/dev/null || true)"
      [ -n "$blob" ] || return 1
      "$SECURITY" delete-generic-password -s "$SVC" -a "$KC_ACCOUNT" >/dev/null 2>&1 || true
      if ! "$SECURITY" add-generic-password -s "$SVC" -a "$KC_ACCOUNT" -w "$blob" -A >/dev/null 2>&1; then
        "$SECURITY" add-generic-password -s "$SVC" -a "$KC_ACCOUNT" -w "$blob" -T "$IPATOOL" >/dev/null 2>&1 || return 1
      fi ;;
    linux)
      mkdir -p "$STATE_DIR" 2>/dev/null || true
      chmod 700 "$STATE_DIR" 2>/dev/null || true
      cp "$1" "$STATE_DIR/account.tmp.$$" 2>/dev/null || return 1
      chmod 600 "$STATE_DIR/account.tmp.$$" 2>/dev/null || true
      mv "$STATE_DIR/account.tmp.$$" "$STATE_DIR/account" 2>/dev/null || return 1 ;;
  esac
  return 0
}

# 保存指定账号的会话快照（macOS 可能弹一次钥匙串授权）
snapshot() {
  local email="$1" tmp
  [ -n "$email" ] || return 1
  tmp="$(sess_file "$email").raw.$$"
  ( umask 077; kc_read_to "$tmp" ) || { rm -f "$tmp"; return 1; }
  ( umask 077; base64 < "$tmp" | tr -d '\n' > "$(sess_file "$email")" ) || { rm -f "$tmp"; return 1; }
  rm -f "$tmp"
  chmod 600 "$(sess_file "$email")" 2>/dev/null || true
  if [ -f "$STATE_DIR/cookies" ]; then
    cp "$STATE_DIR/cookies" "$(cook_file "$email")" 2>/dev/null || true
    chmod 600 "$(cook_file "$email")" 2>/dev/null || true
  fi
  return 0
}

# 切到指定账号：0 成功 / 2 没有会话快照 / 1 失败
activate() {
  local email="$1" cur raw
  cur="$(current_email || true)"
  [ "$cur" = "$email" ] && return 0

  [ -s "$(sess_file "$email")" ] || return 2
  [ -n "$cur" ] && snapshot "$cur" >/dev/null 2>&1 || true

  raw="$(sess_file "$email").raw.$$"
  ( umask 077; b64d < "$(sess_file "$email")" > "$raw" 2>/dev/null ) || { rm -f "$raw"; return 1; }
  [ -s "$raw" ] || { rm -f "$raw"; return 1; }
  if ! kc_write_from "$raw"; then rm -f "$raw"; return 1; fi
  rm -f "$raw"

  if [ -f "$(cook_file "$email")" ]; then
    cp "$(cook_file "$email")" "$STATE_DIR/cookies" 2>/dev/null || true
    chmod 600 "$STATE_DIR/cookies" 2>/dev/null || true
  fi
  [ "$(current_email || true)" = "$email" ] || return 1
  return 0
}

ensure_session() {
  local email
  if current_email | grep -q .; then return 0; fi
  email="$(cfg order 2>/dev/null | head -1 || true)"
  [ -n "$email" ] || return 1
  activate "$email" >/dev/null 2>&1 || return 1
}

# ------------------------------------------------------------------ 账号命令
cmd_account_add() {
  local email="" priority="" cred="" cur
  while [ $# -gt 0 ]; do
    case "$1" in
      --priority)   priority="${2:?}"; shift 2 ;;
      --credential) cred="${2:?}"; shift 2 ;;
      -*)           die "未知参数: $1" ;;
      *)            email="$1"; shift ;;
    esac
  done
  ensure_env
  [ -n "$email" ] || die "用法: account add <邮箱> [--priority N] [--credential 路径]"
  if cfg list | awk -F'\t' -v e="$email" 'tolower($2)==tolower(e){f=1} END{exit !f}'; then
    die "账号已存在: ${email}（用 account login 刷新会话）"
  fi

  cur="$(current_email || true)"
  [ -n "$cur" ] && snapshot "$cur" >/dev/null 2>&1 || true

  printf '%s登录 %s%s\n' "$B" "$email" "$N"
  printf '  依次输入：密码（不回显）→ 如需要，6 位验证码\n\n'
  if ! ipa auth login -e "$email"; then
    hr; die "登录失败，未添加账号"
  fi
  [ "$(current_email || true)" = "$email" ] || die "登录后的账号与 $email 不一致"

  printf '%s保存会话快照%s（可能弹出钥匙串授权：输入 Mac 密码并点「始终允许」）\n' "$B" "$N"
  if snapshot "$email"; then
    ok "会话已保存，之后切换不再需要密码/验证码"
  else
    warn "会话保存失败：该账号当前可用，但切走后再切回来需要重新 login"
  fi

  [ -z "$cred" ] && [ -f "$ROOT/creds/$email/credential.bin" ] && cred="creds/$email/credential.bin"

  local -a add_args=(add --email "$email")
  [ -n "$priority" ] && add_args+=(--priority "$priority")
  [ -n "$cred" ] && add_args+=(--credential "$cred")
  cfg "${add_args[@]}" >/dev/null
  ok "账号已添加"
  cmd_account_list
}

cmd_account_save() {
  local email="${1:-}"
  ensure_env
  [ -n "$email" ] || email="$(current_email || true)"
  [ -n "$email" ] || die "当前没有登录账号，请指定邮箱: account save <邮箱>"
  printf '%s保存 %s 的会话快照%s（如弹出钥匙串授权：输入 Mac 密码并点「始终允许」）\n' "$B" "$email" "$N"
  if snapshot "$email"; then ok "会话已保存"; else warn "保存失败（钥匙串读取被拒绝或条目不存在）"; fi
}

cmd_account_import() {
  local cred=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --credential) cred="${2:?}"; shift 2 ;;
      *) die "未知参数: $1" ;;
    esac
  done
  ensure_env
  local email; email="$(current_email || true)"
  [ -n "$email" ] || die "当前没有登录账号"
  if ! cfg list | awk -F'\t' -v e="$email" 'tolower($2)==tolower(e){f=1} END{exit !f}'; then
    [ -z "$cred" ] && cred="credential.bin"
    cfg add --email "$email" --credential "$cred" >/dev/null
    ok "已把当前账号 $email 加入列表"
  fi
  cmd_account_save "$email"
  cmd_account_list
}

cmd_account_login() {
  local email="${1:-}" cur
  ensure_env
  [ -n "$email" ] || die "用法: account login <邮箱>"
  cur="$(current_email || true)"
  if [ -n "$cur" ] && [ "$cur" != "$email" ]; then
    say "先保存当前账号 $cur 的会话快照，避免被覆盖"
    snapshot "$cur" >/dev/null 2>&1 || warn "当前账号会话快照失败（不影响后续登录）"
  fi
  ipa auth login -e "$email" || die "登录失败"
  [ "$(current_email || true)" = "$email" ] || die "登录后的账号与 $email 不一致"
  if snapshot "$email"; then ok "会话已刷新"; else warn "会话快照失败"; fi
}

cmd_account_list() {
  ensure_env
  local cur line prio email cred hascred sess mark
  cur="$(current_email || true)"
  hr
  printf '%s%-4s %-28s %-6s %-6s %s%s\n' "$B" "#" "Apple ID" "凭证" "会话" "凭证路径" "$N"
  while IFS=$'\t' read -r prio email cred hascred; do
    [ -n "${prio:-}" ] || continue
    [ -s "$(sess_file "$email")" ] && sess="✔" || sess="✘"
    [ "$hascred" = "1" ] && mark="${G}✔${N}" || mark="${R}✘${N}"
    [ "$email" = "$cur" ] && email="$email ${Y}(当前)${N}"
    printf '%-4s %-28s %-6s %-6s %s\n' "$prio" "$email" "$mark" "$sess" "${cred:-（未绑定）}"
  done < <(cfg list --check)
  hr
  say "「凭证」= credential.bin 是否存在且为 16 字节；「会话」= 是否保存了免密登录快照"
}

cmd_account_priority() {
  local email="${1:-}" prio="${2:-}"
  [ -n "$email" ] && [ -n "$prio" ] || die "用法: account priority <邮箱> <N>"
  cfg move --email "$email" --priority "$prio" >/dev/null
  ok "已调整优先级"
  cmd_account_list
}

cmd_account_remove() {
  local email="${1:-}"
  [ -n "$email" ] || die "用法: account remove <邮箱>"
  cfg remove --email "$email" >/dev/null
  rm -f "$(sess_file "$email")" "$(cook_file "$email")"
  ok "已删除 $email"
}

cmd_account_credential() {
  local email="${1:-}" path="${2:-}"
  [ -n "$email" ] && [ -n "$path" ] || die "用法: account credential <邮箱> <credential.bin 路径>"
  [ -f "$path" ] && [ "$(file_size "$path")" = "16" ] || die "凭证无效（需要 16 字节文件）: $path"
  cfg credential --email "$email" --path "$path" >/dev/null
  ok "已绑定 $email → $path"
}

cmd_switch() {
  local email="${1:-}"
  ensure_env
  [ -n "$email" ] || die "用法: switch <邮箱>"
  local rc
  set +e; activate "$email"; rc=$?; set -e
  [ $rc -eq 2 ] && die "该账号没有会话快照，先执行: ./appget.sh account login $email"
  [ $rc -eq 0 ] || die "切换失败"
  ok "当前账号: $(current_email)"
}

# ------------------------------------------------------------------ App 解析
resolve_target() {
  local arg="$1" json out
  if printf '%s' "$arg" | grep -qE '^[0-9]+$'; then
    printf 'id:%s\n' "$arg"; return 0
  fi
  if printf '%s' "$arg" | grep -qE '^[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z0-9-]+$'; then
    printf 'bundle:%s\n' "$arg"; return 0
  fi
  ensure_session || die "没有可用登录会话，先执行: ./appget.sh account add <邮箱>"
  json="$(ipa search "$arg" --format json -l 10 2>/dev/null || true)"
  [ -n "$json" ] || die "搜索失败"
  out="$(printf '%s' "$json" | "$PY" -c '
import json, sys
term = sys.argv[1].strip().lower()
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(1)
apps = data.get("apps") or []
if not apps:
    sys.exit(2)
exact = [a for a in apps if (a.get("name") or "").strip().lower() == term]
pick = exact[0] if exact else apps[0]
print("bundle:" + pick["bundleID"])
for a in apps[:6]:
    print("    %-28s %s  (%s)" % (a["bundleID"], a.get("name", ""), a.get("version", "")))
' "$arg")" || die "找不到 App: $arg"
  printf '%s\n' "$out"
}

# ------------------------------------------------------------------ 下载/解密
do_download() {
  local kind="$1" value="$2" out="$3" platform="$4" vid="$5" log="$6"
  local -a args
  if [ "$kind" = "id" ]; then args=(-i "$value"); else args=(-b "$value"); fi
  [ -n "$vid" ] && args+=(--external-version-id "$vid")
  ipa download "${args[@]}" --platform "$platform" --purchase -o "$out" >"$log" 2>&1
}

ipa_meta() {
  "$PY" - "$1" <<'PY'
import plistlib, sys, zipfile

try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        cands = [n for n in z.namelist()
                 if n.startswith("Payload/") and n.endswith(".app/Info.plist")
                 and n.count("/") == 2]
        if not cands:
            raise SystemExit("no Info.plist")
        info = plistlib.loads(z.read(sorted(cands)[0]))
except Exception as exc:  # noqa: BLE001
    print("error=%s" % exc)
    raise SystemExit(0)

for key in ("CFBundleIdentifier", "CFBundleShortVersionString", "CFBundleVersion", "CFBundleExecutable"):
    print("%s=%s" % (key, info.get(key, "")))
PY
}

meta_get() { printf '%s' "$1" | sed -n "s/^$2=//p" | head -1; }

check_cryptid() {
  "$PY" - "$1" "$2" <<'PY'
import struct, sys, zipfile

ipa, exe = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(ipa) as z:
    names = [n for n in z.namelist()
             if n.startswith("Payload/") and n.endswith("/" + exe) and n.count("/") == 2]
    if not names:
        print("missing"); raise SystemExit(0)
    with z.open(names[0]) as f:
        head = f.read(4 << 20)
if len(head) < 32 or struct.unpack("<I", head[:4])[0] != 0xFEEDFACF:
    print("not-macho"); raise SystemExit(0)
ncmds, sizeofcmds = struct.unpack("<II", head[16:24])
off, end = 32, min(32 + sizeofcmds, len(head))
for _ in range(ncmds):
    if off + 8 > end: break
    cmd, cmdsize = struct.unpack("<II", head[off:off + 8])
    if cmdsize <= 0: break
    if cmd == 0x2C:
        print(struct.unpack("<I", head[off + 16:off + 20])[0]); raise SystemExit(0)
    off += cmdsize
print("none")
PY
}

# ---------------------------------------------------------------------- get
cmd_get() {
  local arg="${1:-}"
  [ -n "$arg" ] || die "用法: get <App名称|bundle id|数字ID>"
  shift

  local platform="iphone" vid="" keep=0 force=0 only=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --platform)       platform="${2:?}"; shift 2 ;;
      --version-id)     vid="${2:?}"; shift 2 ;;
      --keep-encrypted) keep=1; shift ;;
      --force|-f)       force=1; shift ;;
      --account)        only="${2:?}"; shift 2 ;;
      -h|--help)        usage ;;
      *)                die "未知参数: $1" ;;
    esac
  done

  ensure_env

  local resolved kind bundle first
  resolved="$(resolve_target "$arg")"
  first="${resolved%%$'\n'*}"
  kind="${first%%:*}"
  bundle="${first#*:}"
  if [ "$kind" = "id" ]; then
    bundle=""
  else
    say "目标: $bundle"
    local cands
    cands="$(printf '%s\n' "$resolved" | tail -n +2)"
    [ -n "$cands" ] && printf '%s\n' "$cands"
  fi

  local -a emails=() ordered=()
  while IFS= read -r e; do [ -n "$e" ] && emails+=("$e"); done < <(cfg order)
  [ ${#emails[@]} -gt 0 ] || die "还没有账号，先执行: ./appget.sh account add <邮箱>"

  if [ -n "$only" ]; then
    emails=("$only")
  elif [ -n "$bundle" ]; then
    local pref; pref="$(cfg preferred --bundle "$bundle" 2>/dev/null || true)"
    if [ -n "$pref" ]; then
      ordered+=("$pref")
      for e in "${emails[@]}"; do [ "$e" = "$pref" ] || ordered+=("$e"); done
      emails=("${ordered[@]}")
      say "该 App 上次由 $pref 处理，优先使用"
    fi
  fi

  local total=${#emails[@]} i=0 email cred rc dl enc out meta ver exe log
  for email in "${emails[@]}"; do
    i=$((i + 1))
    hr
    say "${B}[$i/$total] $email${N}"

    cred="$(cfg get --email "$email" 2>/dev/null || true)"
    [ -n "$cred" ] || { warn "未绑定凭证，跳过（用 account credential 绑定）"; continue; }
    case "$cred" in /*) ;; *) cred="$ROOT/$cred" ;; esac
    if [ ! -f "$cred" ] || [ "$(file_size "$cred")" != "16" ]; then
      warn "凭证无效: $cred"; continue
    fi

    set +e; activate "$email"; rc=$?; set -e
    if [ $rc -eq 2 ]; then warn "没有登录会话快照 → ./appget.sh account login $email"; continue; fi
    [ $rc -eq 0 ] || { warn "切换账号失败"; continue; }

    dl="$DL_DIR/.part-$$.ipa"
    log="$DL_DIR/.part-$$.log"
    rm -f "$dl" "$log"
    say "  下载中..."
    if ! do_download "$kind" "$bundle" "$dl" "$platform" "$vid" "$log"; then
      warn "该账号无法下载此 App"
      grep -aE '"error"|ERR' "$log" 2>/dev/null | tail -2 | sed 's/^/    /' || true
      rm -f "$dl" "$log"
      continue
    fi
    [ -s "$dl" ] || { warn "下载结果为空"; rm -f "$dl" "$log"; continue; }
    rm -f "$log"

    meta="$(ipa_meta "$dl")"
    ver="$(meta_get "$meta" CFBundleShortVersionString)"
    exe="$(meta_get "$meta" CFBundleExecutable)"
    [ -n "$bundle" ] || bundle="$(meta_get "$meta" CFBundleIdentifier)"
    [ -n "$bundle" ] && [ "$kind" = "id" ] && kind="bundle"
    [ -n "$ver" ] || ver="unknown"
    say "  App: $bundle  $ver"

    out="$OUT_DIR/${bundle}-${ver}.decrypted.ipa"
    if [ -e "$out" ] && [ "$force" != "1" ]; then
      warn "已存在: $out"
      rm -f "$dl"
      say "  （加 --force 覆盖；该 App 归属账号已确认为 ${email}）"
      cfg set-preferred --bundle "$bundle" --email "$email" || true
      rm -f "$dl"
      return 0
    fi
    [ -e "$out" ] && rm -f "$out"

    say "  解密中..."
    set +e
    "$PY" "$DECRYPT_PY" "$dl" "$out" --credential "$cred" >"$DL_DIR/.dec-$$.log" 2>&1
    rc=$?
    set -e
    if [ $rc -ne 0 ]; then
      if grep -qi 'does not decode this SINF' "$DL_DIR/.dec-$$.log"; then
        warn "该 App 不属于这个账号（凭证不匹配），换下一个"
      else
        warn "解密失败:"
        tail -3 "$DL_DIR/.dec-$$.log" | sed 's/^/    /'
      fi
      rm -f "$dl" "$DL_DIR/.dec-$$.log" "$out"
      continue
    fi
    rm -f "$DL_DIR/.dec-$$.log"

    ok "解密完成: $out ($(du -h "$out" | awk '{print $1}'))"
    if [ -n "$exe" ]; then
      local cid; cid="$(check_cryptid "$out" "$exe")"
      case "$cid" in
        0)    ok "校验: $exe cryptid=0" ;;
        none) say "    校验: $exe 无加密段" ;;
        *)    warn "校验异常: cryptid=$cid" ;;
      esac
    fi

    cfg set-preferred --bundle "$bundle" --email "$email" || true
    if [ "$keep" = "1" ]; then
      mv "$dl" "$DL_DIR/${bundle}-${ver}.encrypted.ipa"
      say "    加密包保留: $DL_DIR/${bundle}-${ver}.encrypted.ipa"
    else
      rm -f "$dl"
    fi
    say "    归属账号: ${email}（下次会优先用它）"
    return 0
  done

  hr
  die "所有账号都没能处理 $arg"
}

# ---------------------------------------------------------------------- 入口
cmd="${1:-}"
case "$cmd" in
  ""|-h|--help|help)  usage ;;
  account|acct)
    shift
    sub="${1:-}"; shift || true
    case "$sub" in
      add|a)          cmd_account_add "$@" ;;
      login)          cmd_account_login "$@" ;;
      save)           cmd_account_save "$@" ;;
      import)         cmd_account_import "$@" ;;
      list|ls|"")     cmd_account_list ;;
      priority|pri)   cmd_account_priority "$@" ;;
      credential|cred) cmd_account_credential "$@" ;;
      remove|rm)      cmd_account_remove "$@" ;;
      *)              die "未知子命令: account $sub" ;;
    esac ;;
  install)            ensure_ipatool ;;
  switch|use)         shift; cmd_switch "$@" ;;
  get|d|decrypt)      shift; cmd_get "$@" ;;
  search)             shift; ensure_env; ensure_session || die "先登录一个账号"
                      ipa search "${1:?用法: search <关键词>}" --platform iphone -l "${2:-10}" ;;
  status)
    ensure_env
    hr
    say "当前 ipatool 账号: ${B}$(current_email || echo "（未登录）")${N}"
    say "配置文件: $ROOT/accounts.json"
    say "会话目录: $SESS_DIR"
    cmd_account_list ;;
  *)                  cmd_get "$@" ;;
esac
