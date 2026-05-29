# 生产部署记录 2026-05-29

## 1. 部署前确认

### 1.1 代码状态
- 后端 **202/202 测试绿**（含 7 项 E2E 异常处理回归测试）
- 前端 `vue-tsc` exit 0
- 9 项 ACC 补充任务 S1-S9 全部完成并接入主路径
- 47 migrations 落地（001-047）+ 8 seeds
- AuthPrincipal.branchId 真实路径打通（BRANCH_MANAGER 隔离生效）

### 1.2 E2E 全流程发现的 bug（已修复）
跑业务全流程时通过 curl 探 26 个 GET 列表端点 + 多个 POST，发现并修复：

| Bug | 现象 | 修复 commit |
|-----|------|------|
| 1 | 带 token 访问不存在路径返 500（应 404） | 0337e1e |
| 2 | `POST /api/acc/customers {}` 返 500（应 400 + 字段名提示） | 0337e1e |
| 附 | 405 / 缺参数 / 外键缺失 / 唯一冲突 兜底成 500 | 0337e1e |

修复方法：扩展 `GlobalExceptionHandler` 分别处理 `NoHandlerFoundException` /
`NoResourceFoundException` / `DataIntegrityViolationException` /
`HttpRequestMethodNotSupportedException` / `MissingServletRequestParameterException`。

回归覆盖：`GlobalExceptionHandlerTest` +7 测试。

### 1.3 E2E 验证通过的业务能力
- 26 个 ACC 列表端点全部 200（customers / orders / shipments / charges / profits /
  6 类财务流水 / stowages / transits / dispatches / bills / 主数据等）
- 登录 + JWT 鉴权链路 ✓
- S1 timeline 端点 ✓
- S3 quote 端点 ✓（行为正确，演示数据缺 EU-AIR-UPS 价表是数据非 bug）
- S4 orders 真实 sellCharge=80.02 / costCharge / branch ✓
- POST 成功创建客户路径 ✓

## 2. 部署目标
- 服务器：`root@8.148.227.76`（CentOS 7 + Docker 26.1.4 + Compose v2.27.1，剩 120GB）
- 部署路径：`/opt/xqt-saas`
- 外部端口：80 (web → backend 反代)
- 内部服务：postgres (5432) + redis (6379) + backend (8080)

## 3. 部署产物

### 3.1 镜像
- `xqt-backend:prod`：multi-stage maven build → eclipse-temurin:17-jre（linux/amd64）
- `xqt-web:prod`：vite build → nginx:1.27-alpine（linux/amd64）

### 3.2 优化（针对国内网络）
- backend Dockerfile：aliyun maven mirror + buildkit `/root/.m2/repository` cache mount
- web Dockerfile：npmmirror.com + buildkit `/root/.npm` cache mount
- 首次部署仍需下载基础镜像（~150MB），但后续 rebuild 增量

### 3.3 docker-compose.prod.yml
- 4 service：postgres + redis + backend + web
- postgres 挂载全 47 migrations + 8 seeds（首次启动自动初始化）
- backend 默认开 `RATES_STRICT_QUOTE=true` + `APP_CARRIER_STRICT_GATEWAY=true`（生产严格模式）
- web 反代 `/api/*` 和 `/health` 到 backend，其余 SPA fallback 到 index.html

### 3.4 deploy.sh
5 步一键：
1. 本地构建 backend 镜像
2. 本地构建 web 镜像
3. `docker save | gzip` 打包到 tar.gz
4. scp tar + compose + db/ 到服务器（用 sshpass 自动登录）
5. ssh: docker load + compose up + 等待 backend healthy

## 4. 验收步骤（部署完成后）

```bash
# 远端容器状态
sshpass -p '...' ssh root@8.148.227.76 'cd /opt/xqt-saas && docker compose ps'

# 健康检查
curl http://8.148.227.76/health

# 登录验证
curl -X POST http://8.148.227.76/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantCode":"xqt","username":"admin","password":"Test@1234"}'

# 浏览器打开
open http://8.148.227.76/
```

## 5. 部署后必做

### 5.1 安全
- [ ] **轮换服务器 root 密码**（之前在沟通中出现过）
- [ ] 改 admin 用户的默认密码 `Test@1234`（修改 SQL）
- [ ] 修改 `deploy/.env` 中 `POSTGRES_PASSWORD`（默认 `Xqt_prod_DB_2026_change_me`）
- [ ] 建议禁 root SSH，改密钥登录

### 5.2 数据
- [ ] users.branch_id 数据按运营在用户管理界面或 SQL 批量填
- [ ] customers.branch_id 数据填写（新建 shipment 时会级联）
- [ ] 可选：历史 shipments.branch_id NULL backfill：
  ```sql
  UPDATE shipments SET branch_id = (
    SELECT branch_id FROM customers WHERE customers.id = shipments.customer_id
  ) WHERE branch_id IS NULL;
  ```

### 5.3 第三方
- [ ] 真实 UPS/FedEx adapter 接入（替换 Sandbox）
- [ ] 真实汇率 feed（exchange_rates 表）
- [ ] ShipmentDeliveredEvent listener（利润结算）

## 6. 故障排查

| 现象 | 排查 |
|------|------|
| backend not healthy | `ssh root@8.148.227.76 'cd /opt/xqt-saas && docker compose logs backend'` |
| 502 from web | `docker compose logs web` 看 nginx 错误；`docker compose exec backend wget -q -O- http://localhost:8080/actuator/health` 内部健康检查 |
| DB schema 错误 | postgres volume 已有旧数据 → migrations 不会自动跑；手动 `docker compose exec -T postgres psql -U xqt -d xqt_saas < db/migrations/04X.sql` |
| 端口冲突 | `lsof -i:80` 看占用；改 `deploy/.env` `WEB_PORT=8080` |

## 7. 回滚

```bash
ssh root@8.148.227.76 'cd /opt/xqt-saas && docker compose down'
# 恢复前一版本镜像（如保留 tag）：
ssh root@8.148.227.76 'cd /opt/xqt-saas && docker tag xqt-backend:<prev> xqt-backend:prod && docker compose up -d'
```

## 8. 后续部署（增量）

源码改动后：
```bash
cd /Users/chaowang/新航线/xqt-saas
git pull # if needed
./deploy/deploy.sh
```

deploy.sh 会重建镜像（cache mount 加速）+ tar + scp + 替换容器。postgres volume 保留数据。
