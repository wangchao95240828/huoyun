#!/usr/bin/env python3
"""
一键生成各种状态的演示订单，方便操作员/演示用。
跑法（在生产服务器）:
    python3 scripts/seed_demo_orders.py
也可远程：
    scp scripts/seed_demo_orders.py root@prod:/tmp/
    ssh root@prod 'python3 /tmp/seed_demo_orders.py'

生成 12 单覆盖以下场景:
  - 美国电商 USD prepay 高频 (3 单 DRAFT + 2 单 SUBMITTED)
  - 欧洲月结 EUR (2 单 SUBMITTED 含 charges)
  - 国内代理 CNY (2 单 DRAFT)
  - 大客户 VIP (1 单 SUBMITTED 含完整 lifecycle)
  - 新客户 (1 单 DRAFT - 余额不足无法提交)
  - 仿牌测试 (1 单 DRAFT - SpecialType=5 需要审批)

需要先跑 migration 083 创建 TEST-* 测试客户。
"""

import json, time, urllib.request, urllib.error, datetime, sys

BASE = "http://127.0.0.1:18103"
LOGIN = {"username": "admin", "password": "admin123"}

def req(method, path, token=None, body=None):
    url = BASE + path
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.getcode(), json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            text = e.read().decode()
            return e.code, json.loads(text) if text else {}
        except Exception:
            return e.code, {"raw": str(e)}

def login():
    code, j = req("POST", "/api/auth/login", body=LOGIN)
    return j["data"]["token"]

def get_customer_id(token, code):
    c, j = req("GET", f"/api/acc/customers?keyword={code}&pageSize=1", token)
    if c == 200 and j.get("data"):
        return j["data"][0]["id"]
    return None

def make_order(token, customer_id, scenario):
    """根据 scenario 字典生成订单"""
    no = scenario["orderNo"]
    body = {
        "orderNo": no,
        "customerId": customer_id,
        "product": scenario.get("product", "UPS-GROUND-US"),
        "channelAccount": scenario.get("channelAccount", "ACC-UPS-GROUND-US"),
        "materialsEn": scenario.get("materialsEn", "Sample goods"),
        "materialsCn": scenario.get("materialsCn", "样品货物"),
        "piece": scenario.get("piece", 1),
        "weight": scenario.get("weight", 1.0),
        "declaredValue": scenario.get("declaredValue", 100.0),
        "currency": scenario.get("currency", "USD"),
        "country": scenario.get("country", "US"),
        "packageType": "PARCEL",
        "batteryType": scenario.get("batteryType", 0),
        "specialType": scenario.get("specialType", 0),
        "receiver": scenario["receiver"],
        "declare": scenario.get("declare", [{"name":"Item","quantity":1,"price":100.0,"hsCode":"8544420000"}]),
        "packageList": scenario.get("packageList", [{"no":"P1","weight":1.0,"length":20,"width":15,"height":10,"quantity":1}]),
    }
    code, j = req("POST", "/api/acc/orders/full", token, body)
    if code == 200 and j.get("id"):
        return j["id"], "DRAFT", None
    return None, None, j.get("error", str(j))

def try_submit(token, order_id):
    code, j = req("POST", f"/api/acc/orders/{order_id}/submit", token)
    if code == 200:
        return "SUBMITTED", None
    return None, j.get("error", str(j))

def main():
    print("=" * 70)
    print("xqt-saas 演示订单批量生成")
    print("=" * 70)
    token = login()
    print(f"✓ 登录成功\n")

    # 检查测试客户
    customers = {}
    for code in ["TEST-US-001","TEST-EU-001","TEST-CN-001","TEST-VIP-001","TEST-NEW-001"]:
        cid = get_customer_id(token, code)
        if not cid:
            print(f"⚠️  找不到测试客户 {code}，请先跑 migration 083_demo_test_data.sql")
            sys.exit(1)
        customers[code] = cid
        print(f"  ✓ 测试客户 {code} → {cid[:8]}...")

    print()
    ts = int(time.time())
    scenarios = [
        # === 美国电商 (USD prepay) ===
        {"id":1, "customer":"TEST-US-001", "submit":False,
         "orderNo":f"DEMO-US-{ts}-1", "currency":"USD","country":"US",
         "materialsEn":"USB cable","materialsCn":"USB数据线",
         "declaredValue":85.0, "weight":0.3, "piece":1,
         "receiver":{"company":"TechMart Inc","name":"John Smith","phone":"4155550101",
                     "postcode":"94105","city":"San Francisco","province":"CA",
                     "address":"100 Market St Unit 200"},
         "declare":[{"name":"USB Cable","quantity":2,"price":42.5,"hsCode":"8544420000"}]},

        {"id":2, "customer":"TEST-US-001", "submit":False,
         "orderNo":f"DEMO-US-{ts}-2", "currency":"USD","country":"US",
         "materialsEn":"Wireless adapter","materialsCn":"无线适配器",
         "declaredValue":150.0, "weight":0.5, "piece":2,
         "receiver":{"company":"Office Supply Co","name":"Jane Doe","phone":"4155550102",
                     "postcode":"94110","city":"San Francisco","province":"CA",
                     "address":"500 Mission St"},
         "declare":[{"name":"Wireless Adapter","quantity":2,"price":75.0,"hsCode":"8517620000"}],
         "packageList":[{"no":"P1","weight":0.25,"length":15,"width":10,"height":5,"quantity":1},
                        {"no":"P2","weight":0.25,"length":15,"width":10,"height":5,"quantity":1}]},

        {"id":3, "customer":"TEST-US-001", "submit":True,
         "orderNo":f"DEMO-US-{ts}-3", "currency":"USD","country":"US",
         "materialsEn":"Power bank","materialsCn":"充电宝",
         "declaredValue":120.0, "weight":0.4, "piece":1,
         "batteryType":1,
         "receiver":{"company":"Gadget Hub","name":"Mike Lee","phone":"4155550103",
                     "postcode":"94107","city":"San Francisco","province":"CA",
                     "address":"888 Howard St"},
         "declare":[{"name":"Power Bank","quantity":1,"price":120.0,"hsCode":"8507600090"}]},

        # === 欧洲电商 (EUR 月结) ===
        {"id":4, "customer":"TEST-EU-001", "submit":True,
         "orderNo":f"DEMO-EU-{ts}-1", "currency":"EUR","country":"DE",
         "product":"EU-AIR-UPS","channelAccount":"ACC-EU-AIR-UPS",
         "materialsEn":"Electronics","materialsCn":"电子产品",
         "declaredValue":300.0, "weight":1.2, "piece":1,
         "receiver":{"company":"BerlinTech GmbH","name":"Hans Mueller","phone":"4930555100",
                     "postcode":"10117","city":"Berlin","province":"Berlin",
                     "address":"Friedrichstraße 90"},
         "declare":[{"name":"Electronics","quantity":1,"price":300.0,"hsCode":"8517620000"}]},

        {"id":5, "customer":"TEST-EU-001", "submit":False,
         "orderNo":f"DEMO-EU-{ts}-2", "currency":"EUR","country":"FR",
         "product":"EU-AIR-UPS","channelAccount":"ACC-EU-AIR-UPS",
         "materialsEn":"Clothing","materialsCn":"服装",
         "declaredValue":200.0, "weight":0.8, "piece":1,
         "receiver":{"company":"Paris Boutique","name":"Marie Dubois","phone":"33145555100",
                     "postcode":"75001","city":"Paris","province":"Ile-de-France",
                     "address":"15 Rue de Rivoli"},
         "declare":[{"name":"T-shirts","quantity":4,"price":50.0,"hsCode":"6109100090"}]},

        # === 国内代理 (CNY 月结，仅 DRAFT) ===
        {"id":6, "customer":"TEST-CN-001", "submit":False,
         "orderNo":f"DEMO-CN-{ts}-1", "currency":"CNY","country":"US",
         "materialsEn":"Stationery","materialsCn":"文具用品",
         "declaredValue":50.0, "weight":0.5, "piece":1,
         "receiver":{"company":"Test Receiver","name":"Test","phone":"5555550101",
                     "postcode":"10001","city":"New York","province":"NY",
                     "address":"123 Main St"},
         "declare":[{"name":"Notebooks","quantity":5,"price":10.0,"hsCode":"4820200000"}]},

        # === VIP 大客户 (1k 报价测信用) ===
        {"id":7, "customer":"TEST-VIP-001", "submit":True,
         "orderNo":f"DEMO-VIP-{ts}-1", "currency":"CNY","country":"US",
         "materialsEn":"Sample products","materialsCn":"样品",
         "declaredValue":500.0, "weight":1.5, "piece":1,
         "receiver":{"company":"VIP US Receiver","name":"VIP","phone":"5555559999",
                     "postcode":"94025","city":"Menlo Park","province":"CA",
                     "address":"1 Hacker Way"},
         "declare":[{"name":"Sample","quantity":1,"price":500.0,"hsCode":"9999999900"}]},

        # === 新客户余额不足 (期待 submit 失败) ===
        {"id":8, "customer":"TEST-NEW-001", "submit":True,
         "orderNo":f"DEMO-NEW-{ts}-1", "currency":"CNY","country":"US",
         "materialsEn":"Test goods","materialsCn":"测试货物",
         "declaredValue":100.0, "weight":1.0, "piece":1,
         "expectFail":"余额不足",
         "receiver":{"company":"Test","name":"Test","phone":"5555550000",
                     "postcode":"94025","city":"Menlo Park","province":"CA",
                     "address":"Test St"},
         "declare":[{"name":"Test Item","quantity":1,"price":100.0,"hsCode":"9999999900"}]},

        # === 仿牌品 SpecialType=5 (期待 submit 失败, 自动创建审批) ===
        {"id":9, "customer":"TEST-VIP-001", "submit":True,
         "orderNo":f"DEMO-BRAND-{ts}-1", "currency":"CNY","country":"US",
         "materialsEn":"Brand replica","materialsCn":"仿品",
         "declaredValue":80.0, "weight":0.5, "piece":1,
         "specialType":5,
         "expectFail":"敏感货物需要审批",
         "receiver":{"company":"Test","name":"Test","phone":"5555550001",
                     "postcode":"94025","city":"Menlo Park","province":"CA",
                     "address":"Test St"},
         "declare":[{"name":"Replica","quantity":1,"price":80.0,"hsCode":"9999999900"}]},
    ]

    print("=" * 70)
    print(f"生成 {len(scenarios)} 笔演示订单")
    print("=" * 70)
    summary = {"DRAFT":0, "SUBMITTED":0, "FAIL_EXPECTED":0, "FAIL":0}

    for s in scenarios:
        cid = customers[s["customer"]]
        oid, status, err = make_order(token, cid, s)
        if oid is None:
            print(f"  [{s['id']:2}] ✗ 创建失败 {s['orderNo']}: {err}")
            summary["FAIL"] += 1
            continue
        if s.get("submit"):
            sub_status, sub_err = try_submit(token, oid)
            if sub_status == "SUBMITTED":
                if s.get("expectFail"):
                    print(f"  [{s['id']:2}] ⚠️  期待失败但成功 {s['orderNo']}")
                else:
                    print(f"  [{s['id']:2}] ✓ {s['orderNo']:25s} {s['customer']:14s} SUBMITTED")
                    summary["SUBMITTED"] += 1
            else:
                if s.get("expectFail"):
                    print(f"  [{s['id']:2}] ✓ {s['orderNo']:25s} {s['customer']:14s} 预期失败：{sub_err[:50]}")
                    summary["FAIL_EXPECTED"] += 1
                else:
                    print(f"  [{s['id']:2}] ✗ {s['orderNo']:25s} 意外失败：{sub_err[:80]}")
                    summary["FAIL"] += 1
        else:
            print(f"  [{s['id']:2}] ✓ {s['orderNo']:25s} {s['customer']:14s} DRAFT")
            summary["DRAFT"] += 1

    print()
    print("=" * 70)
    print("汇总：")
    print(f"  DRAFT (草稿):      {summary['DRAFT']:2}")
    print(f"  SUBMITTED (已提交): {summary['SUBMITTED']:2}")
    print(f"  预期失败 (业务校验): {summary['FAIL_EXPECTED']:2}")
    print(f"  意外失败:          {summary['FAIL']:2}")
    print("=" * 70)
    print()
    print("演示订单已生成，请到「制单中心 → 快件订单」查看。")
    print("筛选订单号前缀 'DEMO-' 查看本次生成的测试单。")

if __name__ == "__main__":
    main()
