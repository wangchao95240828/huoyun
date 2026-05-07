# API Request Body 总表

生成日期：2026-05-07  
适用范围：新航线统一平台 Spring Boot 正式 API，以及为完全匹配 ACC / 新智慧而规划的目标 API。  
说明：本文档只定义 request body，不涉及代码实现。`GET`、`DELETE` 接口统一明确为“无 request body”，查询条件放 query string 或 path variable。

## 1. 通用约定

### 1.1 查询类 body

所有正式 `POST .../search` 查询接口优先使用统一包装：

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {},
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ],
  "includeExternalFields": false
}
```

当前已实现的卖货/制单订单接口仍支持轻量 body：

```json
{
  "page": 1,
  "pageSize": 20,
  "keyword": "",
  "customerCode": "",
  "status": ""
}
```

### 1.2 客户方向

| 流程 | `customerDirection` | `serviceMode` | 路径 |
| --- | --- | --- | --- |
| 卖货流程 | `SELLER_CUSTOMER` | `SELLER_FULFILLMENT` | `/api/seller/*` |
| 制单流程 | `DOCUMENT_CUSTOMER` | `DOCUMENT_SHIPPING` | `/api/document/*`, `/api/customer-api/*` |

`/api/seller/orders/*` 和 `/api/document/orders/*` 当前由后端根据路径自动写入方向，不要求前端在 body 中重复传。

## 2. 基础与认证 API

### 2.1 `GET /api/health`

无 request body。

### 2.2 `POST /api/auth/login`

```json
{
  "tenantCode": "xqt",
  "username": "admin",
  "password": "Admin@123456"
}
```

### 2.3 `GET /api/auth/me`

无 request body。

### 2.4 `POST /api/auth/logout`

无 request body。

### 2.5 `GET /api/business-flows`

无 request body。

## 3. 系统管理 API

### 3.1 `GET /api/admin/users`

无 request body。查询参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码 |
| `pageSize` | 每页数量 |
| `keyword` | 用户名、姓名、邮箱关键字 |
| `status` | 用户状态 |

### 3.2 `POST /api/admin/users`

```json
{
  "username": "operator01",
  "displayName": "操作员",
  "email": "operator@example.com",
  "phone": "",
  "password": "Initial@123456",
  "status": "ACTIVE",
  "roleIds": []
}
```

### 3.3 `PUT /api/admin/users/{id}`

```json
{
  "displayName": "操作员",
  "email": "operator@example.com",
  "phone": "",
  "status": "ACTIVE",
  "roleIds": []
}
```

### 3.4 `DELETE /api/admin/users/{id}`

无 request body。

### 3.5 `GET /api/admin/roles`

无 request body。

### 3.6 `POST /api/admin/roles`

```json
{
  "code": "finance",
  "name": "财务",
  "description": "财务人员",
  "permissionCodes": [
    "finance.receivable.read",
    "finance.payable.read"
  ]
}
```

### 3.7 `PUT /api/admin/roles/{id}`

```json
{
  "name": "财务",
  "description": "财务人员",
  "permissionCodes": [
    "finance.receivable.read",
    "finance.payable.read"
  ]
}
```

### 3.8 `PUT /api/admin/roles/{id}/permissions`

```json
{
  "permissionCodes": [
    "business.flow.read",
    "flow.seller.read",
    "flow.document.read"
  ]
}
```

### 3.9 `DELETE /api/admin/roles/{id}`

无 request body。

### 3.10 `GET /api/admin/permissions`

无 request body。

### 3.11 `GET /api/admin/audit-logs`

无 request body。查询参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码 |
| `pageSize` | 每页数量 |
| `entityType` | 实体类型 |
| `action` | 操作动作 |
| `operatorId` | 操作人 |
| `createdFrom` | 开始时间 |
| `createdTo` | 结束时间 |

## 4. 主数据 API

主数据接口用于客户、供应商、服务、渠道、仓库、币种、费用类型等基础资料。路径按资源名区分：

| 资源 | 路径前缀 |
| --- | --- |
| 客户 | `/api/master-data/customers` |
| 供应商 | `/api/master-data/partners` |
| 服务 | `/api/master-data/services` |
| 渠道 | `/api/master-data/channels` |
| 仓库 | `/api/master-data/warehouses` |
| 币种 | `/api/master-data/currencies` |
| 费用类型 | `/api/master-data/charge-types` |

### 4.1 `POST /api/master-data/customers/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "customerDirection": "",
    "serviceMode": "",
    "accountMode": "",
    "status": "ACTIVE",
    "sourceSystem": "",
    "sellerId": "",
    "servicerId": "",
    "financeId": ""
  },
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ]
}
```

### 4.2 `POST /api/master-data/customers`

```json
{
  "code": "CUST001",
  "name": "客户名称",
  "customerDirection": "SELLER_CUSTOMER",
  "serviceModes": [
    "SELLER_FULFILLMENT"
  ],
  "accountMode": "MONTHLY",
  "defaultCurrency": "CNY",
  "creditLimit": 0,
  "sellerId": "",
  "servicerId": "",
  "financeId": "",
  "contacts": [
    {
      "name": "联系人",
      "phone": "",
      "email": "",
      "isDefault": true
    }
  ],
  "metadata": {}
}
```

### 4.3 `PUT /api/master-data/customers/{id}`

```json
{
  "name": "客户名称",
  "customerDirection": "BOTH",
  "serviceModes": [
    "SELLER_FULFILLMENT",
    "DOCUMENT_SHIPPING"
  ],
  "accountMode": "MONTHLY",
  "defaultCurrency": "CNY",
  "creditLimit": 0,
  "sellerId": "",
  "servicerId": "",
  "financeId": "",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.4 `DELETE /api/master-data/customers/{id}`

无 request body。

### 4.5 `POST /api/master-data/partners/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "partnerType": "",
    "country": "",
    "status": "ACTIVE"
  }
}
```

### 4.6 `POST /api/master-data/partners`

```json
{
  "code": "PARTNER001",
  "name": "供应商名称",
  "partnerType": "CARRIER",
  "country": "",
  "defaultCurrency": "CNY",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.7 `PUT /api/master-data/partners/{id}`

```json
{
  "name": "供应商名称",
  "partnerType": "CARRIER",
  "country": "",
  "defaultCurrency": "CNY",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.8 `POST /api/master-data/services/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "serviceMode": "",
    "carrierId": "",
    "status": "ACTIVE"
  }
}
```

### 4.9 `POST /api/master-data/services`

```json
{
  "code": "SERVICE001",
  "name": "服务名称",
  "serviceMode": "SELLER_FULFILLMENT",
  "carrierId": "",
  "currency": "CNY",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.10 `PUT /api/master-data/services/{id}`

```json
{
  "name": "服务名称",
  "serviceMode": "SELLER_FULFILLMENT",
  "carrierId": "",
  "currency": "CNY",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.11 `POST /api/master-data/channels/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "carrierId": "",
    "country": "",
    "status": "ACTIVE"
  }
}
```

### 4.12 `POST /api/master-data/channels`

```json
{
  "code": "CHANNEL001",
  "name": "渠道名称",
  "carrierId": "",
  "country": "",
  "status": "ACTIVE",
  "metadata": {}
}
```

### 4.13 `PUT /api/master-data/channels/{id}`

```json
{
  "name": "渠道名称",
  "carrierId": "",
  "country": "",
  "status": "ACTIVE",
  "metadata": {}
}
```

## 5. 卖货流程 API

### 5.1 `POST /api/seller/orders/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "serviceMode": "SELLER_FULFILLMENT",
    "keyword": "",
    "orderNo": "",
    "customerCode": "",
    "customerId": "",
    "customerRef": "",
    "status": "",
    "createdRange": [],
    "updatedRange": []
  },
  "sorts": [
    {
      "field": "createdAt",
      "direction": "desc"
    }
  ],
  "includeExternalFields": true
}
```

### 5.2 `POST /api/seller/orders`

```json
{
  "customerCode": "SELLER-DEMO",
  "customerRef": "SELLER-REF-001",
  "serviceId": "",
  "status": "DRAFT",
  "orderEntryType": "SALES_ORDER",
  "metadata": {
    "sourcePage": "seller_order",
    "remark": ""
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "商品名称",
      "sku": "SKU-001",
      "quantity": 1,
      "declaredValue": 10,
      "declaredCurrency": "USD",
      "weightKg": 1.2,
      "metadata": {
        "cartonNo": "CTN001"
      }
    }
  ]
}
```

### 5.3 `GET /api/seller/orders/{id}`

无 request body。

### 5.4 `PUT /api/seller/orders/{id}`

```json
{
  "customerCode": "SELLER-DEMO",
  "customerRef": "SELLER-REF-001",
  "serviceId": "",
  "status": "DRAFT",
  "orderEntryType": "SALES_ORDER",
  "metadata": {
    "remark": ""
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "商品名称",
      "sku": "SKU-001",
      "quantity": 1,
      "declaredValue": 10,
      "declaredCurrency": "USD",
      "weightKg": 1.2,
      "metadata": {}
    }
  ]
}
```

### 5.5 `DELETE /api/seller/orders/{id}`

无 request body。

### 5.6 `POST /api/seller/shipments/search`

对照新智慧 `/rest/tms/aos/shipment/lists`。

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "serviceMode": "SELLER_FULFILLMENT",
    "keyword": "",
    "shipmentNo": "",
    "waybillNumber": "",
    "ladingNumber": "",
    "trackingNumber": "",
    "customerId": "",
    "customerName": "",
    "serviceCode": "",
    "country": "",
    "postcode": "",
    "sellerId": "",
    "servicerId": "",
    "financeId": "",
    "organizationId": "",
    "createdRange": [],
    "shipRange": [],
    "deliveredRange": [],
    "tags": [],
    "chargeAudit": "",
    "chargePaid": "",
    "sellChargeAmount": {
      "min": null,
      "max": null
    },
    "costChargeAmount": {
      "min": null,
      "max": null
    },
    "sellerProfit": {
      "min": null,
      "max": null
    },
    "sellProfit": {
      "min": null,
      "max": null
    }
  },
  "includeExternalFields": true
}
```

### 5.7 `POST /api/warehouse/receipts/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "warehouseId": "",
    "receiptNo": "",
    "customerId": "",
    "shipmentNo": "",
    "cartonNo": "",
    "status": "",
    "createdRange": [],
    "receivedRange": []
  }
}
```

### 5.8 `POST /api/warehouse/picklists/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "warehouseId": "",
    "picklistNo": "",
    "shipmentNo": "",
    "cartonNo": "",
    "status": "",
    "createdRange": [],
    "pickedRange": []
  }
}
```

## 6. 财务 API，对照新智慧 XQT

### 6.1 `POST /api/finance/ledger-records/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "keyword": "",
    "serialNumber": "",
    "paymentType": "",
    "audited": null,
    "companyAccountId": "",
    "customerAccountId": "",
    "partnerAccountId": "",
    "staffAccountId": "",
    "payTimeRange": [],
    "createdRange": [],
    "auditTimeRange": [],
    "creatorId": "",
    "invoiced": null,
    "amountRange": {
      "min": null,
      "max": null
    },
    "customerName": "",
    "invoiceNumber": ""
  },
  "includeExternalFields": true
}
```

### 6.2 `POST /api/finance/shipment-audits/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "keyword": "",
    "waybillNumber": "",
    "ladingNumber": "",
    "serviceCode": "",
    "customerName": "",
    "customerGrade": "",
    "payType": "",
    "country": "",
    "toWarehouseCode": "",
    "postcode": "",
    "sellerId": "",
    "servicerId": "",
    "financeId": "",
    "organizationId": "",
    "partnerService": "",
    "depotId": "",
    "pickupDepotId": "",
    "creatorId": "",
    "createdRange": [],
    "pickingRange": [],
    "ratesRange": [],
    "deliveredRange": [],
    "shipRange": [],
    "tags": [],
    "excludeTags": [],
    "chargeAudit": "",
    "chargePaid": "",
    "sellChargeAmount": {
      "min": null,
      "max": null
    },
    "costChargeAmount": {
      "min": null,
      "max": null
    },
    "sellerProfit": {
      "min": null,
      "max": null
    },
    "sellProfit": {
      "min": null,
      "max": null
    },
    "outerCarrierCode": "",
    "config": "",
    "vatNumber": "",
    "mainName": ""
  },
  "includeExternalFields": true
}
```

### 6.3 `POST /api/finance/receivable-reports/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "customerId": "",
    "customerGrade": "",
    "servicerId": "",
    "sellerId": "",
    "financeId": "",
    "currency": "",
    "payType": "",
    "dateRange": [],
    "amountRange": {
      "min": null,
      "max": null
    }
  }
}
```

### 6.4 `POST /api/finance/payable-reports/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "partnerId": "",
    "currency": "",
    "dateRange": []
  }
}
```

### 6.5 `POST /api/finance/rates/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "serviceCode": "",
    "zoneId": "",
    "customerGradeId": "",
    "customerIds": [],
    "status": "ACTIVE"
  }
}
```

### 6.6 `POST /api/finance/customer-charge-details/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "customerId": "",
    "customerGrade": "",
    "customerSellerId": "",
    "chargeType": "",
    "invoiceTimeRange": [],
    "keyword": "",
    "detailId": "",
    "invoiceNumber": "",
    "itemNumber": "",
    "shipmentId": "",
    "trackingNumber": "",
    "waybillNumber": "",
    "clientReference": "",
    "audited": null,
    "chargeAmount": {
      "min": null,
      "max": null
    },
    "currency": "",
    "paid": null,
    "invoiceStatus": null,
    "createdRange": [],
    "creatorId": "",
    "tags": [],
    "excludeTags": [],
    "serviceCode": "",
    "payTimeRange": [],
    "approverId": ""
  },
  "includeExternalFields": true
}
```

### 6.7 `POST /api/finance/customer-invoices/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "keyword": "",
    "customerName": "",
    "payType": "",
    "customerGrade": "",
    "currency": "",
    "amountRange": {
      "min": null,
      "max": null
    },
    "invoiceDateRange": [],
    "sellerId": "",
    "servicerId": "",
    "financeId": "",
    "organizationId": "",
    "dueActions": [],
    "tags": [],
    "excludeTags": [],
    "paidTimeRange": [],
    "createdRange": [],
    "shipTimeRange": [],
    "creatorId": "",
    "dueDateRange": [],
    "taxDateRange": [],
    "fluentChargeAmount": {
      "min": null,
      "max": null
    },
    "invoiceConfirm": null
  },
  "includeExternalFields": true
}
```

### 6.8 `POST /api/finance/partner-charge-details/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "partnerId": "",
    "chargeType": "",
    "invoiceTimeRange": [],
    "chargeAmount": {
      "min": null,
      "max": null
    },
    "currency": "",
    "keyword": "",
    "detailId": "",
    "invoiceNumber": "",
    "itemNumber": "",
    "shipmentId": "",
    "trackingNumber": "",
    "waybillNumber": "",
    "containerNumber": "",
    "clientReference": "",
    "audited": null,
    "paid": null,
    "invoiceStatus": null,
    "createdRange": [],
    "creatorId": "",
    "tags": [],
    "excludeTags": [],
    "payTimeRange": []
  },
  "includeExternalFields": true
}
```

### 6.9 `POST /api/finance/partner-invoices/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "keyword": "",
    "partnerName": "",
    "currency": "",
    "amountRange": {
      "min": null,
      "max": null
    },
    "creatorId": "",
    "tags": [],
    "invoiceDateRange": [],
    "paidTimeRange": [],
    "createdRange": [],
    "shipTimeRange": [],
    "dueDateRange": []
  },
  "includeExternalFields": true
}
```

### 6.10 `POST /api/finance/seller-cost-details/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "keyword": "",
    "customerId": "",
    "chargeType": "",
    "invoiceTimeRange": [],
    "currency": "",
    "itemNumber": "",
    "detailId": "",
    "shipmentId": "",
    "trackingNumber": "",
    "waybillNumber": "",
    "audited": null,
    "creatorId": "",
    "createdRange": []
  }
}
```

### 6.11 `POST /api/finance/seller-commission-details/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "customerId": "",
    "shipmentId": "",
    "chargeType": "",
    "creatorId": "",
    "audited": null,
    "createdRange": [],
    "invoiceTimeRange": []
  }
}
```

### 6.12 `POST /api/finance/seller-commission-invoices/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "SELLER_CUSTOMER",
    "customerId": "",
    "invoiceDateRange": [],
    "createdRange": []
  }
}
```

### 6.13 `POST /api/finance/accounts/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "keyword": "",
    "ownerName": "",
    "ownerGrade": "",
    "createdRange": []
  }
}
```

### 6.14 `POST /api/finance/account-records/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "recordId": "",
    "accountId": "",
    "payTimeRange": [],
    "type": "",
    "customerName": "",
    "partnerId": "",
    "createdRange": []
  }
}
```

### 6.15 `POST /api/finance/currencies/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "currencyCode": "",
    "enabled": null
  }
}
```

### 6.16 `POST /api/finance/charge-types/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "name": "",
    "code": "",
    "type": "",
    "visible": null
  }
}
```

### 6.17 `POST /api/finance/monthly-locks/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "sellStatus": "",
    "costStatus": "",
    "sellerStatus": "",
    "creatorId": "",
    "lockId": "",
    "periodRange": []
  }
}
```

### 6.18 `POST /api/finance/charge-approvals/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerId": "",
    "chargeType": "",
    "serialNumber": "",
    "shipmentId": "",
    "approvalTimeRange": [],
    "status": ""
  }
}
```

### 6.19 `POST /api/finance/approvals/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "approvalNumber": "",
    "businessNumber": "",
    "type": "",
    "detailType": "",
    "timeRange": [],
    "status": ""
  }
}
```

## 7. 制单流程 API，对照 ACC

### 7.1 `POST /api/document/orders/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "DOCUMENT_CUSTOMER",
    "serviceMode": "DOCUMENT_SHIPPING",
    "keyword": "",
    "orderNo": "",
    "customerCode": "",
    "customerId": "",
    "trackingNo": "",
    "status": "",
    "createdRange": []
  },
  "includeExternalFields": true
}
```

### 7.2 `POST /api/document/orders`

```json
{
  "customerCode": "DOC-DEMO",
  "customerRef": "DOC-REF-001",
  "serviceId": "",
  "status": "DRAFT",
  "orderEntryType": "MANUAL_DOCUMENT",
  "metadata": {
    "sourcePage": "document_order",
    "receiverCountry": "US",
    "receiverPostcode": "90001",
    "receiverCity": "",
    "receiverAddress": "",
    "receiverName": "",
    "receiverPhone": "",
    "senderName": "",
    "senderPhone": "",
    "remark": ""
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "申报品名",
      "sku": "DOC-SKU-001",
      "quantity": 1,
      "declaredValue": 20,
      "declaredCurrency": "USD",
      "weightKg": 0.8,
      "metadata": {
        "hsCode": "",
        "originCountry": "CN"
      }
    }
  ]
}
```

### 7.3 `GET /api/document/orders/{id}`

无 request body。

### 7.4 `PUT /api/document/orders/{id}`

```json
{
  "customerCode": "DOC-DEMO",
  "customerRef": "DOC-REF-001",
  "serviceId": "",
  "status": "DRAFT",
  "orderEntryType": "MANUAL_DOCUMENT",
  "metadata": {
    "receiverCountry": "US",
    "receiverPostcode": "90001",
    "receiverCity": "",
    "receiverAddress": "",
    "receiverName": "",
    "receiverPhone": "",
    "remark": ""
  },
  "lines": [
    {
      "lineNo": 1,
      "itemName": "申报品名",
      "sku": "DOC-SKU-001",
      "quantity": 1,
      "declaredValue": 20,
      "declaredCurrency": "USD",
      "weightKg": 0.8,
      "metadata": {}
    }
  ]
}
```

### 7.5 `DELETE /api/document/orders/{id}`

无 request body。

### 7.6 `POST /api/customer-api/orders`

```json
{
  "customerCode": "CUST001",
  "referenceNo": "REF001",
  "serviceCode": "SERVICE",
  "country": "US",
  "weight": 1.2,
  "pieces": 1,
  "receiver": {
    "name": "Receiver",
    "phone": "",
    "postcode": "",
    "city": "",
    "state": "",
    "address1": "",
    "address2": ""
  },
  "sender": {
    "name": "",
    "phone": "",
    "country": "CN",
    "city": "",
    "address": ""
  },
  "items": [
    {
      "name": "Product",
      "sku": "",
      "quantity": 1,
      "declaredValue": 10,
      "declaredCurrency": "USD",
      "hsCode": "",
      "originCountry": "CN"
    }
  ],
  "idempotencyKey": "customer-reference-unique-key"
}
```

### 7.7 `POST /api/document/rates/quote`

```json
{
  "customerId": "uuid",
  "customerCode": "DOC-DEMO",
  "serviceCode": "SERVICE",
  "destinationCountry": "US",
  "destinationPostcode": "91710",
  "destinationCity": "",
  "pieces": 1,
  "weightKg": 1.2,
  "volumeWeightKg": null,
  "declaredValue": 10,
  "declaredCurrency": "USD",
  "items": [
    {
      "name": "Product",
      "quantity": 1,
      "weightKg": 1.2,
      "lengthCm": null,
      "widthCm": null,
      "heightCm": null
    }
  ],
  "options": {
    "includeRemoteArea": true,
    "includeFuel": true,
    "validateBalance": true
  }
}
```

### 7.8 `POST /api/document/orders/{id}/submit`

```json
{
  "validateOnly": false,
  "channelId": "",
  "serviceCode": "",
  "deductBalance": true,
  "generateLabel": true,
  "remark": "提交制单"
}
```

### 7.9 `POST /api/labels/generate`

```json
{
  "shipmentId": "uuid",
  "orderId": "uuid",
  "labelFormat": "PDF",
  "paperSize": "A4",
  "forceRegenerate": false
}
```

### 7.10 `POST /api/labels/relabel`

```json
{
  "originalTrackingNo": "OLD_TRACKING_NO",
  "shipmentId": "uuid",
  "newServiceCode": "",
  "newChannelId": "",
  "reason": "退件二次制单",
  "chargeDifference": true
}
```

### 7.11 `GET /api/labels/{id}/download`

无 request body。

### 7.12 `POST /api/tracking/query`

```json
{
  "trackingNo": "TRACKING_NO",
  "shipmentId": "",
  "includeRawEvents": false,
  "includeExternalFields": true
}
```

### 7.13 `GET /api/customer-api/balance`

无 request body。客户身份来自 API 凭证或 token。

### 7.14 `POST /api/finance/document-charges/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "DOCUMENT_CUSTOMER",
    "customerId": "",
    "customerCode": "",
    "orderNo": "",
    "shipmentNo": "",
    "trackingNo": "",
    "chargeType": "",
    "paid": null,
    "audited": null,
    "createdRange": [],
    "amountRange": {
      "min": null,
      "max": null
    },
    "currency": ""
  },
  "includeExternalFields": true
}
```

### 7.15 `POST /api/finance/document-invoices/search`

```json
{
  "pageNum": 1,
  "pageSize": 20,
  "filters": {
    "customerDirection": "DOCUMENT_CUSTOMER",
    "customerId": "",
    "customerCode": "",
    "invoiceNumber": "",
    "status": "",
    "currency": "",
    "invoiceDateRange": [],
    "dueDateRange": [],
    "paidTimeRange": [],
    "amountRange": {
      "min": null,
      "max": null
    }
  }
}
```

## 8. 外部对照 API

### 8.1 `GET /api/external/systems`

无 request body。

### 8.2 `POST /api/external/systems`

```json
{
  "code": "XQT",
  "name": "新智慧",
  "systemType": "tms_aos",
  "baseUrlKey": "XQT_BASE_URL",
  "status": "active",
  "notes": "客户卖货流程只读对照样本"
}
```

### 8.3 `GET /api/external/modules`

无 request body。查询参数：`systemCode`。

### 8.4 `POST /api/external/modules`

```json
{
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "moduleName": "财务流水",
  "pagePath": "/tms/aos/financial_detail",
  "apiPath": "/rest/tms/aos/financial_detail/lists",
  "method": "POST",
  "safeMode": true
}
```

### 8.5 `GET /api/external/field-mappings`

无 request body。查询参数：`systemCode`、`moduleCode`。

### 8.6 `POST /api/external/field-mappings`

```json
{
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "externalField": "serial_number",
  "externalLabel": "流水号",
  "internalEntity": "FinanceLedgerRecord",
  "internalField": "serialNumber",
  "valueType": "string",
  "mappingStatus": "confirmed",
  "notes": ""
}
```

### 8.7 `GET /api/external/comparison-cases`

无 request body。查询参数：`systemCode`、`moduleCode`、`status`、`caseNo`。

### 8.8 `POST /api/external/comparison-cases`

```json
{
  "caseNo": "XQT-FIN-001",
  "externalSystemCode": "XQT",
  "moduleCode": "financial_detail",
  "internalUrl": "/finance/ledger-records",
  "externalFilter": {
    "timeLimit": 0,
    "scenes": 1,
    "keywords": "ABC"
  },
  "expectedResult": {
    "rowCount": 20,
    "amountSummary": "manual-check"
  },
  "status": "pending"
}
```

### 8.9 `POST /api/external/comparison-cases/{id}/result`

```json
{
  "actualResult": {
    "rowCount": 20,
    "amountSummary": "matched"
  },
  "status": "passed",
  "diff": []
}
```

### 8.10 `POST /api/external/xqt/aos/{module}/lists`

这是新智慧同形 body 对照接口，只查本地库，不向新智慧提交请求。

```json
{
  "timeLimit": 0,
  "scenes": 1,
  "keywords": "ABC",
  "created_daterange": [
    "2026-05-01",
    "2026-05-31"
  ]
}
```

## 9. Fastify 原型 API 处理原则

`/api/acc/*`、`/api/sys/*`、Fastify 里的历史原型路由不作为新系统生产 API 的最终目标。它们的 body 不再逐条扩展到开发主文档中，处理方式如下：

1. ACC 制单能力沉淀到 `/api/document/*`、`/api/customer-api/*`、`/api/labels/*`、`/api/tracking/*`。
2. 新智慧卖货能力沉淀到 `/api/seller/*`、`/api/warehouse/*`、`/api/finance/*`。
3. 旧 body 字段进入 `external_field_mappings`，作为对照证据，不作为主系统接口直接字段。
4. 如果某个原型 API 仍需要保留，必须先在本文档新增目标 API 和 request body，再进入开发。
