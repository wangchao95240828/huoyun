#!/usr/bin/env python3
"""
全自动 runner: 拉 xqt-saas 待跟踪运单 → 调爬虫 HTTP 服务 → 推 ingest。

依赖：dev-jiang 分支的 python/fedex/main.py 已经在跑：
    uv run main.py --port=8080

调度: cron / systemd timer 每 N 分钟跑一次。

环境变量:
  XQT_BASE_URL          (默认 http://127.0.0.1:18103)
  XQT_INGEST_TOKEN      (必填)
  XQT_ADMIN_TOKEN       (拉 pending 列表用；或 stdin 灌运单号)
  FEDEX_CRAWLER_URL     (默认 http://127.0.0.1:8080)
  FEDEX_TIMEOUT         (默认 60，秒)
"""
import json
import os
import sys
import time
from urllib import request as urlreq, error as urlerr

BASE          = os.environ.get("XQT_BASE_URL", "http://127.0.0.1:18103")
INGEST_TOKEN  = os.environ.get("XQT_INGEST_TOKEN", "")
ADMIN_TOKEN   = os.environ.get("XQT_ADMIN_TOKEN", "")
CRAWLER_URL   = os.environ.get("FEDEX_CRAWLER_URL", "http://127.0.0.1:8080").rstrip("/")
TIMEOUT       = int(os.environ.get("FEDEX_TIMEOUT", "60"))


def fetch_pending() -> list[dict]:
    """从 xqt 拉「待更新轨迹」的运单号。
    优先 admin token；没有就从 stdin \\n 分隔读。"""
    if not ADMIN_TOKEN:
        if not sys.stdin.isatty():
            return [{"trackingNo": ln.strip()} for ln in sys.stdin if ln.strip()]
        print("错误：无 XQT_ADMIN_TOKEN 且 stdin 无输入", file=sys.stderr)
        sys.exit(2)

    req = urlreq.Request(
        f"{BASE}/api/acc/shipments?pageSize=500",
        headers={"Authorization": f"Bearer {ADMIN_TOKEN}"},
    )
    with urlreq.urlopen(req, timeout=15) as resp:
        j = json.load(resp)
    out = []
    for r in j.get("data", []):
        if r.get("status") in ("DELIVERED", "CLOSED", "DRAFT"):
            continue
        tn = r.get("track_no") or r.get("trackingNo") or r.get("tracking_no")
        # FedEx 跟踪号纯数字
        if tn and tn.isdigit():
            out.append({"trackingNo": tn, "shipmentNo": r.get("no")})
    return out


def crawl(tn: str) -> dict | None:
    """调 dev-jiang 的 FastAPI 爬虫: GET /{trackingNo}?timeout=N"""
    url = f"{CRAWLER_URL}/{tn}?timeout={TIMEOUT}"
    try:
        with urlreq.urlopen(url, timeout=TIMEOUT + 10) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text)
    except urlerr.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:300]
        print(f"  ✗ crawler HTTP {e.code}: {body}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  ✗ crawler {type(e).__name__}: {e}", file=sys.stderr)
        return None


def push_to_xqt(data: dict) -> bool:
    data.setdefault("carrier", "FEDEX")
    body = json.dumps(data).encode("utf-8")
    req = urlreq.Request(
        f"{BASE}/api/acc/tracking/ingest",
        data=body, method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Ingest-Token": INGEST_TOKEN,
        },
    )
    try:
        with urlreq.urlopen(req, timeout=30) as resp:
            j = json.load(resp)
            print(f"  → ingest: inserted={j.get('inserted')} skipped={j.get('skipped')} "
                  f"shipmentId={j.get('shipmentId') or '(unmatched)'}")
            return True
    except urlerr.HTTPError as e:
        print(f"  → ingest HTTP {e.code}: {e.read().decode()[:200]}", file=sys.stderr)
        return False


def main():
    if not INGEST_TOKEN:
        print("错误：XQT_INGEST_TOKEN 未配置", file=sys.stderr); sys.exit(2)

    pending = fetch_pending()
    print(f"待跟踪运单 {len(pending)} 个\n")

    ok, fail = 0, 0
    for i, item in enumerate(pending, 1):
        tn = item["trackingNo"]
        print(f"[{i}/{len(pending)}] {tn}")
        data = crawl(tn)
        if data is None:
            fail += 1; continue
        data.setdefault("tracking_number", tn)
        if push_to_xqt(data):
            ok += 1
        else:
            fail += 1
        time.sleep(2)  # 礼貌延时

    print(f"\n完成: 成功 {ok} / 失败 {fail}")
    sys.exit(0 if fail == 0 else 1)


if __name__ == "__main__":
    main()
