#!/usr/bin/env python3
"""
把 FedEx 爬虫输出的 JSON 推送到 xqt-saas tracking-ingest 接口。

用法：
  # 单文件
  python pusher.py result.json

  # 批量目录
  python pusher.py /path/to/results/*.json

  # stdin 管道
  cat result.json | python pusher.py -

环境变量 (放 .env 或导出):
  XQT_BASE_URL=https://xqt-saas.com   # 默认 http://127.0.0.1:18103
  XQT_INGEST_TOKEN=...                # 必填，与后端 xqt.tracking.ingest-token 一致
"""
import argparse
import glob
import json
import os
import sys
from urllib import request as urlreq, error as urlerr

DEFAULT_BASE = os.environ.get("XQT_BASE_URL", "http://127.0.0.1:18103")
INGEST_TOKEN = os.environ.get("XQT_INGEST_TOKEN", "")


def push_one(path: str, base: str, token: str) -> tuple[bool, dict]:
    if path == "-":
        data = json.load(sys.stdin)
        src_label = "<stdin>"
    else:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        src_label = path

    # 自动补 carrier 字段（用户的 result.json 没有该字段时默认 FEDEX）
    data.setdefault("carrier", "FEDEX")

    body = json.dumps(data).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "X-Ingest-Token": token,
    }
    req = urlreq.Request(
        f"{base}/api/acc/tracking/ingest",
        data=body, headers=headers, method="POST",
    )
    try:
        with urlreq.urlopen(req, timeout=30) as resp:
            text = resp.read().decode("utf-8")
            j = json.loads(text or "{}")
            print(f"✓ {src_label}: tn={j.get('trackingNo')} "
                  f"inserted={j.get('inserted')} skipped={j.get('skipped')} "
                  f"shipmentId={j.get('shipmentId') or '(unmatched)'}")
            return True, j
    except urlerr.HTTPError as e:
        text = e.read().decode("utf-8", errors="replace")
        print(f"✗ {src_label}: HTTP {e.code} {text[:200]}", file=sys.stderr)
        return False, {"error": text}
    except Exception as e:
        print(f"✗ {src_label}: {type(e).__name__}: {e}", file=sys.stderr)
        return False, {"error": str(e)}


def main():
    parser = argparse.ArgumentParser(description="Push FedEx tracking JSON to xqt-saas")
    parser.add_argument("paths", nargs="+",
                        help="json 文件路径或 glob，用 - 从 stdin 读")
    parser.add_argument("--base", default=DEFAULT_BASE, help="xqt-saas base URL")
    parser.add_argument("--token", default=INGEST_TOKEN,
                        help="X-Ingest-Token (建议用环境变量 XQT_INGEST_TOKEN)")
    args = parser.parse_args()

    if not args.token:
        print("错误：未配置 INGEST_TOKEN（环境变量 XQT_INGEST_TOKEN 或 --token）", file=sys.stderr)
        sys.exit(2)

    # 展开 glob
    paths: list[str] = []
    for p in args.paths:
        if p == "-":
            paths.append(p)
        else:
            matched = glob.glob(p)
            paths.extend(matched if matched else [p])

    ok, fail = 0, 0
    for p in paths:
        success, _ = push_one(p, args.base, args.token)
        if success: ok += 1
        else: fail += 1

    print(f"\n总计: 成功 {ok} / 失败 {fail} / 共 {ok+fail}")
    sys.exit(0 if fail == 0 else 1)


if __name__ == "__main__":
    main()
