<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from "vue";
import {
  AlertTriangle,
  FileSpreadsheet,
  ReceiptText,
  ShieldCheck,
  WalletCards,
  Building2,
  Truck,
  Package,
  BarChart3,
  RefreshCw,
  Search,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Users,
  Landmark,
  Globe,
  Plane,
  FileText,
  DollarSign,
  CreditCard,
  ArrowLeftRight,
  Coins,
  UserCheck,
  Layers,
  MapPin,
  Fuel,
  BookOpen,
  CornerDownLeft,
  Lock,
  HelpCircle,
  PackageOpen,
  Ship,
  Anchor,
  Warehouse,
  ListChecks,
  Tag,
  MinusCircle,
  PlusCircle,
  Undo2,
  Gift,
  Gavel,
  Receipt,
  PiggyBank,
  Banknote,
  HandCoins,
  CalendarClock,
  ClipboardList,
  Clock,
  FolderTree,
  Hash,
  Mail,
  Plus,
  Pencil,
  Trash2,
  X,
  Save,
  CheckCircle,
  XCircle,
  Download,
  Upload,
  Calculator,
  Eye,
  Bell,
  Home,
  Menu,
  PanelLeftClose,
  Settings,
  Maximize2,
} from "lucide-vue-next";

const API = import.meta.env.VITE_API_URL ?? "";
const GlobalTrackingMap = defineAsyncComponent(() => import("./components/GlobalTrackingMap.vue"));

// ═══════════════ Types ═══════════════

interface DashboardData {
  acc: { label: string; revenue: number; cost: number; profit: number; orderCount: number; byBranch: any[] };
  xqt: { label: string; revenue: number; shipmentCount: number; invoiceCount: number; paid: number; unpaid: number };
  local: {
    label: string;
    orders?: number;
    shipments: number;
    receivable: number;
    payable: number;
    profit: number;
    invoiceCount?: number;
    paid?: number;
    unpaid?: number;
  };
  combined: { totalRevenue: number; totalCost: number; totalProfit: number };
  flows?: DashboardFlowData[];
  tracking?: DashboardTrackingData;
  period: { from: string; to: string };
}

interface DashboardFlowData {
  code: string;
  customerDirection: string;
  label: string;
  orders: number;
  shipments: number;
  receivable: number;
  payable: number;
  profit: number;
}

interface DashboardGeoPoint {
  name: string;
  countryCode: string;
  lat: number;
  lng: number;
}

interface DashboardTrackingRoute {
  id: string;
  shipmentNo: string;
  trackingNo: string;
  serviceMode: string;
  customerDirection: string;
  carrierName: string;
  channelName?: string;
  status: string;
  shipmentStatus: string;
  rawStatus: string;
  latestLocation: string;
  latestEventTime?: string;
  destinationCountry: string;
  destinationPostalCode?: string;
  progress: number;
  origin: DashboardGeoPoint;
  current: DashboardGeoPoint;
  destination: DashboardGeoPoint;
}

interface DashboardTrackingData {
  summary: {
    activeShipments: number;
    exceptionCount: number;
    deliveredToday: number;
    trackedShipments: number;
    destinationCountries: number;
  };
  routes: DashboardTrackingRoute[];
}

interface HealthData {
  ok: boolean;
  upstreams: {
    acc: { available: boolean; connected: boolean; error?: string };
    xqt: { available: boolean; connected: boolean; error?: string };
    postgres: { available: boolean; connected: boolean };
  };
}

interface BranchData {
  id: string;
  code: string;
  name: string;
  org_type: string;
  is_active: boolean;
}

// ═══════════════ State ═══════════════

const currentNav = ref("dashboard");
const dashboard = ref<DashboardData | null>(null);
const health = ref<HealthData | null>(null);
const branches = ref<BranchData[]>([]);
const loading = ref(false);
const error = ref("");
const selectedTrackingRouteId = ref("");

interface AuthUser {
  id: string;
  tenantCode: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

const authToken = ref(localStorage.getItem("xqt_auth_token") ?? "");
const authUser = ref<AuthUser | null>(null);
const loginForm = reactive({ tenantCode: "xqt", username: "admin", password: "" });
const loginLoading = ref(false);
const loginError = ref("");
const isAuthenticated = computed(() => !!authToken.value && !!authUser.value);
const sidebarCollapsed = ref(false);
const openedTabs = ref<string[]>(["dashboard"]);

// ACC sub-module state
const accTab = ref("orders");
const accData = ref<any[]>([]);
const accTotal = ref(0);
const accPage = ref(1);
const accPageSize = ref(50);
const accLoading = ref(false);
const accKeyword = ref("");
const accDateFrom = ref("");
const accDateTo = ref("");
const accStats = ref<any>(null);

const accTotalPages = computed(() => Math.max(1, Math.ceil(accTotal.value / accPageSize.value)));

// Form dialog state
interface FormField {
  col: string;
  label: string;
  type: 'text' | 'number' | 'date' | 'textarea' | 'select' | 'boolean';
  required?: boolean;
  opts?: Array<{ v: number | string; l: string }>;
  ref?: string;
}

const showForm = ref(false);
const formMode = ref<'add' | 'edit'>('add');
const formData = reactive<Record<string, any>>({});
const editId = ref(0);
const formSaving = ref(false);
const formError = ref('');
const selectOptions = ref<Record<string, Array<{ id: number; name: string }>>>({});
const showDeleteConfirm = ref(false);
const deleteTarget = ref<{ id: number; label: string }>({ id: 0, label: '' });

// Business operation state
const selectedIds = ref<Set<number>>(new Set());
const showDetail = ref(false);
const detailData = ref<any>(null);
const detailType = ref('');
const bizLoading = ref(false);
const bizMessage = ref('');
const showBizDialog = ref(false);
const bizDialogType = ref('');
const bizDialogData = reactive<Record<string, any>>({});

// Audit history drawer state (审核流转抽屉)
const showAuditHistory = ref(false);
const auditHistoryRows = ref<Array<any>>([]);
const auditHistoryRowLabel = ref('');
const auditHistoryLoading = ref(false);

// System management state
const sysTab = ref('users');
const sysData = ref<any[]>([]);
const sysTotal = ref(0);
const sysPage = ref(1);
const sysLoading = ref(false);
const sysInfo = ref<any>(null);

const navItems = [
  { key: "dashboard", label: "驾驶舱", icon: BarChart3 },
  { key: "acc", label: "委托运输 (ACC)", icon: Truck },
  { key: "xqt", label: "集货入仓 (XQT)", icon: Package },
  { key: "branches", label: "分公司管理", icon: Building2 },
  { key: "system", label: "系统管理", icon: ShieldCheck },
];

const navMap = computed(() => Object.fromEntries(navItems.map(item => [item.key, item])));
const activeNavItem = computed(() => navMap.value[currentNav.value] ?? navItems[0]);
const activePageTitle = computed(() => {
  if (currentNav.value === "acc") {
    return accTabs.find(tab => tab.key === accTab.value)?.label ?? activeNavItem.value.label;
  }
  if (currentNav.value === "system") {
    return sysTabs.find(tab => tab.key === sysTab.value)?.label ?? activeNavItem.value.label;
  }
  return activeNavItem.value.label;
});

const breadcrumbItems = computed(() => {
  const root = activeNavItem.value.label;
  if (currentNav.value === "dashboard") return ["首页", root];
  return ["首页", root, activePageTitle.value].filter((item, index, list) => index === 0 || item !== list[index - 1]);
});

function navTo(key: string) {
  currentNav.value = key;
  if (!openedTabs.value.includes(key)) openedTabs.value.push(key);
}

function closeTab(key: string) {
  if (key === "dashboard") return;
  const next = openedTabs.value.filter(item => item !== key);
  openedTabs.value = next.length ? next : ["dashboard"];
  if (currentNav.value === key) currentNav.value = openedTabs.value[openedTabs.value.length - 1] ?? "dashboard";
}

function statusLabel(status?: { available: boolean; connected: boolean }) {
  if (!status) return "未知";
  if (status.connected) return "正常";
  if (status.available) return "待配置";
  return "未配置";
}

function statusTone(status?: { available: boolean; connected: boolean }) {
  if (!status) return "gray";
  if (status.connected) return "green";
  if (status.available) return "yellow";
  return "gray";
}

const sysTabs = [
  // 用户权限
  { key: "users", label: "用户管理", icon: Users },
  { key: "roles", label: "角色权限", icon: ShieldCheck },
  { key: "menus", label: "菜单管理", icon: ListChecks },
  // 日志审计
  { key: "op-logs", label: "操作日志", icon: FileText },
  { key: "error-logs", label: "错误日志", icon: AlertTriangle },
  { key: "sms-logs", label: "短信记录", icon: Mail },
  // 系统配置
  { key: "configs", label: "系统配置", icon: ListChecks },
  { key: "sys-info", label: "系统信息", icon: Globe },
  { key: "db-stats", label: "数据库统计", icon: BarChart3 },
  // 客户/服务
  { key: "cust-accounts", label: "客户账号", icon: Users },
  { key: "service-msg", label: "在线客服", icon: Mail },
  { key: "tools", label: "系统工具", icon: ListChecks },
  // 接口
  { key: "hardware", label: "硬件接口", icon: Layers },
];

const sysColumns: Record<string, Array<{ key: string; label: string }>> = {
  users: [
    { key: "id", label: "ID" }, { key: "username", label: "用户名" }, { key: "realName", label: "姓名" },
    { key: "roleName", label: "角色" }, { key: "email", label: "邮箱" }, { key: "phone", label: "电话" },
    { key: "status", label: "状态" }, { key: "lastLogin", label: "最后登录" },
  ],
  roles: [
    { key: "id", label: "ID" }, { key: "name", label: "角色名" }, { key: "description", label: "描述" },
    { key: "permissions", label: "权限" }, { key: "createdAt", label: "创建时间" },
  ],
  menus: [
    { key: "id", label: "ID" }, { key: "parentId", label: "父级" }, { key: "name", label: "菜单名" },
    { key: "path", label: "路径" }, { key: "icon", label: "图标" }, { key: "sort", label: "排序" },
    { key: "permission", label: "权限码" }, { key: "visible", label: "可见" },
  ],
  "op-logs": [
    { key: "id", label: "ID" }, { key: "username", label: "用户" }, { key: "module", label: "模块" },
    { key: "action", label: "操作" }, { key: "target", label: "目标" },
    { key: "ip", label: "IP" }, { key: "createdAt", label: "时间" },
  ],
  "error-logs": [
    { key: "id", label: "ID" }, { key: "level", label: "级别" }, { key: "module", label: "模块" },
    { key: "message", label: "消息" }, { key: "created_at", label: "时间" },
  ],
  "sms-logs": [
    { key: "id", label: "ID" }, { key: "phone", label: "手机号" }, { key: "content", label: "内容" },
    { key: "template", label: "模板" }, { key: "status", label: "状态" }, { key: "created_at", label: "时间" },
  ],
  configs: [
    { key: "key", label: "配置键" }, { key: "value", label: "值" },
    { key: "description", label: "描述" }, { key: "group", label: "分组" },
  ],
  "db-stats": [
    { key: "table_name", label: "表名" }, { key: "row_count", label: "行数" },
  ],
  "cust-accounts": [
    { key: "id", label: "ID" }, { key: "customerId", label: "客户ID" }, { key: "username", label: "用户名" },
    { key: "status", label: "状态" }, { key: "lastLogin", label: "最后登录" },
  ],
  "service-msg": [
    { key: "id", label: "ID" }, { key: "customerId", label: "客户ID" }, { key: "direction", label: "方向" },
    { key: "content", label: "内容" }, { key: "createdAt", label: "时间" },
  ],
  hardware: [
    { key: "id", label: "ID" }, { key: "type", label: "类型" }, { key: "name", label: "名称" },
    { key: "status", label: "状态" }, { key: "last_heartbeat", label: "最后心跳" },
  ],
};

// 后端实际暴露的 admin 端点：/api/admin/{users,roles,permissions,audit-logs}。
// 旧 sys 命名空间的其余 tab（menus/configs/sys-info/db-stats/sms-logs/hardware/...）
// 在新平台没有对应控制器，暂时返回空数组并由 UI 提示"未上线"。
const SYS_TAB_API_MAP: Record<string, string> = {
  users: '/api/admin/users',
  roles: '/api/admin/roles',
  'op-logs': '/api/admin/audit-logs',
  configs: '/api/admin/permissions',
};
const SYS_TAB_UNAVAILABLE: Set<string> = new Set([
  'menus', 'error-logs', 'sms-logs', 'sys-info',
  'db-stats', 'cust-accounts', 'service-msg', 'hardware', 'tools',
]);

async function loadSystemData() {
  sysLoading.value = true;
  try {
    const tab = sysTab.value;
    if (SYS_TAB_UNAVAILABLE.has(tab)) {
      sysData.value = [];
      sysTotal.value = 0;
      sysInfo.value = { unavailable: true, message: '此模块尚未在新后端上线' };
      return;
    }
    const url = SYS_TAB_API_MAP[tab];
    if (!url) { sysData.value = []; return; }
    const res = await apiFetch(`${API}${url}`);
    const json = await res.json();

    if (Array.isArray(json)) {
      sysData.value = json;
      sysTotal.value = json.length;
    } else if (json.data) {
      sysData.value = json.data;
      sysTotal.value = json.total ?? json.data.length;
    } else {
      sysData.value = [];
    }
  } catch { sysData.value = []; }
  finally { sysLoading.value = false; }
}

const accTabs = [
  // 订单管理
  { key: "orders", label: "快件订单", icon: FileText, api: "orders" },
  { key: "collects", label: "总单/留仓", icon: ClipboardList, api: "collects" },
  { key: "returns", label: "退件管理", icon: CornerDownLeft, api: "returns" },
  { key: "detains", label: "扣件管理", icon: Lock, api: "detains" },
  { key: "asks", label: "问题件", icon: HelpCircle, api: "asks" },
  { key: "reparations", label: "赔偿管理", icon: Gavel, api: "reparations" },
  { key: "quick-orders", label: "快速下单", icon: ClipboardList, api: "quick-orders" },
  { key: "void-orders", label: "订单作废", icon: MinusCircle, api: "void-orders" },
  // 物流出货
  { key: "shipments", label: "出货管理", icon: Truck, api: "shipments" },
  { key: "stowages", label: "配载管理", icon: Plane, api: "stowages" },
  { key: "packages", label: "装箱单", icon: PackageOpen, api: "packages" },
  { key: "transits", label: "转运管理", icon: Ship, api: "transits" },
  { key: "ports", label: "港口管理", icon: Anchor, api: "ports" },
  { key: "warehouses", label: "仓库管理", icon: Warehouse, api: "warehouses" },
  { key: "dispatches", label: "上门揽收", icon: Truck, api: "dispatches" },
  { key: "stowage-categories", label: "配载分类", icon: FolderTree, api: "stowage-categories" },
  { key: "stowage-steps", label: "配载步骤", icon: ListChecks, api: "stowage-steps" },
  { key: "forecasts", label: "预报包裹", icon: CalendarClock, api: "forecasts" },
  { key: "tracks", label: "轨迹项目", icon: MapPin, api: "tracks" },
  // 财务管理
  { key: "charges", label: "应收运费", icon: DollarSign, api: "charges" },
  { key: "costs", label: "应付成本", icon: CreditCard, api: "costs" },
  { key: "bills", label: "客户账单", icon: ReceiptText, api: "bills" },
  { key: "payments", label: "供应商付款", icon: Landmark, api: "payments" },
  { key: "receiveds", label: "客户收款", icon: Coins, api: "receiveds" },
  { key: "profits", label: "利润查询", icon: BarChart3, api: "profits" },
  { key: "fees", label: "杂费套餐", icon: ListChecks, api: "fees" },
  { key: "fee-types", label: "附加费类型", icon: Tag, api: "fee-types" },
  { key: "customer-fines", label: "客户罚款", icon: MinusCircle, api: "customer-fines" },
  { key: "supplier-fines", label: "物流商罚款", icon: MinusCircle, api: "supplier-fines" },
  { key: "customer-adjusts", label: "客户调账", icon: ArrowLeftRight, api: "customer-adjusts" },
  { key: "supplier-adjusts", label: "物流商调账", icon: ArrowLeftRight, api: "supplier-adjusts" },
  { key: "customer-refunds", label: "客户退款", icon: Undo2, api: "customer-refunds" },
  { key: "supplier-refunds", label: "物流商退款", icon: Undo2, api: "supplier-refunds" },
  { key: "customer-rebates", label: "客户返利", icon: Gift, api: "customer-rebates" },
  { key: "supplier-rebates", label: "物流商返利", icon: Gift, api: "supplier-rebates" },
  { key: "expenses", label: "费用收支", icon: Receipt, api: "expenses" },
  { key: "banks", label: "银行账户", icon: PiggyBank, api: "banks" },
  { key: "commissions", label: "员工提成", icon: UserCheck, api: "commissions" },
  { key: "transfers", label: "转账记录", icon: ArrowLeftRight, api: "transfers" },
  { key: "dividends", label: "分红入股", icon: HandCoins, api: "dividends" },
  { key: "borrowings", label: "资金借贷", icon: Banknote, api: "borrowings" },
  { key: "currencies", label: "货币汇率", icon: Coins, api: "currencies" },
  { key: "assets", label: "固定资产", icon: Landmark, api: "assets" },
  { key: "cycles", label: "周期费用", icon: CalendarClock, api: "cycles" },
  { key: "received-sms", label: "收款短信", icon: Mail, api: "received-sms" },
  { key: "expense-categories", label: "费用分类", icon: Tag, api: "expense-categories" },
  { key: "fee-item-types", label: "费用项类型", icon: Tag, api: "fee-item-types" },
  // 客户/供应商
  { key: "customers", label: "客户管理", icon: Users, api: "customers" },
  { key: "customer-groups", label: "客户分组", icon: FolderTree, api: "customer-groups" },
  { key: "suppliers", label: "物流商", icon: Truck, api: "suppliers" },
  { key: "channels", label: "渠道管理", icon: Layers, api: "channels" },
  { key: "channel-accounts", label: "渠道账号", icon: Layers, api: "channel-accounts" },
  { key: "products", label: "价格表", icon: WalletCards, api: "products" },
  { key: "product-items", label: "品名管理", icon: Hash, api: "product-items" },
  { key: "potentials", label: "潜在客户", icon: UserCheck, api: "potentials" },
  { key: "sold-tos", label: "收件地址库", icon: MapPin, api: "sold-tos" },
  { key: "notices", label: "客户通知", icon: FileText, api: "notices" },
  // 人事组织
  { key: "employees", label: "员工管理", icon: Users, api: "employees" },
  { key: "wages", label: "工资发放", icon: Banknote, api: "wages" },
  { key: "attendances", label: "考勤管理", icon: Clock, api: "attendances" },
  { key: "acc-branches", label: "分店管理", icon: Building2, api: "branches" },
  { key: "departments", label: "部门管理", icon: Building2, api: "departments" },
  { key: "socials", label: "社保缴纳", icon: ShieldCheck, api: "socials" },
  { key: "social-persons", label: "社保人员", icon: Users, api: "social-persons" },
  { key: "funds", label: "公积金缴纳", icon: PiggyBank, api: "funds" },
  { key: "fund-persons", label: "公积金人员", icon: Users, api: "fund-persons" },
  { key: "commission-rules", label: "提成规则", icon: ClipboardList, api: "commission-rules" },
  // 基础数据
  { key: "countries", label: "国家地区", icon: Globe, api: "countries" },
  { key: "zones", label: "价格分区", icon: MapPin, api: "zones" },
  { key: "postcodes", label: "邮编库", icon: Mail, api: "postcodes" },
  { key: "remotes", label: "偏远邮编", icon: MapPin, api: "remotes" },
  { key: "fuels", label: "燃油费率", icon: Fuel, api: "fuels" },
  { key: "hscodes", label: "HS编码", icon: BookOpen, api: "hscodes" },
  { key: "bank-names", label: "银行名称", icon: PiggyBank, api: "bank-names" },
  { key: "districts", label: "行政区域", icon: Globe, api: "districts" },
  { key: "logistics-interfaces", label: "物流接口", icon: Layers, api: "logistics-interfaces" },
  { key: "tasks", label: "定时任务", icon: ListChecks, api: "tasks" },
  { key: "templates", label: "消息模板", icon: FileText, api: "templates" },
];

// ACC 二级菜单：6 大功能组（ERP 式折叠菜单树）。索引区间对应 accTabs 顺序。
const accMenuGroups = [
  { key: "order", label: "订单管理", icon: FileText, tabs: accTabs.slice(0, 8) },
  { key: "logistics", label: "物流管理", icon: Truck, tabs: accTabs.slice(8, 19) },
  { key: "finance", label: "财务管理", icon: DollarSign, tabs: accTabs.slice(19, 47) },
  { key: "partner", label: "客户/供应商", icon: Users, tabs: accTabs.slice(47, 57) },
  { key: "hr", label: "人事组织", icon: Building2, tabs: accTabs.slice(57, 67) },
  { key: "basic", label: "基础数据", icon: Globe, tabs: accTabs.slice(67) },
];
// 当前展开的 ACC 功能组（手风琴，一次展开一个）
const expandedAccGroup = ref<string>("order");
function toggleAccGroup(key: string) {
  expandedAccGroup.value = expandedAccGroup.value === key ? "" : key;
}
// 切到某 ACC tab：设置当前 tab，并自动展开它所在的组
function selectAccTab(key: string) {
  accTab.value = key;
  const group = accMenuGroups.find(g => g.tabs.some(t => t.key === key));
  if (group) expandedAccGroup.value = group.key;
}

// Column definitions per ACC tab
const accColumns: Record<string, Array<{ key: string; label: string; fmt?: string }>> = {
  orders: [
    { key: "orderNo", label: "客户单号" },
    { key: "trackNo", label: "服务商单号" },
    { key: "customerName", label: "客户" },
    { key: "product", label: "销售产品" },
    { key: "country", label: "目的地" },
    { key: "piece", label: "件数" },
    { key: "chargeWeight", label: "计费重(kg)" },
    { key: "sellCharge", label: "运费", fmt: "money" },
    { key: "costCharge", label: "成本", fmt: "money" },
    { key: "branch", label: "分公司" },
    { key: "addTime", label: "添加时间", fmt: "date" },
  ],
  returns: [
    { key: "expressNo", label: "快件单号" },
    { key: "customerName", label: "客户" },
    { key: "reason", label: "退件原因" },
    { key: "amount", label: "退还费用", fmt: "money" },
    { key: "status", label: "状态" },
    { key: "addTime", label: "操作时间", fmt: "date" },
  ],
  shipments: [
    { key: "no", label: "出货单号" },
    { key: "channelName", label: "渠道" },
    { key: "supplierName", label: "物流商" },
    { key: "country", label: "国家" },
    { key: "totalPiece", label: "件数" },
    { key: "totalWeight", label: "重量(kg)" },
    { key: "totalCharge", label: "运费", fmt: "money" },
    { key: "totalCost", label: "成本", fmt: "money" },
    { key: "auditName", label: "审核人" },
    { key: "addTime", label: "添加时间", fmt: "date" },
  ],
  stowages: [
    { key: "no", label: "配载单号" },
    { key: "flight", label: "航班号" },
    { key: "departurePort", label: "起始港" },
    { key: "arrivalPort", label: "目的港" },
    { key: "statusText", label: "状态" },
    { key: "totalPiece", label: "件数" },
    { key: "totalWeight", label: "重量(kg)" },
    { key: "totalVolume", label: "体积" },
    { key: "etd", label: "ETD" },
    { key: "eta", label: "ETA" },
    { key: "addTime", label: "创建时间", fmt: "date" },
  ],
  charges: [
    { key: "expressNo", label: "快件单号" },
    { key: "customerName", label: "客户" },
    { key: "productName", label: "产品" },
    { key: "country", label: "国家" },
    { key: "chargeWeight", label: "计费重(kg)" },
    { key: "type", label: "类型" },
    { key: "amount", label: "应收金额", fmt: "money" },
    { key: "paid", label: "实收金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
  ],
  costs: [
    { key: "expressNo", label: "快件单号" },
    { key: "supplierName", label: "物流商" },
    { key: "channelName", label: "渠道" },
    { key: "country", label: "国家" },
    { key: "channelWeight", label: "渠道重(kg)" },
    { key: "type", label: "类型" },
    { key: "amount", label: "应付金额", fmt: "money" },
    { key: "paid", label: "实付金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
  ],
  bills: [
    { key: "no", label: "账单号" },
    { key: "customerName", label: "客户" },
    { key: "settlement", label: "结算方式" },
    { key: "theDate", label: "账单日" },
    { key: "amount", label: "账单金额", fmt: "money" },
    { key: "paid", label: "已付金额", fmt: "money" },
    { key: "unpay", label: "未付金额", fmt: "money" },
    { key: "quantity", label: "数量" },
    { key: "status", label: "状态" },
    { key: "salesman", label: "业务员" },
  ],
  payments: [
    { key: "no", label: "付款单号" },
    { key: "supplierName", label: "物流商" },
    { key: "bankName", label: "账户" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
    { key: "remark", label: "备注" },
  ],
  receiveds: [
    { key: "no", label: "收款单号" },
    { key: "customerName", label: "客户" },
    { key: "bankName", label: "账户" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
    { key: "remark", label: "备注" },
  ],
  profits: [
    { key: "no", label: "快件单号" },
    { key: "customerName", label: "客户" },
    { key: "productName", label: "产品" },
    { key: "country", label: "国家" },
    { key: "chargeWeight", label: "计费重(kg)" },
    { key: "channelWeight", label: "渠道重(kg)" },
    { key: "revenue", label: "运费", fmt: "money" },
    { key: "cost", label: "成本", fmt: "money" },
    { key: "profit", label: "利润", fmt: "money" },
    { key: "theDate", label: "日期", fmt: "date" },
  ],
  commissions: [
    { key: "name", label: "姓名" },
    { key: "type", label: "提成方式" },
    { key: "percent", label: "提成比例(%)" },
    { key: "month", label: "月份" },
    { key: "amount", label: "销售额", fmt: "money" },
    { key: "quantity", label: "销售数量" },
    { key: "commission", label: "提成金额", fmt: "money" },
  ],
  transfers: [
    { key: "fromBank", label: "转出账户" },
    { key: "toBank", label: "转入账户" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "remark", label: "备注" },
    { key: "addName", label: "操作人" },
  ],
  customers: [
    { key: "code", label: "编码" },
    { key: "name", label: "名称" },
    { key: "contact", label: "联系人" },
    { key: "mobile", label: "手机" },
    { key: "balance", label: "余额", fmt: "money" },
    { key: "credits", label: "授信额度", fmt: "money" },
    { key: "settlement", label: "结算方式" },
    { key: "branch", label: "分公司" },
    { key: "group", label: "分组" },
    { key: "salesman", label: "业务员" },
  ],
  suppliers: [
    { key: "name", label: "名称" },
    { key: "contact", label: "联系人" },
    { key: "mobile", label: "手机" },
    { key: "phone", label: "电话" },
    { key: "product", label: "主营产品" },
    { key: "balance", label: "结余", fmt: "money" },
    { key: "settlement", label: "结算方式" },
  ],
  channels: [
    { key: "name", label: "渠道名称" },
    { key: "code", label: "编号" },
    { key: "isOpen", label: "启用", fmt: "bool" },
    { key: "isDebug", label: "调试模式", fmt: "bool" },
    { key: "remark", label: "说明" },
  ],
  "channel-accounts": [
    { key: "channelName", label: "渠道" },
    { key: "name", label: "账号名称" },
    { key: "code", label: "编号" },
    { key: "supplierName", label: "物流商" },
    { key: "isOpen", label: "启用", fmt: "bool" },
  ],
  products: [
    { key: "name", label: "产品名称" },
    { key: "code", label: "编号" },
    { key: "channelName", label: "渠道" },
    { key: "supplierName", label: "物流商" },
    { key: "isOpen", label: "启用", fmt: "bool" },
    { key: "remark", label: "备注" },
  ],
  employees: [
    { key: "name", label: "姓名" },
    { key: "gender", label: "性别" },
    { key: "mobile", label: "手机" },
    { key: "department", label: "部门" },
    { key: "branch", label: "分公司" },
    { key: "position", label: "职位" },
    { key: "status", label: "状态" },
    { key: "entryDate", label: "入职日期" },
  ],
  "acc-branches": [
    { key: "name", label: "名称" },
    { key: "code", label: "编码" },
    { key: "contact", label: "联系人" },
    { key: "phone", label: "电话" },
    { key: "address", label: "地址" },
    { key: "remark", label: "备注" },
  ],
  departments: [
    { key: "name", label: "部门名称" },
    { key: "branchName", label: "所属分店" },
    { key: "remark", label: "备注" },
  ],
  countries: [
    { key: "cn", label: "中文名" },
    { key: "name", label: "英文名" },
    { key: "code", label: "代码" },
    { key: "isOpen", label: "启用", fmt: "bool" },
  ],
  remotes: [
    { key: "postcode", label: "邮编" },
    { key: "country", label: "国家" },
    { key: "supplierName", label: "物流商" },
    { key: "type", label: "类型" },
  ],
  fuels: [
    { key: "name", label: "名称" },
    { key: "rate", label: "费率(%)" },
    { key: "startDate", label: "开始日期" },
    { key: "endDate", label: "结束日期" },
  ],
  hscodes: [
    { key: "code", label: "HS编码" },
    { key: "nameEN", label: "英文品名" },
    { key: "nameCN", label: "中文品名" },
  ],
  currencies: [
    { key: "name", label: "货币名称" },
    { key: "code", label: "代码" },
    { key: "symbol", label: "符号" },
    { key: "rate", label: "汇率" },
    { key: "decimal", label: "小数位" },
  ],
  collects: [
    { key: "no", label: "总单号" },
    { key: "piece", label: "件数" },
    { key: "weight", label: "重量(kg)" },
    { key: "status", label: "状态" },
    { key: "remark", label: "备注" },
    { key: "addName", label: "操作员" },
    { key: "addTime", label: "创建时间", fmt: "date" },
  ],
  detains: [
    { key: "no", label: "单号" },
    { key: "customerName", label: "客户" },
    { key: "type", label: "扣件类型" },
    { key: "status", label: "处理状态" },
    { key: "reason", label: "扣件原因" },
    { key: "addName", label: "操作员" },
    { key: "addTime", label: "操作时间", fmt: "date" },
  ],
  asks: [
    { key: "expressNo", label: "快件单号" },
    { key: "content", label: "问题内容" },
    { key: "source", label: "来源" },
    { key: "type", label: "类型" },
    { key: "status", label: "状态" },
    { key: "addName", label: "创建人" },
    { key: "addTime", label: "创建时间", fmt: "date" },
  ],
  reparations: [
    { key: "expressNo", label: "快件单号" },
    { key: "customerName", label: "客户" },
    { key: "applyAmount", label: "申请金额", fmt: "money" },
    { key: "paidAmount", label: "赔偿金额", fmt: "money" },
    { key: "reason", label: "赔偿原因" },
    { key: "addName", label: "操作员" },
    { key: "addTime", label: "操作时间", fmt: "date" },
  ],
  packages: [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "consignee", label: "收货人" },
    { key: "company", label: "公司" },
    { key: "country", label: "国家" },
    { key: "piece", label: "件数" },
    { key: "quantity", label: "数量" },
    { key: "declaredValue", label: "申报价值", fmt: "money" },
    { key: "postcode", label: "邮编" },
  ],
  transits: [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "type", label: "类型" },
    { key: "quantity", label: "票数" },
    { key: "piece", label: "件数" },
    { key: "weight", label: "重量(kg)" },
    { key: "supplierName", label: "服务商" },
    { key: "tariff", label: "关税", fmt: "money" },
    { key: "amount", label: "成本", fmt: "money" },
    { key: "remark", label: "备注" },
  ],
  ports: [
    { key: "name", label: "名称" },
    { key: "consignee", label: "收件人" },
    { key: "company", label: "公司名称" },
    { key: "type", label: "港口类型" },
    { key: "remark", label: "备注" },
  ],
  warehouses: [
    { key: "name", label: "名称" },
    { key: "code", label: "仓库编码" },
    { key: "consignee", label: "收件人" },
    { key: "company", label: "公司名称" },
    { key: "country", label: "国家" },
    { key: "province", label: "省/洲" },
    { key: "postcode", label: "邮编" },
    { key: "type", label: "类型" },
  ],
  fees: [
    { key: "name", label: "套餐名称" },
    { key: "itemCount", label: "收费项" },
    { key: "linkedProducts", label: "关联价格" },
    { key: "remark", label: "备注" },
  ],
  "fee-types": [
    { key: "name", label: "费用名称" },
    { key: "type", label: "类型" },
    { key: "unit", label: "单位" },
    { key: "method", label: "计费方式" },
    { key: "remark", label: "备注" },
  ],
  "customer-fines": [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "customerName", label: "客户" },
    { key: "amount", label: "罚款金额", fmt: "money" },
    { key: "remark", label: "备注" },
    { key: "auditName", label: "审核人" },
  ],
  "supplier-fines": [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "supplierName", label: "物流商" },
    { key: "amount", label: "罚款金额", fmt: "money" },
    { key: "remark", label: "备注" },
    { key: "auditName", label: "审核人" },
  ],
  "customer-adjusts": [
    { key: "addName", label: "申请人" },
    { key: "addTime", label: "日期", fmt: "date" },
    { key: "customerName", label: "客户" },
    { key: "amount", label: "调账金额", fmt: "money" },
    { key: "reason", label: "申请理由" },
    { key: "remark", label: "备注" },
  ],
  "supplier-adjusts": [
    { key: "addName", label: "申请人" },
    { key: "addTime", label: "日期", fmt: "date" },
    { key: "supplierName", label: "物流商" },
    { key: "amount", label: "调账金额", fmt: "money" },
    { key: "reason", label: "申请理由" },
    { key: "remark", label: "备注" },
  ],
  "customer-refunds": [
    { key: "no", label: "单号" },
    { key: "customerName", label: "客户" },
    { key: "amount", label: "退款金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
    { key: "remark", label: "备注" },
  ],
  "supplier-refunds": [
    { key: "no", label: "单号" },
    { key: "supplierName", label: "物流商" },
    { key: "amount", label: "退款金额", fmt: "money" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
    { key: "remark", label: "备注" },
  ],
  "customer-rebates": [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "customerName", label: "客户" },
    { key: "amount", label: "返利金额", fmt: "money" },
    { key: "remark", label: "备注" },
    { key: "auditName", label: "审核人" },
  ],
  "supplier-rebates": [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "supplierName", label: "物流商" },
    { key: "amount", label: "返利金额", fmt: "money" },
    { key: "remark", label: "备注" },
    { key: "auditName", label: "审核人" },
  ],
  expenses: [
    { key: "name", label: "名称" },
    { key: "theDate", label: "日期" },
    { key: "category", label: "类别" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "bankName", label: "账户" },
    { key: "remark", label: "备注" },
    { key: "auditName", label: "审核人" },
    { key: "addName", label: "经手人" },
  ],
  banks: [
    { key: "name", label: "账户名称" },
    { key: "currency", label: "币种" },
    { key: "deposit", label: "存款", fmt: "money" },
    { key: "remark", label: "备注" },
    { key: "lastUpdate", label: "变更时间" },
    { key: "isShow", label: "显示", fmt: "bool" },
  ],
  dividends: [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "type", label: "类型" },
    { key: "name", label: "姓名" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "bankName", label: "资金账户" },
    { key: "remark", label: "备注" },
  ],
  borrowings: [
    { key: "name", label: "借贷人" },
    { key: "theDate", label: "日期" },
    { key: "type", label: "类型" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "rate", label: "利率(%)" },
    { key: "remark", label: "备注" },
    { key: "addName", label: "操作人" },
  ],
  "customer-groups": [
    { key: "name", label: "分组名称" },
    { key: "remark", label: "备注" },
  ],
  "product-items": [
    { key: "nameEN", label: "英文品名" },
    { key: "nameCN", label: "中文品名" },
    { key: "hsCode", label: "HS编码" },
  ],
  wages: [
    { key: "name", label: "姓名" },
    { key: "month", label: "月份" },
    { key: "basic", label: "基本工资", fmt: "money" },
    { key: "bonus", label: "奖金", fmt: "money" },
    { key: "commission", label: "提成", fmt: "money" },
    { key: "deduction", label: "扣款", fmt: "money" },
    { key: "total", label: "实发", fmt: "money" },
    { key: "auditName", label: "审核人" },
  ],
  attendances: [
    { key: "name", label: "员工" },
    { key: "theDate", label: "日期" },
    { key: "type", label: "类型" },
    { key: "hours", label: "时长(h)" },
    { key: "remark", label: "备注" },
  ],
  zones: [
    { key: "name", label: "分区名称" },
    { key: "zoneCount", label: "分区数量" },
    { key: "countryCount", label: "包含国家" },
    { key: "remark", label: "备注" },
  ],
  postcodes: [
    { key: "postcode", label: "邮编" },
    { key: "country", label: "国家" },
    { key: "province", label: "省/洲" },
    { key: "city", label: "城市" },
  ],
  "quick-orders": [
    { key: "no", label: "总单号" },
    { key: "piece", label: "件数" },
    { key: "weight", label: "重量(kg)" },
    { key: "status", label: "状态" },
    { key: "remark", label: "备注" },
    { key: "addName", label: "操作员" },
    { key: "addTime", label: "创建时间", fmt: "date" },
  ],
  "void-orders": [
    { key: "No", label: "运单号" },
    { key: "TrackNo", label: "跟踪号" },
    { key: "CustomerName", label: "客户" },
    { key: "Status", label: "状态" },
    { key: "Amount", label: "金额", fmt: "money" },
    { key: "Paid", label: "已付", fmt: "money" },
    { key: "AddName", label: "操作员" },
    { key: "AddTime", label: "作废时间", fmt: "date" },
  ],
  dispatches: [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "customerName", label: "客户" },
    { key: "productName", label: "产品" },
    { key: "piece", label: "件数" },
    { key: "weight", label: "重量(kg)" },
    { key: "employee", label: "收件员" },
    { key: "picker", label: "取件人" },
    { key: "phone", label: "电话" },
    { key: "status", label: "状态" },
    { key: "remark", label: "备注" },
  ],
  "stowage-categories": [
    { key: "name", label: "分类名称" },
    { key: "color", label: "颜色标识" },
    { key: "remark", label: "备注" },
  ],
  "stowage-steps": [
    { key: "name", label: "步骤名称" },
    { key: "category", label: "所属分类" },
    { key: "status", label: "对应状态" },
    { key: "remark", label: "备注" },
  ],
  forecasts: [
    { key: "no", label: "单号" },
    { key: "theDate", label: "日期" },
    { key: "customerName", label: "客户" },
    { key: "productName", label: "产品" },
    { key: "consignee", label: "收件人" },
    { key: "company", label: "公司" },
    { key: "country", label: "国家" },
    { key: "postcode", label: "邮编" },
    { key: "addTime", label: "创建时间", fmt: "date" },
  ],
  tracks: [
    { key: "name", label: "轨迹名称" },
    { key: "shortName", label: "简称" },
    { key: "type", label: "类型" },
  ],
  assets: [
    { key: "name", label: "名称" },
    { key: "theDate", label: "日期" },
    { key: "currency", label: "币种" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "depreciation", label: "折旧", fmt: "money" },
    { key: "surplus", label: "残值", fmt: "money" },
    { key: "month", label: "折旧月数" },
    { key: "remark", label: "备注" },
  ],
  cycles: [
    { key: "name", label: "名称" },
    { key: "cycle", label: "周期" },
    { key: "startDate", label: "开始日期" },
    { key: "endDate", label: "结束日期" },
    { key: "currency", label: "币种" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "account", label: "账户" },
    { key: "remark", label: "备注" },
  ],
  "received-sms": [
    { key: "name", label: "来源" },
    { key: "account", label: "账号" },
    { key: "pay", label: "付款方" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "time", label: "时间" },
    { key: "bankName", label: "绑定账户" },
    { key: "remark", label: "备注" },
  ],
  "expense-categories": [
    { key: "name", label: "分类名称" },
    { key: "type", label: "费用类型" },
    { key: "isComing", label: "收入", fmt: "bool" },
    { key: "remark", label: "备注" },
  ],
  "fee-item-types": [
    { key: "name", label: "名称" },
    { key: "type", label: "类型" },
    { key: "color", label: "颜色标识" },
    { key: "remark", label: "备注" },
  ],
  potentials: [
    { key: "name", label: "公司名称" },
    { key: "contacts", label: "联系人" },
    { key: "phone", label: "电话" },
    { key: "address", label: "地址" },
    { key: "product", label: "主营产品" },
    { key: "status", label: "状态" },
    { key: "qq", label: "QQ" },
    { key: "weixin", label: "微信" },
    { key: "addTime", label: "添加时间", fmt: "date" },
  ],
  "sold-tos": [
    { key: "name", label: "名称" },
    { key: "code", label: "编号" },
    { key: "consignee", label: "收件人" },
    { key: "company", label: "公司" },
    { key: "country", label: "国家" },
    { key: "phone", label: "电话" },
    { key: "postcode", label: "邮编" },
    { key: "address", label: "地址" },
  ],
  notices: [
    { key: "name", label: "发布人" },
    { key: "title", label: "标题" },
    { key: "summary", label: "摘要" },
    { key: "category", label: "分类" },
    { key: "addTime", label: "发布时间", fmt: "date" },
  ],
  socials: [
    { key: "month", label: "月份" },
    { key: "theDate", label: "日期" },
    { key: "wage", label: "基数", fmt: "money" },
    { key: "average", label: "平均工资", fmt: "money" },
    { key: "rate", label: "缴纳比例(%)" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "bankName", label: "缴纳账户" },
    { key: "remark", label: "备注" },
  ],
  "social-persons": [
    { key: "name", label: "姓名" },
    { key: "code", label: "编号" },
    { key: "type", label: "社保类型" },
    { key: "wage", label: "基数", fmt: "money" },
    { key: "rate", label: "比例(%)" },
    { key: "remark", label: "备注" },
  ],
  funds: [
    { key: "month", label: "月份" },
    { key: "theDate", label: "日期" },
    { key: "minWage", label: "最低基数", fmt: "money" },
    { key: "maxWage", label: "最高基数", fmt: "money" },
    { key: "rate", label: "缴纳比例(%)" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "bankName", label: "缴纳账户" },
    { key: "remark", label: "备注" },
  ],
  "fund-persons": [
    { key: "name", label: "姓名" },
    { key: "code", label: "编号" },
    { key: "wage", label: "基数", fmt: "money" },
    { key: "rate", label: "比例(%)" },
    { key: "company", label: "公司" },
    { key: "remark", label: "备注" },
  ],
  "commission-rules": [
    { key: "ruleName", label: "规则名称" },
    { key: "quota", label: "配额" },
    { key: "type", label: "提成方式" },
    { key: "percent", label: "比例(%)" },
    { key: "startDate", label: "开始日期" },
    { key: "endDate", label: "结束日期" },
    { key: "remark", label: "备注" },
  ],
  "bank-names": [
    { key: "name", label: "银行名称" },
    { key: "remark", label: "备注" },
  ],
  districts: [
    { key: "name", label: "英文名" },
    { key: "cn", label: "中文名" },
    { key: "code2", label: "二字码" },
    { key: "code3", label: "三字码" },
    { key: "phone", label: "区号" },
  ],
  "logistics-interfaces": [
    { key: "name", label: "接口名称" },
    { key: "code", label: "编号" },
    { key: "isOpen", label: "启用", fmt: "bool" },
    { key: "remark", label: "备注" },
  ],
  tasks: [
    { key: "name", label: "任务名称" },
    { key: "code", label: "编号" },
    { key: "interval", label: "间隔(秒)" },
    { key: "port", label: "端口" },
    { key: "count", label: "执行次数" },
    { key: "isOpen", label: "启用", fmt: "bool" },
    { key: "remark", label: "备注" },
  ],
  templates: [
    { key: "name", label: "模板名称" },
    { key: "title", label: "标题" },
    { key: "sendCustomer", label: "发客户", fmt: "bool" },
    { key: "sendSelf", label: "发自己", fmt: "bool" },
    { key: "isSave", label: "保存", fmt: "bool" },
  ],
};

const moduleCards = [
  { icon: WalletCards, title: "费率引擎", desc: "规则版本、低消、分抛、燃油、附加费叠加/取大", status: "P0" },
  { icon: ReceiptText, title: "应收账单", desc: "客户模板、币种、账单版本、门户下载与核销", status: "P0" },
  { icon: FileSpreadsheet, title: "成本对账", desc: "渠道账单映射、子单匹配、差异队列和申诉状态", status: "P0" },
  { icon: ShieldCheck, title: "不可变账本", desc: "复式分录、冲销调整、已过账不可修改", status: "P0" },
  { icon: AlertTriangle, title: "风险预警", desc: "卡派转快递前提示超长/超重和预计附加费", status: "P1" },
  { icon: Building2, title: "分公司管理", desc: "组织架构、分公司业绩、利润按分公司拆分", status: "P0" },
];

// ═══════════════ Form Schemas ═══════════════

const readOnlyTabs = new Set(['profits', 'void-orders']);

const settlementOpts = [{ v: 0, l: '不限' }, { v: 1, l: '货到付款' }, { v: 2, l: '日结' }, { v: 3, l: '周结' }, { v: 4, l: '半月结' }, { v: 5, l: '月结' }, { v: 6, l: '自定义' }];

const accFormFields: Record<string, FormField[]> = {
  orders: [
    { col: 'TheDate', label: '日期', type: 'date', required: true },
    { col: 'No', label: '客户单号', type: 'text', required: true },
    { col: 'TrackNo', label: '服务商单号', type: 'text' },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Product', label: '销售产品', type: 'select', ref: 'products' },
    { col: 'Channel', label: '渠道', type: 'select', ref: 'channels' },
    { col: 'Country', label: '目的地', type: 'select', ref: 'countries' },
    { col: 'Branch', label: '分公司', type: 'select', ref: 'branches' },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '实重(kg)', type: 'number' },
    { col: 'ChargeWeight', label: '计费重(kg)', type: 'number' },
    { col: 'Volume', label: '体积(m³)', type: 'number' },
    { col: 'DeclaredValue', label: '申报价值', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  collects: [
    { col: 'No', label: '总单号', type: 'text', required: true },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '未完成' }, { v: 1, l: '已完成' }] },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  returns: [
    { col: 'Express', label: '快件ID', type: 'number', required: true },
    { col: 'Reason', label: '退件原因', type: 'textarea', required: true },
    { col: 'Amount', label: '退还费用', type: 'number' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '待处理' }, { v: 1, l: '已退还' }] },
  ],
  detains: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers' },
    { col: 'Type', label: '扣件类型', type: 'select', opts: [{ v: 0, l: '系统扣件' }, { v: 1, l: '人工扣件' }, { v: 2, l: '客户扣件' }] },
    { col: 'Status', label: '处理状态', type: 'select', opts: [{ v: 0, l: '待扣件' }, { v: 1, l: '待放行' }, { v: 2, l: '已扣件' }, { v: 3, l: '已放行' }, { v: 4, l: '已退件' }] },
    { col: 'Remark', label: '扣件原因', type: 'textarea' },
  ],
  asks: [
    { col: 'No', label: '快件单号', type: 'text' },
    { col: 'Text', label: '问题内容', type: 'textarea', required: true },
    { col: 'Source', label: '来源', type: 'text' },
    { col: 'Type', label: '类型', type: 'text' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '待处理' }, { v: 1, l: '处理中' }, { v: 2, l: '待反馈' }, { v: 3, l: '已关闭' }] },
  ],
  reparations: [
    { col: 'Express', label: '快件ID', type: 'number', required: true },
    { col: 'Amount', label: '申请金额', type: 'number', required: true },
    { col: 'Paid', label: '赔偿金额', type: 'number' },
    { col: 'Remark', label: '赔偿原因', type: 'textarea' },
  ],
  shipments: [
    { col: 'No', label: '出货单号', type: 'text', required: true },
    { col: 'Channel', label: '渠道', type: 'select', ref: 'channels' },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers' },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Charge', label: '运费', type: 'number' },
    { col: 'Cost', label: '成本', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  stowages: [
    { col: 'No', label: '配载单号', type: 'text', required: true },
    { col: 'Flight', label: '航班号', type: 'text' },
    { col: 'DeparturePort', label: '起始港', type: 'select', ref: 'ports' },
    { col: 'ArrivalPort', label: '目的港', type: 'select', ref: 'ports' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '录单中' }, { v: 1, l: '国内出发' }, { v: 2, l: '国内抵达' }, { v: 3, l: '离境出发' }, { v: 4, l: '国外抵达' }, { v: 5, l: '清关完成' }, { v: 6, l: '出口查验' }, { v: 7, l: '航班延误' }, { v: 8, l: '清关查验' }] },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Volume', label: '体积(m³)', type: 'number' },
    { col: 'ETD', label: 'ETD', type: 'date' },
    { col: 'ETA', label: 'ETA', type: 'date' },
  ],
  packages: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Consignee', label: '收货人', type: 'text' },
    { col: 'Company', label: '公司', type: 'text' },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Quantity', label: '数量', type: 'number' },
    { col: 'DeclaredValue', label: '申报价值', type: 'number' },
    { col: 'Postcode', label: '邮编', type: 'text' },
  ],
  transits: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Type', label: '类型', type: 'text' },
    { col: 'Quantity', label: '票数', type: 'number' },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Supplier', label: '服务商', type: 'select', ref: 'suppliers' },
    { col: 'Tariff', label: '关税', type: 'number' },
    { col: 'Amount', label: '成本', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  ports: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Consignee', label: '收件人', type: 'text' },
    { col: 'Company', label: '公司名称', type: 'text' },
    { col: 'Type', label: '港口类型', type: 'text' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  warehouses: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Code', label: '仓库编码', type: 'text' },
    { col: 'Consignee', label: '收件人', type: 'text' },
    { col: 'Company', label: '公司名称', type: 'text' },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Province', label: '省/洲', type: 'text' },
    { col: 'Postcode', label: '邮编', type: 'text' },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '亚马逊' }, { v: 1, l: '海外仓' }] },
  ],
  charges: [
    { col: 'Express', label: '快件ID', type: 'number', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers' },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Type', label: '类型', type: 'text' },
    { col: 'Amount', label: '应收金额', type: 'number', required: true },
    { col: 'Paid', label: '实收金额', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  costs: [
    { col: 'Express', label: '快件ID', type: 'number', required: true },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers' },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Type', label: '类型', type: 'text' },
    { col: 'Amount', label: '应付金额', type: 'number', required: true },
    { col: 'Paid', label: '实付金额', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  bills: [
    { col: 'No', label: '账单号', type: 'text', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Settlement', label: '结算方式', type: 'select', opts: settlementOpts },
    { col: 'TheDate', label: '账单日', type: 'date', required: true },
    { col: 'EndDate', label: '截止日', type: 'date' },
    { col: 'Amount', label: '账单金额', type: 'number' },
    { col: 'Paid', label: '已付金额', type: 'number' },
    { col: 'Quantity', label: '数量', type: 'number' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '待结款' }, { v: 1, l: '已结清' }, { v: 2, l: '已过结' }, { v: 3, l: '已逾期' }] },
  ],
  payments: [
    { col: 'No', label: '付款单号', type: 'text', required: true },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers', required: true },
    { col: 'Bank', label: '账户', type: 'select', ref: 'banks' },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  receiveds: [
    { col: 'No', label: '收款单号', type: 'text', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Bank', label: '账户', type: 'select', ref: 'banks' },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  fees: [
    { col: 'Name', label: '套餐名称', type: 'text', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'fee-types': [
    { col: 'Name', label: '费用名称', type: 'text', required: true },
    { col: 'Type', label: '类型', type: 'text' },
    { col: 'Unit', label: '单位', type: 'text' },
    { col: 'Method', label: '计费方式', type: 'text' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-fines': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Amount', label: '罚款金额', type: 'number', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'supplier-fines': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers', required: true },
    { col: 'Amount', label: '罚款金额', type: 'number', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-adjusts': [
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Amount', label: '调账金额', type: 'number', required: true },
    { col: 'Reason', label: '申请理由', type: 'textarea', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'supplier-adjusts': [
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers', required: true },
    { col: 'Amount', label: '调账金额', type: 'number', required: true },
    { col: 'Reason', label: '申请理由', type: 'textarea', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-refunds': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Amount', label: '退款金额', type: 'number', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'supplier-refunds': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers', required: true },
    { col: 'Amount', label: '退款金额', type: 'number', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-rebates': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'Amount', label: '返利金额', type: 'number', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'supplier-rebates': [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers', required: true },
    { col: 'Amount', label: '返利金额', type: 'number', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  expenses: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'Bank', label: '账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  banks: [
    { col: 'Name', label: '账户名称', type: 'text', required: true },
    { col: 'Currency', label: '币种', type: 'select', ref: 'currencies' },
    { col: 'Deposit', label: '存款', type: 'number' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'isShow', label: '显示', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  commissions: [
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Type', label: '提成方式', type: 'select', opts: [{ v: 0, l: '按销售额' }, { v: 1, l: '按利润额' }, { v: 2, l: '按销售数' }] },
    { col: 'Percent', label: '提成比例(%)', type: 'number' },
    { col: 'Month', label: '月份', type: 'text' },
    { col: 'Amount', label: '销售额', type: 'number' },
    { col: 'Quantity', label: '销售数量', type: 'number' },
    { col: 'Commission', label: '提成金额', type: 'number' },
  ],
  transfers: [
    { col: 'FromBank', label: '转出账户', type: 'select', ref: 'banks', required: true },
    { col: 'ToBank', label: '转入账户', type: 'select', ref: 'banks', required: true },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  dividends: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '入股' }, { v: 1, l: '分红' }] },
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'Bank', label: '资金账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  borrowings: [
    { col: 'Name', label: '借贷人', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '借入' }, { v: 1, l: '借出' }, { v: 2, l: '还款' }, { v: 3, l: '回款' }, { v: 4, l: '还息' }, { v: 5, l: '收息' }] },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'Rate', label: '利率(%)', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  currencies: [
    { col: 'Name', label: '货币名称', type: 'text', required: true },
    { col: 'Code', label: '代码', type: 'text', required: true },
    { col: 'Symbol', label: '符号', type: 'text' },
    { col: 'Rate', label: '汇率', type: 'number' },
    { col: 'Decimal', label: '小数位', type: 'number' },
  ],
  customers: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Code', label: '编码', type: 'text', required: true },
    { col: 'Contact', label: '联系人', type: 'text' },
    { col: 'Mobile', label: '手机', type: 'text' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Email', label: '邮箱', type: 'text' },
    { col: 'Address', label: '地址', type: 'textarea' },
    { col: 'Grade', label: '等级', type: 'text' },
    { col: 'Group', label: '分组', type: 'select', ref: 'customer-groups' },
    { col: 'Branch', label: '分公司', type: 'select', ref: 'branches' },
    { col: 'Settlement', label: '结算方式', type: 'select', opts: settlementOpts },
    { col: 'Credits', label: '授信额度', type: 'number' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-groups': [
    { col: 'Name', label: '分组名称', type: 'text', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  suppliers: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Contact', label: '联系人', type: 'text' },
    { col: 'Mobile', label: '手机', type: 'text' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Email', label: '邮箱', type: 'text' },
    { col: 'Address', label: '地址', type: 'textarea' },
    { col: 'Product', label: '主营产品', type: 'text' },
    { col: 'Settlement', label: '结算方式', type: 'select', opts: settlementOpts },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  channels: [
    { col: 'Name', label: '渠道名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'isDebug', label: '调试模式', type: 'boolean' },
    { col: 'Remark', label: '说明', type: 'textarea' },
  ],
  'channel-accounts': [
    { col: 'Name', label: '账号名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Channel', label: '渠道', type: 'select', ref: 'channels', required: true },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
  ],
  products: [
    { col: 'Name', label: '产品名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Channel', label: '渠道', type: 'select', ref: 'channels' },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'product-items': [
    { col: 'NameEN', label: '英文品名', type: 'text', required: true },
    { col: 'NameCN', label: '中文品名', type: 'text' },
    { col: 'HSCode', label: 'HS编码', type: 'text' },
  ],
  employees: [
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Gender', label: '性别', type: 'select', opts: [{ v: 0, l: '男' }, { v: 1, l: '女' }] },
    { col: 'Mobile', label: '手机', type: 'text' },
    { col: 'Email', label: '邮箱', type: 'text' },
    { col: 'Department', label: '部门', type: 'select', ref: 'departments' },
    { col: 'Branch', label: '分公司', type: 'select', ref: 'branches' },
    { col: 'Position', label: '职位', type: 'text' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '未入职' }, { v: 1, l: '试用期' }, { v: 2, l: '正式员工' }, { v: 3, l: '长期休假' }, { v: 4, l: '离职' }] },
    { col: 'EntryDate', label: '入职日期', type: 'date' },
  ],
  wages: [
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Month', label: '月份', type: 'text', required: true },
    { col: 'Basic', label: '基本工资', type: 'number' },
    { col: 'Bonus', label: '奖金', type: 'number' },
    { col: 'Commission', label: '提成', type: 'number' },
    { col: 'Deduction', label: '扣款', type: 'number' },
    { col: 'Total', label: '实发', type: 'number' },
  ],
  attendances: [
    { col: 'Employee', label: '员工', type: 'select', ref: 'employees', required: true },
    { col: 'TheDate', label: '日期', type: 'date', required: true },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '免打卡' }, { v: 1, l: '事假' }, { v: 2, l: '病假' }, { v: 3, l: '法定假' }, { v: 4, l: '迟到' }, { v: 5, l: '早退' }, { v: 6, l: '旷工' }, { v: 7, l: '违纪' }, { v: 8, l: '加班' }, { v: 9, l: '假日加班' }] },
    { col: 'Hours', label: '时长(h)', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'acc-branches': [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Code', label: '编码', type: 'text' },
    { col: 'Contact', label: '联系人', type: 'text' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Address', label: '地址', type: 'textarea' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  departments: [
    { col: 'Name', label: '部门名称', type: 'text', required: true },
    { col: 'Branch', label: '所属分店', type: 'select', ref: 'branches' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  countries: [
    { col: 'Name', label: '英文名', type: 'text', required: true },
    { col: 'CN', label: '中文名', type: 'text' },
    { col: 'Code', label: '代码', type: 'text' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
  ],
  zones: [
    { col: 'Name', label: '分区名称', type: 'text', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  postcodes: [
    { col: 'Postcode', label: '邮编', type: 'text', required: true },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Province', label: '省/洲', type: 'text' },
    { col: 'City', label: '城市', type: 'text' },
  ],
  remotes: [
    { col: 'Postcode', label: '邮编', type: 'text', required: true },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Supplier', label: '物流商', type: 'select', ref: 'suppliers' },
    { col: 'Type', label: '类型', type: 'text' },
  ],
  fuels: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Rate', label: '费率(%)', type: 'number' },
    { col: 'StartDate', label: '开始日期', type: 'date' },
    { col: 'EndDate', label: '结束日期', type: 'date' },
  ],
  hscodes: [
    { col: 'Code', label: 'HS编码', type: 'text', required: true },
    { col: 'NameEN', label: '英文品名', type: 'text' },
    { col: 'NameCN', label: '中文品名', type: 'text' },
  ],
  'quick-orders': [
    { col: 'No', label: '总单号', type: 'text', required: true },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '未完成' }, { v: 1, l: '已完成' }] },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  dispatches: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers' },
    { col: 'Product', label: '产品', type: 'select', ref: 'products' },
    { col: 'Piece', label: '件数', type: 'number' },
    { col: 'Weight', label: '重量(kg)', type: 'number' },
    { col: 'Volume', label: '体积(m³)', type: 'number' },
    { col: 'Employee', label: '收件员', type: 'select', ref: 'employees' },
    { col: 'Picker', label: '取件人', type: 'text' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '待收件' }, { v: 1, l: '已安排' }, { v: 2, l: '已收货' }, { v: 3, l: '已取消' }] },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'stowage-categories': [
    { col: 'Name', label: '分类名称', type: 'text', required: true },
    { col: 'Color', label: '颜色标识', type: 'text' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'stowage-steps': [
    { col: 'Name', label: '步骤名称', type: 'text', required: true },
    { col: 'Category', label: '所属分类', type: 'select', ref: 'stowage-categories' },
    { col: 'Status', label: '对应状态', type: 'select', opts: [{ v: 0, l: '录单中' }, { v: 1, l: '国内出发' }, { v: 2, l: '国内抵达' }, { v: 3, l: '离境出发' }, { v: 4, l: '国外抵达' }, { v: 5, l: '清关完成' }, { v: 6, l: '出口查验' }, { v: 7, l: '航班延误' }, { v: 8, l: '清关查验' }] },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  forecasts: [
    { col: 'No', label: '单号', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers' },
    { col: 'Product', label: '产品', type: 'select', ref: 'products' },
    { col: 'Consignee', label: '收件人', type: 'text' },
    { col: 'Company', label: '公司', type: 'text' },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Postcode', label: '邮编', type: 'text' },
  ],
  tracks: [
    { col: 'Name', label: '轨迹名称', type: 'text', required: true },
    { col: 'ShortName', label: '简称', type: 'text' },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '人工轨迹' }, { v: 1, l: '追踪轨迹' }, { v: 2, l: '已提取' }, { v: 3, l: '送货中' }, { v: 4, l: '已签收' }, { v: 5, l: '普通延误' }, { v: 6, l: '严重延误' }, { v: 7, l: '信息错误' }, { v: 8, l: '补充费用' }, { v: 9, l: '快件丢失' }, { v: 10, l: '快件损坏' }, { v: 11, l: '快件拒收' }, { v: 12, l: '快件退件' }, { v: 13, l: '快件赔索' }] },
  ],
  assets: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Currency', label: '币种', type: 'select', ref: 'currencies' },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'Depreciation', label: '折旧', type: 'number' },
    { col: 'Surplus', label: '残值', type: 'number' },
    { col: 'Month', label: '折旧月数', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  cycles: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Cycle', label: '周期', type: 'select', opts: [{ v: 0, l: '每年' }, { v: 1, l: '每月' }, { v: 2, l: '每周' }, { v: 3, l: '每天' }] },
    { col: 'StartDate', label: '开始日期', type: 'date' },
    { col: 'EndDate', label: '结束日期', type: 'date' },
    { col: 'Currency', label: '币种', type: 'select', ref: 'currencies' },
    { col: 'Amount', label: '金额', type: 'number', required: true },
    { col: 'Account', label: '账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'received-sms': [
    { col: 'Name', label: '来源', type: 'text' },
    { col: 'Account', label: '账号', type: 'text' },
    { col: 'Pay', label: '付款方', type: 'text' },
    { col: 'Amount', label: '金额', type: 'number' },
    { col: 'Time', label: '时间', type: 'text' },
    { col: 'Binding', label: '绑定账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'expense-categories': [
    { col: 'Name', label: '分类名称', type: 'text', required: true },
    { col: 'Type', label: '费用类型', type: 'select', opts: [{ v: 0, l: '管理费用' }, { v: 1, l: '销售费用' }, { v: 2, l: '财务费用' }, { v: 3, l: '采购费用' }] },
    { col: 'isComing', label: '收入', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'fee-item-types': [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Type', label: '类型', type: 'select', opts: [{ v: 0, l: '费用' }, { v: 1, l: '成本' }] },
    { col: 'Color', label: '颜色标识', type: 'text' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  potentials: [
    { col: 'Name', label: '公司名称', type: 'text', required: true },
    { col: 'Contacts', label: '联系人', type: 'text' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Address', label: '地址', type: 'textarea' },
    { col: 'Product', label: '主营产品', type: 'text' },
    { col: 'Status', label: '状态', type: 'select', opts: [{ v: 0, l: '待分配' }, { v: 1, l: '公关中' }, { v: 2, l: '待回复' }, { v: 3, l: '不合作' }, { v: 4, l: '已签单' }] },
    { col: 'QQ', label: 'QQ', type: 'text' },
    { col: 'Weixin', label: '微信', type: 'text' },
  ],
  'sold-tos': [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Consignee', label: '收件人', type: 'text' },
    { col: 'Company', label: '公司', type: 'text' },
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'Phone', label: '电话', type: 'text' },
    { col: 'Postcode', label: '邮编', type: 'text' },
    { col: 'Address', label: '地址', type: 'textarea' },
  ],
  notices: [
    { col: 'Name', label: '发布人', type: 'text' },
    { col: 'Title', label: '标题', type: 'text', required: true },
    { col: 'Summary', label: '摘要', type: 'textarea' },
    { col: 'Category', label: '分类', type: 'select', opts: [{ v: 1, l: '业务调整通知' }, { v: 2, l: '公司重要公告' }, { v: 3, l: '系统更新通知' }, { v: 4, l: '常用文档下载' }] },
  ],
  socials: [
    { col: 'Month', label: '月份', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'Wage', label: '基数', type: 'number' },
    { col: 'Average', label: '平均工资', type: 'number' },
    { col: 'Rate', label: '缴纳比例(%)', type: 'number' },
    { col: 'Amount', label: '金额', type: 'number' },
    { col: 'Bank', label: '缴纳账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'social-persons': [
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Type', label: '社保类型', type: 'select', opts: [{ v: 0, l: '不购买' }, { v: 1, l: '深户社保' }, { v: 2, l: '综合社保' }, { v: 3, l: '住院社保' }, { v: 4, l: '民工社保' }] },
    { col: 'Wage', label: '基数', type: 'number' },
    { col: 'Rate', label: '比例(%)', type: 'number' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  funds: [
    { col: 'Month', label: '月份', type: 'text', required: true },
    { col: 'TheDate', label: '日期', type: 'date' },
    { col: 'MinWage', label: '最低基数', type: 'number' },
    { col: 'MaxWage', label: '最高基数', type: 'number' },
    { col: 'Rate', label: '缴纳比例(%)', type: 'number' },
    { col: 'Amount', label: '金额', type: 'number' },
    { col: 'Bank', label: '缴纳账户', type: 'select', ref: 'banks' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'fund-persons': [
    { col: 'Name', label: '姓名', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Wage', label: '基数', type: 'number' },
    { col: 'Rate', label: '比例(%)', type: 'number' },
    { col: 'Company', label: '公司', type: 'text' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'commission-rules': [
    { col: 'Rule', label: '所属规则', type: 'number', required: true },
    { col: 'Quota', label: '配额', type: 'number' },
    { col: 'Type', label: '提成方式', type: 'select', opts: [{ v: 0, l: '销售额' }, { v: 1, l: '利润额' }, { v: 2, l: '销售数量' }] },
    { col: 'Percent', label: '比例(%)', type: 'number' },
    { col: 'StartDate', label: '开始日期', type: 'date' },
    { col: 'EndDate', label: '结束日期', type: 'date' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'bank-names': [
    { col: 'Name', label: '银行名称', type: 'text', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  districts: [
    { col: 'Name', label: '英文名', type: 'text', required: true },
    { col: 'CN', label: '中文名', type: 'text' },
    { col: 'Code2', label: '二字码', type: 'text' },
    { col: 'Code3', label: '三字码', type: 'text' },
    { col: 'Phone', label: '区号', type: 'text' },
  ],
  'logistics-interfaces': [
    { col: 'Name', label: '接口名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  tasks: [
    { col: 'Name', label: '任务名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'Interval', label: '间隔(秒)', type: 'number' },
    { col: 'Port', label: '端口', type: 'number' },
    { col: 'Count', label: '执行次数', type: 'number' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  templates: [
    { col: 'Name', label: '模板名称', type: 'text', required: true },
    { col: 'Title', label: '标题', type: 'text' },
    { col: 'SendCustomer', label: '发客户', type: 'boolean' },
    { col: 'SendSelf', label: '发自己', type: 'boolean' },
    { col: 'isSave', label: '保存', type: 'boolean' },
  ],
};

const currentFormFields = computed(() => accFormFields[accTab.value] ?? []);
const canCrud = computed(() => !readOnlyTabs.has(accTab.value) && !!accFormFields[accTab.value]);

const refEndpoints: Record<string, { api: string; nameField: string }> = {
  customers: { api: 'customers', nameField: 'name' },
  suppliers: { api: 'suppliers', nameField: 'name' },
  channels: { api: 'channels', nameField: 'name' },
  products: { api: 'products', nameField: 'name' },
  countries: { api: 'countries', nameField: 'cn' },
  branches: { api: 'branches', nameField: 'name' },
  departments: { api: 'departments', nameField: 'name' },
  banks: { api: 'banks', nameField: 'name' },
  employees: { api: 'employees', nameField: 'name' },
  'customer-groups': { api: 'customer-groups', nameField: 'name' },
  ports: { api: 'ports', nameField: 'name' },
  currencies: { api: 'currencies', nameField: 'name' },
  'stowage-categories': { api: 'stowage-categories', nameField: 'name' },
};

// ═══════════════ Helpers ═══════════════

function fmt(n: number): string {
  return n.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtInt(n?: number): string {
  return Number(n ?? 0).toLocaleString("zh-CN");
}

function fmtPercent(n: number): string {
  return `${(n * 100).toFixed(1)}%`;
}

function ratio(value: number, total: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(total) || total <= 0) return 0;
  return Math.max(0, Math.min(100, (value / total) * 100));
}

function trackingStatusLabel(status?: string): string {
  const labels: Record<string, string> = {
    CREATED: "已建单",
    ORDERED: "已下单",
    DRAFT: "草稿",
    IN_WAREHOUSE: "已入仓",
    MEASURED: "已测量",
    BOOKED: "已订舱",
    IN_TRANSIT: "运输中",
    OUT_FOR_DELIVERY: "派送中",
    DELIVERED: "已签收",
    CLOSED: "已关闭",
    EXCEPTION: "异常",
    CLAIMING: "理赔中",
    RETURNED: "已退回",
    VOID: "已作废",
  };
  const code = String(status ?? "").toUpperCase();
  return labels[code] ?? (code || "-");
}

function fmtTime(value?: string): string {
  if (!value) return "暂无轨迹时间";
  return value.slice(0, 16).replace("T", " ");
}

/** 模板里渲染 provider evidence 用：JSON 美化 + 缺失兜底。 */
function fmtJson(value: any): string {
  if (value === null || value === undefined) return "—";
  try { return JSON.stringify(value, null, 2); }
  catch { return String(value); }
}

function fmtCell(value: any, format?: string): string {
  if (value === null || value === undefined) return "-";
  if (format === "money") return "¥" + fmt(Number(value));
  if (format === "bool") return value ? "是" : "否";
  if (format === "date" && typeof value === "string") return value.slice(0, 19).replace("T", " ");
  return String(value);
}

const dashboardFlows = computed<DashboardFlowData[]>(() => {
  const flows = dashboard.value?.flows ?? [];
  if (flows.length) return flows;
  if (!dashboard.value) return [];
  return [
    {
      code: "SELLER_FULFILLMENT",
      customerDirection: "SELLER_CUSTOMER",
      label: "卖货客户履约",
      orders: dashboard.value.xqt.shipmentCount ?? 0,
      shipments: dashboard.value.xqt.shipmentCount ?? 0,
      receivable: dashboard.value.xqt.revenue ?? 0,
      payable: 0,
      profit: dashboard.value.xqt.revenue ?? 0,
    },
    {
      code: "DOCUMENT_SHIPPING",
      customerDirection: "DOCUMENT_CUSTOMER",
      label: "制单客户发货",
      orders: dashboard.value.acc.orderCount ?? 0,
      shipments: dashboard.value.acc.orderCount ?? 0,
      receivable: dashboard.value.acc.revenue ?? 0,
      payable: dashboard.value.acc.cost ?? 0,
      profit: dashboard.value.acc.profit ?? 0,
    },
  ];
});

const dashboardKpis = computed(() => {
  if (!dashboard.value) return [];
  const totalRevenue = dashboard.value.combined.totalRevenue;
  const totalProfit = dashboard.value.combined.totalProfit;
  const profitRate = totalRevenue > 0 ? totalProfit / totalRevenue : 0;
  const paid = Number(dashboard.value.local.paid ?? 0);
  const unpaid = Number(dashboard.value.local.unpaid ?? 0);
  const collectionRate = paid + unpaid > 0 ? paid / (paid + unpaid) : 0;
  return [
    { icon: WalletCards, label: "总营收", value: `¥${fmt(totalRevenue)}`, sub: "客户应收口径", tone: "teal" },
    { icon: ReceiptText, label: "总成本", value: `¥${fmt(dashboard.value.combined.totalCost)}`, sub: "供应商应付口径", tone: "amber" },
    {
      icon: HandCoins,
      label: "毛利",
      value: `¥${fmt(totalProfit)}`,
      sub: `毛利率 ${fmtPercent(profitRate)}`,
      tone: totalProfit >= 0 ? "green" : "red",
    },
    { icon: Package, label: "运单", value: fmtInt(dashboard.value.local.shipments), sub: `订单 ${fmtInt(dashboard.value.local.orders)}`, tone: "blue" },
    { icon: FileText, label: "账单", value: fmtInt(dashboard.value.local.invoiceCount), sub: `回款率 ${fmtPercent(collectionRate)}`, tone: "violet" },
  ];
});

const maxFlowRevenue = computed(() => Math.max(1, ...dashboardFlows.value.map(flow => Math.abs(flow.receivable))));
const maxFinanceValue = computed(() => {
  if (!dashboard.value) return 1;
  return Math.max(
    1,
    Math.abs(dashboard.value.local.receivable),
    Math.abs(dashboard.value.local.payable),
    Math.abs(dashboard.value.local.profit),
  );
});

const financeBars = computed(() => {
  if (!dashboard.value) return [];
  return [
    { label: "应收", value: dashboard.value.local.receivable, tone: "green" },
    { label: "应付", value: dashboard.value.local.payable, tone: "amber" },
    { label: "毛利", value: dashboard.value.local.profit, tone: dashboard.value.local.profit >= 0 ? "blue" : "red" },
  ];
});

const donutStyle = computed(() => {
  const colors = ["#0f8f7f", "#2563eb", "#f59e0b", "#8b5cf6"];
  const total = dashboardFlows.value.reduce((sum, flow) => sum + Math.max(0, flow.receivable), 0);
  if (total <= 0) return { background: "#e5e7eb" };
  let cursor = 0;
  const stops = dashboardFlows.value.map((flow, index) => {
    const start = cursor;
    cursor += ratio(Math.max(0, flow.receivable), total);
    return `${colors[index % colors.length]} ${start}% ${cursor}%`;
  });
  return { background: `conic-gradient(${stops.join(", ")})` };
});

const pipelineNodes = computed(() => {
  if (!dashboard.value) return [];
  return [
    { label: "订单", value: fmtInt(dashboard.value.local.orders), desc: "接入需求" },
    { label: "运单", value: fmtInt(dashboard.value.local.shipments), desc: "履约执行" },
    { label: "应收", value: `¥${fmt(dashboard.value.local.receivable)}`, desc: "客户账单" },
    { label: "应付", value: `¥${fmt(dashboard.value.local.payable)}`, desc: "成本结算" },
    { label: "毛利", value: `¥${fmt(dashboard.value.local.profit)}`, desc: "经营结果" },
  ];
});

const dashboardAlerts = computed(() => {
  const items = [];
  if (!health.value?.upstreams.postgres.connected) {
    items.push({ tone: "red", title: "数据库连接异常", desc: "PostgreSQL 当前不可用，业务看板数据可能滞后。" });
  }
  if (dashboard.value && dashboard.value.local.profit < 0) {
    items.push({ tone: "red", title: "毛利为负", desc: "当前周期成本高于应收，需要复核成本规则和账单。" });
  }
  if (dashboard.value && dashboardFlows.value.every(flow => flow.orders === 0 && flow.shipments === 0)) {
    items.push({ tone: "amber", title: "业务线暂无数据", desc: "卖货/制单流程尚未形成可分析样本。" });
  }
  if (!health.value?.upstreams.acc.connected || !health.value?.upstreams.xqt.connected) {
    items.push({ tone: "blue", title: "外部系统仅作对照", desc: "ACC 与 XQT 当前不作为运行时依赖，新系统数据以本地库为准。" });
  }
  if (!items.length) {
    items.push({ tone: "green", title: "核心链路正常", desc: "认证、数据库、业务聚合接口均可用于驾驶舱刷新。" });
  }
  return items.slice(0, 4);
});

const trackingRoutes = computed<DashboardTrackingRoute[]>(() => dashboard.value?.tracking?.routes ?? []);
const trackingSummary = computed(() => dashboard.value?.tracking?.summary ?? {
  activeShipments: 0,
  exceptionCount: 0,
  deliveredToday: 0,
  trackedShipments: 0,
  destinationCountries: 0,
});
const selectedTrackingRoute = computed(() => {
  if (!trackingRoutes.value.length) return null;
  return trackingRoutes.value.find(route => route.id === selectedTrackingRouteId.value) ?? trackingRoutes.value[0];
});
const trackingStats = computed(() => [
  { label: "在途运单", value: fmtInt(trackingSummary.value.activeShipments), tone: "blue" },
  { label: "已挂轨迹", value: fmtInt(trackingSummary.value.trackedShipments), tone: "green" },
  { label: "目的国家", value: fmtInt(trackingSummary.value.destinationCountries), tone: "violet" },
  { label: "异常件", value: fmtInt(trackingSummary.value.exceptionCount), tone: trackingSummary.value.exceptionCount > 0 ? "red" : "green" },
]);

function isSelectedTrackingRoute(route: DashboardTrackingRoute): boolean {
  return selectedTrackingRoute.value?.id === route.id;
}

function selectTrackingRoute(route: DashboardTrackingRoute): void {
  selectedTrackingRouteId.value = route.id;
}

async function readJson(response: Response, label = "请求") {
  const body = await response.text();
  if (!body.trim()) {
    throw new Error(`${label}返回空响应 (${response.status})`);
  }
  try {
    return JSON.parse(body);
  } catch {
    throw new Error(`${label}返回的不是 JSON (${response.status})`);
  }
}

async function fetchJson(input: string, init: RequestInit = {}, label = "请求") {
  // 默认走 apiFetch 注入 Bearer；对于明确无需鉴权的路径（如 /api/public/*）调用方可自行用原始 fetch。
  const response = await apiFetch(input, init);
  const json = await readJson(response, label);
  if (!response.ok || json?.ok === false) {
    throw new Error(json?.error ?? `${label}失败 (${response.status})`);
  }
  return json;
}

async function fetchOptionalJson<T>(input: string, fallback: T, label: string): Promise<T> {
  try {
    return await fetchJson(input, {}, label) as T;
  } catch (e: any) {
    error.value = e.message ?? `${label}失败`;
    return fallback;
  }
}

function authHeaders(init?: HeadersInit): Headers {
  const headers = new Headers(init);
  if (authToken.value) headers.set("Authorization", `Bearer ${authToken.value}`);
  return headers;
}

async function apiFetch(input: string, init: RequestInit = {}) {
  const response = await fetch(input, {
    ...init,
    headers: authHeaders(init.headers),
  });
  if (response.status === 401) {
    logout(false);
  }
  return response;
}

async function loadMe() {
  if (!authToken.value) return;
  const res = await apiFetch(`${API}/api/auth/me`);
  if (!res.ok) throw new Error("登录已过期");
  const json = await readJson(res, "加载当前用户");
  authUser.value = json.data?.user ?? json.user;
}

async function login() {
  loginLoading.value = true;
  loginError.value = "";
  try {
    const json = await fetchJson(`${API}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(loginForm),
    }, "登录");
    const loginData = json.data ?? json;
    authToken.value = loginData.token;
    authUser.value = loginData.user;
    localStorage.setItem("xqt_auth_token", loginData.token);
    await fetchDashboard();
  } catch (e: any) {
    loginError.value = e.message ?? "登录失败";
  } finally {
    loginLoading.value = false;
  }
}

async function logout(callApi = true) {
  if (callApi && authToken.value) {
    await apiFetch(`${API}/api/auth/logout`, { method: "POST" }).catch(() => {});
  }
  authToken.value = "";
  authUser.value = null;
  localStorage.removeItem("xqt_auth_token");
}

async function bootstrap() {
  if (authToken.value) {
    try {
      await loadMe();
      await fetchDashboard();
      return;
    } catch {
      logout(false);
    }
  }
}

// ═══════════════ Data Fetching ═══════════════

async function fetchDashboard() {
  loading.value = true;
  error.value = "";
  try {
    const [dashData, healthData, branchData] = await Promise.all([
      fetchOptionalJson<DashboardData | null>(`${API}/api/finance/dashboard`, null, "加载财务看板"),
      fetchOptionalJson<HealthData | null>(`${API}/api/finance/dashboard/health`, null, "加载系统状态"),
      fetchOptionalJson<{ data?: BranchData[] }>(`${API}/api/finance/branches`, { data: [] }, "加载分公司"),
    ]);
    dashboard.value = dashData;
    health.value = healthData;
    branches.value = branchData.data ?? [];
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

// ACC data fetching

const noDateTabs = new Set(["channels", "acc-branches", "departments", "countries", "fuels", "currencies", "fees", "fee-types", "banks", "ports", "warehouses", "customer-groups", "zones", "stowage-categories", "stowage-steps", "tracks", "expense-categories", "fee-item-types", "bank-names", "logistics-interfaces", "potentials", "sold-tos", "notices", "social-persons", "fund-persons", "commission-rules", "districts", "tasks", "templates"]);
const noPaginationTabs = new Set(["channels", "acc-branches", "departments", "countries", "fuels", "currencies", "fees", "fee-types", "banks", "ports", "warehouses", "customer-groups", "zones", "stowage-categories", "stowage-steps", "tracks", "expense-categories", "fee-item-types", "bank-names", "logistics-interfaces"]);

async function fetchAccData() {
  accLoading.value = true;
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;

  const params = new URLSearchParams();
  if (!noPaginationTabs.has(accTab.value)) {
    params.set("page", String(accPage.value));
    params.set("pageSize", String(accPageSize.value));
  }
  if (accKeyword.value) params.set("keyword", accKeyword.value);
  if (accDateFrom.value && !noDateTabs.has(accTab.value)) params.set("dateFrom", accDateFrom.value);
  if (accDateTo.value && !noDateTabs.has(accTab.value)) params.set("dateTo", accDateTo.value);

  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}?${params}`);
    const json = await res.json();
    if (Array.isArray(json)) {
      accData.value = json;
      accTotal.value = json.length;
    } else {
      accData.value = json.data ?? [];
      accTotal.value = json.total ?? accData.value.length;
    }
  } catch (e: any) {
    accData.value = [];
    accTotal.value = 0;
  } finally {
    accLoading.value = false;
  }
}

async function fetchAccStats() {
  try {
    const res = await apiFetch(`${API}/api/acc/stats`);
    accStats.value = await res.json();
  } catch {
    accStats.value = null;
  }
}

function accSearch() {
  accPage.value = 1;
  fetchAccData();
}

function accPrev() {
  if (accPage.value > 1) { accPage.value--; fetchAccData(); }
}

function accNext() {
  if (accPage.value < accTotalPages.value) { accPage.value++; fetchAccData(); }
}

watch(accTab, () => {
  accPage.value = 1;
  accKeyword.value = "";
  accDateFrom.value = "";
  accDateTo.value = "";
  fetchAccData();
});

watch(currentNav, (nav) => {
  if (nav === "acc") {
    fetchAccStats();
    fetchAccData();
  }
});

onMounted(bootstrap);

// ═══════════════ Form CRUD ═══════════════

async function loadSelectOptions(fields: FormField[]) {
  const refs = [...new Set(fields.filter(f => f.ref).map(f => f.ref!))];
  for (const r of refs) {
    if (selectOptions.value[r]) continue;
    const ep = refEndpoints[r];
    if (!ep) continue;
    try {
      const res = await apiFetch(`${API}/api/acc/${ep.api}?pageSize=9999`);
      const json = await res.json();
      const list = Array.isArray(json) ? json : (json.data ?? []);
      selectOptions.value[r] = list.map((row: any) => ({
        id: row.id,
        name: row[ep.nameField] || row.name || row.code || String(row.id),
      }));
    } catch {
      selectOptions.value[r] = [];
    }
  }
}

async function openAdd() {
  formMode.value = 'add';
  editId.value = 0;
  formError.value = '';
  const fields = currentFormFields.value;
  Object.keys(formData).forEach(k => delete formData[k]);
  fields.forEach(f => {
    if (f.type === 'date') formData[f.col] = new Date().toISOString().slice(0, 10);
    else if (f.type === 'number') formData[f.col] = 0;
    else if (f.type === 'boolean') formData[f.col] = 1;
    else formData[f.col] = '';
  });
  await loadSelectOptions(fields);
  showForm.value = true;
}

async function openEdit(row: any) {
  formMode.value = 'edit';
  editId.value = row.id;
  formError.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}/${row.id}/raw`);
    const raw = await res.json();
    if (raw?.error) { formError.value = raw.error; return; }
    Object.keys(formData).forEach(k => delete formData[k]);
    const fields = currentFormFields.value;
    fields.forEach(f => {
      let val = raw[f.col] ?? '';
      if (f.type === 'date' && val) val = String(val).slice(0, 10);
      formData[f.col] = val;
    });
    await loadSelectOptions(fields);
    showForm.value = true;
  } catch (e: any) {
    formError.value = '获取记录失败: ' + e.message;
  }
}

function closeForm() {
  showForm.value = false;
  formError.value = '';
}

async function saveForm() {
  formSaving.value = true;
  formError.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    const url = formMode.value === 'add'
      ? `${API}/api/acc/${tab.api}`
      : `${API}/api/acc/${tab.api}/${editId.value}`;
    const method = formMode.value === 'add' ? 'POST' : 'PUT';
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formData),
    });
    const json = await res.json();
    if (json.error) {
      formError.value = json.error;
    } else {
      showForm.value = false;
      fetchAccData();
    }
  } catch (e: any) {
    formError.value = e.message;
  } finally {
    formSaving.value = false;
  }
}

function confirmDeleteRow(row: any) {
  deleteTarget.value = {
    id: row.id,
    label: row.no ?? row.name ?? row.orderNo ?? row.code ?? String(row.id),
  };
  showDeleteConfirm.value = true;
}

async function doDelete() {
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    await apiFetch(`${API}/api/acc/${tab.api}/${deleteTarget.value.id}`, { method: 'DELETE' });
    showDeleteConfirm.value = false;
    fetchAccData();
  } catch (e: any) {
    formError.value = e.message;
  }
}

// ═══════════════ Business Operations ═══════════════

const auditableTabs = new Set([
  'orders', 'collects', 'shipments', 'packages', 'stowages', 'charges', 'costs',
  'bills', 'payments', 'receiveds', 'commissions', 'transfers',
  'expenses', 'dividends', 'borrowings', 'wages', 'reparations', 'returns',
  'transits', 'customer-fines', 'supplier-fines',
  'customer-adjusts', 'supplier-adjusts',
  'customer-rebates', 'supplier-rebates',
  'customer-refunds', 'supplier-refunds',
  'assets', 'funds', 'socials',
  // 2026-05-28 阶段 1：对齐后端 AuditService.AUDITABLE_ENTITIES（HR + 配置类）
  'employees', 'attendances', 'social-persons', 'fund-persons',
  'channel-accounts', 'logistics-interfaces', 'templates',
]);

const bizAuditTabs = new Set([
  'orders', 'shipments', 'packages', 'stowages', 'charges', 'costs', 'bills',
  'receiveds', 'payments', 'commissions', 'transits',
  'expenses', 'transfers', 'dividends', 'borrowings', 'wages', 'reparations', 'returns',
  'customer-fines', 'supplier-fines', 'customer-adjusts', 'supplier-adjusts',
  'customer-rebates', 'supplier-rebates',
  'customer-refunds', 'supplier-refunds',
  'assets', 'funds', 'socials',
  // 2026-05-28 阶段 1：HR + 配置类
  'employees', 'attendances', 'social-persons', 'fund-persons',
  'channel-accounts', 'logistics-interfaces', 'templates',
]);

// 批量审核：所有业务可审核 tab 都启用（ACC 原行为也是凡审核处都能批量）
const batchAuditTabs = new Set([
  'orders', 'shipments', 'packages', 'stowages', 'charges', 'costs', 'bills',
  'receiveds', 'payments', 'commissions', 'transits',
  'expenses', 'transfers', 'dividends', 'borrowings', 'wages', 'reparations', 'returns',
  'customer-fines', 'supplier-fines', 'customer-adjusts', 'supplier-adjusts',
  'customer-rebates', 'supplier-rebates', 'customer-refunds', 'supplier-refunds',
  'assets', 'funds', 'socials',
  // 2026-05-28 阶段 1：HR + 配置类
  'employees', 'attendances', 'social-persons', 'fund-persons',
  'channel-accounts', 'logistics-interfaces', 'templates',
]);
const importTabs = new Set(['orders', 'charges', 'costs']);
const exportTabs = new Set([
  'orders', 'shipments', 'charges', 'costs', 'bills', 'payments', 'receiveds',
  'profits', 'commissions', 'expenses', 'customers', 'suppliers',
]);

const canAudit = computed(() => bizAuditTabs.has(accTab.value));
const canBatchAudit = computed(() => batchAuditTabs.has(accTab.value));
const canImport = computed(() => importTabs.has(accTab.value));
const canExport = computed(() => exportTabs.has(accTab.value));

function toggleSelect(id: number) {
  if (selectedIds.value.has(id)) selectedIds.value.delete(id);
  else selectedIds.value.add(id);
}

function toggleSelectAll() {
  if (selectedIds.value.size === accData.value.length) {
    selectedIds.value.clear();
  } else {
    accData.value.forEach((row: any) => { if (row.id) selectedIds.value.add(row.id); });
  }
}

async function doAudit(id: any) {
  bizLoading.value = true;
  bizMessage.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    // 新后端：id 在 URL 里、不需要 body
    const res = await apiFetch(`${API}/api/acc/${tab.api}/${encodeURIComponent(String(id))}/audit-biz`, {
      method: 'POST',
    });
    const json = await res.json().catch(() => ({}));
    if (res.ok) {
      bizMessage.value = '审核成功';
      fetchAccData();
    } else {
      bizMessage.value = '审核失败: ' + (json.error ?? `HTTP ${res.status}`);
    }
  } catch (e: any) {
    bizMessage.value = '审核失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

async function doUndoAudit(id: any) {
  bizLoading.value = true;
  bizMessage.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}/${encodeURIComponent(String(id))}/undo-biz`, {
      method: 'POST',
    });
    const json = await res.json().catch(() => ({}));
    if (res.ok) {
      bizMessage.value = '反审核成功';
      fetchAccData();
    } else {
      bizMessage.value = '反审核失败: ' + (json.error ?? `HTTP ${res.status}`);
    }
  } catch (e: any) {
    bizMessage.value = '反审核失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

async function doBatchAudit() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先选择记录'; return; }
  bizLoading.value = true;
  bizMessage.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}/batch-audit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: [...selectedIds.value].map(String) }),
    });
    const json = await res.json().catch(() => ({}));
    if (res.ok) {
      bizMessage.value = `批量审核完成：${json.audited ?? 0} 条成功，${json.skipped ?? 0} 条跳过`;
      selectedIds.value.clear();
      fetchAccData();
    } else {
      bizMessage.value = '批量审核失败: ' + (json.error ?? `HTTP ${res.status}`);
    }
  } catch (e: any) {
    bizMessage.value = '批量审核失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 5000);
  }
}

async function openAuditHistory(row: any) {
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  auditHistoryRowLabel.value = `${tab.label} · ${row.no ?? row.code ?? row.name ?? row.id}`;
  auditHistoryLoading.value = true;
  showAuditHistory.value = true;
  auditHistoryRows.value = [];
  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}/${encodeURIComponent(String(row.id))}/audit-history?limit=50`);
    const json = await res.json();
    auditHistoryRows.value = Array.isArray(json.data) ? json.data : [];
  } catch (e: any) {
    bizMessage.value = '加载审核流转失败: ' + e.message;
  } finally {
    auditHistoryLoading.value = false;
  }
}

async function openBizDialog(type: string) {
  bizDialogType.value = type;
  Object.keys(bizDialogData).forEach(k => delete bizDialogData[k]);
  if (type === 'generate-bill') {
    bizDialogData.customerId = '';
    bizDialogData.dateFrom = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    bizDialogData.dateTo = new Date().toISOString().slice(0, 10);
  } else if (type === 'quick-payment') {
    bizDialogData.customerId = '';
    bizDialogData.amount = 0;
    bizDialogData.bankId = '';
  } else if (type === 'calc-commission') {
    const now = new Date();
    bizDialogData.month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  } else if (type === 'profit-summary') {
    bizDialogData.dateFrom = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    bizDialogData.dateTo = new Date().toISOString().slice(0, 10);
    bizDialogData.groupBy = 'customer';
  }
  if (type === 'generate-bill' || type === 'quick-payment') {
    await loadSelectOptions([{ col: '', label: '', type: 'select', ref: 'customers' }, { col: '', label: '', type: 'select', ref: 'banks' }]);
  }
  showBizDialog.value = true;
}

async function executeBizDialog() {
  bizLoading.value = true;
  bizMessage.value = '';
  try {
    if (bizDialogType.value === 'generate-bill') {
      const res = await apiFetch(`${API}/api/acc/bills/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customerId: Number(bizDialogData.customerId),
          dateFrom: bizDialogData.dateFrom,
          dateTo: bizDialogData.dateTo,
        }),
      });
      const json = await res.json();
      if (json.ok) {
        bizMessage.value = `账单生成成功，ID: ${json.billId}`;
        showBizDialog.value = false;
        fetchAccData();
      } else {
        bizMessage.value = '生成失败: ' + (json.error ?? '');
      }
    } else if (bizDialogType.value === 'quick-payment') {
      const res = await apiFetch(`${API}/api/acc/receiveds/quick`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customerId: Number(bizDialogData.customerId),
          amount: Number(bizDialogData.amount),
          bankId: Number(bizDialogData.bankId),
        }),
      });
      const json = await res.json();
      if (json.ok) {
        bizMessage.value = `快速收款成功，ID: ${json.id}`;
        showBizDialog.value = false;
        fetchAccData();
      } else {
        bizMessage.value = '收款失败: ' + (json.error ?? '');
      }
    } else if (bizDialogType.value === 'calc-commission') {
      const res = await apiFetch(`${API}/api/acc/commissions/calculate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ month: bizDialogData.month }),
      });
      const json = await res.json();
      detailData.value = json;
      detailType.value = 'commission-result';
      showDetail.value = true;
      showBizDialog.value = false;
    } else if (bizDialogType.value === 'profit-summary') {
      const params = new URLSearchParams({
        dateFrom: bizDialogData.dateFrom,
        dateTo: bizDialogData.dateTo,
        groupBy: bizDialogData.groupBy,
      });
      const res = await apiFetch(`${API}/api/acc/profits/summary?${params}`);
      const json = await res.json();
      detailData.value = json;
      detailType.value = 'profit-summary';
      showDetail.value = true;
      showBizDialog.value = false;
    }
  } catch (e: any) {
    bizMessage.value = '操作失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 5000);
  }
}

async function doExport() {
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  bizLoading.value = true;
  try {
    const params = new URLSearchParams();
    if (accKeyword.value) params.set('keyword', accKeyword.value);
    if (accDateFrom.value) params.set('dateFrom', accDateFrom.value);
    if (accDateTo.value) params.set('dateTo', accDateTo.value);
    const res = await apiFetch(`${API}/api/acc/export/${tab.api}?${params}`);
    const json = await res.json();
    if (Array.isArray(json) && json.length > 0) {
      const headers = Object.keys(json[0]);
      const csv = [headers.join(','), ...json.map((row: any) =>
        headers.map(h => {
          const val = String(row[h] ?? '').replace(/"/g, '""');
          return `"${val}"`;
        }).join(',')
      )].join('\n');
      const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${tab.label}_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      bizMessage.value = `导出成功，${json.length} 条记录`;
    } else {
      bizMessage.value = '无数据可导出';
    }
  } catch (e: any) {
    bizMessage.value = '导出失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

async function viewDetail(row: any) {
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  if (accTab.value === 'shipments') {
    // 并行取 items + evidence，让运单详情既能看装箱清单又能看 provider 报文
    const [itemsRes, evRes] = await Promise.all([
      apiFetch(`${API}/api/acc/shipments/${row.id}/items`),
      apiFetch(`${API}/api/acc/shipments/${row.id}/evidence`),
    ]);
    const items = await itemsRes.json();
    const ev = await evRes.json();
    detailData.value = { ...items, evidence: ev };
    detailType.value = 'shipment-items';
  } else if (accTab.value === 'bills') {
    const res = await apiFetch(`${API}/api/acc/bills/${row.id}/items`);
    detailData.value = await res.json();
    detailType.value = 'bill-items';
  } else if (accTab.value === 'stowages') {
    const res = await apiFetch(`${API}/api/acc/stowages/${row.id}/packages`);
    detailData.value = await res.json();
    detailType.value = 'stowage-packages';
  } else {
    const res = await apiFetch(`${API}/api/acc/${tab.api}/${row.id}/raw`);
    detailData.value = await res.json();
    detailType.value = 'raw';
  }
  showDetail.value = true;
}

async function doSyncStowage(id: number) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/stowages/sync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    const json = await res.json();
    bizMessage.value = json.ok ? '同步成功' : '同步失败: ' + (json.error ?? '');
    fetchAccData();
  } catch (e: any) {
    bizMessage.value = '同步失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

async function doReloadBill(id: number) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/bills/reload`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    const json = await res.json();
    bizMessage.value = json.ok ? '账单重算成功' : '重算失败: ' + (json.error ?? '');
    fetchAccData();
  } catch (e: any) {
    bizMessage.value = '重算失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}
</script>

<template>
  <main v-if="!isAuthenticated" class="login-screen">
    <section class="login-panel">
      <div class="login-brand">
        <ShieldCheck :size="28" />
        <div>
          <p>新航线统一平台</p>
          <h1>登录主系统</h1>
        </div>
      </div>
      <form class="login-form" @submit.prevent="login">
        <label>
          <span>租户</span>
          <input v-model="loginForm.tenantCode" autocomplete="organization" />
        </label>
        <label>
          <span>用户名</span>
          <input v-model="loginForm.username" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="loginForm.password" type="password" autocomplete="current-password" />
        </label>
        <div class="error-bar" v-if="loginError">{{ loginError }}</div>
        <button class="primary login-submit" :disabled="loginLoading">
          <Lock :size="15" />
          {{ loginLoading ? "登录中..." : "登录" }}
        </button>
      </form>
    </section>
  </main>

  <main v-else :class="['ruoyi-shell', 'erp-shell', { collapsed: sidebarCollapsed }]">
    <!-- 一级图标栏 -->
    <aside class="nav-rail">
      <div class="rail-brand" title="新航线统一业务平台">
        <Truck :size="20" />
      </div>
      <nav class="rail-menu">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="{ active: currentNav === item.key }"
          @click="navTo(item.key)"
          :title="item.label"
        >
          <component :is="item.icon" :size="20" />
          <span class="rail-label">{{ item.label.replace(/\s*\(.*\)/, '') }}</span>
        </button>
      </nav>
      <div class="rail-status" v-if="health" :title="`PostgreSQL ${health.upstreams.postgres.connected ? '正常' : '异常'}`">
        <span :class="['dot', health.upstreams.postgres.connected ? 'green' : 'red']" />
      </div>
    </aside>

    <!-- 二级菜单栏 -->
    <aside class="nav-submenu" v-if="!sidebarCollapsed">
      <div class="submenu-head">{{ activeNavItem.label }}</div>

      <!-- ACC：6 大功能组手风琴 -->
      <nav class="menu-tree" v-if="currentNav === 'acc'">
        <div class="menu-tree-group" v-for="g in accMenuGroups" :key="g.key">
          <button class="group-head" :class="{ open: expandedAccGroup === g.key }" @click="toggleAccGroup(g.key)">
            <component :is="g.icon" :size="15" />
            <span>{{ g.label }}</span>
            <ChevronDown v-if="expandedAccGroup === g.key" :size="14" class="group-chevron" />
            <ChevronRight v-else :size="14" class="group-chevron" />
          </button>
          <div class="group-items" v-show="expandedAccGroup === g.key">
            <button
              v-for="tab in g.tabs"
              :key="tab.key"
              :class="{ active: accTab === tab.key }"
              @click="selectAccTab(tab.key)"
            >
              <component :is="tab.icon" :size="14" />
              <span>{{ tab.label }}</span>
            </button>
          </div>
        </div>
      </nav>

      <!-- 系统管理：平铺子菜单 -->
      <nav class="menu-tree flat" v-else-if="currentNav === 'system'">
        <button
          v-for="tab in sysTabs"
          :key="tab.key"
          :class="{ active: sysTab === tab.key }"
          @click="sysTab = tab.key"
        >
          <component :is="tab.icon" :size="15" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 其它单页模块 -->
      <div class="submenu-empty" v-else>
        <component :is="activeNavItem.icon" :size="32" />
        <p>{{ activeNavItem.label }}</p>
      </div>
    </aside>

    <section class="ruoyi-main">
      <header class="ruoyi-header">
        <div class="header-left">
          <button class="icon-button" @click="sidebarCollapsed = !sidebarCollapsed" title="折叠菜单">
            <PanelLeftClose v-if="!sidebarCollapsed" :size="18" />
            <Menu v-else :size="18" />
          </button>
          <nav class="breadcrumb">
            <Home :size="15" />
            <span v-for="(item, index) in breadcrumbItems" :key="item">
              <ChevronRight v-if="index > 0" :size="13" />
              {{ item }}
            </span>
          </nav>
        </div>
        <div class="header-right">
          <button class="icon-button" title="消息"><Bell :size="17" /></button>
          <button class="icon-button" title="全屏"><Maximize2 :size="17" /></button>
          <button class="icon-button" title="设置"><Settings :size="17" /></button>
          <div class="header-user">
            <span class="avatar">{{ (authUser?.displayName || authUser?.username || 'U').slice(0, 1) }}</span>
            <div>
              <strong>{{ authUser?.displayName || authUser?.username }}</strong>
              <span>{{ authUser?.tenantCode }} · {{ authUser?.roles?.join(', ') }}</span>
            </div>
          </div>
          <button class="text-button" @click="logout()">退出</button>
        </div>
      </header>

      <div class="tag-view">
        <button
          v-for="key in openedTabs"
          :key="key"
          :class="{ active: currentNav === key }"
          @click="currentNav = key"
        >
          {{ navMap[key]?.label ?? key }}
          <X v-if="key !== 'dashboard'" :size="12" @click.stop="closeTab(key)" />
        </button>
      </div>

      <section class="workspace">
      <!-- ════════ Dashboard ════════ -->
      <template v-if="currentNav === 'dashboard'">
        <header class="dashboard-hero">
          <div class="dashboard-title">
            <p>DataGear 风格驾驶舱</p>
            <h1>两线业务经营看板</h1>
            <span v-if="dashboard">统计周期 {{ dashboard.period.from }} 至 {{ dashboard.period.to }}</span>
          </div>
          <div class="dashboard-actions">
            <span class="live-chip"><span class="pulse-dot" /> 实时汇总</span>
            <button class="primary" @click="fetchDashboard" :disabled="loading">
              <RefreshCw :size="14" :class="{ spinning: loading }" />
              刷新数据
            </button>
          </div>
        </header>

        <div class="error-bar" v-if="error">{{ error }}</div>

        <section class="dashboard-kpis" v-if="dashboard">
          <article v-for="item in dashboardKpis" :key="item.label" :class="['kpi-tile', item.tone]">
            <div class="kpi-icon"><component :is="item.icon" :size="18" /></div>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.sub }}</small>
          </article>
        </section>

        <section class="dashboard-grid" v-if="dashboard">
          <article class="dashboard-panel tracking-panel">
            <div class="panel-title">
              <div>
                <p>全球物流跟踪</p>
                <h2>货物轨迹地图</h2>
              </div>
              <span>{{ fmtInt(trackingSummary.activeShipments) }} 在途</span>
            </div>
            <div class="tracking-layout">
              <GlobalTrackingMap
                :routes="trackingRoutes"
                :selected-route-id="selectedTrackingRoute?.id"
                @select="selectTrackingRoute"
              />

              <aside class="tracking-sidebar">
                <div class="tracking-stats">
                  <div v-for="item in trackingStats" :key="item.label" :class="item.tone">
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>

                <div class="selected-shipment" v-if="selectedTrackingRoute">
                  <div>
                    <span>当前追踪</span>
                    <strong>{{ selectedTrackingRoute.trackingNo || selectedTrackingRoute.shipmentNo }}</strong>
                  </div>
                  <p>{{ selectedTrackingRoute.carrierName }} · {{ selectedTrackingRoute.latestLocation }}</p>
                  <i><b :style="{ width: selectedTrackingRoute.progress + '%' }" /></i>
                  <small>{{ fmtTime(selectedTrackingRoute.latestEventTime) }} · {{ selectedTrackingRoute.rawStatus }}</small>
                </div>

                <div class="route-list" v-if="trackingRoutes.length">
                  <button
                    v-for="route in trackingRoutes.slice(0, 5)"
                    :key="route.id"
                    :class="{ active: isSelectedTrackingRoute(route) }"
                    @click="selectTrackingRoute(route)"
                  >
                    <span>
                      <strong>{{ route.shipmentNo }}</strong>
                      <em>{{ route.destination.countryCode }} · {{ trackingStatusLabel(route.status) }}</em>
                    </span>
                    <small>{{ route.progress }}%</small>
                  </button>
                </div>
                <div class="map-empty" v-else>
                  <Globe :size="28" />
                  <span>暂无可跟踪运单</span>
                </div>
              </aside>
            </div>
          </article>

          <article class="dashboard-panel flow-panel">
            <div class="panel-title">
              <div>
                <p>业务线结构</p>
                <h2>卖货客户 / 制单客户</h2>
              </div>
              <span>Flow</span>
            </div>
            <div class="flow-list">
              <div v-for="flow in dashboardFlows" :key="flow.code" class="flow-row">
                <div class="flow-main">
                  <strong>{{ flow.label }}</strong>
                  <span>{{ flow.customerDirection }} · {{ fmtInt(flow.orders) }} 单 / {{ fmtInt(flow.shipments) }} 票</span>
                </div>
                <div class="flow-money">
                  <strong>¥{{ fmt(flow.receivable) }}</strong>
                  <span :class="flow.profit >= 0 ? 'positive' : 'negative'">毛利 ¥{{ fmt(flow.profit) }}</span>
                </div>
                <div class="flow-track">
                  <i :style="{ width: ratio(flow.receivable, maxFlowRevenue) + '%' }" />
                </div>
              </div>
            </div>
          </article>

          <article class="dashboard-panel mix-panel">
            <div class="panel-title">
              <div>
                <p>收入占比</p>
                <h2>Revenue Mix</h2>
              </div>
              <span>Chart</span>
            </div>
            <div class="donut-wrap">
              <div class="donut" :style="donutStyle">
                <div>
                  <strong>¥{{ fmt(dashboard.combined.totalRevenue) }}</strong>
                  <span>总营收</span>
                </div>
              </div>
              <div class="donut-legend">
                <div v-for="flow in dashboardFlows" :key="flow.code">
                  <span />
                  <strong>{{ flow.label }}</strong>
                  <em>{{ fmtPercent(ratio(flow.receivable, dashboard.combined.totalRevenue) / 100) }}</em>
                </div>
              </div>
            </div>
          </article>

          <article class="dashboard-panel finance-panel">
            <div class="panel-title">
              <div>
                <p>财务结构</p>
                <h2>应收 / 应付 / 毛利</h2>
              </div>
              <span>Finance</span>
            </div>
            <div class="finance-bars">
              <div v-for="bar in financeBars" :key="bar.label" class="finance-bar">
                <div>
                  <span>{{ bar.label }}</span>
                  <strong>¥{{ fmt(bar.value) }}</strong>
                </div>
                <i><b :class="bar.tone" :style="{ width: ratio(Math.abs(bar.value), maxFinanceValue) + '%' }" /></i>
              </div>
            </div>
          </article>

          <article class="dashboard-panel pipeline-panel">
            <div class="panel-title">
              <div>
                <p>经营链路</p>
                <h2>从接单到毛利</h2>
              </div>
              <span>Pipeline</span>
            </div>
            <div class="pipeline">
              <div v-for="node in pipelineNodes" :key="node.label" class="pipeline-node">
                <strong>{{ node.value }}</strong>
                <span>{{ node.label }}</span>
                <small>{{ node.desc }}</small>
              </div>
            </div>
          </article>

          <article class="dashboard-panel alert-panel">
            <div class="panel-title">
              <div>
                <p>关注事项</p>
                <h2>运营提醒</h2>
              </div>
              <span>Notice</span>
            </div>
            <div class="alert-list">
              <div v-for="item in dashboardAlerts" :key="item.title" :class="['alert-row', item.tone]">
                <span />
                <div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.desc }}</p>
                </div>
              </div>
            </div>
          </article>

          <article class="dashboard-panel branch-panel">
            <div class="panel-title">
              <div>
                <p>组织视图</p>
                <h2>分公司覆盖</h2>
              </div>
              <span>{{ branches.length }} 个</span>
            </div>
            <div class="branch-mini-list">
              <div v-for="b in branches.slice(0, 6)" :key="b.id">
                <strong>{{ b.name }}</strong>
                <span>{{ b.code }} · {{ b.org_type }}</span>
                <em :class="{ active: b.is_active }">{{ b.is_active ? '启用' : '停用' }}</em>
              </div>
            </div>
          </article>
        </section>

        <section class="module-grid dashboard-modules">
          <article v-for="item in moduleCards" :key="item.title">
            <div class="module-header">
              <component :is="item.icon" :size="20" />
              <span class="badge" :class="item.status.toLowerCase()">{{ item.status }}</span>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.desc }}</p>
          </article>
        </section>
      </template>

      <!-- ════════ ACC Module ════════ -->
      <template v-if="currentNav === 'acc'">
        <header class="topbar">
          <div>
            <p>委托运输</p>
            <h1>ACC 业务管理系统</h1>
          </div>
          <div class="acc-stats" v-if="accStats">
            <span>订单 <strong>{{ accStats.orderCount?.toLocaleString() ?? '-' }}</strong></span>
            <span>客户 <strong>{{ accStats.customerCount?.toLocaleString() ?? '-' }}</strong></span>
            <span>物流商 <strong>{{ accStats.supplierCount?.toLocaleString() ?? '-' }}</strong></span>
            <span>渠道 <strong>{{ accStats.channelCount?.toLocaleString() ?? '-' }}</strong></span>
          </div>
        </header>

        <!-- 当前功能标识（替代原顶部 78 按钮平铺，功能已移到左侧二级菜单） -->
        <div class="acc-current-tab">
          <component :is="accTabs.find(t => t.key === accTab)?.icon ?? FileText" :size="16" />
          <strong>{{ accTabs.find(t => t.key === accTab)?.label ?? '快件订单' }}</strong>
        </div>

        <!-- Search bar -->
        <div class="acc-search-bar">
          <div class="search-group">
            <Search :size="14" class="search-icon" />
            <input type="text" v-model="accKeyword" placeholder="搜索关键词..." @keyup.enter="accSearch" />
          </div>
          <template v-if="!noDateTabs.has(accTab)">
            <input type="date" v-model="accDateFrom" class="date-input" />
            <span class="date-sep">~</span>
            <input type="date" v-model="accDateTo" class="date-input" />
          </template>
          <button class="primary sm" @click="accSearch" :disabled="accLoading">
            <Search :size="13" /> 查询
          </button>
          <button class="secondary sm" @click="accKeyword = ''; accDateFrom = ''; accDateTo = ''; accSearch()">
            重置
          </button>
          <button class="primary sm" v-if="canCrud" @click="openAdd">
            <Plus :size="13" /> 新增
          </button>
          <button class="secondary sm" v-if="canBatchAudit" @click="doBatchAudit" :disabled="bizLoading || selectedIds.size === 0">
            <CheckCircle :size="13" /> 批量审核({{ selectedIds.size }})
          </button>
          <button class="secondary sm" v-if="canExport" @click="doExport" :disabled="bizLoading">
            <Download :size="13" /> 导出
          </button>
          <button class="secondary sm" v-if="accTab === 'bills'" @click="openBizDialog('generate-bill')">
            <Calculator :size="13" /> 生成账单
          </button>
          <button class="secondary sm" v-if="accTab === 'receiveds'" @click="openBizDialog('quick-payment')">
            <Coins :size="13" /> 快速收款
          </button>
          <button class="secondary sm" v-if="accTab === 'commissions'" @click="openBizDialog('calc-commission')">
            <Calculator :size="13" /> 计算提成
          </button>
          <button class="secondary sm" v-if="accTab === 'profits'" @click="openBizDialog('profit-summary')">
            <BarChart3 :size="13" /> 利润汇总
          </button>
          <span class="result-count" v-if="!accLoading">共 {{ accTotal }} 条</span>
          <span class="biz-message" v-if="bizMessage">{{ bizMessage }}</span>
        </div>

        <!-- Data table -->
        <div class="acc-table-wrap">
          <table class="data-table" v-if="accColumns[accTab]">
            <thead>
              <tr>
                <th v-if="canBatchAudit" class="check-col">
                  <input type="checkbox" @change="toggleSelectAll()" :checked="selectedIds.size > 0 && selectedIds.size === accData.length" />
                </th>
                <th v-for="col in accColumns[accTab]" :key="col.key">{{ col.label }}</th>
                <th class="audit-status-col">审核状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="accLoading">
                <td :colspan="accColumns[accTab].length + 2 + (canBatchAudit ? 1 : 0)" class="loading-cell">
                  <RefreshCw :size="16" class="spinning" /> 加载中...
                </td>
              </tr>
              <tr v-else-if="accData.length === 0">
                <td :colspan="accColumns[accTab].length + 2 + (canBatchAudit ? 1 : 0)" class="empty-cell">暂无数据</td>
              </tr>
              <tr v-for="row in accData" :key="row.id ?? row.code ?? row.no"
                  :class="{ 'audited-row': row.auditStatus === 'AUDITED' }" v-else>
                <td v-if="canBatchAudit" class="check-col">
                  <input type="checkbox" :checked="selectedIds.has(row.id)" @change="toggleSelect(row.id)" />
                </td>
                <td v-for="col in accColumns[accTab]" :key="col.key"
                    :class="{ 'money-cell': col.fmt === 'money' }">
                  {{ fmtCell(row[col.key], col.fmt) }}
                </td>
                <td class="audit-status-cell">
                  <span v-if="row.auditStatus === 'AUDITED'" class="audit-badge audited" :title="`审核人: ${row.auditName ?? ''}\n审核时间: ${row.auditedAt ?? ''}`">已审核</span>
                  <span v-else-if="row.auditStatus === 'UNAUDITED'" class="audit-badge unaudited">已反审</span>
                  <span v-else-if="row.auditStatus === 'PENDING'" class="audit-badge pending">待审核</span>
                </td>
                <td class="action-cell">
                  <button class="action-btn" @click="viewDetail(row)" title="详情">
                    <Eye :size="12" />
                  </button>
                  <button class="action-btn" v-if="canCrud && row.auditStatus !== 'AUDITED'"
                          @click="openEdit(row)" title="编辑">
                    <Pencil :size="12" />
                  </button>
                  <button class="action-btn audit-btn"
                          v-if="canAudit && row.auditStatus !== 'AUDITED'"
                          @click="doAudit(row.id)" title="审核" :disabled="bizLoading">
                    <CheckCircle :size="12" />
                  </button>
                  <button class="action-btn undo-btn"
                          v-if="canAudit && row.auditStatus === 'AUDITED'"
                          @click="doUndoAudit(row.id)" title="反审核" :disabled="bizLoading">
                    <XCircle :size="12" />
                  </button>
                  <button class="action-btn history-btn"
                          v-if="canAudit && row.auditStatus"
                          @click="openAuditHistory(row)" title="审核流转" :disabled="bizLoading">
                    <Clock :size="12" />
                  </button>
                  <button class="action-btn" v-if="accTab === 'stowages'" @click="doSyncStowage(row.id)" title="同步" :disabled="bizLoading">
                    <RefreshCw :size="12" />
                  </button>
                  <button class="action-btn" v-if="accTab === 'bills'" @click="doReloadBill(row.id)" title="重算" :disabled="bizLoading">
                    <Calculator :size="12" />
                  </button>
                  <button class="action-btn del" v-if="canCrud && row.auditStatus !== 'AUDITED'"
                          @click="confirmDeleteRow(row)" title="删除">
                    <Trash2 :size="12" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div class="pagination" v-if="!noPaginationTabs.has(accTab) && accTotal > 0">
          <button @click="accPrev" :disabled="accPage <= 1">
            <ChevronLeft :size="14" /> 上一页
          </button>
          <span class="page-info">第 {{ accPage }} / {{ accTotalPages }} 页</span>
          <button @click="accNext" :disabled="accPage >= accTotalPages">
            下一页 <ChevronRight :size="14" />
          </button>
        </div>
      </template>

      <!-- ════════ XQT ════════ -->
      <template v-if="currentNav === 'xqt'">
        <header class="topbar">
          <div>
            <p>集货入仓</p>
            <h1>XQT 系统 — 客户卖货给我们</h1>
          </div>
        </header>
        <section class="placeholder">
          <Package :size="48" />
          <p>XQT 运单管理（收货 → 换标 → 出货 → 轨迹跟踪）</p>
          <p class="sub">通过 REST API 调用 XQT ParcelOS 系统</p>
        </section>
      </template>

      <!-- ════════ Branches ════════ -->
      <template v-if="currentNav === 'branches'">
        <header class="topbar">
          <div>
            <p>组织架构</p>
            <h1>分公司管理</h1>
          </div>
        </header>
        <section class="branch-list" v-if="branches.length">
          <table class="data-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>类型</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="b in branches" :key="b.id">
                <td>{{ b.code }}</td>
                <td>{{ b.name }}</td>
                <td>{{ b.org_type === 'hq' ? '总部' : b.org_type === 'branch' ? '分公司' : '部门' }}</td>
                <td><span :class="['dot', b.is_active ? 'green' : 'red']" /> {{ b.is_active ? '启用' : '停用' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
        <section class="placeholder" v-else>
          <Building2 :size="48" />
          <p>暂无分公司数据，请先启动数据库</p>
        </section>
      </template>

      <!-- ════════ System Management ════════ -->
      <template v-if="currentNav === 'system'">
        <header class="topbar">
          <div>
            <p>系统基础设施</p>
            <h1>系统管理</h1>
          </div>
          <div class="topbar-actions">
            <button class="secondary" @click="loadSystemData"><RefreshCw :size="16" /> 刷新</button>
          </div>
        </header>
        <nav class="acc-tabs-bar">
          <div class="acc-group">
            <span class="acc-group-label">用户权限</span>
            <button v-for="tab in sysTabs.slice(0, 3)" :key="tab.key"
              :class="['acc-tab', { active: sysTab === tab.key }]" @click="sysTab = tab.key; loadSystemData()">
              <component :is="tab.icon" :size="14" /> {{ tab.label }}
            </button>
          </div>
          <div class="acc-group">
            <span class="acc-group-label">日志审计</span>
            <button v-for="tab in sysTabs.slice(3, 6)" :key="tab.key"
              :class="['acc-tab', { active: sysTab === tab.key }]" @click="sysTab = tab.key; loadSystemData()">
              <component :is="tab.icon" :size="14" /> {{ tab.label }}
            </button>
          </div>
          <div class="acc-group">
            <span class="acc-group-label">系统配置</span>
            <button v-for="tab in sysTabs.slice(6, 9)" :key="tab.key"
              :class="['acc-tab', { active: sysTab === tab.key }]" @click="sysTab = tab.key; loadSystemData()">
              <component :is="tab.icon" :size="14" /> {{ tab.label }}
            </button>
          </div>
          <div class="acc-group">
            <span class="acc-group-label">客户/服务</span>
            <button v-for="tab in sysTabs.slice(9, 12)" :key="tab.key"
              :class="['acc-tab', { active: sysTab === tab.key }]" @click="sysTab = tab.key; loadSystemData()">
              <component :is="tab.icon" :size="14" /> {{ tab.label }}
            </button>
          </div>
          <div class="acc-group">
            <span class="acc-group-label">接口/工具</span>
            <button v-for="tab in sysTabs.slice(12)" :key="tab.key"
              :class="['acc-tab', { active: sysTab === tab.key }]" @click="sysTab = tab.key; loadSystemData()">
              <component :is="tab.icon" :size="14" /> {{ tab.label }}
            </button>
          </div>
        </nav>
        <section class="panel-card">
          <div v-if="sysLoading" class="placeholder"><RefreshCw :size="32" class="spinning" /><p>加载中...</p></div>
          <div v-else-if="sysTab === 'sys-info' && sysInfo">
            <h3 class="panel-title">系统信息</h3>
            <div class="form-grid">
              <div class="form-field"><label>系统版本</label><input readonly :value="sysInfo.version" /></div>
              <div class="form-field"><label>Node版本</label><input readonly :value="sysInfo.nodeVersion" /></div>
              <div class="form-field"><label>平台</label><input readonly :value="sysInfo.platform" /></div>
              <div class="form-field"><label>运行时间</label><input readonly :value="Math.round(sysInfo.uptime/3600) + '小时'" /></div>
              <div class="form-field"><label>在线用户数</label><input readonly :value="sysInfo.users" /></div>
              <div class="form-field"><label>角色数</label><input readonly :value="sysInfo.roles" /></div>
              <div class="form-field"><label>今日操作</label><input readonly :value="sysInfo.todayLogs" /></div>
              <div class="form-field"><label>今日错误</label><input readonly :value="sysInfo.todayErrors" /></div>
            </div>
          </div>
          <div v-else>
            <table class="data-table" v-if="sysData.length">
              <thead>
                <tr>
                  <th v-for="col in sysColumns[sysTab] ?? [{key:'id',label:'ID'}]" :key="col.key">{{ col.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in sysData" :key="row.id ?? row.key">
                  <td v-for="col in sysColumns[sysTab] ?? [{key:'id',label:'ID'}]" :key="col.key">{{ row[col.key] ?? '-' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="placeholder" v-else><p>暂无数据</p></div>
          </div>
        </section>
      </template>
    </section>
    </section>

    <!-- ════════ Form Dialog ════════ -->
    <div class="modal-backdrop" v-if="showForm" @click.self="closeForm">
      <div class="modal-dialog">
        <div class="modal-header">
          <h3>{{ formMode === 'add' ? '新增' : '编辑' }} {{ accTabs.find(t => t.key === accTab)?.label }}</h3>
          <button class="modal-close" @click="closeForm"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div class="error-bar" v-if="formError">{{ formError }}</div>
          <div class="form-grid">
            <div class="form-field" v-for="field in currentFormFields" :key="field.col"
                 :class="{ 'full-width': field.type === 'textarea' }">
              <label>{{ field.label }} <span class="required" v-if="field.required">*</span></label>
              <input v-if="field.type === 'text'" type="text" v-model="formData[field.col]" />
              <input v-else-if="field.type === 'number'" type="number" step="any" v-model.number="formData[field.col]" />
              <input v-else-if="field.type === 'date'" type="date" v-model="formData[field.col]" />
              <textarea v-else-if="field.type === 'textarea'" v-model="formData[field.col]" rows="3" />
              <select v-else-if="field.type === 'select' && field.opts" v-model="formData[field.col]">
                <option value="">请选择</option>
                <option v-for="opt in field.opts" :key="opt.v" :value="opt.v">{{ opt.l }}</option>
              </select>
              <select v-else-if="field.type === 'select' && field.ref" v-model="formData[field.col]">
                <option value="">请选择</option>
                <option v-for="opt in (selectOptions[field.ref!] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
              </select>
              <div v-else-if="field.type === 'boolean'" class="toggle-wrap">
                <input type="checkbox" :id="'f_' + field.col" v-model="formData[field.col]" :true-value="1" :false-value="0" />
                <label :for="'f_' + field.col" class="toggle-label">{{ formData[field.col] == 1 ? '是' : '否' }}</label>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="closeForm">取消</button>
          <button class="primary" @click="saveForm" :disabled="formSaving">
            <RefreshCw v-if="formSaving" :size="14" class="spinning" />
            <Save v-else :size="14" />
            {{ formSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ════════ Delete Confirm ════════ -->
    <div class="modal-backdrop" v-if="showDeleteConfirm" @click.self="showDeleteConfirm = false">
      <div class="modal-dialog modal-sm">
        <div class="modal-header">
          <h3>确认删除</h3>
          <button class="modal-close" @click="showDeleteConfirm = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <p>确认删除记录 <strong>{{ deleteTarget.label }}</strong> ？此操作不可撤销。</p>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showDeleteConfirm = false">取消</button>
          <button class="danger-btn" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>

    <!-- ════════ Business Dialog ════════ -->
    <div class="modal-backdrop" v-if="showBizDialog" @click.self="showBizDialog = false">
      <div class="modal-dialog">
        <div class="modal-header">
          <h3>{{ bizDialogType === 'generate-bill' ? '生成客户账单' : bizDialogType === 'quick-payment' ? '快速收款' : bizDialogType === 'calc-commission' ? '计算员工提成' : '利润汇总' }}</h3>
          <button class="modal-close" @click="showBizDialog = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div class="form-grid">
            <template v-if="bizDialogType === 'generate-bill'">
              <div class="form-field">
                <label>客户 <span class="required">*</span></label>
                <select v-model="bizDialogData.customerId">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field"><label>起始日期</label><input type="date" v-model="bizDialogData.dateFrom" /></div>
              <div class="form-field"><label>截止日期</label><input type="date" v-model="bizDialogData.dateTo" /></div>
            </template>
            <template v-if="bizDialogType === 'quick-payment'">
              <div class="form-field">
                <label>客户 <span class="required">*</span></label>
                <select v-model="bizDialogData.customerId">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field"><label>金额 <span class="required">*</span></label><input type="number" step="any" v-model.number="bizDialogData.amount" /></div>
              <div class="form-field">
                <label>银行账户</label>
                <select v-model="bizDialogData.bankId">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['banks'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
            </template>
            <template v-if="bizDialogType === 'calc-commission'">
              <div class="form-field"><label>月份 <span class="required">*</span></label><input type="month" v-model="bizDialogData.month" /></div>
            </template>
            <template v-if="bizDialogType === 'profit-summary'">
              <div class="form-field"><label>起始日期</label><input type="date" v-model="bizDialogData.dateFrom" /></div>
              <div class="form-field"><label>截止日期</label><input type="date" v-model="bizDialogData.dateTo" /></div>
              <div class="form-field">
                <label>分组维度</label>
                <select v-model="bizDialogData.groupBy">
                  <option value="customer">按客户</option>
                  <option value="product">按产品</option>
                  <option value="branch">按分公司</option>
                  <option value="employee">按业务员</option>
                </select>
              </div>
            </template>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showBizDialog = false">取消</button>
          <button class="primary" @click="executeBizDialog" :disabled="bizLoading">
            <RefreshCw v-if="bizLoading" :size="14" class="spinning" />
            执行
          </button>
        </div>
      </div>
    </div>

    <!-- ════════ Detail / Report Modal ════════ -->
    <div class="modal-backdrop" v-if="showDetail" @click.self="showDetail = false">
      <div class="modal-dialog" style="max-width: 900px;">
        <div class="modal-header">
          <h3>{{ detailType === 'shipment-items' ? '出货明细' : detailType === 'bill-items' ? '账单明细' : detailType === 'stowage-packages' ? '配载包裹' : detailType === 'commission-result' ? '提成计算结果' : detailType === 'profit-summary' ? '利润汇总报表' : '记录详情' }}</h3>
          <button class="modal-close" @click="showDetail = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <!-- Shipment items（cartons + declarations + evidence） -->
          <div v-if="detailType === 'shipment-items' && detailData && !Array.isArray(detailData)">
            <h4 class="detail-section-title">装箱清单</h4>
            <table class="data-table">
              <thead><tr><th>箱号</th><th>子单号</th><th>主单号</th><th>实重(kg)</th><th>计费重(kg)</th><th>CBM</th></tr></thead>
              <tbody>
                <tr v-for="c in (detailData.cartons ?? [])" :key="c.id">
                  <td>{{ c.carton_no }}</td>
                  <td>{{ c.tracking_no || '-' }}</td>
                  <td>{{ c.carrier_master_tracking_no || '-' }}</td>
                  <td>{{ c.actual_weight_kg ?? '-' }}</td>
                  <td>{{ c.chargeable_weight_kg ?? '-' }}</td>
                  <td>{{ c.cbm ?? '-' }}</td>
                </tr>
                <tr v-if="(detailData.cartons ?? []).length === 0">
                  <td colspan="6" class="empty-cell">无装箱</td>
                </tr>
              </tbody>
            </table>

            <h4 class="detail-section-title">申报明细</h4>
            <table class="data-table">
              <thead><tr><th>品名</th><th>材质</th><th>HS</th><th>数量</th><th>申报价值</th></tr></thead>
              <tbody>
                <tr v-for="d in (detailData.declarations ?? [])" :key="d.id">
                  <td>{{ d.item_name }}</td>
                  <td>{{ d.material || '-' }}</td>
                  <td>{{ d.hs_code || '-' }}</td>
                  <td>{{ d.quantity }}</td>
                  <td class="money-cell">{{ d.value_amount }}</td>
                </tr>
                <tr v-if="(detailData.declarations ?? []).length === 0">
                  <td colspan="5" class="empty-cell">无申报明细</td>
                </tr>
              </tbody>
            </table>

            <h4 class="detail-section-title">Provider Evidence（取号 / 面单 / 费用 真实报文）</h4>
            <div v-if="detailData.evidence" class="evidence-blocks">
              <div class="evidence-block">
                <div class="evidence-label">渠道取号 carrier ({{ (detailData.evidence.carrier ?? []).length }})</div>
                <div v-if="(detailData.evidence.carrier ?? []).length === 0" class="evidence-empty">— 暂无</div>
                <details v-for="(c, i) in (detailData.evidence.carrier ?? [])" :key="'car-'+i">
                  <summary>箱 {{ c.carton_no }} · {{ c.tracking_no || '-' }}</summary>
                  <pre class="evidence-pre">{{ fmtJson(c.evidence) }}</pre>
                </details>
              </div>
              <div class="evidence-block">
                <div class="evidence-label">面单 label ({{ (detailData.evidence.labels ?? []).length }})</div>
                <div v-if="(detailData.evidence.labels ?? []).length === 0" class="evidence-empty">— 暂无</div>
                <details v-for="(l, i) in (detailData.evidence.labels ?? [])" :key="'lab-'+i">
                  <summary>{{ l.label_type }} · {{ l.tracking_no || '-' }} · {{ l.created_at }}</summary>
                  <pre class="evidence-pre">{{ fmtJson(l.evidence) }}</pre>
                </details>
              </div>
              <div class="evidence-block">
                <div class="evidence-label">费用 charges ({{ (detailData.evidence.charges ?? []).length }})</div>
                <div v-if="(detailData.evidence.charges ?? []).length === 0" class="evidence-empty">— 暂无</div>
                <details v-for="(ch, i) in (detailData.evidence.charges ?? [])" :key="'ch-'+i">
                  <summary>{{ ch.side }} · {{ ch.status }} · {{ ch.currency }} {{ ch.amount }}</summary>
                  <pre class="evidence-pre">{{ fmtJson(ch.evidence) }}</pre>
                </details>
              </div>
            </div>
          </div>
          <!-- Bill items -->
          <table class="data-table" v-if="detailType === 'bill-items' && Array.isArray(detailData)">
            <thead><tr><th>快件单号</th><th>客户</th><th>金额</th><th>已收</th><th>日期</th><th>类型</th></tr></thead>
            <tbody>
              <tr v-for="item in detailData" :key="item.id">
                <td>{{ item.expressNo }}</td><td>{{ item.customerName }}</td>
                <td class="money-cell">¥{{ fmt(item.amount) }}</td><td class="money-cell">¥{{ fmt(item.paid) }}</td>
                <td>{{ item.theDate }}</td><td>{{ item.lineType }}</td>
              </tr>
              <tr v-if="detailData.length === 0"><td colspan="6" class="empty-cell">无明细</td></tr>
            </tbody>
          </table>
          <!-- Stowage packages -->
          <table class="data-table" v-if="detailType === 'stowage-packages' && Array.isArray(detailData)">
            <thead><tr><th>单号</th><th>客户</th><th>收件人</th><th>国家</th><th>件数</th><th>重量</th><th>申报价值</th><th>邮编</th></tr></thead>
            <tbody>
              <tr v-for="item in detailData" :key="item.id">
                <td>{{ item.no }}</td><td>{{ item.customerName }}</td><td>{{ item.consignee }}</td>
                <td>{{ item.country }}</td><td>{{ item.piece }}</td><td>{{ item.weight }}</td>
                <td class="money-cell">¥{{ fmt(item.declaredValue) }}</td><td>{{ item.postcode }}</td>
              </tr>
              <tr v-if="detailData.length === 0"><td colspan="8" class="empty-cell">无包裹</td></tr>
            </tbody>
          </table>
          <!-- Commission result -->
          <table class="data-table" v-if="detailType === 'commission-result' && Array.isArray(detailData)">
            <thead><tr><th>员工</th><th>提成方式</th><th>比例(%)</th><th>销售额</th><th>利润</th><th>数量</th><th>提成金额</th></tr></thead>
            <tbody>
              <tr v-for="item in detailData" :key="item.employeeId">
                <td>{{ item.name }}</td>
                <td>{{ item.type === 0 ? '按销售额' : item.type === 1 ? '按利润额' : '按销售数' }}</td>
                <td>{{ item.percent }}</td>
                <td class="money-cell">¥{{ fmt(item.sAmount) }}</td>
                <td class="money-cell">¥{{ fmt(item.profit) }}</td>
                <td>{{ item.sQuantity }}</td>
                <td class="money-cell">¥{{ fmt(item.commission) }}</td>
              </tr>
              <tr v-if="detailData.length === 0"><td colspan="7" class="empty-cell">无提成数据</td></tr>
            </tbody>
          </table>
          <!-- Profit summary -->
          <table class="data-table" v-if="detailType === 'profit-summary' && Array.isArray(detailData)">
            <thead><tr><th>分组</th><th>票数</th><th>件数</th><th>总重量(kg)</th><th>营收</th><th>成本</th><th>利润</th></tr></thead>
            <tbody>
              <tr v-for="item in detailData" :key="item.groupName">
                <td>{{ item.groupName }}</td><td>{{ item.count }}</td><td>{{ item.pieces }}</td>
                <td>{{ Number(item.totalWeight).toFixed(2) }}</td>
                <td class="money-cell">¥{{ fmt(item.revenue) }}</td>
                <td class="money-cell">¥{{ fmt(item.cost) }}</td>
                <td class="money-cell" :class="item.profit >= 0 ? 'positive' : 'negative'">¥{{ fmt(item.profit) }}</td>
              </tr>
              <tr v-if="detailData.length === 0"><td colspan="7" class="empty-cell">无数据</td></tr>
            </tbody>
          </table>
          <!-- Raw record -->
          <div v-if="detailType === 'raw' && detailData" class="form-grid">
            <div class="form-field" v-for="(val, key) in detailData" :key="key">
              <label>{{ key }}</label>
              <input type="text" :value="val" readonly />
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showDetail = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Audit history drawer：查看单条单据的审核流转（audit_events 时间线） -->
    <div class="modal-backdrop" v-if="showAuditHistory" @click.self="showAuditHistory = false">
      <div class="modal-dialog audit-history-dialog">
        <div class="modal-header">
          <h3>审核流转 · {{ auditHistoryRowLabel }}</h3>
          <button class="modal-close" @click="showAuditHistory = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div v-if="auditHistoryLoading" class="loading-cell">
            <RefreshCw :size="16" class="spinning" /> 加载中...
          </div>
          <div v-else-if="auditHistoryRows.length === 0" class="empty-cell">
            暂无审核流转记录
          </div>
          <ol v-else class="audit-history-timeline">
            <li v-for="evt in auditHistoryRows" :key="evt.id"
                :class="['audit-history-item', `act-${(evt.action ?? '').toLowerCase()}`]">
              <div class="audit-history-marker">
                <CheckCircle v-if="evt.action === 'AUDIT'" :size="14" />
                <XCircle v-else-if="evt.action === 'UNDO_AUDIT'" :size="14" />
                <Clock v-else :size="14" />
              </div>
              <div class="audit-history-body">
                <div class="audit-history-title">
                  <strong>{{
                    evt.action === 'AUDIT' ? '审核通过'
                      : evt.action === 'UNDO_AUDIT' ? '反审核'
                      : evt.action
                  }}</strong>
                  <span class="audit-history-actor">{{ evt.actor_name ?? '系统' }}</span>
                </div>
                <div class="audit-history-time">{{ evt.occurred_at }}</div>
                <div class="audit-history-remark" v-if="evt.remark">{{ evt.remark }}</div>
              </div>
            </li>
          </ol>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showAuditHistory = false">关闭</button>
        </div>
      </div>
    </div>
  </main>
</template>
