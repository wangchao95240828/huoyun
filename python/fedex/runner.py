#!/usr/bin/env python3
"""
全自动 runner: 拉 xqt-saas 待跟踪运单 → 按 carrier 分流轮询 → 落 tracking_events。

- UPS 运单 (前缀 "1Z"): 直接调 xqt-saas POST /api/acc/tracking/poll-ups
  后端用 acc_channel_accounts 已配置的 UPS OAuth 凭证查 UPS Track API。
- FedEx 运单 (纯数字): 调本地 dev-jiang 爬虫 GET :8080/{tn} → POST /api/acc/tracking/ingest

调度: cron / systemd timer 每 N 分钟跑一次。

环境变量:
  XQT_BASE_URL         (默认 http://127.0.0.1:18103)
  XQT_INGEST_TOKEN     (必填)
  XQT_ADMIN_TOKEN      (拉 pending 列表用)
  FEDEX_CRAWLER_URL    (默认 http://127.0.0.1:8080)
  FEDEX_TIMEOUT        (默认 60)
"""
import json
import os
import sys
import time
from urllib import request as urlreq, error as urlerr, parse as urlparse

BASE         = os.environ.get("XQT_BASE_URL", "http://127.0.0.1:18103")
INGEST_TOKEN = os.environ.get("XQT_INGEST_TOKEN", "")
ADMIN_TOKEN  = os.environ.get("XQT_ADMIN_TOKEN", "")
CRAWLER_URL  = os.environ.get("FEDEX_CRAWLER_URL", "http://127.0.0.1:8080").rstrip("/")
TIMEOUT      = int(os.environ.get("FEDEX_TIMEOUT", "60"))


def classify(tn: str) -> str | None:
    """按运单号格式判断承运商。"""
    t = tn.strip().upper()
    if t.startswith("1Z") and len(t) == 18:
        return "UPS"
    if t.isdigit() and 12 <= len(t) <= 22:
        return "FEDEX"
    return None


def fetch_pending() -> list[dict]:
    """拉待跟踪运单。优先 admin token；没有就从 stdin \\n 分隔读。"""
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
        if tn:
            out.append({"trackingNo": tn})
    return out


# ─── FedEx: 调爬虫 + POST ingest ───
def crawl_fedex(tn: str) -> dict | None:
    url = f"{CRAWLER_URL}/{tn}?timeout={TIMEOUT}"
    try:
        with urlreq.urlopen(url, timeout=TIMEOUT + 10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urlerr.HTTPError as e:
        print(f"  ✗ fedex crawler HTTP {e.code}: {e.read().decode()[:200]}", file=sys.stderr)
    except Exception as e:
        print(f"  ✗ fedex crawler {type(e).__name__}: {e}", file=sys.stderr)
    return None


def push_fedex(data: dict) -> bool:
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
            print(f"  → fedex ingest: inserted={j.get('inserted')} skipped={j.get('skipped')} "
                  f"shipmentId={j.get('shipmentId') or '(unmatched)'}")
            return True
    except urlerr.HTTPError as e:
        print(f"  → fedex ingest HTTP {e.code}: {e.read().decode()[:200]}", file=sys.stderr)
        return False


# ─── UPS: 后端代理调 UPS Track API ───
def poll_ups(tn: str) -> bool:
    qs = urlparse.urlencode({"trackingNo": tn})
    req = urlreq.Request(
        f"{BASE}/api/acc/tracking/poll-ups?{qs}",
        method="POST",
        headers={"X-Ingest-Token": INGEST_TOKEN},
    )
    try:
        with urlreq.urlopen(req, timeout=45) as resp:
            j = json.load(resp)
            print(f"  → ups poll: inserted={j.get('inserted')} skipped={j.get('skipped')} "
                  f"shipmentId={j.get('shipmentId') or '(unmatched)'}")
            return True
    except urlerr.HTTPError as e:
        print(f"  → ups poll HTTP {e.code}: {e.read().decode()[:200]}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"  → ups poll {type(e).__name__}: {e}", file=sys.stderr)
        return False


def main():
    if not INGEST_TOKEN:
        print("错误：XQT_INGEST_TOKEN 未配置", file=sys.stderr); sys.exit(2)

    pending = fetch_pending()
    counts = {"UPS": 0, "FEDEX": 0, "UNKNOWN": 0}
    for p in pending: counts[classify(p["trackingNo"]) or "UNKNOWN"] += 1
    print(f"待跟踪运单 {len(pending)} 个 (UPS={counts['UPS']} FEDEX={counts['FEDEX']} "
          f"UNKNOWN={counts['UNKNOWN']})\n")

    ok, fail = 0, 0
    for i, item in enumerate(pending, 1):
        tn = item["trackingNo"]
        carrier = classify(tn)
        print(f"[{i}/{len(pending)}] {carrier or '?':5s} {tn}")
        if carrier == "UPS":
            success = poll_ups(tn)
        elif carrier == "FEDEX":
            data = crawl_fedex(tn)
            if data is None:
                fail += 1; continue
            data.setdefault("tracking_number", tn)
            success = push_fedex(data)
        else:
            print(f"  ⚠ 未知承运商，跳过")
            continue
        ok += 1 if success else 0
        fail += 0 if success else 1
        time.sleep(2)  # 礼貌延时避免被风控

    print(f"\n完成: 成功 {ok} / 失败 {fail}")
    sys.exit(0 if fail == 0 else 1)


if __name__ == "__main__":
    main()
