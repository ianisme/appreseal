#!/usr/bin/env python3
"""accounts.json 管理：Apple ID 列表、优先级、凭证绑定、App→账号偏好。

优先级 = 数组顺序，1 最高。每次写入会重排 priority 字段。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CFG = Path(os.environ.get("ACC_CONFIG", str(ROOT / "accounts.json")))


def load() -> dict:
    if CFG.exists():
        try:
            data = json.loads(CFG.read_text())
            data.setdefault("accounts", [])
            data.setdefault("apps", {})
            return data
        except Exception:
            pass
    return {"accounts": [], "apps": {}}


def save(cfg: dict) -> None:
    for i, acc in enumerate(cfg["accounts"], 1):
        acc["priority"] = i
    tmp = CFG.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n")
    os.chmod(tmp, 0o600)
    os.replace(tmp, CFG)


def find(cfg: dict, email: str) -> dict | None:
    for acc in cfg["accounts"]:
        if acc["email"].lower() == email.lower():
            return acc
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("list")
    p.add_argument("--check", action="store_true", help="标记凭证/会话文件是否存在")

    p = sub.add_parser("add")
    p.add_argument("--email", required=True)
    p.add_argument("--credential", default="")
    p.add_argument("--priority", type=int, default=0)

    p = sub.add_parser("remove")
    p.add_argument("--email", required=True)

    p = sub.add_parser("move")
    p.add_argument("--email", required=True)
    p.add_argument("--priority", type=int, required=True)

    p = sub.add_parser("credential")
    p.add_argument("--email", required=True)
    p.add_argument("--path", required=True)

    sub.add_parser("order")

    p = sub.add_parser("preferred")
    p.add_argument("--bundle", required=True)

    p = sub.add_parser("set-preferred")
    p.add_argument("--bundle", required=True)
    p.add_argument("--email", required=True)

    p = sub.add_parser("get")
    p.add_argument("--email", required=True)

    args = parser.parse_args()
    cfg = load()

    if args.cmd == "list":
        if args.check:
            for acc in cfg["accounts"]:
                cred = Path(acc.get("credential") or "")
                okc = cred.is_file() and cred.stat().st_size == 16
                print(f'{acc["priority"]}\t{acc["email"]}\t{acc.get("credential", "")}\t{int(okc)}')
        else:
            for acc in cfg["accounts"]:
                print(f'{acc["priority"]}\t{acc["email"]}\t{acc.get("credential", "")}')
        return

    if args.cmd == "add":
        if find(cfg, args.email):
            sys.exit(f"账号已存在: {args.email}")
        entry = {"email": args.email, "credential": args.credential, "priority": len(cfg["accounts"]) + 1}
        pos = args.priority if args.priority > 0 else len(cfg["accounts"]) + 1
        pos = max(1, min(pos, len(cfg["accounts"]) + 1))
        cfg["accounts"].insert(pos - 1, entry)
        save(cfg)
        print(f'added {args.email} -> priority {pos}')
        return

    if args.cmd == "remove":
        acc = find(cfg, args.email)
        if not acc:
            sys.exit(f"账号不存在: {args.email}")
        cfg["accounts"].remove(acc)
        for bundle, email in list(cfg["apps"].items()):
            if email.lower() == args.email.lower():
                del cfg["apps"][bundle]
        save(cfg)
        print(f'removed {args.email}')
        return

    if args.cmd == "move":
        acc = find(cfg, args.email)
        if not acc:
            sys.exit(f"账号不存在: {args.email}")
        cfg["accounts"].remove(acc)
        pos = max(1, min(args.priority, len(cfg["accounts"]) + 1))
        cfg["accounts"].insert(pos - 1, acc)
        save(cfg)
        print(f'moved {args.email} -> priority {pos}')
        return

    if args.cmd == "credential":
        acc = find(cfg, args.email)
        if not acc:
            sys.exit(f"账号不存在: {args.email}")
        acc["credential"] = args.path
        save(cfg)
        print(f'{args.email} credential -> {args.path}')
        return

    if args.cmd == "order":
        for acc in cfg["accounts"]:
            print(acc["email"])
        return

    if args.cmd == "preferred":
        print(cfg["apps"].get(args.bundle, ""))
        return

    if args.cmd == "set-preferred":
        cfg["apps"][args.bundle] = args.email
        save(cfg)
        return

    if args.cmd == "get":
        acc = find(cfg, args.email)
        if not acc:
            sys.exit(1)
        print(acc.get("credential", ""))
        return


if __name__ == "__main__":
    main()
