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
  Boxes,
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
  Scale,
  KeyRound,
  Webhook,
  Send,
  TrendingUp,
  Wallet,
  AlertCircle,
} from "lucide-vue-next";

const API = import.meta.env.VITE_API_URL ?? "";
const GlobalTrackingMap = defineAsyncComponent(() => import("./components/GlobalTrackingMap.vue"));
const StowagePlan3D = defineAsyncComponent(() => import("./components/StowagePlan3D.vue"));
import MultiSelect from "./components/MultiSelect.vue";

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
const fwbBalance = ref<any>(null);
const fwbDashboard = ref<any>(null);
const fwbSelectedCustomer = ref<string>('');
const fwbSelectedCurrency = ref<string>('USD');

async function fetchFwbDashboard() {
  fwbDashboard.value = null;
  if (!accTab.value.startsWith('fwb-')) return;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/dashboard`);
    if (res.ok) fwbDashboard.value = await res.json();
  } catch {}
}

const swbDashboard = ref<any>(null);
async function fetchSwbDashboard() {
  swbDashboard.value = null;
  if (!accTab.value.startsWith('swb-')) return;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/profit-summary`);
    if (res.ok) swbDashboard.value = await res.json();
  } catch {}
}

async function fetchFwbBalance() {
  fwbBalance.value = null;
  if (accTab.value !== 'fwb-prepay' || !fwbSelectedCustomer.value) return;
  const url = new URL(`${API}/api/acc/finance-workbench/customer-balance`, location.origin);
  url.searchParams.set('customerId', fwbSelectedCustomer.value);
  url.searchParams.set('currency', fwbSelectedCurrency.value || 'USD');
  try {
    const res = await apiFetch(url.toString());
    if (res.ok) fwbBalance.value = await res.json();
  } catch {}
}
const accTotal = ref(0);
const accAggregations = ref<Record<string, any> | null>(null);
const accPage = ref(1);
const accPageSize = ref(50);
const accLoading = ref(false);
const accKeyword = ref("");
const showAdvancedFilter = ref(false);
// 财务对账：仅看缺成本订单（有 AR 无 AP）
const missingCostOnly = ref(false);
// 3D 配载方案 — 新建求解对话框
const showStowageDialog = ref(false);
const stowageData = reactive({
  mode: 'single' as 'single' | 'multi',
  containerCode: '40HC-001',
  length_cm: 1200,
  width_cm: 230,
  height_cm: 260,
  max_weight_kg: 26000,
  shipmentIds: '',
  route: '',
  enableLifo: true,
  // 多柜模式
  multi40hcCount: 3,
  multi20gpCount: 3,
  packingFactor: 0.85,
});
const stowageShipmentOptions = ref<{value: string; label: string}[]>([]);
const stowageSelectedShipments = ref<string[]>([]);
async function loadStowageShipments() {
  try {
    const res = await apiFetch(`${API}/api/acc/shipments?pageSize=200`);
    const j = await res.json();
    stowageShipmentOptions.value = (j.data || []).map((s: any) => ({
      value: s.id,
      label: `${s.no || s.shipment_no || s.id.slice(0,8)} (${s.totalPiece || 0}件/${s.totalWeight || 0}kg) ${s.status || ''}`,
    }));
  } catch (e: any) {
    setBizError('加载 shipments 失败: ' + e.message);
  }
}
async function openStowageDialog() {
  showStowageDialog.value = true;
  stowageData.shipmentIds = '';
  stowageData.route = '';
  stowageSelectedShipments.value = [];
  await loadStowageShipments();
}

// 求解按钮 disabled 原因 — 给用户提示
const stowageSolveBlockedReason = computed(() => {
  if (bizLoading.value) return '正在求解中，请稍候...';
  const cnt = stowageSelectedShipments.value.length
    + stowageData.shipmentIds.split(/[,\s]+/).filter(Boolean).length;
  if (cnt === 0) return '请先在「选 Shipments」下拉框勾选至少 1 个运单，或在文本框输入 ID';
  if (stowageData.mode === 'single') {
    if (!stowageData.length_cm || !stowageData.width_cm || !stowageData.height_cm)
      return '柜尺寸 (长/宽/高) 必填';
    if (!stowageData.max_weight_kg) return '柜载重上限必填';
  }
  return null;
});

// 智能分柜 — 按 (客户, 目的国) 分组, 每组 1 个柜
async function doSmartGroupStowage() {
  bizLoading.value = true;
  try {
    // 拉 SUBMITTED+ shipments
    const res = await apiFetch(`${API}/api/acc/shipments?pageSize=200`);
    const j = await res.json();
    const valid = (j.data || []).filter((s: any) =>
      ['ORDERED','BOOKED','IN_TRANSIT','IN_WAREHOUSE'].includes(s.status));
    if (valid.length === 0) {
      setBizError('没有待配载的 shipments (需要状态为 ORDERED/BOOKED/IN_TRANSIT/IN_WAREHOUSE)');
      return;
    }
    // 按 (customerId, country) 分组
    const groups = new Map<string, any[]>();
    for (const s of valid) {
      const key = `${s.customerId || s.customer_id || '_NOCUST'}__${s.country || s.destination_country || '_NA'}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(s);
    }
    if (groups.size === 0) {
      setBizError('分组为空');
      return;
    }
    if (!confirm(`将按「客户 + 目的国」分组生成 ${groups.size} 个配载方案。是否继续？`)) return;
    let ok = 0, fail = 0;
    for (const [key, ships] of groups) {
      const [custId, country] = key.split('__');
      try {
        const r = await apiFetch(`${API}/api/acc/stowage/plan/auto`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            container: { code: `AUTO-${country}-${custId.slice(0,4)}`,
              length_cm: 1200, width_cm: 230, height_cm: 260, max_weight_kg: 26000 },
            shipmentIds: ships.map((s: any) => s.id),
            route: [], enableLifo: true,
          }),
        });
        if (r.ok) ok++; else fail++;
      } catch { fail++; }
    }
    setBizOk(`智能分柜: 新建 ${ok} 个方案 (${fail} 失败). 列表中可能含历史方案行, 看时间排序最新的 ${ok} 条即本次结果.`);
    showStowageDialog.value = false;
    fetchAccData();
  } catch (e: any) {
    setBizError('智能分柜异常: ' + e.message);
  } finally {
    bizLoading.value = false;
  }
}
async function downloadStowageSheet(planId: string) {
  // 走 fetch 拿 PDF, 用 Bearer token 鉴权
  try {
    const res = await apiFetch(`${API}/api/acc/stowage/plan/${planId}/loading-sheet`);
    if (!res.ok) { setBizError('下载失败: ' + res.status); return; }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.target = '_blank';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: any) {
    setBizError('下载异常: ' + e.message);
  }
}

async function doStowageSolve() {
  // 同步选中的 shipmentIds (优先 dropdown 选的, 兼容旧 textarea)
  const fromPicker = stowageSelectedShipments.value;
  const fromText = stowageData.shipmentIds.split(/[,\s]+/).filter(Boolean);
  const allShipmentIds = fromPicker.length > 0 ? fromPicker : fromText;
  if (allShipmentIds.length === 0) {
    setBizError('请至少选 1 个 shipment 或在文本框输入 ID');
    return;
  }
  bizLoading.value = true;
  try {
    if (stowageData.mode === 'single') {
      const body = {
        container: {
          code: stowageData.containerCode,
          length_cm: Number(stowageData.length_cm),
          width_cm: Number(stowageData.width_cm),
          height_cm: Number(stowageData.height_cm),
          max_weight_kg: Number(stowageData.max_weight_kg),
        },
        shipmentIds: allShipmentIds,
        route: stowageData.route ? stowageData.route.split(/[,\s]+/).filter(Boolean) : [],
        enableLifo: stowageData.enableLifo,
      };
      const res = await apiFetch(`${API}/api/acc/stowage/plan/auto`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const j = await res.json();
      if (res.ok) {
        setBizOk(`配载方案 ${j.planNo} 求解完成: 装入 ${j.result.fitted_count} 件, 利用率 ${(j.result.volume_utilization * 100).toFixed(1)}%`);
        showStowageDialog.value = false;
        fetchAccData();
      } else {
        setBizError('求解失败: ' + (j.error ?? res.status));
      }
    } else {
      // 多柜模式 — /multi/auto 自动从 shipmentIds 展开 cartons
      const body = {
        containers: [
          { code: '40HC', length_cm: 1200, width_cm: 230, height_cm: 260, max_weight_kg: 26000 },
          { code: '20GP', length_cm: 590, width_cm: 230, height_cm: 240, max_weight_kg: 21500 },
        ],
        container_max_count: {
          '40HC': Number(stowageData.multi40hcCount) || 3,
          '20GP': Number(stowageData.multi20gpCount) || 3,
        },
        shipmentIds: allShipmentIds,
        route: stowageData.route ? stowageData.route.split(/[,\s]+/).filter(Boolean) : [],
        enableLifo: stowageData.enableLifo,
        packingFactor: Number(stowageData.packingFactor) || 0.65,
      };
      const res = await apiFetch(`${API}/api/acc/stowage/plan/multi/auto`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      const j = await res.json();
      if (res.ok) {
        setBizOk(`多柜求解完成: 用 ${j.totalContainersUsed} 个柜 (${j.assignmentStatus}), `
          + `${(j.unfittedOverall || []).length === 0 ? '全部装入' : (j.unfittedOverall.length + ' 件未装')}`);
        showStowageDialog.value = false;
        fetchAccData();
      } else {
        setBizError('多柜求解失败: ' + (j.error ?? res.status));
      }
    }
  } catch (e: any) {
    setBizError('求解异常: ' + e.message);
  } finally {
    bizLoading.value = false;
  }
}

// 财务中心 "新增成本" — 哪些 tab 显示按钮
const costAddTabSet = new Set([
  'costs', 'costs-pending', 'costs-history', 'costs-import',
  'fwb-pending', 'fwb-prepay', 'fwb-invoiced',
  'charges', 'charges-pending',
]);
const showCostAddButton = computed(() => costAddTabSet.has(accTab.value));

// 添加成本对话框
const showAddCostDialog = ref(false);
const addCostOrderId = ref('');
const addCostOrderNo = ref('');
const addCostData = reactive({
  amount: '' as string | number,
  currency: 'CNY',
  chargeItemCode: '',
  remark: '',
});
function openAddCostDialog(row: any) {
  addCostStandalone.value = false;
  addCostOrderId.value = row.id;
  addCostOrderNo.value = row.orderNo || row.order_no || '';
  addCostData.amount = '';
  addCostData.currency = 'CNY';
  addCostData.chargeItemCode = '';
  addCostData.remark = '';
  showAddCostDialog.value = true;
}

// 财务中心独立入口：先 preload 客户+订单选项，再开 dialog 让用户选订单
const addCostStandalone = ref(false);
const addCostCustomerId = ref('');
const addCostOrderOptions = ref<{id: string; orderNo: string; status: string}[]>([]);
async function openAddCostStandalone() {
  addCostStandalone.value = true;
  addCostOrderId.value = '';
  addCostOrderNo.value = '';
  addCostCustomerId.value = '';
  addCostOrderOptions.value = [];
  addCostData.amount = '';
  addCostData.currency = 'CNY';
  addCostData.chargeItemCode = '';
  addCostData.remark = '';
  showAddCostDialog.value = true;
  // preload 客户下拉
  await loadSelectOptions([{ type: 'select', ref: 'customers' } as any]);
}
// 选定客户后加载该客户已 SUBMITTED+ 订单
async function loadOrdersForCustomer() {
  addCostOrderOptions.value = [];
  addCostOrderId.value = '';
  addCostOrderNo.value = '';
  if (!addCostCustomerId.value) return;
  try {
    const res = await apiFetch(
      `${API}/api/acc/orders?customerIds=${addCostCustomerId.value}&pageSize=100&statuses=SUBMITTED,ACCEPTED,FULFILLING,COMPLETED`
    );
    const j = await res.json();
    addCostOrderOptions.value = (j.data || []).map((o: any) => ({
      id: o.id, orderNo: o.orderNo || o.order_no, status: o.status,
    }));
  } catch (e: any) {
    setBizError('加载订单失败: ' + e.message);
  }
}
function pickAddCostOrder(orderId: string) {
  addCostOrderId.value = orderId;
  const opt = addCostOrderOptions.value.find(o => o.id === orderId);
  addCostOrderNo.value = opt?.orderNo || '';
}
async function doAddCost() {
  if (!addCostOrderId.value || !addCostData.amount) {
    setBizError('请输入成本金额');
    return;
  }
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/${addCostOrderId.value}/add-cost`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        amount: Number(addCostData.amount),
        currency: addCostData.currency,
        chargeItemCode: addCostData.chargeItemCode || null,
        remark: addCostData.remark || null,
      }),
    });
    const j = await res.json();
    if (res.ok) {
      setBizOk(`成本添加成功: ${j.amount} ${j.currency}，已落到「核算中心 → 待核成本」，等待一审`);
      showAddCostDialog.value = false;
      fetchAccData();
    } else {
      setBizError('添加失败：' + (j.error ?? res.status));
    }
  } catch (e: any) {
    setBizError('添加异常：' + e.message);
  } finally {
    bizLoading.value = false;
  }
}

// 多条件筛选 — 30+ 字段覆盖 ACC 大货运单页全部条件
const advFilters = reactive<{
  // 多选 chip
  customers: string[]; channels: string[]; countries: string[];
  statuses: string[]; auditStatuses: string[]; addNames: string[];
  branches: string[]; sellers: string[]; servicers: string[];
  warehouses: string[]; carriers: string[];
  currencies: string[]; sides: string[]; settlementStatuses: string[];
  // 单值文本
  postcode: string; trackingNo: string; recipientName: string;
  vatNo: string; poNumber: string; amazonRef: string;
  binLocation: string; mainItem: string;
  // 时间区间
  createdFrom: string; createdTo: string;
  submittedFrom: string; submittedTo: string;
  deliveredFrom: string; deliveredTo: string;
  // 金额区间
  amountFrom: string; amountTo: string;
}>({
  customers: [], channels: [], countries: [],
  statuses: [], auditStatuses: [], addNames: [],
  branches: [], sellers: [], servicers: [],
  warehouses: [], carriers: [],
  currencies: [], sides: [], settlementStatuses: [],
  postcode: '', trackingNo: '', recipientName: '',
  vatNo: '', poNumber: '', amazonRef: '',
  binLocation: '', mainItem: '',
  createdFrom: '', createdTo: '',
  submittedFrom: '', submittedTo: '',
  deliveredFrom: '', deliveredTo: '',
  amountFrom: '', amountTo: '',
});

// 每个 tab 显示哪些 filter（按业务相关性裁剪）
type FilterKey = keyof typeof advFilters;
const tabFilterSpec: Record<string, FilterKey[]> = {
  orders: ['customers','channels','countries','statuses','auditStatuses',
           'branches','sellers','servicers','warehouses','carriers',
           'postcode','trackingNo','recipientName','vatNo','poNumber','amazonRef','binLocation','mainItem',
           'createdFrom','submittedFrom','deliveredFrom','addNames'],
  shipments: ['customers','channels','countries','statuses','auditStatuses','branches',
              'postcode','trackingNo','createdFrom','submittedFrom','deliveredFrom'],
  charges:   ['customers','currencies','sides','statuses','auditStatuses','settlementStatuses',
              'createdFrom','amountFrom'],
  costs:     ['customers','currencies','sides','statuses','auditStatuses','settlementStatuses',
              'createdFrom','amountFrom'],
  bills:     ['customers','currencies','statuses','auditStatuses','createdFrom','amountFrom'],
  receiveds: ['customers','currencies','statuses','auditStatuses','createdFrom','amountFrom'],
  payments:  ['customers','currencies','statuses','auditStatuses','createdFrom','amountFrom'],
};
const advFilterFieldsForTab = computed<Set<FilterKey>>(() => {
  const apiKey = (() => {
    if (ordersTabSet.has(accTab.value)) return 'orders';
    const t = accTabs.find(t => t.key === accTab.value);
    return t?.api ?? accTab.value;
  })();
  return new Set((tabFilterSpec[apiKey] ?? []) as FilterKey[]);
});
const showAdvFilterButton = computed(() => advFilterFieldsForTab.value.size > 0);

// preload select options 当展开高级筛选时
watch([accTab, showAdvancedFilter], async () => {
  if (!showAdvancedFilter.value) return;
  const fields = advFilterFieldsForTab.value;
  const refs: string[] = [];
  if (fields.has('customers'))  refs.push('customers');
  if (fields.has('channels'))   refs.push('channels');
  if (fields.has('countries'))  refs.push('countries');
  if (fields.has('branches'))   refs.push('branches');
  if (fields.has('sellers') || fields.has('servicers')) refs.push('employees');
  if (fields.has('warehouses')) refs.push('warehouses');
  if (fields.has('currencies')) refs.push('currencies');
  if (refs.length) {
    await loadSelectOptions(refs.map(r => ({ type: 'select', ref: r } as any)));
  }
});

function clearAdvFilters() {
  Object.keys(advFilters).forEach(k => {
    const v = (advFilters as any)[k];
    (advFilters as any)[k] = Array.isArray(v) ? [] : '';
  });
}
function activeAdvFilterCount(): number {
  let n = 0;
  Object.values(advFilters).forEach(v => {
    if (Array.isArray(v)) { if (v.length) n++; }
    else if (typeof v === 'string' && v.trim()) n++;
  });
  // 区间字段 from-to 视为 1 个条件
  if (advFilters.createdFrom && advFilters.createdTo) n--;
  if (advFilters.submittedFrom && advFilters.submittedTo) n--;
  if (advFilters.deliveredFrom && advFilters.deliveredTo) n--;
  if (advFilters.amountFrom && advFilters.amountTo) n--;
  return Math.max(0, n);
}

// ───────── 筛选预设 (B) — localStorage 持久化 ─────────
interface FilterPreset { name: string; tab: string; data: any; isDefault?: boolean }
const filterPresets = ref<FilterPreset[]>([]);
const showPresetMenu = ref(false);
function loadPresetsFromStorage() {
  try {
    const raw = localStorage.getItem('xqt.filter.presets');
    filterPresets.value = raw ? JSON.parse(raw) : [];
  } catch { filterPresets.value = []; }
}
function savePresetsToStorage() {
  try { localStorage.setItem('xqt.filter.presets', JSON.stringify(filterPresets.value)); }
  catch { /* quota exceeded */ }
}
function saveCurrentAsPreset() {
  const name = prompt('保存为预设方案，输入方案名称:')?.trim();
  if (!name) return;
  const data: any = {};
  Object.entries(advFilters).forEach(([k,v]) => {
    if (Array.isArray(v) ? v.length : (typeof v === 'string' && v.trim()))
      data[k] = Array.isArray(v) ? v.slice() : v;
  });
  filterPresets.value = filterPresets.value.filter(p => !(p.name === name && p.tab === accTab.value));
  filterPresets.value.push({ name, tab: accTab.value, data });
  savePresetsToStorage();
  setBizOk(`已保存预设: ${name}`);
}
function applyPreset(p: FilterPreset) {
  clearAdvFilters();
  Object.entries(p.data).forEach(([k,v]) => { (advFilters as any)[k] = Array.isArray(v) ? (v as any[]).slice() : v; });
  showPresetMenu.value = false;
  accSearch();
}
function deletePreset(p: FilterPreset) {
  if (!confirm(`删除预设方案 "${p.name}"?`)) return;
  filterPresets.value = filterPresets.value.filter(x => !(x.name === p.name && x.tab === p.tab));
  savePresetsToStorage();
}
function toggleDefaultPreset(p: FilterPreset) {
  // 同一 tab 内只能 1 个默认。点击 star：当前是默认则取消，否则设为默认（清空同 tab 其他默认）
  const wasDefault = !!p.isDefault;
  filterPresets.value.forEach(x => {
    if (x.tab === p.tab) x.isDefault = false;
  });
  if (!wasDefault) {
    const target = filterPresets.value.find(x => x.name === p.name && x.tab === p.tab);
    if (target) target.isDefault = true;
  }
  savePresetsToStorage();
}
const presetsForCurrentTab = computed(() => filterPresets.value.filter(p => p.tab === accTab.value));
// 切换 tab 时如果该 tab 有默认预设且当前 filter 为空，自动套用
watch(accTab, (newTab) => {
  if (activeAdvFilterCount() > 0) return;
  const def = filterPresets.value.find(p => p.tab === newTab && p.isDefault);
  if (def) {
    clearAdvFilters();
    Object.entries(def.data).forEach(([k,v]) => { (advFilters as any)[k] = Array.isArray(v) ? (v as any[]).slice() : v; });
  }
});

// ACC 客服中心「发起新问题/申请赔偿」是入口 tab(对齐 ACC),
// 切到这两个 tab 自动打开新建表单, 用户不必再点「+ 新增」.
const AUTO_OPEN_ADD_TABS = new Set(['asks-new', 'reparations-apply']);
watch(accTab, async (newTab) => {
  if (AUTO_OPEN_ADD_TABS.has(newTab)) {
    await openAdd();
  }
});
onMounted(() => loadPresetsFromStorage());
const accDateFrom = ref("");
const accDateTo = ref("");
const accStats = ref<any>(null);

const accTotalPages = computed(() => Math.max(1, Math.ceil(accTotal.value / accPageSize.value)));

// Form dialog state
interface FormField {
  col: string;
  label: string;
  type: 'text' | 'number' | 'date' | 'textarea' | 'select' | 'boolean' | 'radio' | 'section';
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
const selectOptions = ref<Record<string, Array<{ id: any; name: string; code?: string }>>>({});
const showDeleteConfirm = ref(false);
const deleteTarget = ref<{ id: number; label: string }>({ id: 0, label: '' });

// Business operation state
const selectedIds = ref<Set<number>>(new Set());
const showDetail = ref(false);
const detailData = ref<any>(null);
const detailType = ref('');
const bizLoading = ref(false);
const bizMessage = ref('');
const bizMessageType = ref<'info' | 'success' | 'error'>('info');
function setBizError(msg: string, holdMs = 15000) {
  bizMessage.value = msg;
  bizMessageType.value = 'error';
  if (holdMs > 0) setTimeout(() => { bizMessage.value = ''; bizMessageType.value = 'info'; }, holdMs);
}
function setBizOk(msg: string, holdMs = 4000) {
  bizMessage.value = msg;
  bizMessageType.value = 'success';
  if (holdMs > 0) setTimeout(() => { bizMessage.value = ''; bizMessageType.value = 'info'; }, holdMs);
}
function clearBizMessage() { bizMessage.value = ''; bizMessageType.value = 'info'; }
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
    { key: "id", label: "编号" }, { key: "username", label: "用户名" }, { key: "realName", label: "姓名" },
    { key: "roleName", label: "角色" }, { key: "email", label: "邮箱" }, { key: "phone", label: "电话" },
    { key: "status", label: "状态" }, { key: "lastLogin", label: "最后登录" },
  ],
  roles: [
    { key: "id", label: "编号" }, { key: "name", label: "角色名" }, { key: "description", label: "描述" },
    { key: "permissions", label: "权限" }, { key: "createdAt", label: "创建时间" },
  ],
  menus: [
    { key: "id", label: "编号" }, { key: "parentId", label: "父级" }, { key: "name", label: "菜单名" },
    { key: "path", label: "路径" }, { key: "icon", label: "图标" }, { key: "sort", label: "排序" },
    { key: "permission", label: "权限码" }, { key: "visible", label: "可见" },
  ],
  "op-logs": [
    { key: "id", label: "编号" }, { key: "username", label: "用户" }, { key: "module", label: "模块" },
    { key: "action", label: "操作" }, { key: "target", label: "目标" },
    { key: "ip", label: "IP 地址" }, { key: "createdAt", label: "时间" },
  ],
  "error-logs": [
    { key: "id", label: "编号" }, { key: "level", label: "级别" }, { key: "module", label: "模块" },
    { key: "message", label: "消息" }, { key: "created_at", label: "时间" },
  ],
  "sms-logs": [
    { key: "id", label: "编号" }, { key: "phone", label: "手机号" }, { key: "content", label: "内容" },
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
    { key: "id", label: "编号" }, { key: "customerId", label: "客户ID" }, { key: "username", label: "用户名" },
    { key: "status", label: "状态" }, { key: "lastLogin", label: "最后登录" },
  ],
  "service-msg": [
    { key: "id", label: "编号" }, { key: "customerId", label: "客户ID" }, { key: "direction", label: "方向" },
    { key: "content", label: "内容" }, { key: "createdAt", label: "时间" },
  ],
  hardware: [
    { key: "id", label: "编号" }, { key: "type", label: "类型" }, { key: "name", label: "名称" },
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
  // 订单管理（对应 ACC 制单中心左侧 4 个状态分类）
  // 注：orders.status 枚举不含 VOID，作废 == CANCELLED
  { key: "orders", label: "快件订单", icon: FileText, api: "orders" },
  { key: "orders-draft", label: "未提交", icon: FileText, api: "orders", statusFilter: "DRAFT" },
  { key: "orders-history", label: "历史制单", icon: FileText, api: "orders", statusFilter: "HISTORY" },
  { key: "orders-cancelled", label: "取消订单", icon: FileText, api: "orders", statusFilter: "CANCELLED" },
  { key: "orders-void", label: "作废订单", icon: FileText, api: "orders", statusFilter: "VOID_AUDIT" },
  { key: "collects", label: "总单/留仓", icon: ClipboardList, api: "collects" },
  { key: "returns", label: "退件管理", icon: CornerDownLeft, api: "returns" },
  { key: "detains", label: "扣件管理", icon: Lock, api: "detains" },
  { key: "asks", label: "问题件", icon: HelpCircle, api: "asks" },
  // 问题件 ACC 7 子页（按角色/状态过滤）
  { key: "asks-customer", label: "客户查询件", icon: HelpCircle, api: "asks", statusFilter: "CUSTOMER" },
  { key: "asks-supplier", label: "服务商反馈", icon: HelpCircle, api: "asks", statusFilter: "SUPPLIER" },
  { key: "asks-processing", label: "处理中问题", icon: HelpCircle, api: "asks", statusFilter: "PROCESSING" },
  { key: "asks-pending", label: "未处理问题", icon: HelpCircle, api: "asks", statusFilter: "PENDING" },
  { key: "asks-new", label: "发起新问题", icon: PlusCircle, api: "asks" },
  { key: "asks-history", label: "历史问题件", icon: HelpCircle, api: "asks", statusFilter: "DONE" },
  { key: "reparations", label: "赔偿管理", icon: Gavel, api: "reparations" },
  // 赔偿 3 子页
  { key: "reparations-apply", label: "申请赔偿", icon: PlusCircle, api: "reparations", statusFilter: "DRAFT" },
  { key: "reparations-pending", label: "待审赔偿", icon: Gavel, api: "reparations", statusFilter: "PENDING" },
  { key: "reparations-history", label: "历史赔偿", icon: Gavel, api: "reparations", statusFilter: "DONE" },
  { key: "quick-orders", label: "快速下单", icon: ClipboardList, api: "quick-orders" },
  // 制单中心工具类 (ACC 缺失补齐) - 大多是 orders 数据源 + 不同 filter
  { key: "orders-queue", label: "制单队列", icon: ClipboardList, api: "orders", statusFilter: "QUEUE" },
  { key: "orders-import", label: "导入快件", icon: Upload, api: "orders" },
  { key: "orders-batch-print", label: "批量打印", icon: FileText, api: "orders" },
  { key: "orders-batch-track", label: "追踪快递", icon: MapPin, api: "orders" },
  { key: "orders-update-tracking", label: "更新转单号", icon: RefreshCw, api: "orders" },
  { key: "orders-update-weight", label: "更新计费重", icon: RefreshCw, api: "orders" },
  { key: "orders-change-customer", label: "变更客户", icon: ArrowLeftRight, api: "orders" },
  { key: "orders-batch-charge", label: "批量计费", icon: Calculator, api: "orders" },
  // 物流出货
  { key: "shipments", label: "出货管理", icon: Truck, api: "shipments" },
  // ACC「配载中心」拆分快件查询 / 今日快件 (沿用 shipments 数据源 + 不同过滤)
  { key: "shipments-query", label: "快件查询", icon: Search, api: "shipments" },
  { key: "shipments-today", label: "今日快件", icon: CalendarClock, api: "shipments", statusFilter: "TODAY" },
  // 配载中心 ACC 缺失补齐
  { key: "stowages-exception", label: "异常提单", icon: AlertTriangle, api: "stowages", statusFilter: "EXCEPTION" },
  { key: "shipments-channel-stats", label: "渠道统计", icon: BarChart3, api: "shipments/channel-stats" },
  { key: "shipments-pickup-today", label: "今日提取", icon: CalendarClock, api: "shipments", statusFilter: "PICKUP_TODAY" },
  { key: "shipments-pickup-week", label: "本周提取", icon: CalendarClock, api: "shipments", statusFilter: "PICKUP_WEEK" },
  { key: "shipments-intransit", label: "在途订单", icon: Plane, api: "shipments", statusFilter: "INTRANSIT" },
  { key: "shipments-exception", label: "异常订单", icon: AlertTriangle, api: "shipments", statusFilter: "EXCEPTION" },
  { key: "shipments-delivered-today", label: "今日签收", icon: CheckCircle, api: "shipments", statusFilter: "DELIVERED_TODAY" },
  { key: "stowages", label: "配载管理", icon: Plane, api: "stowages" },
  { key: "stowage-plans", label: "3D 配载方案", icon: Boxes, api: "stowage/plan" },
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
  // 核算中心 运费核算 ACC 子页
  { key: "charges-history", label: "历史费用", icon: DollarSign, api: "charges", statusFilter: "HISTORY" },
  { key: "charges-pending", label: "待核费用", icon: DollarSign, api: "charges", statusFilter: "UNAUDITED" },
  { key: "charges-pending-return", label: "待核退件", icon: CornerDownLeft, api: "charges", statusFilter: "RETURN_PENDING" },
  { key: "charges-pending-reparation", label: "待核赔偿", icon: Gavel, api: "charges", statusFilter: "REPARATION_PENDING" },
  { key: "charges-import", label: "导入费用", icon: Upload, api: "charges" },
  { key: "costs", label: "应付成本", icon: CreditCard, api: "costs" },
  // 核算中心 成本核算 ACC 子页
  { key: "costs-pending", label: "待核成本", icon: CreditCard, api: "costs", statusFilter: "UNAUDITED" },
  { key: "costs-estimate", label: "预估成本", icon: CreditCard, api: "costs", statusFilter: "ESTIMATE" },
  { key: "costs-recent", label: "近期成本", icon: CreditCard, api: "costs", statusFilter: "RECENT" },
  { key: "costs-history", label: "历史成本", icon: CreditCard, api: "costs", statusFilter: "HISTORY" },
  { key: "costs-import", label: "导入成本", icon: Upload, api: "costs" },
  { key: "costs-transit", label: "转运成本", icon: CreditCard, api: "costs", statusFilter: "TRANSIT" },
  { key: "costs-zhonggang", label: "中港成本", icon: CreditCard, api: "costs", statusFilter: "ZHONGGANG" },
  { key: "costs-air", label: "航空成本", icon: Plane, api: "costs", statusFilter: "AIR" },
  // 核算工作台 — AP 三阶段 + 利润
  { key: "swb-cost-pending", label: "待核成本 (UPS 单)", icon: AlertCircle, api: "settlement-workbench/cost-pending" },
  { key: "swb-pending-pay",  label: "待付供应商",         icon: CreditCard,  api: "settlement-workbench/pending-pay" },
  { key: "swb-paid",         label: "已付成本",           icon: ReceiptText, api: "settlement-workbench/paid" },
  { key: "swb-profit",       label: "利润分析",           icon: BarChart3,   api: "settlement-workbench/profit-list" },
  { key: "swb-aging",        label: "账龄分析",           icon: Clock,       api: "settlement-workbench/aging" },
  { key: "swb-monthly",      label: "月度财报",           icon: BarChart3,   api: "settlement-workbench/monthly-report" },
  { key: "gl-income",        label: "利润表 (GL)",         icon: BarChart3,   api: "gl/reports/income-statement" },
  { key: "gl-balance",       label: "资产负债表 (GL)",      icon: BarChart3,   api: "gl/reports/balance-sheet" },
  { key: "gl-cash",          label: "现金流量表 (GL)",      icon: BarChart3,   api: "gl/reports/cash-flow" },
  { key: "gl-trial",         label: "试算平衡 (GL)",        icon: BarChart3,   api: "gl/reports/trial-balance" },
  { key: "approval-pending", label: "审批待办",            icon: AlertCircle, api: "../admin/approval/pending" },
  { key: "swb-commissions",  label: "业绩提成",           icon: Gift,        api: "settlement-workbench/commissions" },
  // alair: 应收款项目（A1 财务任务）
  { key: "customer-receivables", label: "应收款项目", icon: TrendingUp, api: "customer-receivables" },
  // 财务工作台 — 一票货的 3 阶段视图
  { key: "fwb-prepay",   label: "预扣明细", icon: Wallet, api: "finance-workbench/prepay-details" },
  { key: "fwb-pending",  label: "待财务审核", icon: AlertCircle, api: "finance-workbench/pending-audit" },
  { key: "fwb-invoiced", label: "已出账", icon: ReceiptText, api: "finance-workbench/invoiced" },
  { key: "fwb-needs-verify", label: "待二审账单", icon: AlertCircle, api: "finance-workbench/needs-verify" },
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
  { key: "importer-templates", label: "进口商模板", icon: FileText, api: "importer-templates" },
  // ACC 基础信息 → 运费管理 新增 4 个 tab
  { key: "sales-prices", label: "销售价格", icon: WalletCards, api: "sales-prices" },
  { key: "customer-prices", label: "客户价格", icon: WalletCards, api: "customer-prices" },
  { key: "published-prices", label: "公布价格", icon: WalletCards, api: "published-prices" },
  { key: "files", label: "文件管理", icon: FileText, api: "files" },
  // DWS 实物分拣 (客服中心 收货线)
  { key: "inbound-parcels", label: "入仓预报", icon: PackageOpen, api: "inbound-parcels" },
  { key: "dws-scans", label: "DWS 扫描流水", icon: Scale, api: "dws-scans" },
  { key: "dws-discrepancies", label: "重量差异", icon: AlertTriangle, api: "dws-discrepancies" },
  // ACC 财务中心 新增 8 个 tab（往来账户 + 4 个待审 + 3 个利润视图）
  { key: "account-transactions", label: "往来账户", icon: ArrowLeftRight, api: "account-transactions" },
  { key: "receiveds-pending", label: "待审收款", icon: Coins, api: "receiveds", statusFilter: "UNAUDITED" },
  { key: "customer-refunds-pending", label: "待审退款(应收)", icon: Undo2, api: "customer-refunds", statusFilter: "UNAUDITED" },
  { key: "payments-pending", label: "待审付款", icon: Landmark, api: "payments", statusFilter: "UNAUDITED" },
  { key: "supplier-refunds-pending", label: "待审退款(应付)", icon: Undo2, api: "supplier-refunds", statusFilter: "UNAUDITED" },
  { key: "profits-unfinished", label: "未完结快件", icon: BarChart3, api: "profits", statusFilter: "UNFINISHED" },
  { key: "profits-overdue", label: "逾期未结", icon: AlertTriangle, api: "profits", statusFilter: "OVERDUE" },
  { key: "profits-lowprofit", label: "低利快件", icon: BarChart3, api: "profits", statusFilter: "LOWPROFIT" },
  // API 对接（对应 ACC CustomerAPI.php / OnlineAPI.php）
  { key: "api-credentials", label: "API 凭证", icon: KeyRound, api: "api-credentials" },
  { key: "api-call-logs", label: "API 调用日志", icon: ListChecks, api: "api-call-logs" },
  { key: "webhook-endpoints", label: "Webhook 回调地址", icon: Webhook, api: "webhook-endpoints" },
  { key: "webhook-events", label: "Webhook 投递记录", icon: Send, api: "webhook-events" },
  { key: "api-docs", label: "API 文档", icon: BookOpen, api: "api-docs" },
];

// ACC 基础信息（对应 ACC 顶部"基础信息"菜单的 4 大子组、共 21 项）。
// 复用 accTabs 里已有的 key，新增 4 个价格/文件 tab。
const accBasicTabs = [
  // 信息管理 (8)
  accTabs.find(t => t.key === "acc-branches")!,        // 分店管理
  accTabs.find(t => t.key === "customer-groups")!,     // 分组管理
  accTabs.find(t => t.key === "districts")!,           // 地区管理
  accTabs.find(t => t.key === "postcodes")!,           // 邮编管理
  accTabs.find(t => t.key === "remotes")!,             // 偏远邮编
  accTabs.find(t => t.key === "fee-types")!,           // 杂费类型
  accTabs.find(t => t.key === "fees")!,                // 杂费套餐
  accTabs.find(t => t.key === "fuels")!,               // 燃油费用
  // 运费管理 (8)
  accTabs.find(t => t.key === "channel-accounts")!,    // 渠道账号
  accTabs.find(t => t.key === "products")!,            // 销售产品
  accTabs.find(t => t.key === "sales-prices")!,        // 销售价格
  accTabs.find(t => t.key === "customer-prices")!,     // 客户价格
  accTabs.find(t => t.key === "published-prices")!,    // 公布价格
  accTabs.find(t => t.key === "zones")!,               // 价格分区
  accTabs.find(t => t.key === "channels")!,            // 渠道类型
  accTabs.find(t => t.key === "files")!,               // 文件管理
  // 物流商管理 (2)（"创建物流商"用列表里的"新增"按钮，不单独占 tab）
  accTabs.find(t => t.key === "suppliers")!,           // 物流商列表
  accTabs.find(t => t.key === "supplier-adjusts")!,    // 物流商调账
  // 物流商往来 (5)
  accTabs.find(t => t.key === "bills")!,               // 应付款项（账单视图，过滤 supplier）
  accTabs.find(t => t.key === "supplier-rebates")!,    // 物流商返利
  accTabs.find(t => t.key === "supplier-fines")!,      // 物流商罚款
  accTabs.find(t => t.key === "payments")!,            // 付款记录
  accTabs.find(t => t.key === "supplier-refunds")!,    // 退款记录
];

// ACC 财务中心（对应 ACC 顶部"财务中心"菜单的 4 大子组、共 20 项）
const accFinanceTabs = [
  // 资金账户 (5)
  accTabs.find(t => t.key === "banks")!,                  // 账户管理
  accTabs.find(t => t.key === "transfers")!,              // 资金转账
  accTabs.find(t => t.key === "borrowings")!,             // 资金借贷
  accTabs.find(t => t.key === "account-transactions")!,   // 往来账户
  accTabs.find(t => t.key === "currencies")!,             // 币种管理
  // 应收 (5)
  accTabs.find(t => t.key === "charges")!,                // 应收款项
  accTabs.find(t => t.key === "receiveds")!,              // 收款记录
  accTabs.find(t => t.key === "receiveds-pending")!,      // 待审收款
  accTabs.find(t => t.key === "customer-refunds")!,       // 退款记录
  accTabs.find(t => t.key === "customer-refunds-pending")!, // 待审退款
  // 应付 (5)
  accTabs.find(t => t.key === "costs")!,                  // 应付款项
  accTabs.find(t => t.key === "payments")!,               // 付款记录
  accTabs.find(t => t.key === "payments-pending")!,       // 待审付款
  accTabs.find(t => t.key === "supplier-refunds")!,       // 退款记录
  accTabs.find(t => t.key === "supplier-refunds-pending")!, // 待审退款
  // 利润列表 (5)
  accTabs.find(t => t.key === "profits")!,                // 利润查询（含快件利润）
  accTabs.find(t => t.key === "profits-unfinished")!,     // 未完结快件
  accTabs.find(t => t.key === "profits-overdue")!,        // 逾期未结
  accTabs.find(t => t.key === "profits-lowprofit")!,      // 低利快件
];

// ACC 二级菜单：对齐 ACC PHP 原版的 9 大顶部菜单（系统设置/数据管理合并）
const T = (k: string) => accTabs.find(t => t.key === k)!;

// 制单中心
const accGroupOrder = [
  T("orders"), T("orders-draft"), T("orders-history"), T("orders-cancelled"), T("orders-void"),
  T("quick-orders"),
  T("orders-queue"),                    // 制单队列
  T("orders-import"),                   // 导入快件
  T("orders-batch-print"),              // 批量打印
  // 批量操作子组：5 个独立批量页面（ACC ExpressBatch.php）
  T("orders-update-tracking"),          // 更新转单号
  T("orders-update-weight"),            // 更新计费重
  T("orders-change-customer"),          // 变更客户
  T("orders-batch-charge"),             // 批量计费
  T("orders-batch-track"),              // 追踪快递
];
// 配载中心
const accGroupStowage = [
  T("shipments"),
  T("shipments-query"),                     // ACC 配载中心「快件查询」
  T("shipments-today"),                     // ACC 配载中心「今日快件」
  T("stowages"),
  T("stowage-plans"),                       // 3D 配载方案 (新)
  T("stowages-exception"),                  // 异常提单
  T("packages"),
  T("transits"),
  T("shipments-channel-stats"),             // 渠道统计
  T("shipments-pickup-today"),              // 今日提取
  T("shipments-pickup-week"),               // 本周提取
  T("shipments-intransit"),                 // 在途订单
  T("shipments-exception"),                 // 异常订单
  T("shipments-delivered-today"),           // 今日签收
  T("ports"), T("warehouses"), T("stowage-categories"), T("stowage-steps"),
  T("forecasts"), T("tracks"),
];
// 客服中心（收货 + 问题件 + 赔偿）
const accGroupCustomerService = [
  T("dispatches"),         // 上门揽收（收货前置）
  T("inbound-parcels"),    // 入仓预报（收货主表，DWS 扫描的对象）
  T("dws-scans"),          // DWS 实物分拣流水
  T("dws-discrepancies"),  // 重量差异（DWS 实测 vs 客户预报）
  T("collects"),           // 总单/留仓
  T("returns"),            // 退件管理
  T("detains"),            // 扣件管理
  // 问题件 7 子页（ACC 客服中心）
  T("asks"),               // 问题件 (总)
  T("asks-customer"),      // 客户查询件
  T("asks-supplier"),      // 服务商反馈
  T("asks-processing"),    // 处理中问题
  T("asks-pending"),       // 未处理问题
  T("asks-new"),           // 发起新问题
  T("asks-history"),       // 历史问题件
  // 赔偿 4 子页
  T("reparations"),        // 赔偿管理 (总)
  T("reparations-apply"),  // 申请赔偿
  T("reparations-pending"),// 待审赔偿
  T("reparations-history"),// 历史赔偿
  T("received-sms"),       // 收款短信（客服触发）
];
// 销售中心
const accGroupSales = [
  T("customers"), T("customer-groups"), T("suppliers"),
  T("channels"), T("channel-accounts"),
  T("products"), T("product-items"),
  T("potentials"), T("sold-tos"), T("notices"),
];
// 核算中心（业务核算 - SKU 级 AR/AP/利润）
const accGroupAccounting = [
  // 运费核算 ACC 子组
  T("charges"),
  T("charges-history"),                  // 历史费用
  T("charges-pending"),                  // 待核费用
  T("charges-pending-return"),           // 待核退件
  T("charges-pending-reparation"),       // 待核赔偿
  T("charges-import"),                   // 导入费用
  // 成本核算 ACC 子组
  T("costs"),
  T("costs-pending"),                    // 待核成本
  T("costs-estimate"),                   // 预估成本
  T("costs-recent"),                     // 近期成本
  T("costs-history"),                    // 历史成本
  T("costs-import"),                     // 导入成本
  T("costs-transit"),                    // 转运成本
  T("costs-zhonggang"),                  // 中港成本
  T("costs-air"),                        // 航空成本
  // 核算工作台
  T("swb-cost-pending"),
  T("swb-pending-pay"),
  T("swb-paid"),
  T("swb-profit"),
  T("swb-aging"),
  T("swb-monthly"),
  T("gl-income"),
  T("gl-balance"),
  T("gl-cash"),
  T("gl-trial"),
  T("approval-pending"),
  T("swb-commissions"),
  // 其余核算项
  T("bills"),
  T("profits"),
  T("commissions"), T("commission-rules"),
  T("expenses"), T("cycles"),
  T("fees"), T("fee-types"), T("expense-categories"), T("fee-item-types"),
];
// 人事组织（保留，对应 ACC 部分原系统设置/数据管理范畴）
const accGroupHR = [
  T("employees"), T("wages"), T("attendances"),
  T("acc-branches"), T("departments"),
  T("socials"), T("social-persons"), T("funds"), T("fund-persons"),
];
// 数据管理 / 系统设置（master + 接口 + 任务）
const accGroupSystem = [
  T("countries"), T("districts"),
  T("postcodes"), T("remotes"),
  T("fuels"), T("hscodes"),
  T("zones"), T("bank-names"),
  T("logistics-interfaces"), T("tasks"), T("templates"),
  T("importer-templates"),                  // 制单进口商预设
];

// API 对接中心：客户 API 凭证 / 调用日志 / Webhook 出站 / 文档
const accGroupApi = [
  T("api-credentials"),
  T("api-call-logs"),
  T("webhook-endpoints"),
  T("webhook-events"),
  T("api-docs"),
];

const accMenuGroups = [
  { key: "order",          label: "制单中心",       icon: FileText,    tabs: accGroupOrder },
  { key: "stowage",        label: "配载中心",       icon: Plane,       tabs: accGroupStowage },
  { key: "customer-svc",   label: "客服中心 (收货)", icon: HelpCircle,  tabs: accGroupCustomerService },
  { key: "sales",          label: "销售中心",       icon: Users,       tabs: accGroupSales },
  { key: "accounting",     label: "核算中心",       icon: BarChart3,   tabs: accGroupAccounting },
  { key: "finance-center", label: "财务中心",       icon: DollarSign,  tabs: accFinanceTabs },
  { key: "basic-info",     label: "基础信息",       icon: Layers,      tabs: accBasicTabs },
  { key: "hr",             label: "人事组织",       icon: Building2,   tabs: accGroupHR },
  { key: "system",         label: "数据管理",       icon: Globe,       tabs: accGroupSystem },
  { key: "api",            label: "API 对接中心",    icon: KeyRound,    tabs: accGroupApi },
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
const accColumns: Record<string, Array<{ key: string; label: string; fmt?: string }>> = new Proxy({} as any, {
  get(target, key: string) {
    // 4 个订单状态分类 sub-tab 共用 orders 列定义
    if (key === 'orders-draft' || key === 'orders-history'
        || key === 'orders-cancelled' || key === 'orders-void') {
      return target['orders'];
    }
    // 财务中心 sub-tab 复用父 tab 的列定义
    const subTabMap: Record<string, string> = {
      'receiveds-pending': 'receiveds',
      'customer-refunds-pending': 'customer-refunds',
      'payments-pending': 'payments',
      'supplier-refunds-pending': 'supplier-refunds',
      'profits-unfinished': 'profits',
      'profits-overdue': 'profits',
      'profits-lowprofit': 'profits',
      // 配载中心 view tabs (复用 shipments 列)
      'shipments-query': 'shipments',
      'shipments-today': 'shipments',
      'shipments-pickup-today': 'shipments',
      'shipments-pickup-week': 'shipments',
      'shipments-intransit': 'shipments',
      'shipments-exception': 'shipments',
      'shipments-delivered-today': 'shipments',
      'stowages-exception': 'stowages',
      // 制单中心 view tabs (复用 orders 列)
      'orders-queue': 'orders',
      'orders-import': 'orders',
      'orders-batch-print': 'orders',
      'orders-batch-track': 'orders',
      'orders-update-tracking': 'orders',
      'orders-update-weight': 'orders',
      'orders-change-customer': 'orders',
      'orders-batch-charge': 'orders',
      // 核算中心 view tabs (复用 charges/costs 列)
      'charges-history': 'charges',
      'charges-pending': 'charges',
      'charges-pending-return': 'charges',
      'charges-pending-reparation': 'charges',
      'charges-import': 'charges',
      'costs-pending': 'costs',
      'costs-estimate': 'costs',
      'costs-recent': 'costs',
      'costs-history': 'costs',
      'costs-import': 'costs',
      'costs-transit': 'costs',
      'costs-zhonggang': 'costs',
      'costs-air': 'costs',
      // 客服中心 问题件细分 (复用 asks 列)
      'asks-customer': 'asks',
      'asks-supplier': 'asks',
      'asks-processing': 'asks',
      'asks-pending': 'asks',
      'asks-new': 'asks',
      'asks-history': 'asks',
      // 客服中心 赔偿细分 (复用 reparations 列)
      'reparations-apply': 'reparations',
      'reparations-pending': 'reparations',
      'reparations-history': 'reparations',
    };
    if (subTabMap[key]) return target[subTabMap[key]];
    return target[key];
  },
  set(target, key: string, value: any) { target[key] = value; return true; },
  has(target, key: string) {
    if (key === 'orders-draft' || key === 'orders-history'
        || key === 'orders-cancelled' || key === 'orders-void') {
      return 'orders' in target;
    }
    const subTabMap: Record<string, string> = {
      'receiveds-pending': 'receiveds',
      'customer-refunds-pending': 'customer-refunds',
      'payments-pending': 'payments',
      'supplier-refunds-pending': 'supplier-refunds',
      'profits-unfinished': 'profits',
      'profits-overdue': 'profits',
      'profits-lowprofit': 'profits',
      // 配载中心 view tabs (复用 shipments 列)
      'shipments-query': 'shipments',
      'shipments-today': 'shipments',
      'shipments-pickup-today': 'shipments',
      'shipments-pickup-week': 'shipments',
      'shipments-intransit': 'shipments',
      'shipments-exception': 'shipments',
      'shipments-delivered-today': 'shipments',
      'stowages-exception': 'stowages',
      // 制单中心 view tabs (复用 orders 列)
      'orders-queue': 'orders',
      'orders-import': 'orders',
      'orders-batch-print': 'orders',
      'orders-batch-track': 'orders',
      'orders-update-tracking': 'orders',
      'orders-update-weight': 'orders',
      'orders-change-customer': 'orders',
      'orders-batch-charge': 'orders',
      // 核算中心 view tabs (复用 charges/costs 列)
      'charges-history': 'charges',
      'charges-pending': 'charges',
      'charges-pending-return': 'charges',
      'charges-pending-reparation': 'charges',
      'charges-import': 'charges',
      'costs-pending': 'costs',
      'costs-estimate': 'costs',
      'costs-recent': 'costs',
      'costs-history': 'costs',
      'costs-import': 'costs',
      'costs-transit': 'costs',
      'costs-zhonggang': 'costs',
      'costs-air': 'costs',
      // 客服中心 问题件细分 (复用 asks 列)
      'asks-customer': 'asks',
      'asks-supplier': 'asks',
      'asks-processing': 'asks',
      'asks-pending': 'asks',
      'asks-new': 'asks',
      'asks-history': 'asks',
      // 客服中心 赔偿细分 (复用 reparations 列)
      'reparations-apply': 'reparations',
      'reparations-pending': 'reparations',
      'reparations-history': 'reparations',
    };
    if (subTabMap[key]) return subTabMap[key] in target;
    return key in target;
  }
});

Object.assign(accColumns, {
  'stowage-plans': [
    { key: 'plan_no',            label: '方案号' },
    { key: 'container_code',     label: '柜号' },
    { key: 'status',             label: '状态' },
    { key: 'fitted_count',       label: '装入件数' },
    { key: 'unfitted_count',     label: '未装件数' },
    { key: 'volume_utilization', label: '利用率', fmt: 'percent' },
    { key: 'weight_used_kg',     label: '总重', fmt: 'kg' },
    { key: 'created_at',         label: '创建时间', fmt: 'date' },
  ],
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
  // ACC 配载中心「渠道统计」按 渠道 × 客户 × 日期 聚合
  "shipments-channel-stats": [
    { key: "theDate",       label: "日期", fmt: "date" },
    { key: "channelName",   label: "渠道账号" },
    { key: "customerName",  label: "客户" },
    { key: "shipmentCount", label: "出货单数" },
    { key: "totalPiece",    label: "件数" },
    { key: "totalWeight",   label: "重量(kg)", fmt: "kg" },
    { key: "totalCharge",   label: "运费", fmt: "money" },
    { key: "totalCost",     label: "成本", fmt: "money" },
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
    { key: "etd", label: "预计离开 ETD" },
    { key: "eta", label: "预计到达 ETA" },
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
  "customer-receivables": [
    { key: "code",              label: "客户编码" },
    { key: "name",              label: "名称" },
    { key: "contact_name",      label: "联系人" },
    { key: "salesman_name",     label: "业务员" },
    { key: "currency",          label: "结算币种" },
    { key: "unpaid_amount",     label: "欠款金额", fmt: "money" },
    { key: "estimated_balance", label: "预估结余", fmt: "money" },
    { key: "settlement_method", label: "结算方式" },
    { key: "credit_amount",     label: "授信额度", fmt: "money" },
    { key: "last_payment_at",   label: "最后付款", fmt: "datetime" },
  ],
  // 财务工作台 — 三 bucket 共用列定义
  "fwb-prepay": [
    { key: "order_no",      label: "订单号" },
    { key: "customer_code", label: "客户编码" },
    { key: "customer_name", label: "客户" },
    { key: "amount",        label: "扣款金额", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "status",        label: "状态" },
    { key: "created_at",    label: "扣款时间", fmt: "datetime" },
  ],
  "fwb-pending": [
    { key: "order_no",      label: "订单号" },
    { key: "customer_code", label: "客户编码" },
    { key: "customer_name", label: "客户" },
    { key: "amount",        label: "调整后金额", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "status",        label: "状态" },
    { key: "audit_status",  label: "审核状态" },
    { key: "created_at",    label: "更新时间", fmt: "datetime" },
  ],
  "fwb-invoiced": [
    { key: "invoice_no",    label: "账单号" },
    { key: "order_no",      label: "订单号" },
    { key: "customer_code", label: "客户编码" },
    { key: "customer_name", label: "客户" },
    { key: "amount",        label: "出账金额", fmt: "money" },
    { key: "invoice_status",label: "账单状态" },
    { key: "invoice_paid",  label: "账单已付", fmt: "money" },
    { key: "invoice_unpaid",label: "账单未付", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "settlement_status", label: "charge 结算" },
    { key: "created_at",    label: "时间", fmt: "datetime" },
  ],
  "fwb-needs-verify": [
    { key: "invoice_no",    label: "账单号" },
    { key: "customer_code", label: "客户编码" },
    { key: "customer_name", label: "客户" },
    { key: "total_amount",  label: "账单金额", fmt: "money" },
    { key: "paid_amount",   label: "已付", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "status",        label: "状态" },
    { key: "verify_status", label: "二审" },
    { key: "created_at",    label: "出账时间", fmt: "datetime" },
  ],
  // 核算工作台 — AP 三 bucket 共用列定义
  "swb-cost-pending": [
    { key: "order_no",      label: "订单号" },
    { key: "tracking_no",   label: "运单号" },
    { key: "channel_code",  label: "渠道" },
    { key: "amount",        label: "预估成本", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "status",        label: "状态" },
    { key: "audit_status",  label: "审核" },
    { key: "created_at",    label: "时间", fmt: "datetime" },
  ],
  "swb-pending-pay": [
    { key: "order_no",      label: "订单号" },
    { key: "tracking_no",   label: "运单号" },
    { key: "channel_code",  label: "渠道" },
    { key: "amount",        label: "应付金额", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "status",        label: "状态" },
    { key: "audit_status",  label: "审核" },
    { key: "created_at",    label: "审核时间", fmt: "datetime" },
  ],
  "swb-paid": [
    { key: "order_no",      label: "订单号" },
    { key: "tracking_no",   label: "运单号" },
    { key: "channel_code",  label: "渠道" },
    { key: "amount",        label: "应付金额", fmt: "money" },
    { key: "paid_amount",   label: "已付金额", fmt: "money" },
    { key: "currency",      label: "币种" },
    { key: "settlement_status", label: "结算" },
    { key: "created_at",    label: "付款时间", fmt: "datetime" },
  ],
  "swb-profit": [
    { key: "shipment_no",   label: "运单号" },
    { key: "order_no",      label: "订单号" },
    { key: "customer_code", label: "客户编码" },
    { key: "customer_name", label: "客户" },
    { key: "ar_total",      label: "应收 AR", fmt: "money" },
    { key: "ap_total",      label: "应付 AP", fmt: "money" },
    { key: "profit",        label: "利润",    fmt: "money" },
    { key: "currency",      label: "币种" },
  ],
  "swb-aging": [
    { key: "side",   label: "侧" },
    { key: "bucket", label: "账龄分桶" },
    { key: "currency", label: "币种" },
    { key: "row_count", label: "条数" },
    { key: "unpaid_amount", label: "未付金额", fmt: "money" },
  ],
  "swb-monthly": [
    { key: "month", label: "月份" },
    { key: "currency", label: "币种" },
    { key: "ar", label: "应收 (AR)", fmt: "money" },
    { key: "ap", label: "应付 (AP)", fmt: "money" },
    { key: "profit", label: "利润", fmt: "money" },
    { key: "shipment_count", label: "运单数" },
  ],
  "gl-income": [
    { key: "group", label: "分组" },
    { key: "code", label: "科目代码" },
    { key: "name", label: "科目名" },
    { key: "amount", label: "金额", fmt: "money" },
  ],
  "gl-balance": [
    { key: "group", label: "分组" },
    { key: "code", label: "科目代码" },
    { key: "name", label: "科目名" },
    { key: "balance", label: "余额", fmt: "money" },
  ],
  "gl-cash": [
    { key: "the_date", label: "日期", fmt: "date" },
    { key: "voucher_no", label: "凭证号" },
    { key: "description", label: "摘要" },
    { key: "source_type", label: "来源" },
    { key: "net_flow", label: "净流量", fmt: "money" },
  ],
  "gl-trial": [
    { key: "code", label: "科目代码" },
    { key: "name", label: "科目名" },
    { key: "category", label: "类别" },
    { key: "debit", label: "借方合计", fmt: "money" },
    { key: "credit", label: "贷方合计", fmt: "money" },
  ],
  "approval-pending": [
    { key: "resource", label: "资源" },
    { key: "action", label: "动作" },
    { key: "resource_id", label: "资源 ID" },
    { key: "required_count", label: "需审批级数" },
    { key: "decision_count", label: "已批示数" },
    { key: "requested_by", label: "申请人" },
    { key: "created_at", label: "时间", fmt: "datetime" },
  ],
  "swb-commissions": [
    { key: "employee_code", label: "员工编码" },
    { key: "employee_name", label: "员工" },
    { key: "the_month", label: "月份" },
    { key: "sales_amount", label: "销售额", fmt: "money" },
    { key: "profit_amount", label: "利润", fmt: "money" },
    { key: "amount", label: "提成", fmt: "money" },
    { key: "currency", label: "币种" },
    { key: "audit_status", label: "审核" },
    { key: "status", label: "付款" },
    { key: "created_at", label: "生成时间", fmt: "datetime" },
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
    { key: "poundage", label: "手续费", fmt: "money" },
    { key: "payCurrency", label: "付款币种" },
    { key: "fxRate", label: "汇率" },
    { key: "currency", label: "记账币种" },
    { key: "theDate", label: "日期" },
    { key: "auditName", label: "审核人" },
    { key: "remark", label: "备注" },
  ],
  receiveds: [
    { key: "no", label: "收款单号" },
    { key: "customerName", label: "客户" },
    { key: "bankName", label: "账户" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "poundage", label: "手续费", fmt: "money" },
    { key: "fxRate", label: "汇率" },
    { key: "currency", label: "币种" },
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
    { key: "reparation", label: "赔偿", fmt: "money" },
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
    { key: "transferNo", label: "单号" },
    { key: "fromBank", label: "转出账户" },
    { key: "toBank", label: "转入账户" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "transferOutAmount", label: "汇出金额", fmt: "money" },
    { key: "transferInAmount", label: "到账金额", fmt: "money" },
    { key: "fee", label: "手续费", fmt: "money" },
    { key: "currency", label: "币种" },
    { key: "theDate", label: "日期" },
    { key: "remark", label: "备注" },
    { key: "addName", label: "操作人" },
  ],
  customers: [
    { key: "code", label: "编码" },
    { key: "name", label: "名称" },
    { key: "contacts", label: "联系人" },
    { key: "companyMobile", label: "手机号码" },
    { key: "companyPhone", label: "固定电话" },
    { key: "email", label: "邮箱" },
    { key: "mainProduct", label: "主营产品" },
    { key: "grade", label: "等级" },
    { key: "balance", label: "余额", fmt: "money" },
    { key: "credits", label: "授信额度", fmt: "money" },
    { key: "settlement", label: "结算方式" },
    { key: "settlementType", label: "结算类型" },
    { key: "branch", label: "分公司" },
    { key: "group", label: "分组" },
    { key: "salesman", label: "业务员" },
    { key: "loginNo", label: "登陆号" },
    { key: "invoiceTitle", label: "开票抬头" },
    { key: "invoiceTaxNo", label: "税号" },
  ],
  suppliers: [
    { key: "code", label: "编码" },
    { key: "name", label: "名称" },
    { key: "contacts", label: "联系人" },
    { key: "companyMobile", label: "手机号码" },
    { key: "phone", label: "固定电话" },
    { key: "email", label: "邮箱" },
    { key: "mainProduct", label: "主营产品" },
    { key: "grade", label: "等级" },
    { key: "balance", label: "结余", fmt: "money" },
    { key: "credits", label: "授信额度", fmt: "money" },
    { key: "settlement", label: "结算方式" },
    { key: "settlementType", label: "结算类型" },
    { key: "invoiceTitle", label: "开票抬头" },
    { key: "invoiceTaxNo", label: "税号" },
    { key: "bankInfo", label: "银行信息" },
  ],
  channels: [
    { key: "name", label: "渠道名称" },
    { key: "code", label: "编号" },
    { key: "isOpen", label: "启用", fmt: "bool" },
    { key: "isShipping", label: "出货", fmt: "bool" },
    { key: "hasFuel", label: "燃油", fmt: "bool" },
    { key: "volumeModulus", label: "材积系数" },
    { key: "weightModulus", label: "重量系数" },
    { key: "limitWeight", label: "最大重量" },
    { key: "limitDeclare", label: "最大申报" },
    { key: "splitRatio", label: "分抛比例" },
    { key: "weightMethod", label: "重量类型" },
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
    { key: "tw", label: "繁体名" },
    { key: "hk", label: "粤语名" },
    { key: "code", label: "代码(2位)" },
    { key: "code3", label: "代码(3位)" },
    { key: "phone", label: "区号" },
    { key: "sortOrder", label: "排序" },
    { key: "isOpen", label: "启用", fmt: "bool" },
  ],
  remotes: [
    { key: "country", label: "国家" },
    { key: "logisticsType", label: "物流" },
    { key: "postcode", label: "邮编/范围模式" },
    { key: "zipLow", label: "起始邮编" },
    { key: "zipHigh", label: "终止邮编" },
    { key: "supplierName", label: "渠道" },
    { key: "type", label: "类型" },
  ],
  fuels: [
    { key: "name", label: "名称" },
    { key: "fuelType", label: "类型" },
    { key: "rate", label: "费率(%)" },
    { key: "startDate", label: "开始日期" },
    { key: "endDate", label: "结束日期" },
    { key: "remark", label: "备注" },
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
    { key: "isShow", label: "客户可见", fmt: "bool" },
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
    { key: "sortOrder", label: "排序" },
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
    { key: "isDefault", label: "默认", fmt: "bool" },
    { key: "bankName", label: "开户行" },
    { key: "accountNo", label: "银行账号" },
    { key: "swiftCode", label: "SWIFT 码" },
    { key: "bankAddress", label: "开户行地址" },
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
    { key: "currency", label: "币种" },
    { key: "rate", label: "利率(%)" },
    { key: "cycle", label: "周期" },
    { key: "mode", label: "还款模式" },
    { key: "startDate", label: "起始日" },
    { key: "endDate", label: "终止日" },
    { key: "payDate", label: "下次还款" },
    { key: "remaining", label: "剩余本金", fmt: "money" },
    { key: "forward", label: "前置利息", fmt: "money" },
    { key: "fixedAmount", label: "固定金额", fmt: "money" },
    { key: "dueDate", label: "到期日" },
    { key: "repaymentStatus", label: "还款状态" },
    { key: "repaidAmount", label: "已还", fmt: "money" },
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
    { key: "qq", label: "QQ 号" },
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
  "api-credentials": [
    { key: "access_key", label: "访问密钥" },
    { key: "owner_code", label: "客户编号" },
    { key: "owner_name", label: "客户名称" },
    { key: "call_count", label: "累计调用" },
    { key: "remark", label: "备注" },
    { key: "status", label: "状态" },
    { key: "last_used_at", label: "最近调用" },
    { key: "created_at", label: "创建时间" },
  ],
  "api-call-logs": [
    { key: "created_at", label: "时间" },
    { key: "access_key", label: "访问密钥" },
    { key: "owner_name", label: "客户" },
    { key: "method", label: "方法" },
    { key: "endpoint", label: "端点" },
    { key: "http_status", label: "状态码" },
    { key: "error_code", label: "错误码" },
    { key: "duration_ms", label: "耗时(ms)" },
    { key: "ip", label: "来源 IP" },
  ],
  "webhook-endpoints": [
    { key: "url", label: "回调 URL" },
    { key: "customer_name", label: "客户" },
    { key: "event_types", label: "订阅事件" },
    { key: "active", label: "启用", fmt: "bool" },
    { key: "secret_masked", label: "密钥(隐藏)" },
    { key: "total_events", label: "总投递" },
    { key: "dead_events", label: "死信" },
    { key: "description", label: "备注" },
    { key: "created_at", label: "创建时间" },
  ],
  "webhook-events": [
    { key: "created_at", label: "时间" },
    { key: "event_type", label: "事件" },
    { key: "customer_name", label: "客户" },
    { key: "endpoint_url", label: "回调 URL" },
    { key: "status", label: "状态" },
    { key: "attempt_count", label: "尝试次数" },
    { key: "last_response_code", label: "上次响应码" },
    { key: "next_attempt_at", label: "下次重试" },
    { key: "last_error", label: "错误" },
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
  "sales-prices": [
    { key: "name", label: "价格名称" },
    { key: "service", label: "渠道/服务" },
    { key: "receiveArea", label: "收件区域" },
    { key: "minWeight", label: "最小重量" },
    { key: "maxWeight", label: "最大重量" },
    { key: "zipPrefix", label: "邮编前缀" },
    { key: "priority", label: "优先级" },
    { key: "status", label: "启用", fmt: "bool" },
    { key: "createdAt", label: "创建时间", fmt: "datetime" },
  ],
  "customer-prices": [
    { key: "name", label: "价格名称" },
    { key: "userName", label: "客户" },
    { key: "userLevel", label: "客户等级" },
    { key: "service", label: "渠道/服务" },
    { key: "receiveArea", label: "收件区域" },
    { key: "minWeight", label: "最小重量" },
    { key: "maxWeight", label: "最大重量" },
    { key: "status", label: "启用", fmt: "bool" },
    { key: "createdAt", label: "创建时间", fmt: "datetime" },
  ],
  "published-prices": [
    { key: "name", label: "价格名称" },
    { key: "service", label: "渠道/服务" },
    { key: "receiveArea", label: "收件区域" },
    { key: "minWeight", label: "最小重量" },
    { key: "maxWeight", label: "最大重量" },
    { key: "zipPrefix", label: "邮编前缀" },
    { key: "priority", label: "优先级" },
    { key: "status", label: "启用", fmt: "bool" },
    { key: "createdAt", label: "创建时间", fmt: "datetime" },
  ],
  files: [
    { key: "fileName", label: "文件名" },
    { key: "fileType", label: "类型" },
    { key: "mimeType", label: "MIME 类型" },
    { key: "sizeBytes", label: "大小" },
    { key: "uploaderName", label: "上传人" },
    { key: "status", label: "状态" },
    { key: "auditStatus", label: "审核状态" },
    { key: "createdAt", label: "上传时间", fmt: "datetime" },
    { key: "remark", label: "备注" },
  ],
  "account-transactions": [
    { key: "transactionNo", label: "流水号" },
    { key: "accountId", label: "账户" },
    { key: "customerId", label: "客户" },
    { key: "transactionType", label: "类型" },
    { key: "currency", label: "币种" },
    { key: "amount", label: "金额", fmt: "money" },
    { key: "fee", label: "手续费", fmt: "money" },
    { key: "creditAmount", label: "信用额度", fmt: "money" },
    { key: "paymentTime", label: "支付时间", fmt: "datetime" },
    { key: "remark", label: "备注" },
    { key: "createBy", label: "录入人" },
  ],
  "dws-scans": [
    { key: "itemNumber", label: "箱号" },
    { key: "shipmentNumber", label: "运单号" },
    { key: "action", label: "动作" },
    { key: "status", label: "状态" },
    { key: "weightKg", label: "实重(kg)" },
    { key: "lengthCm", label: "长(cm)" },
    { key: "widthCm", label: "宽(cm)" },
    { key: "heightCm", label: "高(cm)" },
    { key: "volumeWeight", label: "体积重(kg)" },
    { key: "chargeableKg", label: "计费重(kg)" },
    { key: "info", label: "提示" },
    { key: "scannedAt", label: "扫描时间", fmt: "datetime" },
  ],
  "dws-discrepancies": [
    { key: "itemNumber", label: "箱号" },
    { key: "shipmentNumber", label: "运单号" },
    { key: "customerName", label: "客户" },
    { key: "destinationCountry", label: "国家" },
    { key: "zone", label: "分区" },
    { key: "expectedWeight", label: "预报重(kg)" },
    { key: "dwsWeight", label: "DWS 实测(kg)" },
    { key: "diff", label: "差值(kg)" },
    { key: "dwsChargeable", label: "计费重(kg)" },
    { key: "scannedAt", label: "扫描时间", fmt: "datetime" },
  ],
  "importer-templates": [
    { key: "name", label: "模板名称" },
    { key: "country", label: "国家" },
    { key: "taxId", label: "税号" },
    { key: "address", label: "地址" },
    { key: "contactName", label: "联系人" },
    { key: "contactPhone", label: "电话" },
    { key: "customerName", label: "绑定客户" },
    { key: "createdAt", label: "创建时间", fmt: "datetime" },
  ],
  "inbound-parcels": [
    { key: "parcelNo", label: "箱号" },
    { key: "waybillNo", label: "运单号" },
    { key: "trackingNo", label: "客户单号" },
    { key: "customerName", label: "客户" },
    { key: "channelName", label: "渠道" },
    { key: "expectedWeight", label: "预报重(kg)" },
    { key: "actualWeight", label: "实测重(kg)" },
    { key: "chargeableKg", label: "计费重(kg)" },
    { key: "destinationCountry", label: "国家" },
    { key: "zone", label: "分区" },
    { key: "rateAmount", label: "应收(¥)", fmt: "money" },
    { key: "status", label: "状态" },
    { key: "receivedAt", label: "收货时间", fmt: "datetime" },
  ],
});

const moduleCards = [
  { icon: WalletCards, title: "费率引擎", desc: "规则版本、低消、分抛、燃油、附加费叠加/取大", status: "P0" },
  { icon: ReceiptText, title: "应收账单", desc: "客户模板、币种、账单版本、门户下载与核销", status: "P0" },
  { icon: FileSpreadsheet, title: "成本对账", desc: "渠道账单映射、子单匹配、差异队列和申诉状态", status: "P0" },
  { icon: ShieldCheck, title: "不可变账本", desc: "复式分录、冲销调整、已过账不可修改", status: "P0" },
  { icon: AlertTriangle, title: "风险预警", desc: "卡派转快递前提示超长/超重和预计附加费", status: "P1" },
  { icon: Building2, title: "分公司管理", desc: "组织架构、分公司业绩、利润按分公司拆分", status: "P0" },
];

// ═══════════════ Form Schemas ═══════════════

const readOnlyTabs = new Set(['profits', 'void-orders', 'sales-prices', 'customer-prices', 'published-prices',
  'account-transactions', 'profits-unfinished', 'profits-overdue', 'profits-lowprofit',
  'receiveds-pending', 'customer-refunds-pending', 'payments-pending', 'supplier-refunds-pending',
  'dws-scans', 'dws-discrepancies',
  // 配载中心 view tab 都是只读
  'shipments-query', 'shipments-today',
  'shipments-pickup-today', 'shipments-pickup-week',
  'shipments-intransit', 'shipments-exception', 'shipments-delivered-today',
  'shipments-channel-stats', 'stowages-exception',
  // 制单工具类多为只读视图
  'orders-queue', 'orders-batch-print', 'orders-update-tracking', 'orders-update-weight',
  'orders-change-customer', 'orders-batch-charge',
  // 核算 view 子页只读
  'charges-history', 'charges-pending', 'charges-pending-return', 'charges-pending-reparation',
  'costs-pending', 'costs-estimate', 'costs-recent', 'costs-history',
  'costs-transit', 'costs-zhonggang', 'costs-air',
  // 核算工作台 view tab 只读
  'swb-cost-pending', 'swb-pending-pay', 'swb-paid', 'swb-profit',
  'swb-aging', 'swb-monthly', 'swb-commissions',
  // GL 报表 + 审批待办 全只读
  'gl-income', 'gl-balance', 'gl-cash', 'gl-trial', 'approval-pending',
  // 问题件/赔偿子页只读
  'asks-customer', 'asks-supplier', 'asks-processing', 'asks-pending', 'asks-history',
  'reparations-pending', 'reparations-history',
  // API 调用日志 + webhook 事件只读
  'api-call-logs', 'webhook-events',
  // alair: 应收款项目（只读视图）
  'customer-receivables',
  // 财务工作台 3 视图都只读
  'fwb-prepay', 'fwb-pending', 'fwb-invoiced', 'fwb-needs-verify']);

const settlementOpts = [{ v: 0, l: '不限' }, { v: 1, l: '货到付款' }, { v: 2, l: '日结' }, { v: 3, l: '周结' }, { v: 4, l: '半月结' }, { v: 5, l: '月结' }, { v: 6, l: '自定义' }];

const accFormFields: Record<string, FormField[]> = {
  orders: [
    { col: '__sec_basic', label: '基本信息', type: 'section' },
    { col: 'TheDate', label: '日期', type: 'date', required: true },
    { col: 'Customer', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'No', label: '运单号 (客户单号)', type: 'text', required: true },
    { col: 'TrackNo', label: '转单号 (服务商单号)', type: 'text' },
    { col: '__sec_product', label: '产品/分类', type: 'section' },
    { col: 'Country', label: '目的地', type: 'select', ref: 'countries', required: true },
    { col: 'ItemType', label: '快件类型', type: 'radio', required: true, opts: [
      { v: 'DOCUMENT', l: '文件' }, { v: 'GENERAL', l: '普货' },
      { v: 'SENSITIVE', l: '敏感货' }, { v: 'LIQUID', l: '液体' }, { v: 'POWDER', l: '粉末' }
    ] },
    { col: 'Product', label: '销售产品', type: 'select', ref: 'products', required: true },
    { col: 'Channel', label: '渠道', type: 'select', ref: 'channels' },
    { col: 'DeparturePort', label: '出发港', type: 'select', ref: 'ports' },
    { col: 'ArrivalPort', label: '抵达港', type: 'select', ref: 'ports' },
    { col: 'BatteryType', label: '电池选项', type: 'radio', required: true, opts: [
      { v: 'NONE', l: '无电池' }, { v: 'PURE', l: '纯电池' },
      { v: 'BUILT_IN', l: '内置电池' }, { v: 'MATCH', l: '配套电池' }
    ] },
    { col: 'SpecialType', label: '特殊货物', type: 'radio', required: true, opts: [
      { v: 'STANDARD', l: '标准' }, { v: 'CHEMICAL', l: '化工品' },
      { v: 'LIQUID', l: '液体' }, { v: 'MAGNETIC', l: '磁性物品' }
    ] },
    { col: '__sec_declare', label: '申报信息', type: 'section' },
    { col: 'MaterialsEn', label: '申报品名(EN)', type: 'text' },
    { col: 'DeclaredValue', label: '申报价值', type: 'number', required: true },
    { col: '__sec_recv', label: '收件/保险', type: 'section' },
    { col: 'IsInsurance', label: '购买保险', type: 'boolean' },
    { col: 'Postcode', label: '邮编', type: 'text' },
    { col: 'Branch', label: '分公司', type: 'select', ref: 'branches' },
    { col: '__sec_weight', label: '计重明细', type: 'section' },
    { col: 'Piece', label: '件数 (Total)', type: 'number' },
    { col: 'Weight', label: '实重 kg (Total)', type: 'number' },
    { col: 'ChargeWeight', label: '计费重 kg (自动计算)', type: 'number' },
    { col: 'Volume', label: '体积 m³ (自动计算)', type: 'number' },
    { col: 'IsRemote', label: '远程地区 (Postcode 自动)', type: 'boolean' },
    { col: '__sec_fee', label: '收费明细', type: 'section' },
    { col: 'SurchargeIds', label: '附加费 (逗号分隔 ID)', type: 'text' },
    { col: '__sec_cartons', label: '货箱明细', type: 'section' },
    { col: 'CartonsRows', label: '货箱明细 (每行: 件,重kg,长cm,宽cm,高cm,追踪号)', type: 'textarea' },
    { col: '__sec_recv2', label: '收件人信息', type: 'section' },
    { col: 'RecipientConsignee', label: '收件人姓名', type: 'text' },
    { col: 'RecipientCompany',   label: '收件人公司', type: 'text' },
    { col: 'RecipientPhone',     label: '收件人电话', type: 'text' },
    { col: 'RecipientEmail',     label: '收件人邮箱', type: 'text' },
    { col: 'RecipientAddress',   label: '收件人地址', type: 'text' },
    { col: 'RecipientCity',      label: '收件人城市', type: 'text' },
    { col: 'RecipientProvince',  label: '收件人州/省', type: 'text' },
    { col: 'RecipientHouseNo',   label: '门牌号', type: 'text' },
    { col: 'AmazonRef',          label: '亚马逊参考号', type: 'text' },
    { col: 'TaxNo',              label: '收件人税号', type: 'text' },
    { col: 'IsCustoms',          label: '清关货物', type: 'boolean' },
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
    { col: 'isShow', label: '客户可见', type: 'boolean' },
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
    { col: 'BankName', label: '开户银行', type: 'text', required: true },
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
    { col: 'Cycle', label: '周期', type: 'select', opts: [{ v: 'WEEK', l: '按周' }, { v: 'MONTH', l: '按月' }, { v: 'QUARTER', l: '按季' }, { v: 'YEAR', l: '按年' }] },
    { col: 'Mode', label: '还款模式', type: 'select', opts: [{ v: 'EQUAL_PRINCIPAL', l: '等额本金' }, { v: 'EQUAL_INSTALLMENT', l: '等额本息' }, { v: 'AT_MATURITY', l: '到期一次' }] },
    { col: 'StartDate', label: '起始日', type: 'date' },
    { col: 'EndDate', label: '终止日', type: 'date' },
    { col: 'PayDate', label: '下次还款日', type: 'date' },
    { col: 'Remaining', label: '剩余本金', type: 'number' },
    { col: 'Forward', label: '前置利息', type: 'number' },
    { col: 'Repayment', label: '还款设置', type: 'text' },
    { col: 'FixedAmount', label: '固定金额', type: 'number' },
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
    { col: 'Contacts', label: '联系人', type: 'text' },
    { col: 'CompanyMobile', label: '手机号码', type: 'text' },
    { col: 'CompanyPhone', label: '固定电话', type: 'text' },
    { col: 'Fax', label: '传真', type: 'text' },
    { col: 'Email', label: '邮箱', type: 'text' },
    { col: 'QQ', label: 'QQ', type: 'text' },
    { col: 'MainProduct', label: '主营产品', type: 'text' },
    { col: 'CompanyAddress', label: '公司地址', type: 'textarea' },
    { col: 'Grade', label: '等级', type: 'text' },
    { col: 'Group', label: '分组', type: 'select', ref: 'customer-groups' },
    { col: 'Branch', label: '分公司', type: 'select', ref: 'branches' },
    { col: 'Settlement', label: '结算方式', type: 'select', opts: settlementOpts },
    { col: 'SettlementType', label: '结算类型', type: 'text' },
    { col: 'DateType', label: '结算条件', type: 'text' },
    { col: 'FormulaDate', label: '结算公式日期', type: 'text' },
    { col: 'FormulaBill', label: '结算公式', type: 'text' },
    { col: 'FormulaType', label: '结算公式类型', type: 'text' },
    { col: 'Credits', label: '授信额度', type: 'number' },
    // ACC 7 档阶梯金额
    { col: 'Amount1', label: '阶梯金额 1', type: 'number' },
    { col: 'Amount2', label: '阶梯金额 2', type: 'number' },
    { col: 'Amount3', label: '阶梯金额 3', type: 'number' },
    { col: 'Amount4', label: '阶梯金额 4', type: 'number' },
    { col: 'Amount5', label: '阶梯金额 5', type: 'number' },
    { col: 'Amount6', label: '阶梯金额 6', type: 'number' },
    { col: 'Amount7', label: '阶梯金额 7', type: 'number' },
    // ACC 发票信息
    { col: 'InvoiceTitle', label: '开票抬头', type: 'text' },
    { col: 'InvoiceTaxNo', label: '税号', type: 'text' },
    { col: 'InvoiceAddress', label: '开票地址', type: 'text' },
    { col: 'LoginNo', label: '登陆号', type: 'text' },
    { col: 'ApiKey', label: 'API Key', type: 'text' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  'customer-groups': [
    { col: 'Name', label: '分组名称', type: 'text', required: true },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  suppliers: [
    { col: 'Name', label: '名称', type: 'text', required: true },
    { col: 'Code', label: '编码', type: 'text' },
    { col: 'Contacts', label: '联系人', type: 'text' },
    { col: 'CompanyMobile', label: '手机号码', type: 'text' },
    { col: 'Phone', label: '固定电话', type: 'text' },
    { col: 'Fax', label: '传真', type: 'text' },
    { col: 'Email', label: '邮箱', type: 'text' },
    { col: 'QQ', label: 'QQ', type: 'text' },
    { col: 'MainProduct', label: '主营产品', type: 'text' },
    { col: 'CompanyAddress', label: '公司地址', type: 'textarea' },
    { col: 'Grade', label: '等级', type: 'text' },
    { col: 'Settlement', label: '结算币种', type: 'select', opts: settlementOpts },
    { col: 'SettlementType', label: '结算类型', type: 'text' },
    { col: 'DateType', label: '结算条件', type: 'text' },
    { col: 'FormulaDate', label: '结算公式日期', type: 'text' },
    { col: 'FormulaBill', label: '结算公式', type: 'text' },
    { col: 'FormulaType', label: '结算公式类型', type: 'text' },
    { col: 'Credits', label: '授信额度', type: 'number' },
    // 7 档阶梯
    { col: 'Amount1', label: '阶梯金额 1', type: 'number' },
    { col: 'Amount2', label: '阶梯金额 2', type: 'number' },
    { col: 'Amount3', label: '阶梯金额 3', type: 'number' },
    { col: 'Amount4', label: '阶梯金额 4', type: 'number' },
    { col: 'Amount5', label: '阶梯金额 5', type: 'number' },
    { col: 'Amount6', label: '阶梯金额 6', type: 'number' },
    { col: 'Amount7', label: '阶梯金额 7', type: 'number' },
    { col: 'InvoiceTitle', label: '开票抬头', type: 'text' },
    { col: 'InvoiceTaxNo', label: '税号', type: 'text' },
    { col: 'BankInfo', label: '银行信息', type: 'textarea' },
    { col: 'Remark', label: '备注', type: 'textarea' },
  ],
  channels: [
    { col: 'Name', label: '渠道名称', type: 'text', required: true },
    { col: 'Code', label: '编号', type: 'text' },
    { col: 'isOpen', label: '启用', type: 'boolean' },
    { col: 'IsShipping', label: '出货功能', type: 'boolean' },
    { col: 'HasFuel', label: '燃油/挂号费', type: 'boolean' },
    { col: 'FuelRequired', label: '强制燃油', type: 'boolean' },
    // ACC 产品配置参数
    { col: 'VolumeModulus', label: '材积值/换算系数', type: 'number' },
    { col: 'WeightModulus', label: '重量相乘系数', type: 'number' },
    { col: 'LimitDeclare', label: '最大申报', type: 'number' },
    { col: 'LimitWeight', label: '最大重量(kg)', type: 'number' },
    { col: 'LimitVolume', label: '最大材积(m³)', type: 'number' },
    { col: 'MinWeightTotal', label: '最低重量(kg)', type: 'number' },
    { col: 'MaxWeightWarn', label: '超重值(kg)', type: 'number' },
    { col: 'MaxLengthWarn', label: '超长值(cm)', type: 'number' },
    { col: 'LimitItemWeight', label: '单件限重(kg)', type: 'number' },
    { col: 'MinItemWeight', label: '单件最低计重(kg)', type: 'number' },
    { col: 'WeightCeilUnit', label: '重量取整单位', type: 'number' },
    { col: 'SplitRatio', label: '分抛比例', type: 'number' },
    { col: 'MinSplit', label: '最低分抛实重', type: 'number' },
    { col: 'WeightMethod', label: '重量/材积类型', type: 'text' },
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
    { col: 'TW', label: '繁体名', type: 'text' },
    { col: 'HK', label: '粤语名', type: 'text' },
    { col: 'Code', label: '代码(2位)', type: 'text' },
    { col: 'Code3', label: '代码(3位)', type: 'text' },
    { col: 'Phone', label: '国家区号', type: 'text' },
    { col: 'SortOrder', label: '排序', type: 'number' },
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
    { col: 'Country', label: '国家', type: 'select', ref: 'countries' },
    { col: 'LogisticsType', label: '物流', type: 'text' },
    { col: 'Postcode', label: '邮编模式', type: 'text' },
    { col: 'ZipLow', label: '起始邮编', type: 'text' },
    { col: 'ZipHigh', label: '终止邮编', type: 'text' },
    { col: 'Supplier', label: '渠道', type: 'select', ref: 'channels' },
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
  'api-credentials': [
    { col: 'ownerId', label: '客户', type: 'select', ref: 'customers', required: true },
    { col: 'remark', label: '备注', type: 'textarea' },
    { col: 'expiresAt', label: '过期时间(ISO)', type: 'text' },
  ],
  'webhook-endpoints': [
    { col: 'url', label: '回调 URL', type: 'text', required: true },
    { col: 'customerId', label: '客户 ID（留空=全租户）', type: 'text' },
    { col: 'description', label: '备注', type: 'textarea' },
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
  files: [
    { col: 'fileName', label: '文件名', type: 'text', required: true },
    { col: 'fileType', label: '类型', type: 'text' },
    { col: 'mimeType', label: 'MIME 类型', type: 'text' },
    { col: 'sizeBytes', label: '大小(字节)', type: 'number' },
    { col: 'storageUrl', label: '存储路径/URL', type: 'text' },
    { col: 'uploaderName', label: '上传人', type: 'text' },
    { col: 'status', label: '状态', type: 'text' },
    { col: 'remark', label: '备注', type: 'textarea' },
  ],
  'inbound-parcels': [
    { col: 'parcelNo', label: '箱号', type: 'text', required: true },
    { col: 'waybillNo', label: '运单号', type: 'text' },
    { col: 'trackingNo', label: '客户单号', type: 'text' },
    { col: 'customerId', label: '客户', type: 'select', ref: 'customers' },
    { col: 'channelId', label: '渠道', type: 'select', ref: 'channels' },
    { col: 'expectedWeight', label: '预报重量(kg)', type: 'number' },
    { col: 'destinationCountry', label: '目的国(2字)', type: 'text' },
    { col: 'destinationPostalCode', label: '邮编', type: 'text' },
    { col: 'zone', label: '分区', type: 'text' },
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
  // 制单表单缺失的 3 个下拉 — accountName/code 全 camelCase (后端 project() 已转换)
  'channel-accounts': { api: 'channel-accounts', nameField: 'accountName' },
  warehouses: { api: 'warehouses', nameField: 'name' },
  'importer-templates': { api: 'importer-templates', nameField: 'name' },
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
    // 订单状态
    DRAFT: "草稿",
    SUBMITTED: "已提交",
    ACCEPTED: "已受理",
    FULFILLING: "履约中",
    COMPLETED: "已完成",
    CANCELLED: "已取消",
    // 运单/物流状态
    CREATED: "已建单",
    ORDERED: "已下单",
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
    // 财务/审核通用状态
    PENDING: "待处理",
    PROCESSING: "处理中",
    AUDITED: "已审核",
    UNAUDITED: "未审核",
    APPROVED: "已通过",
    REJECTED: "已拒绝",
    PAID: "已付清",
    PARTIAL: "部分付",
    UNSETTLED: "未结算",
    SETTLED: "已结算",
    ESTIMATED: "已估算",
    LOCKED: "已锁定",
    ADJUSTED: "已调整",
    // 通用方向 / 类型
    AR: "应收",
    AP: "应付",
    PREPAY: "预扣",
    PAYMENT: "付款",
    REFUND: "退款",
    ADJUST: "调整",
    REBATE: "返利",
    FINE: "罚款",
    COMPENSATE: "赔偿",
    DEBIT: "出账",
    CREDIT: "入账",
    CUSTOMER: "客户",
    SUPPLIER: "供应商",
    PARTNER: "合作伙伴",
    TENANT: "租户",
    DOCUMENT: "文件",
    PARCEL: "包裹",
    // 仓储/分支
    DOMESTIC: "国内仓",
    OVERSEAS: "海外仓",
    TRANSIT: "中转仓",
    VIRTUAL: "虚拟仓",
    ACTIVE: "启用",
    DISABLED: "停用",
    ARCHIVED: "已归档",
  };
  const code = String(status ?? "").toUpperCase();
  return labels[code] ?? (code || "-");
}
// 列里直接做替换：把 row.status 类英文枚举映射成中文
const zhStatus = trackingStatusLabel;

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
  if (format === "kg") return fmt(Number(value)) + " kg";
  if (format === "percent") {
    const n = Number(value);
    return Number.isFinite(n) ? (n * 100).toFixed(2) + "%" : "-";
  }
  if (format === "bool") return value ? "是" : "否";
  if (format === "date" && typeof value === "string") return value.slice(0, 19).replace("T", " ");
  return String(value);
}

// 按币种符号格式化金额（CNY=¥ USD=$ EUR=€ GBP=£ JPY=¥ HKD=HK$ AUD=A$ CAD=C$ 其它=原符号）
function fmtMoney(value: any, currency?: string): string {
  if (value === null || value === undefined) return "-";
  const n = Number(value);
  const cur = (currency ?? "CNY").trim().toUpperCase();
  const symbols: Record<string, string> = {
    CNY: "¥", USD: "$", EUR: "€", GBP: "£", JPY: "¥",
    HKD: "HK$", AUD: "A$", CAD: "C$", SGD: "S$",
  };
  const sym = symbols[cur] ?? (cur + " ");
  return sym + fmt(n);
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
    items.push({ tone: "blue", title: "单一 ACC 体系", desc: "已合并为单套 ACC，制单/收货/财务统一在新平台运行。" });
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

const noDateTabs = new Set(["channels", "acc-branches", "departments", "countries", "fuels", "currencies", "fees", "fee-types", "banks", "ports", "warehouses", "customer-groups", "zones", "stowage-categories", "stowage-steps", "tracks", "expense-categories", "fee-item-types", "bank-names", "logistics-interfaces", "potentials", "sold-tos", "notices", "social-persons", "fund-persons", "commission-rules", "districts", "tasks", "templates", "sales-prices", "customer-prices", "published-prices", "files", "api-credentials"]);
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
  // 按 tab 搜索字段下拉，映射到对应 param
  if (accKeyword.value) {
    const fields = currentTabSearchFields.value;
    const fieldKey = currentSearchField.value;
    if (fields && fieldKey !== 'keyword') {
      const opt = fields.find(o => o.v === fieldKey);
      const v = accKeyword.value.trim();
      if (opt && (opt.v.endsWith('From') || ['weight','declaredValue','chargeWeight','fee'].includes(opt.v))) {
        const [from, to] = v.split('-').map(s => s.trim());
        const paramFrom = opt.param;
        const paramTo = opt.param.replace('From', 'To');
        if (from) params.set(paramFrom, from);
        if (to) params.set(paramTo, to);
      } else if (opt) {
        params.set(opt.param, v);
      }
    } else {
      params.set("keyword", accKeyword.value);
    }
  }
  if (accDateFrom.value && !noDateTabs.has(accTab.value)) params.set("dateFrom", accDateFrom.value);
  if (accDateTo.value && !noDateTabs.has(accTab.value)) params.set("dateTo", accDateTo.value);
  // 高级多条件筛选 — 全部 tab 都注入相应参数；后端各 list endpoint 选择性支持
  {
    const setIfArr = (k: string, arr: string[]) => { if (arr.length) params.set(k, arr.join(',')); };
    const setIfStr = (k: string, v: string) => { if (v && v.trim()) params.set(k, v.trim()); };
    setIfArr('customerIds',        advFilters.customers);
    setIfArr('channelCodes',       advFilters.channels);
    setIfArr('countries',          advFilters.countries);
    setIfArr('statuses',           advFilters.statuses);
    setIfArr('auditStatuses',      advFilters.auditStatuses);
    setIfArr('addNames',           advFilters.addNames);
    setIfArr('branchIds',          advFilters.branches);
    setIfArr('sellerIds',          advFilters.sellers);
    setIfArr('servicerIds',        advFilters.servicers);
    setIfArr('warehouseCodes',     advFilters.warehouses);
    setIfArr('carriers',           advFilters.carriers);
    setIfArr('currencies',         advFilters.currencies);
    setIfArr('sides',              advFilters.sides);
    setIfArr('settlementStatuses', advFilters.settlementStatuses);
    setIfStr('postcode',           advFilters.postcode);
    setIfStr('trackingNo',         advFilters.trackingNo);
    setIfStr('recipientName',      advFilters.recipientName);
    setIfStr('vatNo',              advFilters.vatNo);
    setIfStr('poNumber',           advFilters.poNumber);
    setIfStr('amazonRef',          advFilters.amazonRef);
    setIfStr('binLocation',        advFilters.binLocation);
    setIfStr('mainItem',           advFilters.mainItem);
    setIfStr('createdFrom',        advFilters.createdFrom);
    setIfStr('createdTo',          advFilters.createdTo);
    setIfStr('submittedFrom',      advFilters.submittedFrom);
    setIfStr('submittedTo',        advFilters.submittedTo);
    setIfStr('deliveredFrom',      advFilters.deliveredFrom);
    setIfStr('deliveredTo',        advFilters.deliveredTo);
    setIfStr('amountFrom',         advFilters.amountFrom);
    setIfStr('amountTo',           advFilters.amountTo);
    if (missingCostOnly.value && ordersTabSet.has(accTab.value)) params.set('missingCost', 'true');
  }
  // 状态 sub-tab → status 参数；profits-* sub-tab → mode 参数（后端不同）
  if ((tab as any).statusFilter) {
    const paramName = (tab.api === "profits") ? "mode" : "status";
    params.set(paramName, (tab as any).statusFilter);
  }

  try {
    const res = await apiFetch(`${API}/api/acc/${tab.api}?${params}`);
    const json = await res.json();
    if (Array.isArray(json)) {
      accData.value = json;
      accTotal.value = json.length;
      accAggregations.value = null;
    } else {
      accData.value = json.data ?? [];
      accTotal.value = json.total ?? accData.value.length;
      accAggregations.value = json.aggregations ?? null;
    }
  } catch (e: any) {
    accData.value = [];
    accTotal.value = 0;
    accAggregations.value = null;
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
  // 批量页面预加载客户下拉
  if (accTab.value === 'orders-change-customer') {
    loadSelectOptions([{ type: 'select', ref: 'customers' } as any]);
  }
  // 财务工作台 — 预扣明细页 预加载客户下拉 + 重置选中
  if (accTab.value === 'fwb-prepay') {
    loadSelectOptions([{ type: 'select', ref: 'customers' } as any]);
    fwbBalance.value = null;
  }
  // 财务工作台 — 所有 fwb tab 都加载 dashboard 汇总
  if (accTab.value.startsWith('fwb-')) {
    fetchFwbDashboard();
  }
  // 核算工作台 — 所有 swb tab 都加载利润 dashboard + 付款账户
  if (accTab.value.startsWith('swb-')) {
    fetchSwbDashboard();
    fetchSwbCompanyAccounts();
  }
  // 批量页面 / 批量打印页不需要拉列表数据
  if (!batchPageSet.has(accTab.value) && accTab.value !== 'orders-batch-print') {
    fetchAccData();
  }
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
        // code 用于下拉 :value (优先 code, 渠道账号用 accountNo)
        code: row.code ?? row.accountNo ?? undefined,
      }));
    } catch {
      selectOptions.value[r] = [];
    }
  }
}

async function openAdd() {
  // 订单 tab 走 ACC 完整制单表单
  if (accTab.value === 'orders') {
    return openFullOrderAdd();
  }
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

// ════════ ACC 完整制单表单（对应 ACC PHP 「添加制单」7 分区）════════
const showFullOrderForm = ref(false);
const fullOrderError = ref('');
const fullOrderSaving = ref(false);
const fullOrderModalBody = ref<HTMLDivElement | null>(null);
function scrollFormTo(id: string) {
  const body = fullOrderModalBody.value; if (!body) return;
  const target = body.querySelector('#' + id) as HTMLElement | null;
  if (target) body.scrollTo({ top: target.offsetTop - 12, behavior: 'smooth' });
}

// 货物信息 → 申报明细 智能联动:
// 用户在「货物信息」填了英文/中文品名/件数/货值, 若申报明细只有 1 行且 name 为空,
// 自动同步过去 — 避免重复输入. 用户改过申报明细就停止同步.
watch(
  () => [fullOrderData.materialsEn, fullOrderData.materialsCn, fullOrderData.piece, fullOrderData.declaredValue],
  ([en, cn, pcs, val]) => {
    if (!showFullOrderForm.value) return;
    const d = fullOrderData.declare;
    if (!Array.isArray(d) || d.length !== 1) return;
    const r = d[0];
    if (r.name && r.name !== en) return; // 用户改过, 不覆盖
    r.name = en || r.name;
    r.cnName = cn || r.cnName;
    if (pcs && pcs > 0) r.quantity = pcs;
    if (val && val > 0 && pcs && pcs > 0) r.price = Number((Number(val) / Number(pcs)).toFixed(2));
  }
);
const emptyParty = () => ({ company: '', name: '', phone: '', province: '', postcode: '', city: '', vat: '', address: '', houseNo: '' });
const emptyDeclareRow = () => ({ name: '', cnName: '', origin: '', quantity: 1, price: 0, hsCode: '', remark: '' });
const emptyPackageRow = () => ({ no: '', weight: 0, name: '', cnName: '', hsCode: '', grossWeight: 0, length: 0, width: 0, height: 0, quantity: 1, price: 0, material: '' });
const fullOrderData = reactive<any>({
  orderNo: '',
  orderDate: new Date().toISOString().slice(0, 10),
  customerId: '',
  product: '',
  channelAccount: '',
  packageType: 'PARCEL',
  batteryType: 0,
  batteryCode: '',     // ACC BatteryCode select
  specialType: 0,
  labelType: 'PDF',
  materialsEn: '',     // ACC MaterialsEN
  materialsCn: '',     // ACC MaterialsCN
  country: '',
  weight: 0,
  piece: 1,
  volume: 0,
  currency: 'USD',
  declaredValue: 0,
  freight: 0,          // ACC Freight - 运费
  insurance: 0,        // ACC Insurance - 保险
  services: [] as string[],
  remark: '',
  receiver: { warehouseCode: '', country: '', areaCode: '', ...emptyParty() },
  shipper: emptyParty(),
  shipTo: { templateId: '', ...emptyParty() },
  declare: [emptyDeclareRow()],
  packageList: [emptyPackageRow()],
});

async function openFullOrderAdd() {
  fullOrderError.value = '';
  // reset
  Object.assign(fullOrderData, {
    orderNo: 'ORD-' + Date.now().toString(36).toUpperCase(),
    orderDate: new Date().toISOString().slice(0, 10),
    customerId: '',
    product: '', channelAccount: '', packageType: 'PARCEL',
    batteryType: 0, batteryCode: '', specialType: 0, labelType: 'PDF',
    materialsEn: '', materialsCn: '',
    country: '', weight: 0, piece: 1, volume: 0,
    currency: 'USD', declaredValue: 0, freight: 0, insurance: 0,
    services: [], remark: '',
    receiver: { warehouseCode: '', country: '', areaCode: '', ...emptyParty() },
    shipper: emptyParty(),
    shipTo: { templateId: '', ...emptyParty() },
    declare: [emptyDeclareRow()],
    packageList: [emptyPackageRow()],
  });
  // 加载所需下拉
  await loadSelectOptions([
    { type: 'select', ref: 'customers' } as any,
    { type: 'select', ref: 'channels' } as any,
    { type: 'select', ref: 'channel-accounts' } as any,
    { type: 'select', ref: 'countries' } as any,
    { type: 'select', ref: 'warehouses' } as any,
    { type: 'select', ref: 'importer-templates' } as any,
  ]);
  showFullOrderForm.value = true;
}

// ACC 进口商模板选择 → 自动回填进口商区字段
function applyImporterTemplate() {
  const id = fullOrderData.shipTo.templateId;
  if (!id) return;
  const tpl = ((selectOptions as any)['importer-templates'] ?? []).find((t: any) => t.id === id);
  if (!tpl) return;
  fullOrderData.shipTo.company = tpl.name ?? fullOrderData.shipTo.company;
  fullOrderData.shipTo.name = tpl.contactName ?? fullOrderData.shipTo.name;
  fullOrderData.shipTo.phone = tpl.contactPhone ?? fullOrderData.shipTo.phone;
  fullOrderData.shipTo.address = tpl.address ?? fullOrderData.shipTo.address;
  fullOrderData.shipTo.vat = tpl.taxId ?? fullOrderData.shipTo.vat;
}

function addDeclareRow() { fullOrderData.declare.push(emptyDeclareRow()); }
function removeDeclareRow(i: number) { fullOrderData.declare.splice(i, 1); }
function addPackageRow() { fullOrderData.packageList.push(emptyPackageRow()); }
function removePackageRow(i: number) { fullOrderData.packageList.splice(i, 1); }

async function saveFullOrder() {
  fullOrderError.value = '';
  if (!fullOrderData.orderNo || !fullOrderData.customerId) {
    fullOrderError.value = '客户单号、客户必填';
    return;
  }
  fullOrderSaving.value = true;
  try {
    // 清掉空 declare / package 行
    const body = {
      ...fullOrderData,
      declare: fullOrderData.declare.filter((r: any) => r.name || r.cnName),
      packageList: fullOrderData.packageList.filter((r: any) => r.no || r.name),
    };
    const res = await apiFetch(`${API}/api/acc/orders/full`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json();
    if (data.error || (!data.id && !data.ok)) {
      fullOrderError.value = data.error || '保存失败';
    } else {
      showFullOrderForm.value = false;
      fetchAccData();
    }
  } catch (e: any) {
    fullOrderError.value = e.message;
  } finally {
    fullOrderSaving.value = false;
  }
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

// 财务通用 form PascalCase → 后端键名映射.
// 通用 form col 用 PascalCase ('No', 'Customer', 'Amount', 'TheDate', 'Bank', 'Supplier' ...),
// 后端 createImpl/updateImpl 读 'no'/'customer_id'/'amount'/'the_date'/'financial_account_id'/'partner_id'.
// 不映射 = 字段全丢, 后端报「单号必填」「请选择客户」.
function normalizeFinanceFormBody(api: string, src: Record<string, any>): Record<string, any> {
  const FINANCE_TXN_APIS = new Set([
    'receiveds','payments','customer-refunds','supplier-refunds',
    'customer-rebates','supplier-rebates','customer-fines','supplier-fines',
    'customer-adjusts','supplier-adjusts','borrowings','transfers','expenses','wages','dividends',
  ]);
  // 兼容 ACC 老字段名 (PascalCase) → 新后端 (camelCase / snake_case)
  const MAP: Record<string, string> = {
    No: 'no', TheDate: 'theDate', Amount: 'amount', Currency: 'currency',
    Remark: 'remark', Reason: 'reason', Poundage: 'poundage', FxRate: 'fxRate',
    PayCurrency: 'payCurrency', Status: 'status', AddName: 'addName',
    Customer: 'customer_id', Supplier: 'partner_id', Partner: 'partner_id',
    Bank: 'financial_account_id', BankIn: 'financial_account_id_in', BankOut: 'financial_account_id_out',
    BankName: 'bank_name', Name: 'name', Code: 'code', Deposit: 'deposit',
    Paid: 'paid', EndDate: 'endDate', Settlement: 'settlement',
  };
  // charges/costs 通用 form 字段语义复杂(Express=shipment, Type=charge_item),
  // 推荐用「+ 添加成本」对话框. 通用 form 一律不动 body, 让后端报错指引用户.
  if (api === 'charges' || api === 'costs') return src;
  if (!FINANCE_TXN_APIS.has(api) && api !== 'charges' && api !== 'costs' && api !== 'banks' && api !== 'currencies') {
    return src; // 非财务 tab, 不动 (订单/物流商等沿用 ACC PascalCase)
  }
  const out: Record<string, any> = {};
  for (const [k, v] of Object.entries(src)) {
    if (v === '' || v === null || v === undefined) continue;
    const mapped = MAP[k] ?? (k[0] === k[0].toLowerCase() ? k : k[0].toLowerCase() + k.slice(1));
    out[mapped] = v;
  }
  return out;
}

async function saveForm() {
  formSaving.value = true;
  formError.value = '';
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  try {
    let url: string;
    let method: string;
    let body: any = formData;
    if (formMode.value === 'add' && tab.key === 'orders') {
      // ACC Express 对齐：制单走 /full 端点，前端 PascalCase formData 翻译为 camelCase
      url = `${API}/api/acc/orders/full`;
      method = 'POST';
      body = {
        orderNo:       (formData as any).No,
        customerId:    (formData as any).Customer,
        customerRef:   (formData as any).TrackNo,
        product:       (formData as any).Product,
        channelAccount:(formData as any).Channel,
        departurePort: (formData as any).DeparturePort,
        arrivalPort:   (formData as any).ArrivalPort,
        country:       (formData as any).Country,
        postcode:      (formData as any).Postcode,
        itemType:      (formData as any).ItemType,
        batteryType:   (formData as any).BatteryType,
        specialType:   (formData as any).SpecialType,
        materialsEn:   (formData as any).MaterialsEn,
        piece:         (formData as any).Piece,
        weight:        (formData as any).Weight,
        volume:        (formData as any).Volume,
        declaredValue: (formData as any).DeclaredValue,
        isInsurance:   !!(formData as any).IsInsurance,
        isRemote:      !!(formData as any).IsRemote,
        surchargeIds:  ((formData as any).SurchargeIds || '').split(',').map((s:string)=>s.trim()).filter(Boolean),
        remark:        (formData as any).Remark,
        receiver: {
          // 同时输出 consignee + name 别名，SubmitValidator 读 name，ACC compat 读 consignee
          consignee: (formData as any).RecipientConsignee || '',
          name:      (formData as any).RecipientConsignee || '',
          company:   (formData as any).RecipientCompany   || '',
          phone:     (formData as any).RecipientPhone     || '',
          email:     (formData as any).RecipientEmail     || '',
          address:   (formData as any).RecipientAddress   || '',
          city:      (formData as any).RecipientCity      || '',
          province:  (formData as any).RecipientProvince  || '',
          state:     (formData as any).RecipientProvince  || '',
          houseNo:   (formData as any).RecipientHouseNo   || '',
          postcode:  (formData as any).Postcode           || '',
          amazonRef: (formData as any).AmazonRef          || '',
          taxNo:     (formData as any).TaxNo              || '',
          isCustoms: !!(formData as any).IsCustoms,
        },
        // 货箱明细：每行 "qty,weight,length,width,height,tracking_no" → packageList[]
        packageList: ((formData as any).CartonsRows || '').split(/\r?\n/)
          .map((line: string) => line.trim())
          .filter((line: string) => line.length > 0)
          .map((line: string, idx: number) => {
            const cols = line.split(',').map(s => s.trim());
            return {
              no:       String(idx + 1),
              piece:    Number(cols[0]) || 1,
              weight:   Number(cols[1]) || 0,
              length:   Number(cols[2]) || 0,
              width:    Number(cols[3]) || 0,
              height:   Number(cols[4]) || 0,
              trackingNo: cols[5] || '',
            };
          }),
      };
    } else {
      url = formMode.value === 'add'
        ? `${API}/api/acc/${tab.api}`
        : `${API}/api/acc/${tab.api}/${editId.value}`;
      method = formMode.value === 'add' ? 'POST' : 'PUT';
      // 财务通用 form 用 PascalCase col 名 (No/Customer/Amount/TheDate ...)
      // 但后端 createImpl 读 camelCase/snake_case (no/customer_id/amount/the_date ...)
      // 这里做最小化映射, 不动模板和后端.
      body = normalizeFinanceFormBody(tab.api, formData);
    }
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const json = await res.json();
    if (json.error) {
      formError.value = json.error;
    } else {
      showForm.value = false;
      fetchAccData();
      if (json.secret) {
        alert(`Access Key: ${json.accessKey}\nSecret: ${json.secret}\n\n${json.warning || '请立即保存，不会再次显示。'}`);
      }
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

// bizAuditTabs 控制 ACC 列表行内的 [审核/反审/历史] 按钮显示。
// 与后端 AuditService.AUDITABLE_ENTITIES 对齐，限定为"业务单据流转"类。
// 主数据/字典类（customers/channels/currencies/bank-names/postcodes 等）后端
// 也可审核但前端不显示按钮——审核策略由运营在后端 API 调用而非每行按钮决定。
const bizAuditTabs = new Set([
  // 订单 / 出货 / 配载（含 ACC 订单状态 5 个 sub-tab）
  'orders', 'orders-draft', 'orders-history', 'orders-cancelled', 'orders-void',
  'shipments', 'packages', 'stowages', 'transits',
  // 财务核心
  'charges', 'costs', 'bills', 'receiveds', 'payments', 'commissions',
  'expenses', 'transfers', 'dividends', 'borrowings', 'wages', 'reparations', 'returns',
  // 财务待审 sub-tab(共享 api 但需要行内审核按钮)
  'receiveds-pending', 'payments-pending',
  'customer-refunds-pending', 'supplier-refunds-pending',
  // 调账 / 罚款 / 退款 / 返利
  'customer-fines', 'supplier-fines', 'customer-adjusts', 'supplier-adjusts',
  'customer-rebates', 'supplier-rebates',
  'customer-refunds', 'supplier-refunds',
  // 资金管理
  'assets', 'funds', 'socials',
  // HR
  'employees', 'attendances', 'social-persons', 'fund-persons',
  // 配置类
  'channel-accounts', 'logistics-interfaces', 'templates',
  // 异常流（2026-05-28 任务8 补：审核通过后触发财务副作用，需 list 行内按钮）
  'collects', 'asks', 'detains', 'dispatches', 'forecasts',
]);

// 批量审核：与 bizAuditTabs 同口径，ACC 原行为也是凡审核处都能批量
const batchAuditTabs = new Set([
  'orders', 'orders-draft', 'orders-history', 'orders-cancelled',   'shipments', 'packages', 'stowages', 'transits',
  'charges', 'costs', 'bills', 'receiveds', 'payments', 'commissions',
  'expenses', 'transfers', 'dividends', 'borrowings', 'wages', 'reparations', 'returns',
  'customer-fines', 'supplier-fines', 'customer-adjusts', 'supplier-adjusts',
  'customer-rebates', 'supplier-rebates', 'customer-refunds', 'supplier-refunds',
  'assets', 'funds', 'socials',
  'employees', 'attendances', 'social-persons', 'fund-persons',
  'channel-accounts', 'logistics-interfaces', 'templates',
  // 2026-05-28 任务8 补
  'collects', 'asks', 'detains', 'dispatches', 'forecasts',
]);
const importTabs = new Set(['orders', 'charges', 'costs']);
const exportTabs = new Set([
  'orders', 'shipments', 'charges', 'costs', 'bills', 'payments', 'receiveds',
  'profits', 'commissions', 'expenses', 'customers', 'suppliers',
  'customer-refunds', 'supplier-refunds', 'customer-rebates', 'supplier-rebates',
  'customer-adjusts', 'supplier-adjusts', 'customer-fines', 'supplier-fines',
  'transfers', 'borrowings', 'account-transactions',
  'orders-draft', 'orders-history', 'orders-cancelled',
  'receiveds-pending', 'customer-refunds-pending', 'payments-pending', 'supplier-refunds-pending',
  'profits-unfinished', 'profits-overdue', 'profits-lowprofit',
]);

const canAudit = computed(() => bizAuditTabs.has(accTab.value));
const canBatchAudit = computed(() => batchAuditTabs.has(accTab.value));

// 批量汇款：仅退款类（acc_finance_txns）支持
const batchRemitTabs = new Set([
  'customer-refunds', 'customer-refunds-pending',
  'supplier-refunds', 'supplier-refunds-pending',
]);
const canBatchRemit = computed(() => batchRemitTabs.has(accTab.value));

// 制单中心订单批量操作可用 tab
const ordersTabSet = new Set(['orders', 'orders-draft', 'orders-history', 'orders-cancelled', 'orders-void']);
const canOrdersBatch = computed(() => ordersTabSet.has(accTab.value));
// 作废订单 tab 用不同的工具栏 + 取消订单只有 彻底删除 一个按钮
const isVoidAuditTab = computed(() => accTab.value === 'orders-void');
const isCancelTab = computed(() => accTab.value === 'orders-cancelled');
// ACC: 14 按钮 ONLY 在 未提交 + 历史制单
const fullToolbarTabs = new Set(['orders-draft', 'orders-history']);
const showFullOrdersToolbar = computed(() => fullToolbarTabs.has(accTab.value));

// ACC 风格通用搜索字段配置（按 tab）
type SearchFieldOpt = { v: string; l: string; param: string; placeholder?: string };
const tabSearchConfig: Record<string, SearchFieldOpt[]> = {
  customers: [
    { v: 'keyword',     l: '关键词',  param: 'keyword' },
    { v: 'code',        l: '编码',    param: 'code' },
    { v: 'name',        l: '名称',    param: 'customerName' },
    { v: 'contact',     l: '联系人',  param: 'contact' },
    { v: 'mobile',      l: '手机',    param: 'mobile' },
    { v: 'email',       l: '邮箱',    param: 'email' },
    { v: 'loginNo',     l: '登陆号',  param: 'loginNo' },
    { v: 'invoiceTaxNo',l: '税号',    param: 'invoiceTaxNo' },
  ],
  suppliers: [
    { v: 'keyword',     l: '关键词',  param: 'keyword' },
    { v: 'code',        l: '编码',    param: 'code' },
    { v: 'name',        l: '名称',    param: 'supplierName' },
    { v: 'contact',     l: '联系人',  param: 'contact' },
    { v: 'mobile',      l: '手机',    param: 'mobile' },
    { v: 'product',     l: '主营产品',param: 'mainProduct' },
  ],
  channels: [
    { v: 'keyword',     l: '关键词',  param: 'keyword' },
    { v: 'name',        l: '名称',    param: 'channelName' },
    { v: 'code',        l: '编号',    param: 'channelCode' },
  ],
  charges: [
    { v: 'keyword',     l: '关键词',  param: 'keyword' },
    { v: 'shipmentNo',  l: '运单号',  param: 'shipmentNo' },
    { v: 'customer',    l: '客户',    param: 'customerName' },
    { v: 'country',     l: '国家',    param: 'country' },
  ],
  costs: [
    { v: 'keyword',     l: '关键词',  param: 'keyword' },
    { v: 'shipmentNo',  l: '运单号',  param: 'shipmentNo' },
    { v: 'supplier',    l: '物流商',  param: 'supplierName' },
  ],
  bills: [
    { v: 'keyword',   l: '账单号', param: 'keyword' },
    { v: 'customer',  l: '客户',   param: 'customerName' },
  ],
  payments: [
    { v: 'keyword',  l: '付款单号', param: 'keyword' },
    { v: 'supplier', l: '物流商',   param: 'supplierName' },
  ],
  receiveds: [
    { v: 'keyword',  l: '收款单号', param: 'keyword' },
    { v: 'customer', l: '客户',     param: 'customerName' },
  ],
};

// ACC 订单列表搜索字段下拉（对应 ACC Online.php 搜索索引 22 项）
const ordersSearchField = ref('keyword');
const ordersSearchFieldOpts: SearchFieldOpt[] = [
  { v: 'keyword',           l: '单号',          param: 'keyword',           placeholder: '运单号 / 客户单号' },
  { v: 'trackingNo',        l: '转单号',        param: 'trackingNo',        placeholder: '渠道转单号' },
  { v: 'customerName',      l: '客户',          param: 'customerName',      placeholder: '客户名称' },
  { v: 'weight',            l: '重量',          param: 'weightFrom',        placeholder: '重量(kg)，单值或区间 1-10' },
  { v: 'channelCode',       l: '渠道',          param: 'channelCode',       placeholder: '渠道代码 如 EU-AIR-UPS' },
  { v: 'declaredValue',     l: '申报价值',      param: 'declaredValueFrom', placeholder: '价值或区间 100-500' },
  { v: 'chargeWeight',      l: '收费重量',      param: 'chargeWeightFrom',  placeholder: '计费重(kg) 或区间' },
  { v: 'fee',               l: '费用',          param: 'feeFrom',           placeholder: '应收金额或区间' },
  { v: 'postcode',          l: '邮编',          param: 'postcode',          placeholder: '收件邮编' },
  { v: 'recipientName',     l: '收件人',        param: 'recipientName' },
  { v: 'country',           l: '国家',          param: 'country',           placeholder: '国家代码 如 US' },
  { v: 'province',          l: '省/洲',         param: 'province' },
  { v: 'city',              l: '城市',          param: 'city' },
  { v: 'recipientAddress',  l: '收件地址',      param: 'recipientAddress' },
  { v: 'recipientHouseNo',  l: '门牌号',        param: 'recipientHouseNo' },
  { v: 'recipientPhone',    l: '联系电话',      param: 'recipientPhone' },
  { v: 'remark',            l: '备注',          param: 'remark' },
  { v: 'deliveryArea',      l: '快件到达地区',  param: 'deliveryArea' },
  { v: 'submittedFrom',     l: '提交日期',      param: 'submittedFrom',     placeholder: 'YYYY-MM-DD 或区间' },
  { v: 'addName',           l: '添加人',        param: 'addName' },
  { v: 'createdFrom',       l: '添加时间',      param: 'createdFrom',       placeholder: 'YYYY-MM-DD 或区间' },
  { v: 'updatedFrom',       l: '修改时间',      param: 'updatedFrom',       placeholder: 'YYYY-MM-DD 或区间' },
];
const ordersSearchPlaceholder = computed(() => {
  const opt = ordersSearchFieldOpts.find(o => o.v === ordersSearchField.value);
  return opt?.placeholder ?? `输入 ${opt?.l ?? ''}`;
});

// 当前 tab 的搜索字段定义（覆盖 tabSearchConfig + ordersTabSet）
const currentTabSearchFields = computed<SearchFieldOpt[] | null>(() => {
  if (ordersTabSet.has(accTab.value)) return ordersSearchFieldOpts;
  return tabSearchConfig[accTab.value] ?? null;
});
const genericSearchField = ref('keyword');
watch(accTab, () => {
  genericSearchField.value = 'keyword';
  ordersSearchField.value = 'keyword';
});
const currentSearchField = computed({
  get: () => ordersTabSet.has(accTab.value) ? ordersSearchField.value : genericSearchField.value,
  set: (v: string) => {
    if (ordersTabSet.has(accTab.value)) ordersSearchField.value = v;
    else genericSearchField.value = v;
  },
});
const currentSearchPlaceholder = computed(() => {
  const fields = currentTabSearchFields.value;
  if (!fields) return '搜索关键词...';
  const opt = fields.find(o => o.v === currentSearchField.value);
  return opt?.placeholder ?? `输入 ${opt?.l ?? ''}`;
});

// ACC 5 个批量操作 sub-tab，需要独立专用页面（对照 ExpressBatch.php 截图）
const batchPageSet = new Set([
  'orders-change-customer', 'orders-update-tracking', 'orders-update-weight',
  'orders-batch-charge', 'orders-batch-track',
]);
const batchPagePanel = computed(() => batchPageSet.has(accTab.value));
// 批量打印独立扫描页
const isBatchPrintPage = computed(() => accTab.value === 'orders-batch-print');
// API 文档独立 iframe 页
const isApiDocsPage = computed(() => accTab.value === 'api-docs');
const apiDocsUrl = '/swagger-ui/index.html';
// 批量打印状态
const printScanInput = ref('');
const printScanRows = ref<any[]>([]);
const printScanCount = ref(0);
const printScanSuccess = ref(0);
async function doScanLookup() {
  if (!printScanInput.value.trim()) return;
  const no = printScanInput.value.trim();
  printScanCount.value += 1;
  bizLoading.value = true;
  try {
    // 用 batch-preview 单条查找
    const res = await apiFetch(`${API}/api/acc/orders/batch-preview`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'track', text: no }),
    });
    const j = await res.json();
    if ((j.rows ?? []).length > 0) {
      const r = j.rows[0];
      const existing = printScanRows.value.find((x: any) => x.id === r.id);
      if (!existing) {
        printScanRows.value.unshift({
          ...r,
          scanNo: no,
          masterNo: r.trackNo,
          pageNo: 1,
        });
        printScanSuccess.value += 1;
        bizMessage.value = `扫描成功: ${r.orderNo}`;
      } else {
        bizMessage.value = `重复扫描: ${no}`;
      }
    } else {
      bizMessage.value = `未找到: ${no}`;
    }
    printScanInput.value = '';
  } catch (e: any) {
    bizMessage.value = '查询失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 4000);
  }
}
function clearScanList() {
  printScanRows.value = [];
  printScanCount.value = 0;
  printScanSuccess.value = 0;
}
async function printScanList(kind: string) {
  if (printScanRows.value.length === 0) { bizMessage.value = '请先扫描快件'; return; }
  bizLoading.value = true;
  try {
    const ids = printScanRows.value.map((r: any) => r.id);
    const res = await apiFetch(`${API}/api/acc/orders/print-${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids }),
    });
    const j = await res.json();
    if (res.ok) {
      const w = window.open('', '_blank');
      if (w) {
        w.document.write(`<html><head><title>${kind}</title></head><body><pre>${JSON.stringify(j, null, 2)}</pre></body></html>`);
      }
      bizMessage.value = `${kind} 准备 ${j.count ?? 0} 份`;
    }
  } catch (e: any) {
    bizMessage.value = '打印失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 4000);
  }
}
watch(() => accTab.value, () => {
  if (accTab.value === 'orders-batch-print') {
    clearScanList();
  }
});

// ACC 批量页面统一状态
const batchInputText = ref('');
const batchPreviewRows = ref<any[]>([]);
const batchPreviewSelected = ref<Set<string>>(new Set());
const batchApplyCurrentTime = ref(false);
const batchOverrideAudited = ref(false);
const batchTargetCustomerId = ref('');

watch(() => accTab.value, () => {
  if (batchPageSet.has(accTab.value)) {
    batchInputText.value = '';
    batchPreviewRows.value = [];
    batchPreviewSelected.value = new Set();
    batchApplyCurrentTime.value = false;
    batchOverrideAudited.value = false;
    batchTargetCustomerId.value = '';
  }
});

const batchPageConfig: Record<string, { title: string; placeholder: string; hint?: string; needCustomer?: boolean; showApplyCurrent?: boolean; showOverride?: boolean; confirmLabel?: string; previewLabel?: string }> = {
  'orders-update-tracking': {
    title: '批量更新转单号',
    placeholder: '示例:\nEX20260601001 1Z999AA10123456784\nEX20260601002 1Z999AA10123456785',
    hint: '格式：运单号+空格+新转单号',
  },
  'orders-update-weight': {
    title: '批量更新计费重',
    placeholder: '示例:\nEX20260601001 12.5\nEX20260601002 8.3',
    hint: '格式：运单号+空格+新计费重(kg)',
    showApplyCurrent: true,
    showOverride: true,
  },
  'orders-change-customer': {
    title: '变更客户',
    placeholder: '每行一个运单号',
    hint: '格式：每行一个运单号',
    needCustomer: true,
    showApplyCurrent: true,
  },
  'orders-batch-charge': {
    title: '批量重新计费',
    placeholder: '每行一个运单号',
    hint: '格式：每行一个运单号',
    showApplyCurrent: true,
    showOverride: true,
  },
  'orders-batch-track': {
    title: '批量追踪快件',
    placeholder: '每行一个单号或转单号',
    hint: '格式：每行一个单号或转单号',
    previewLabel: '追踪预览',
    confirmLabel: '确认追踪',
  },
};

const batchPageMeta = computed(() => batchPageConfig[accTab.value]);

async function doBatchPreview() {
  if (!batchInputText.value.trim()) {
    bizMessage.value = '请先输入单号';
    return;
  }
  const mode = accTab.value.replace('orders-', '');
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/batch-preview`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode, text: batchInputText.value }),
    });
    const j = await res.json();
    batchPreviewRows.value = j.rows ?? [];
    batchPreviewSelected.value = new Set(batchPreviewRows.value.map((r: any) => r.id));
    bizMessage.value = `解析 ${j.parsed ?? 0} 行，匹配 ${j.matched ?? 0} 条${j.missing ? '，未找到 ' + j.missing : ''}`;
  } catch (e: any) {
    bizMessage.value = '预览失败：' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 6000);
  }
}

function toggleBatchPreviewRow(id: string) {
  const s = new Set(batchPreviewSelected.value);
  if (s.has(id)) s.delete(id); else s.add(id);
  batchPreviewSelected.value = s;
}
function batchPreviewSelectAll() { batchPreviewSelected.value = new Set(batchPreviewRows.value.map((r: any) => r.id)); }
function batchPreviewSelectNone() { batchPreviewSelected.value = new Set(); }
function batchPreviewInvert() {
  const all = batchPreviewRows.value.map((r: any) => r.id);
  const cur = batchPreviewSelected.value;
  const inv = new Set<string>();
  all.forEach((id: string) => { if (!cur.has(id)) inv.add(id); });
  batchPreviewSelected.value = inv;
}

function cancelBatchPage() {
  batchInputText.value = '';
  batchPreviewRows.value = [];
  batchPreviewSelected.value = new Set();
  batchApplyCurrentTime.value = false;
  batchOverrideAudited.value = false;
  batchTargetCustomerId.value = '';
}

async function doBatchConfirm() {
  if (batchPreviewSelected.value.size === 0) {
    bizMessage.value = '请先勾选要更新的订单';
    return;
  }
  const selected = batchPreviewRows.value.filter((r: any) => batchPreviewSelected.value.has(r.id));
  let endpoint = '';
  const opts: any = {
    applyCurrentTime: batchApplyCurrentTime.value,
    overrideAudited: batchOverrideAudited.value,
  };
  let body: any = opts;

  if (accTab.value === 'orders-update-tracking') {
    endpoint = 'batch-update-tracking';
    body = { ...opts, rows: selected.map((r: any) => ({ id: r.id, trackingNo: r.newValue })) };
  } else if (accTab.value === 'orders-update-weight') {
    endpoint = 'batch-update-weight';
    body = { ...opts, rows: selected.map((r: any) => ({ id: r.id, chargeableKg: Number(r.newValue) })) };
  } else if (accTab.value === 'orders-change-customer') {
    if (!batchTargetCustomerId.value) { bizMessage.value = '请先选目标客户'; return; }
    endpoint = 'batch-change-customer';
    body = { ...opts, ids: selected.map((r: any) => r.id), customerId: batchTargetCustomerId.value };
  } else if (accTab.value === 'orders-batch-charge') {
    endpoint = 'batch-recharge';
    body = { ...opts, ids: selected.map((r: any) => r.id) };
  } else if (accTab.value === 'orders-batch-track') {
    endpoint = 'batch-track';
    body = { ids: selected.map((r: any) => r.id) };
  }

  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const j = await res.json();
    if (endpoint === 'batch-track') {
      const eventMap = new Map((j.rows ?? []).map((r: any) => [r.id, r.events ?? []]));
      batchPreviewRows.value = batchPreviewRows.value.map((r: any) => ({ ...r, events: eventMap.get(r.id) ?? [] }));
      bizMessage.value = `追踪完成 ${j.total ?? 0} 条`;
    } else {
      bizMessage.value = `${batchPageMeta.value?.title} 完成: ${JSON.stringify(j).slice(0, 200)}`;
      cancelBatchPage();
    }
  } catch (e: any) {
    bizMessage.value = '操作失败：' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 8000);
  }
}

// 行复选框：批量审核 OR 批量汇款 OR 订单批量操作 OR 批量页面 OR 财务工作台待审核
const canSelect = computed(() => canBatchAudit.value || canBatchRemit.value || canOrdersBatch.value
  || batchPageSet.has(accTab.value)
  || ['fwb-pending', 'fwb-invoiced', 'fwb-needs-verify'].includes(accTab.value));

// ═══ 制单中心批量按钮 ═══
async function callOrdersBatch(endpoint: string, extra: Record<string, any> = {}) {
  if (selectedIds.value.size === 0) {
    bizMessage.value = '请先勾选订单';
    return;
  }
  bizLoading.value = true;
  try {
    const ids = Array.from(selectedIds.value);
    const res = await apiFetch(`${API}/api/acc/orders/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids, ...extra }),
    });
    const j = await res.json();
    if (res.ok) {
      setBizOk(`${endpoint} 完成: ${JSON.stringify(j).slice(0, 200)}`);
      selectedIds.value.clear();
      await fetchAccData();
    } else {
      setBizError(`${endpoint} 失败：${j.error ?? res.status}`);
    }
  } catch (e: any) {
    setBizError(`${endpoint} 异常：${e.message}`);
  } finally {
    bizLoading.value = false;
  }
}

const doOrdersBatchSubmit = () => callOrdersBatch('batch-submit');
const doOrdersBatchQuery  = () => callOrdersBatch('batch-query');
const doOrdersBatchVoid   = () => callOrdersBatch('batch-void', { reason: '批量作废' });
const doOrdersBatchRecharge = () => callOrdersBatch('batch-recharge');
const doOrdersBatchMerge  = () => callOrdersBatch('batch-merge');

// 取消订单 + 作废订单 专属操作
async function doOrdersHardDelete() {
  if (!confirm(`确定彻底删除 ${selectedIds.value.size} 个订单？操作不可恢复`)) return;
  await callOrdersBatch('batch-hard-delete');
}
async function doBatchAuditVoid() {
  if (!confirm(`确定审核通过 ${selectedIds.value.size} 个作废申请？`)) return;
  await callOrdersBatch('batch-audit-void');
}
async function doBatchRestore() {
  if (!confirm(`确定恢复 ${selectedIds.value.size} 个订单？`)) return;
  await callOrdersBatch('batch-restore');
}
// 「待核订单」按钮：本地过滤显示 audit_status 不为 AUDITED 的行
function filterVoidPending() {
  accKeyword.value = '';
  accDateFrom.value = '';
  accDateTo.value = '';
  // 添加一个临时过滤标记
  accData.value = accData.value.filter((r: any) => r.auditStatus !== 'AUDITED');
  bizMessage.value = `已过滤显示 ${accData.value.length} 条待核订单`;
  setTimeout(() => { bizMessage.value = ''; }, 4000);
}

// 导出选中（仅勾选的 ids，前端基于 accData 子集渲染 CSV）
async function doExportSelected() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  const cols = accColumns[accTab.value];
  if (!cols || !cols.length) return;
  const selected = accData.value.filter((r: any) => selectedIds.value.has(r.id));
  if (!selected.length) { bizMessage.value = '当前页未含勾选行'; return; }
  const headers = cols.map(c => c.label);
  const csv = ['﻿' + headers.join(',')];
  for (const r of selected) {
    csv.push(cols.map(c => `"${String((r as any)[c.key] ?? '').replace(/"/g, '""')}"`).join(','));
  }
  const blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${tab.label}_选中_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
  bizMessage.value = `导出 ${selected.length} 条`;
  setTimeout(() => { bizMessage.value = ''; }, 4000);
}

// 打印类按钮（弹一个新窗口预览）
async function doOrdersPrint(kind: string) {
  if (selectedIds.value.size === 0) {
    bizMessage.value = '请先勾选订单';
    return;
  }
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/print-${kind}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (res.ok) {
      const w = window.open('', '_blank');
      if (w) {
        w.document.write(`<html><head><title>${kind}</title></head><body><pre>${JSON.stringify(j, null, 2)}</pre></body></html>`);
      }
      bizMessage.value = `${kind} 准备 ${j.count ?? 0} 份`;
    }
  } catch (e: any) {
    bizMessage.value = `打印失败: ${e.message}`;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 6000);
  }
}

// 导入快件
const showImportDialog = ref(false);
async function doDownloadImportTemplate() {
  const res = await apiFetch(`${API}/api/acc/orders/import-template`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'order-import-template.csv';
  a.click();
  URL.revokeObjectURL(url);
}
async function doImportExcel(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  const fd = new FormData();
  fd.append('file', file);
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/import-excel`, {
      method: 'POST',
      body: fd,
    });
    const j = await res.json();
    bizMessage.value = `导入完成: 成功 ${j.created ?? 0} / 失败 ${j.failed ?? 0}`;
    if ((j.errors ?? []).length > 0) {
      console.log('导入错误:', j.errors);
    }
    await fetchAccData();
  } catch (e: any) {
    bizMessage.value = `导入失败: ${e.message}`;
  } finally {
    bizLoading.value = false;
    target.value = '';
    setTimeout(() => { bizMessage.value = ''; }, 10000);
  }
}

// 批量汇款 dialog state
const showBatchRemitDialog = ref(false);
const batchRemitAccountId = ref("");
const batchRemitName = ref("");
const remitBanks = ref<any[]>([]);

async function openBatchRemitDialog() {
  if (selectedIds.value.size === 0) {
    bizMessage.value = '请先勾选要汇款的退款记录';
    return;
  }
  // 拉资金账户列表（财务账户 owner=COMPANY）
  try {
    const res = await apiFetch(`${API}/api/acc/banks?pageSize=200`);
    const j = await res.json();
    remitBanks.value = Array.isArray(j) ? j : (j.data ?? []);
  } catch { remitBanks.value = []; }
  batchRemitAccountId.value = remitBanks.value[0]?.id ?? '';
  batchRemitName.value = authUser.value?.displayName ?? '';
  showBatchRemitDialog.value = true;
}

async function doBatchRemit() {
  if (!batchRemitAccountId.value) {
    bizMessage.value = '请选择资金账户';
    return;
  }
  const tab = accTabs.find(t => t.key === accTab.value);
  if (!tab) return;
  // 退款 sub-tab 走父端点
  const apiPath = tab.api.replace(/-pending$/, '');
  bizLoading.value = true;
  try {
    const ids = Array.from(selectedIds.value);
    const res = await apiFetch(`${API}/api/acc/${apiPath}/batch-remit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ids,
        financialAccountId: batchRemitAccountId.value,
        remitName: batchRemitName.value,
      }),
    });
    const json = await res.json();
    if (res.ok) {
      bizMessage.value = `批量汇款完成：${json.remitted ?? 0} 条成功，${json.skipped ?? 0} 条跳过（未审核或已汇款的会跳过）`;
      selectedIds.value.clear();
      showBatchRemitDialog.value = false;
      await fetchAccData();
    } else {
      bizMessage.value = '批量汇款失败: ' + (json.error ?? `HTTP ${res.status}`);
    }
  } catch (e: any) {
    bizMessage.value = '批量汇款失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 6000);
  }
}
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

// 财务工作台 — 预扣明细 Excel/CSV 导出
async function doExportPrepayCsv() {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/prepay-details/export`);
    if (!res.ok) { bizMessage.value = '导出失败 ' + res.status; return; }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `预扣明细_${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    bizMessage.value = '已导出';
  } catch (e: any) { bizMessage.value = '导出失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 3000); }
}

// 财务工作台 — UPS 账单 CSV 导入（tracking_no, actual_amount[, currency]）
async function doImportActualBill(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input?.files?.[0];
  if (!file) return;
  bizLoading.value = true;
  try {
    const form = new FormData();
    form.append('file', file);
    const res = await apiFetch(`${API}/api/acc/finance-workbench/import-actual-bill`, {
      method: 'POST', body: form,
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '导入失败: ' + (j.error || res.status); return; }
    bizMessage.value = `导入完成：匹配 ${j.matched} 条，跳过 ${j.skipped} 条`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '导入失败: ' + e.message; }
  finally {
    bizLoading.value = false; input.value = '';
    setTimeout(()=>bizMessage.value='', 6000);
  }
}

// 核算工作台 — 导入 UPS 实际成本 CSV
async function doImportActualCost(ev: Event) {
  const input = ev.target as HTMLInputElement;
  const file = input?.files?.[0];
  if (!file) return;
  bizLoading.value = true;
  try {
    const form = new FormData();
    form.append('file', file);
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/import-actual-cost`, {
      method: 'POST', body: form,
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '导入失败: ' + (j.error || res.status); return; }
    bizMessage.value = `成本导入完成：匹配 ${j.matched} 条，跳过 ${j.skipped} 条`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '导入失败: ' + e.message; }
  finally {
    bizLoading.value = false; input.value = '';
    setTimeout(()=>bizMessage.value='', 6000);
  }
}

// 核算工作台 — 审核 AP charges
async function doAuditCostCharges() {
  if (selectedIds.value.size === 0) return;
  if (!confirm(`将一审 ${selectedIds.value.size} 条 AP 成本（PENDING → AUDITED），确定？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/audit-cost-charges`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '审核失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已审核 ${j.auditedCount} 条 AP 成本`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '审核失败: ' + e.message; }
  finally {
    bizLoading.value = false;
    setTimeout(()=>bizMessage.value='', 6000);
  }
}

// 核算工作台 — 付供应商
// 核算工作台 — 付款账户下拉数据
const swbFromAccountId = ref<string>('');
const swbCompanyAccounts = ref<any[]>([]);
async function fetchSwbCompanyAccounts() {
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/company-accounts`);
    if (res.ok) { const j = await res.json(); swbCompanyAccounts.value = j.data || []; }
  } catch {}
}

async function doPaySupplier(largeConfirmed = false) {
  if (selectedIds.value.size === 0) return;
  const remark = prompt('付款备注（如：UPS 2026-06 月结）', 'UPS 月结');
  if (remark === null) return;
  if (!confirm(`将给供应商付款 ${selectedIds.value.size} 条 AP 成本，写 PAYMENT 流水。确定？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/pay-supplier`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chargeIds: Array.from(selectedIds.value),
        remark,
        fromAccountId: swbFromAccountId.value || null,
        largeConfirmed,
      }),
    });
    const j = await res.json();
    if (!res.ok) {
      // 大额付款触发二次确认
      if (j.error && j.error.includes('largeConfirmed=true') && !largeConfirmed) {
        bizLoading.value = false;
        if (confirm(`${j.error}\n再次确认付款吗？`)) {
          return doPaySupplier(true);
        }
        return;
      }
      bizMessage.value = '付款失败: ' + (j.error || res.status); return;
    }
    bizMessage.value = `已付 ${j.paidCount} 条 AP，总额 ${j.totalAmount}，写 ${j.groupCount} 条 ledger`;
    selectedIds.value.clear();
    fetchAccData();
    fetchSwbCompanyAccounts();
  } catch (e: any) { bizMessage.value = '付款失败: ' + e.message; }
  finally {
    bizLoading.value = false;
    setTimeout(()=>bizMessage.value='', 6000);
  }
}

async function doUnauditCostCharges() {
  if (selectedIds.value.size === 0) return;
  if (!confirm(`反审 ${selectedIds.value.size} 条 AP 成本（AUDITED → PENDING），确定？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/unaudit-cost-charges`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '反审失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已反审 ${j.unauditedCount} 条 AP`;
    selectedIds.value.clear(); fetchAccData();
  } catch (e: any) { bizMessage.value = '反审失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

async function doUnsettlePayment() {
  if (selectedIds.value.size === 0) return;
  const reason = prompt('撤销付款原因', '错付');
  if (reason === null) return;
  if (!confirm(`撤销 ${selectedIds.value.size} 条 AP 付款（资金会退回 COMPANY 账户），确定？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/unsettle-payment`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value), reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '撤销失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已撤销 ${j.unsettledCount} 条付款，资金已退回`;
    selectedIds.value.clear(); fetchAccData(); fetchSwbCompanyAccounts();
  } catch (e: any) { bizMessage.value = '撤销失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

async function doBatchVoidCost() {
  if (selectedIds.value.size === 0) return;
  if (!confirm(`将 ${selectedIds.value.size} 条 AP 成本作废（UNSETTLED 才能作废），确定？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/batch-void-cost`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '作废失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已作废 ${j.voidedCount} 条 AP`;
    selectedIds.value.clear(); fetchAccData();
  } catch (e: any) { bizMessage.value = '作废失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

async function doApprovalDecide(decision: 'APPROVE' | 'REJECT') {
  if (selectedIds.value.size === 0) return;
  const verb = decision === 'APPROVE' ? '通过' : '驳回';
  const comment = prompt(`${verb}意见（可选）`, '');
  if (comment === null) return;
  if (!confirm(`将 ${selectedIds.value.size} 条审批请求标 ${verb}，确定？`)) return;
  bizLoading.value = true;
  let ok = 0, fail = 0;
  for (const id of Array.from(selectedIds.value)) {
    try {
      const res = await apiFetch(`${API}/api/admin/approval/${id}/decide`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, comment }),
      });
      if (res.ok) ok++; else fail++;
    } catch { fail++; }
  }
  bizMessage.value = `${verb} 完成：成功 ${ok} 条，失败 ${fail} 条`;
  selectedIds.value.clear();
  fetchAccData();
  bizLoading.value = false;
  setTimeout(()=>bizMessage.value='', 6000);
}

async function doPeriodClose() {
  const period = prompt('关账月份 YYYY-MM', new Date().toISOString().slice(0,7));
  if (period === null) return;
  bizLoading.value = true;
  try {
    // 先 preview
    const previewRes = await apiFetch(`${API}/api/acc/period-closing/preview`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ period }),
    });
    const preview = await previewRes.json();
    if (!previewRes.ok) { bizMessage.value = '预览失败: ' + (preview.error || previewRes.status); return; }
    if (preview.alreadyLocked) {
      bizMessage.value = `${period} 已关账`;
      return;
    }
    const confirmText = `${period} 期间结算预览：\n` +
      `收入: ${preview.totalRevenue}\n支出: ${preview.totalExpense}\n` +
      `净利: ${preview.netIncome}\n\n确认关账（不可逆）？`;
    if (!confirm(confirmText)) return;
    const closeRes = await apiFetch(`${API}/api/acc/period-closing/close`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ period }),
    });
    const close = await closeRes.json();
    if (!closeRes.ok) { bizMessage.value = '关账失败: ' + (close.error || closeRes.status); return; }
    bizMessage.value = `${period} 关账成功，凭证 ${close.voucherNo}, 净利 ${close.netIncome}`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '关账失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 8000); }
}

async function doGenerateCommissions() {
  const rate = prompt('提成比例（默认 5%）', '0.05');
  if (rate === null) return;
  const month = prompt('月份 YYYY-MM（默认当月）', new Date().toISOString().slice(0,7));
  if (month === null) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/settlement-workbench/commissions/generate`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rate, month }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '生成失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已生成 ${j.generated} 条提成（${j.month} × ${j.rate}）`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '生成失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

function doExportSupplierStatement() {
  const url = `${API}/api/acc/settlement-workbench/export-supplier-statement`;
  // 用 fetch 拿 blob 触发下载（保留 auth header）
  apiFetch(url).then(async (res) => {
    if (!res.ok) { bizMessage.value = '导出失败: ' + res.status; return; }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `supplier-statement-${new Date().toISOString().slice(0,10)}.csv`;
    a.click(); URL.revokeObjectURL(a.href);
    bizMessage.value = '已导出';
  });
}

// 财务工作台 — charge 审计时间线弹窗
// 后端 audit_events.before_state/after_state 是 PG jsonb，Spring 序列化时会包成
// {type:"jsonb", value:"...", null:false}。这里 unwrap 出真正的 JSON。
function unwrapJsonb(v: any): any {
  if (v == null) return null;
  if (typeof v === 'object' && v.type === 'jsonb' && typeof v.value === 'string') {
    try { return JSON.parse(v.value); } catch { return v.value; }
  }
  return v;
}
async function doViewChargeHistory(row: any) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/charges/${row.id}/audit-history`);
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '取历史失败: ' + (j.error || res.status); return; }
    const events = (j.data || []) as any[];
    if (events.length === 0) { bizMessage.value = '该 charge 还没有审计记录'; return; }
    const lines = events.map((e: any) => {
      const t = (e.occurred_at || '').slice(0, 19).replace('T', ' ');
      const before = unwrapJsonb(e.before_state);
      const after = unwrapJsonb(e.after_state);
      // 只挑常见关键字段显示，避免显示 AUDIT 全 charge 那种 30+ 字段巨大文本
      const keys = ['amount', 'status', 'audit_status', 'settlement_status', 'paid_amount'];
      const fmt = (obj: any) => {
        if (!obj || typeof obj !== 'object') return String(obj);
        const filtered: Record<string, any> = {};
        keys.forEach(k => { if (k in obj) filtered[k] = obj[k]; });
        return Object.keys(filtered).length ? JSON.stringify(filtered) : '(...)';
      };
      const remark = e.remark ? ` [${e.remark}]` : '';
      return `${t}  ${e.action}  by ${e.actor_name || '-'}\n  before: ${fmt(before)}\n  after:  ${fmt(after)}${remark}`;
    });
    alert(`审计时间线 (${events.length} 条)\n订单: ${row.order_no || row.id}\n\n` + lines.join('\n\n'));
  } catch (e: any) { bizMessage.value = '取历史失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 3000); }
}

// 财务工作台 — 撤销 charge 调整（ADJUSTED → ESTIMATED）
async function doUnadjustCharge(row: any) {
  if (!confirm(`撤销调整 ${row.order_no || row.id}？金额会回到 audit_events 里的原始值。`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/charges/${row.id}/unadjust`, {
      method: 'POST',
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '撤销失败: ' + (j.error || res.status); return; }
    bizMessage.value = `撤销成功：${j.previousAmount} → ${j.newAmount}（回到 ESTIMATED）`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '撤销失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 订单 → 财务详情弹窗（charges + ledger + 客户余额）
async function doViewOrderFinance(row: any) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/orders/${row.id}/finance`);
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '查询失败: ' + (j.error || res.status); return; }
    const chs = (j.charges || []) as any[];
    const lg = (j.ledger || []) as any[];
    const bal = j.balance || {};
    const lines: string[] = [];
    lines.push(`订单 ${row.orderNo || row.id}`);
    if (bal.currency) {
      lines.push(`客户可打单余额: ${bal.usableBalance} ${bal.currency}`);
    }
    lines.push('');
    lines.push(`Charges (${chs.length} 条):`);
    chs.forEach((c: any) => {
      const tag = c.invoice_no ? `账单 ${c.invoice_no}` : '未出账';
      lines.push(`  ${c.side} ${c.amount} ${c.currency}  status=${c.status} settle=${c.settlement_status}  [${tag}]`);
    });
    lines.push('');
    lines.push(`Ledger (${lg.length} 条):`);
    lg.forEach((l: any) => {
      const t = (l.created_at || '').slice(0, 19).replace('T', ' ');
      lines.push(`  ${t}  ${l.biz_type} ${l.direction} ${l.amount}  (${l.balance_before}→${l.balance_after})  ${l.remark || ''}`);
    });
    alert(lines.join('\n'));
  } catch (e: any) { bizMessage.value = '查询失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 3000); }
}

// 财务工作台 — 打印账单（弹新窗口浏览器 Ctrl+P 另存 PDF）
function doPrintInvoice(row: any) {
  const invoiceId = row.invoice_id;
  if (!invoiceId) { bizMessage.value = '该行没有 invoice_id'; return; }
  // 用 Bearer token 拼 URL 不安全，改用同源 fetch 拿到 HTML 再 document.write
  const token = localStorage.getItem('token') || '';
  fetch(`${API}/api/acc/finance-workbench/invoices/${invoiceId}/print`, {
    headers: { 'Authorization': `Bearer ${token}` },
  }).then(r => r.text()).then(html => {
    const w = window.open('', '_blank');
    if (w) {
      w.document.open();
      w.document.write(html);
      w.document.close();
    }
  }).catch(e => { bizMessage.value = '打开打印页失败: ' + e.message; });
}

// 财务工作台 — 退款（PAID/PARTIAL → 减 paid_amount + ledger REFUND）
async function doRefundInvoice(row: any) {
  const invoiceId = row.invoice_id;
  if (!invoiceId) { bizMessage.value = '该行没有 invoice_id'; return; }
  const maxRefund = Number(row.invoice_paid || row.paid_amount || 0);
  const v = prompt(`退款金额（账单 ${row.invoice_no}）\n已付 ${maxRefund} ${row.currency}，输入退款金额：`, String(maxRefund));
  if (!v) return;
  const amount = Number(v);
  if (!Number.isFinite(amount) || amount <= 0) { bizMessage.value = '金额无效'; return; }
  if (amount > maxRefund) { bizMessage.value = `退款金额不能超过已付 ${maxRefund}`; return; }
  const reason = prompt('退款原因：') || '';
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/${invoiceId}/refund`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount, reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '退款失败: ' + (j.error || res.status); return; }
    bizMessage.value = `退款 ${j.refundedAmount} 成功 → 账单 ${j.status}，已付 ${j.paidAmount} 未付 ${j.unpaidAmount}`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '退款失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

// 财务工作台 — 批量核销
async function doBatchMarkPaid() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  if (!confirm(`批量标记 ${selectedIds.value.size} 张账单已付（全额）？`)) return;
  bizLoading.value = true;
  try {
    // 收集 invoice_id（按行查 row.invoice_id，不是 charge_id）
    const ids = accData.value.filter((r: any) => selectedIds.value.has(r.id))
      .map((r: any) => r.invoice_id).filter(Boolean);
    if (!ids.length) { bizMessage.value = '选中的行没有 invoice_id'; return; }
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/batch-mark-paid`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '批量核销失败: ' + (j.error || res.status); return; }
    bizMessage.value = `成功 ${j.success}，失败 ${j.failed}`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '批量核销失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

// 财务工作台 — 批量作废
async function doBatchVoid() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  const reason = prompt(`批量作废 ${selectedIds.value.size} 张账单，输入原因：`);
  if (reason === null) return;
  bizLoading.value = true;
  try {
    const ids = accData.value.filter((r: any) => selectedIds.value.has(r.id))
      .map((r: any) => r.invoice_id).filter(Boolean);
    if (!ids.length) { bizMessage.value = '选中的行没有 invoice_id'; return; }
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/batch-void`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids, reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '批量作废失败: ' + (j.error || res.status); return; }
    bizMessage.value = `成功 ${j.success}，失败 ${j.failed}`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '批量作废失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

// 财务工作台 — 清理孤立 charges（订单 CANCELLED 但 charges 还挂预扣）
async function doCleanupOrphan() {
  if (!confirm('扫描订单已取消但 charges 还挂在预扣的孤儿，将它们标 VOID 并回退余额，继续？')) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/cleanup-orphan-charges`, { method: 'POST' });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '清理失败: ' + (j.error || res.status); return; }
    bizMessage.value = `已清理 ${j.cleanedCount} 条孤立 charge`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '清理失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 二审通过单张账单（>5w 阈值）
async function doVerifyOneBill(row: any) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/bills/${row.id}/verify-biz`, { method: 'POST' });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '二审失败: ' + (j.error || res.status); return; }
    bizMessage.value = `账单 ${row.invoice_no} 二审通过`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '二审失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 4000); }
}

// 财务工作台 — 批量二审
async function doBatchVerifyBills() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  bizLoading.value = true;
  let ok = 0, fail = 0;
  for (const id of Array.from(selectedIds.value)) {
    try {
      const res = await apiFetch(`${API}/api/acc/bills/${id}/verify-biz`, { method: 'POST' });
      if (res.ok) ok++; else fail++;
    } catch { fail++; }
  }
  bizMessage.value = `二审通过 ${ok}，失败 ${fail}`;
  selectedIds.value.clear();
  bizLoading.value = false;
  fetchAccData();
  setTimeout(()=>bizMessage.value='', 5000);
}

// 财务工作台 — 反核销 (PAID/PARTIAL → PENDING，charges 回 UNSETTLED，ledger 反扣)
async function doUnsettleInvoice(row: any) {
  const invoiceId = row.invoice_id;
  if (!invoiceId) { bizMessage.value = '该行没有 invoice_id'; return; }
  const reason = prompt(`反核销账单 ${row.invoice_no}\n已付金额会从客户余额扣回，charges 会回到 UNSETTLED。\n输入反核销原因：`);
  if (reason === null) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/${invoiceId}/unsettle`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '反核销失败: ' + (j.error || res.status); return; }
    bizMessage.value = `账单回退 PENDING，反扣 ${j.reversedAmount}，${j.unsettledCharges} 条 charge 回 UNSETTLED`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '反核销失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 作废账单（charges 回到 ADJUSTED + PENDING）
async function doVoidInvoice(row: any) {
  const invoiceId = row.invoice_id;
  if (!invoiceId) { bizMessage.value = '该行没有 invoice_id'; return; }
  const reason = prompt(`作废账单 ${row.invoice_no}\n关联的 charges 会回到待审核。\n请输入作废原因：`);
  if (reason === null) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/${invoiceId}/void`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '作废失败: ' + (j.error || res.status); return; }
    bizMessage.value = `账单 ${j.invoiceNo} 已作废，${j.unlinkedCharges} 条 charge 回到待审核`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '作废失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 标记账单已付 (手动核销扣减确认)
async function doMarkInvoicePaid(row: any) {
  const invoiceId = row.invoice_id;
  if (!invoiceId) { bizMessage.value = '该行没有 invoice_id'; return; }
  const v = prompt(`确认收款金额（订单 ${row.order_no || '-'}，账单 ${row.invoice_no}）\n留空表示全额付清，原金额 ${row.amount} ${row.currency}：`, '');
  let amount: number | null = null;
  if (v && v.trim() !== '') {
    amount = Number(v);
    if (!Number.isFinite(amount) || amount <= 0) { bizMessage.value = '金额无效'; return; }
  }
  const remark = prompt('备注（可选）：') || '';
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/invoices/${invoiceId}/mark-paid`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(amount !== null ? { amount, remark } : { remark }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '核销失败: ' + (j.error || res.status); return; }
    bizMessage.value = `账单状态 → ${j.status} | 已付 ${j.paidAmount} | 未付 ${j.unpaidAmount}`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '核销失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 单条调金额
async function doAdjustCharge(row: any) {
  const cur = row.amount;
  const v = prompt(`调整 charge 金额（订单 ${row.order_no || row.id}）\n原 ${cur} ${row.currency}，输入新金额：`, String(cur));
  if (!v) return;
  const newAmount = Number(v);
  if (!Number.isFinite(newAmount) || newAmount < 0) { bizMessage.value = '金额无效'; return; }
  const reason = prompt('调整原因（可选）：') || '';
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/charges/${row.id}/adjust`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: newAmount, reason }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '调整失败: ' + (j.error || res.status); return; }
    bizMessage.value = `调整成功：${j.oldAmount} → ${j.newAmount}（差额 ${j.diff}）`;
    fetchAccData();
  } catch (e: any) { bizMessage.value = '调整失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 仅一审通过（不出账）
async function doAuditCharges() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/audit-charges`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '一审失败: ' + (j.error || res.status); return; }
    bizMessage.value = `一审通过 ${j.auditedCount} 条（audit_status=AUDITED）`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '一审失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 出账已审（已 AUDITED 的合一期）
async function doCreateInvoiceOnly() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/create-invoice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '出账失败: ' + (j.error || res.status); return; }
    bizMessage.value = `生成 ${j.invoiceCount} 张账单（${j.chargeCount} 条 charge）`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '出账失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 5000); }
}

// 财务工作台 — 批量审核并出账
async function doAuditAndInvoice() {
  if (selectedIds.value.size === 0) { bizMessage.value = '请先勾选'; return; }
  if (!confirm(`将审核 ${selectedIds.value.size} 条 charge 并生成账单（按客户+币种自动分组），继续？`)) return;
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/finance-workbench/audit-and-invoice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chargeIds: Array.from(selectedIds.value) }),
    });
    const j = await res.json();
    if (!res.ok) { bizMessage.value = '出账失败: ' + (j.error || res.status); return; }
    bizMessage.value = `生成 ${j.invoiceCount} 张账单（${j.chargeCount} 条 charge）`;
    selectedIds.value.clear();
    fetchAccData();
  } catch (e: any) { bizMessage.value = '出账失败: ' + e.message; }
  finally { bizLoading.value = false; setTimeout(()=>bizMessage.value='', 6000); }
}

// 下载面单 PDF（GET /api/acc/labels/by-order/{orderId}）
// Submit 时 UPS 返回的 PDF 已落盘到 label_files；按 order_id 取最新一张
async function doDownloadLabel(row: any) {
  bizLoading.value = true;
  try {
    const res = await apiFetch(`${API}/api/acc/labels/by-order/${row.id}`);
    if (!res.ok) {
      const j = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
      bizMessage.value = `下载面单失败: ${j.error || j.message || res.status}`;
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    // 浏览器另存 — 文件名取 tracking_no 或 orderNo
    const a = document.createElement('a');
    a.href = url;
    a.download = `${row.trackingNo || row.orderNo || row.id}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
    bizMessage.value = `面单已下载: ${a.download}`;
  } catch (e: any) {
    bizMessage.value = `下载面单失败: ${e.message}`;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

// 申请作废订单（对应 ACC 制单中心「申请作废」按钮）
// audit_status → PENDING，进入「作废订单」队列等待审核
async function doRequestVoid(row: any) {
  if (!confirm(`确认申请作废订单 ${row.orderNo}？提交后需财务审核。`)) return;
  bizLoading.value = true;
  bizMessage.value = '';
  try {
    const res = await apiFetch(`${API}/api/acc/orders/${row.id}/request-void`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: '' }),
    });
    const json = await res.json().catch(() => ({}));
    if (res.ok) {
      bizMessage.value = '已申请作废，等待审核';
      fetchAccData();
    } else {
      bizMessage.value = '申请失败: ' + (json.error ?? `HTTP ${res.status}`);
    }
  } catch (e: any) {
    bizMessage.value = '申请失败: ' + e.message;
  } finally {
    bizLoading.value = false;
    setTimeout(() => { bizMessage.value = ''; }, 3000);
  }
}

// 恢复作废订单（已 status=VOID 的订单走 undo-biz 触发 OrderVoidAuditSideEffect.onUndone）
async function doRestoreVoid(row: any) {
  if (!confirm(`确认恢复订单 ${row.orderNo}？将取消作废并恢复到作废前的状态。`)) return;
  return doUndoAudit(row.id);
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
    bizDialogData.customer_id = '';
    bizDialogData.currency = 'CNY';
    bizDialogData.date_from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    bizDialogData.date_to = new Date().toISOString().slice(0, 10);
  } else if (type === 'quick-payment') {
    bizDialogData.customer_id = '';
    bizDialogData.currency = 'CNY';
    bizDialogData.settled_amount = 0;
    bizDialogData.bank_account_id = '';
  } else if (type === 'calc-commission') {
    const now = new Date();
    bizDialogData.month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  } else if (type === 'profit-summary') {
    bizDialogData.dateFrom = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    bizDialogData.dateTo = new Date().toISOString().slice(0, 10);
    bizDialogData.groupBy = 'customer';
  }
  if (type === 'generate-bill' || type === 'quick-payment') {
    await loadSelectOptions([
      { col: '', label: '', type: 'select', ref: 'customers' },
      { col: '', label: '', type: 'select', ref: 'banks' },
      { col: '', label: '', type: 'select', ref: 'currencies' },
    ]);
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
          customer_id: bizDialogData.customer_id,
          currency: bizDialogData.currency || 'CNY',
          date_from: bizDialogData.date_from,
          date_to: bizDialogData.date_to,
        }),
      });
      const json = await res.json().catch(() => ({}));
      if (res.ok) {
        bizMessage.value = `账单生成成功：${json.invoice_no}，总额 ${json.total_amount}`;
        showBizDialog.value = false;
        fetchAccData();
      } else {
        bizMessage.value = '生成失败: ' + (json.error ?? json.message ?? '');
      }
    } else if (bizDialogType.value === 'quick-payment') {
      const res = await apiFetch(`${API}/api/acc/receiveds/settle`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          customer_id: bizDialogData.customer_id,
          currency: bizDialogData.currency || 'CNY',
          settled_amount: Number(bizDialogData.settled_amount),
          bank_account_id: bizDialogData.bank_account_id || null,
        }),
      });
      const json = await res.json().catch(() => ({}));
      if (res.ok) {
        const matched = json.matched_invoices?.length ?? json.total_matched ?? 0;
        const unmatched = json.unmatched_amount ?? 0;
        bizMessage.value = `收款成功，核销 ${matched} 张账单` + (unmatched > 0 ? `，多余 ${unmatched} 进预付余额` : '');
        showBizDialog.value = false;
        fetchAccData();
      } else {
        bizMessage.value = '收款失败: ' + (json.error ?? json.message ?? '');
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
    // 用 list 端点 + 大 pageSize 拿全量数据（后端 AccPaging.MAX_PAGE_SIZE=10000）
    params.set('page', '1');
    params.set('pageSize', '10000');
    if (accKeyword.value) params.set('keyword', accKeyword.value);
    if (accDateFrom.value) params.set('dateFrom', accDateFrom.value);
    if (accDateTo.value) params.set('dateTo', accDateTo.value);
    if ((tab as any).statusFilter) {
      const paramName = (tab.api === 'profits') ? 'mode' : 'status';
      params.set(paramName, (tab as any).statusFilter);
    }
    const res = await apiFetch(`${API}/api/acc/${tab.api}?${params}`);
    const raw = await res.json();
    const json = Array.isArray(raw) ? raw : (raw.data ?? []);
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
  } else if (tab.api === 'orders') {
    // ACC 风格订单详情：基本/货物/收件/发件/进口商/申报/装箱/财务/跟踪/审计
    const res = await apiFetch(`${API}/api/acc/orders/${row.id}/detail`);
    detailData.value = await res.json();
    detailType.value = 'order-detail';
  } else if (accTab.value === 'stowage-plans') {
    // 3D 配载方案
    const res = await apiFetch(`${API}/api/acc/stowage/plan/${row.id}`);
    detailData.value = await res.json();
    detailType.value = 'stowage-3d';
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
        <!-- 单租户部署，tenantCode 隐式 xqt，去 UI 字段 -->
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
              <span>业务线</span>
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
                <h2>收入构成</h2>
              </div>
              <span>图表</span>
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
              <span>财务</span>
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
              <span>经营链路</span>
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
              <span>提醒</span>
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

        <!-- Search bar (批量页面/打印页/API文档页隐藏) -->
        <div class="acc-search-bar" v-if="!batchPagePanel && !isBatchPrintPage && !isApiDocsPage">
          <!-- ACC 风格：搜索字段下拉 + 输入框 -->
          <template v-if="currentTabSearchFields">
            <span style="font-size:13px;margin-right:4px">搜索:</span>
            <select v-model="currentSearchField" style="padding:4px;font-size:13px;margin-right:4px">
              <option v-for="opt in currentTabSearchFields" :key="opt.v" :value="opt.v">{{ opt.l }}</option>
            </select>
            <input type="text" v-model="accKeyword" :placeholder="currentSearchPlaceholder" @keyup.enter="accSearch" style="min-width:240px" />
          </template>
          <div v-else class="search-group">
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
          <button class="secondary sm" @click="accKeyword = ''; accDateFrom = ''; accDateTo = ''; clearAdvFilters(); accSearch()">
            重置
          </button>
          <button v-if="showAdvFilterButton" class="secondary sm"
                  @click="showAdvancedFilter = !showAdvancedFilter"
                  :title="showAdvancedFilter ? '收起多条件筛选' : '展开多条件筛选'">
            {{ showAdvancedFilter ? '收起筛选 ▴' : '多条件筛选 ▾' }}<span v-if="activeAdvFilterCount()" class="adv-filter-badge">{{ activeAdvFilterCount() }}</span>
          </button>
          <button v-if="ordersTabSet.has(accTab)" class="sm"
                  :class="missingCostOnly ? 'primary' : 'secondary'"
                  @click="missingCostOnly = !missingCostOnly; accSearch()"
                  :title="'只看缺成本订单（有应收无应付）'"
                  :style="missingCostOnly ? 'background:#dc2626;border-color:#dc2626' : ''">
            ⚠️ {{ missingCostOnly ? '✓ 只看缺成本' : '只看缺成本' }}
          </button>
          <!-- charges/costs 走「+ 添加成本」对话框 (语义复杂: 必须选订单 + 费用项),
               通用「+ 新增」会让用户误以为能直接填表, 这里隐藏 -->
          <button class="primary sm" v-if="canCrud && accTab !== 'charges' && accTab !== 'costs'" @click="openAdd">
            <Plus :size="13" /> 新增
          </button>
          <!-- ACC 核算中心「导入费用/导入成本」: tab 即上传入口 -->
          <label class="primary sm" v-if="accTab === 'charges-import'" style="cursor:pointer">
            <Upload :size="13" /> 上传费用 CSV
            <input type="file" accept=".csv" style="display:none" @change="doImportActualBill" />
          </label>
          <label class="primary sm" v-if="accTab === 'costs-import'" style="cursor:pointer">
            <Upload :size="13" /> 上传成本 CSV
            <input type="file" accept=".csv" style="display:none" @change="doImportActualCost" />
          </label>
          <button class="secondary sm" v-if="canBatchAudit" @click="doBatchAudit" :disabled="bizLoading || selectedIds.size === 0">
            <CheckCircle :size="13" /> 批量审核({{ selectedIds.size }})
          </button>
          <!-- 财务工作台 - 预扣明细 tab 工具栏 -->
          <template v-if="accTab === 'fwb-prepay'">
            <select v-model="fwbSelectedCustomer" @change="fetchFwbBalance"
                    style="padding:4px 8px; border:1px solid #cbd5e1; border-radius:4px; font-size:12px;">
              <option value="">— 选客户看余额 —</option>
              <option v-for="c in (selectOptions['customers'] ?? [])" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <select v-model="fwbSelectedCurrency" @change="fetchFwbBalance"
                    style="padding:4px 8px; border:1px solid #cbd5e1; border-radius:4px; font-size:12px;">
              <option value="USD">USD</option>
              <option value="CNY">CNY</option>
              <option value="EUR">EUR</option>
            </select>
            <button class="secondary sm" @click="doExportPrepayCsv" :disabled="bizLoading">
              <FileText :size="13" /> 导出客户对账 CSV
            </button>
            <label class="secondary sm" style="cursor:pointer; display:inline-flex; align-items:center; gap:4px;">
              <Upload :size="13" /> 导入 UPS 账单 CSV
              <input type="file" accept=".csv" @change="doImportActualBill" style="display:none" :disabled="bizLoading" />
            </label>
          </template>
          <!-- 财务工作台 - 已出账 批量工具栏 -->
          <template v-if="accTab === 'fwb-invoiced'">
            <button class="secondary sm" @click="doBatchMarkPaid" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 批量核销({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doBatchVoid" :disabled="bizLoading || selectedIds.size === 0"
                    style="color:#dc2626">
              <XCircle :size="13" /> 批量作废({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doCleanupOrphan" :disabled="bizLoading"
                    title="把订单已取消但 charges 仍挂在预扣的孤儿 VOID">
              <RefreshCw :size="13" /> 清理孤立 charges
            </button>
          </template>
          <!-- 财务工作台 - 待二审账单 工具栏 -->
          <template v-if="accTab === 'fwb-needs-verify'">
            <button class="primary sm" @click="doBatchVerifyBills" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 二审通过({{ selectedIds.size }})
            </button>
          </template>
          <!-- 核算工作台 - 待核成本 工具栏（导入 UPS 实际账单） -->
          <template v-if="accTab === 'swb-cost-pending'">
            <label class="secondary sm" style="cursor:pointer; display:inline-flex; align-items:center; gap:4px;">
              <Upload :size="13" /> 导入 UPS 实际成本 CSV
              <input type="file" accept=".csv" @change="doImportActualCost" style="display:none" :disabled="bizLoading" />
            </label>
          </template>
          <!-- 核算工作台 - 待付成本 工具栏（审核 + 付供应商 + 反审 + 批量作废）-->
          <template v-if="accTab === 'swb-pending-pay'">
            <select v-model="swbFromAccountId" style="padding:4px 8px; border:1px solid #cbd5e1; border-radius:4px; font-size:12px;">
              <option value="">— 默认付款账户 —</option>
              <option v-for="a in swbCompanyAccounts" :key="a.id" :value="a.id">{{ a.account_name }} ({{ a.currency }} 余额 {{ a.balance }})</option>
            </select>
            <button class="secondary sm" @click="doAuditCostCharges" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 审核成本({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doUnauditCostCharges" :disabled="bizLoading || selectedIds.size === 0">
              <RefreshCw :size="13" /> 反审({{ selectedIds.size }})
            </button>
            <button class="primary sm" @click="doPaySupplier()" :disabled="bizLoading || selectedIds.size === 0">
              <Landmark :size="13" /> 付供应商({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doBatchVoidCost" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <XCircle :size="13" /> 批量作废({{ selectedIds.size }})
            </button>
          </template>
          <!-- 核算工作台 - 业绩提成（生成 + 审批 + 付款）-->
          <template v-if="accTab === 'swb-commissions'">
            <button class="primary sm" @click="doGenerateCommissions" :disabled="bizLoading">
              <Plus :size="13" /> 自动生成本月提成
            </button>
          </template>
          <!-- 核算工作台 - 已付成本（撤销付款）-->
          <template v-if="accTab === 'swb-paid'">
            <button class="secondary sm" @click="doUnsettlePayment" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <RefreshCw :size="13" /> 撤销付款({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doExportSupplierStatement" :disabled="bizLoading">
              <FileText :size="13" /> 导出供应商对账 CSV
            </button>
          </template>
          <!-- 审批待办 — 双按钮（通过/驳回） -->
          <template v-if="accTab === 'approval-pending'">
            <button class="primary sm" @click="doApprovalDecide('APPROVE')" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 通过({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doApprovalDecide('REJECT')" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <XCircle :size="13" /> 驳回({{ selectedIds.size }})
            </button>
          </template>
          <!-- 月度财报 — 期间关账按钮 -->
          <template v-if="accTab === 'swb-monthly'">
            <button class="primary sm" @click="doPeriodClose" :disabled="bizLoading">
              <Save :size="13" /> 月结关账
            </button>
          </template>
          <!-- 财务工作台 - 待审核 tab 工具栏（一审 / 出账 / 合并 三选一）-->
          <!-- 配载中心 → 3D 配载方案 -->
          <button v-if="accTab === 'stowage-plans'" class="primary sm"
                  @click="openStowageDialog" :disabled="bizLoading"
                  title="自动求解配载方案（指定 shipmentIds + 柜规格）">
            <Boxes :size="13" /> 自动求解配载方案
          </button>
          <!-- 财务/核算中心通用：新增成本（独立入口，自选订单） -->
          <button v-if="showCostAddButton" class="primary sm"
                  @click="openAddCostStandalone" :disabled="bizLoading"
                  title="给指定订单补录应付成本（落到「待核成本」一审通过即可付供应商）"
                  style="background:#dc2626;border-color:#dc2626">
            <CreditCard :size="13" /> ⊕ 新增成本
          </button>
          <template v-if="accTab === 'fwb-pending'">
            <button class="secondary sm" @click="doAuditCharges" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 一审通过({{ selectedIds.size }})
            </button>
            <button class="secondary sm" @click="doCreateInvoiceOnly" :disabled="bizLoading || selectedIds.size === 0"
                    title="选中行须已一审通过">
              <ReceiptText :size="13" /> 出账已审({{ selectedIds.size }})
            </button>
            <button class="primary sm" @click="doAuditAndInvoice" :disabled="bizLoading || selectedIds.size === 0"
                    title="一审 + 出账一步到位">
              <CheckCircle :size="13" /> 审核并出账({{ selectedIds.size }})
            </button>
          </template>
          <button class="secondary sm" v-if="canBatchRemit" @click="openBatchRemitDialog"
                  :disabled="bizLoading || selectedIds.size === 0" style="color:#0ea5e9">
            <Landmark :size="13" /> 批量汇款({{ selectedIds.size }})
          </button>
          <!-- ACC 取消订单：仅 1 个按钮 彻底删除 -->
          <template v-if="isCancelTab">
            <button class="secondary sm" @click="doOrdersHardDelete" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <Trash2 :size="13" /> 彻底删除
            </button>
          </template>
          <!-- ACC 作废订单：5 按钮 彻底删除/待核订单/批量审核/批量恢复/导出结果 -->
          <template v-if="isVoidAuditTab">
            <button class="secondary sm" @click="doOrdersHardDelete" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <Trash2 :size="13" /> 彻底删除
            </button>
            <button class="secondary sm" @click="filterVoidPending" :disabled="bizLoading">
              <Clock :size="13" /> 待核订单
            </button>
            <button class="secondary sm" @click="doBatchAuditVoid" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 批量审核
            </button>
            <button class="secondary sm" @click="doBatchRestore" :disabled="bizLoading || selectedIds.size === 0" style="color:#059669">
              <Undo2 :size="13" /> 批量恢复
            </button>
            <button class="secondary sm" @click="doExport" :disabled="bizLoading">
              <Download :size="13" /> 导出结果
            </button>
          </template>
          <!-- ACC 制单中心 14 个工具栏按钮（按 ACC PHP 顺序） -->
          <template v-if="showFullOrdersToolbar">
            <button class="secondary sm" @click="doOrdersPrint('a4-all')" :disabled="bizLoading || selectedIds.size === 0" title="A4所有文档">
              <FileText :size="13" /> A4所有文档
            </button>
            <button class="secondary sm" @click="doOrdersPrint('label')" :disabled="bizLoading || selectedIds.size === 0">
              <FileText :size="13" /> 打印标签
            </button>
            <button class="secondary sm" @click="doOrdersPrint('invoice')" :disabled="bizLoading || selectedIds.size === 0">
              <Receipt :size="13" /> 打印发票
            </button>
            <button class="secondary sm" @click="doOrdersPrint('battery-letter')" :disabled="bizLoading || selectedIds.size === 0">
              <FileText :size="13" /> 打印电池信
            </button>
            <button class="secondary sm" @click="doOrdersPrint('master-label')" :disabled="bizLoading || selectedIds.size === 0">
              <Tag :size="13" /> 多主单标签
            </button>
            <button class="secondary sm" @click="doOrdersPrint('invoice2-battery2')" :disabled="bizLoading || selectedIds.size === 0">
              <FileText :size="13" /> 2发票和2电池信
            </button>
            <button class="secondary sm" @click="doOrdersPrint('simple-label')" :disabled="bizLoading || selectedIds.size === 0">
              <Tag :size="13" /> 简易标签
            </button>
            <button class="secondary sm" @click="doOrdersPrint('handover')" :disabled="bizLoading || selectedIds.size === 0">
              <FileText :size="13" /> 交接清单
            </button>
            <button class="secondary sm" @click="doOrdersBatchMerge" :disabled="bizLoading || selectedIds.size < 2">
              <PackageOpen :size="13" /> 合并制单
            </button>
            <button class="secondary sm" @click="doOrdersBatchSubmit" :disabled="bizLoading || selectedIds.size === 0">
              <CheckCircle :size="13" /> 批量提交
            </button>
            <button class="secondary sm" @click="doOrdersBatchQuery" :disabled="bizLoading || selectedIds.size === 0">
              <Search :size="13" /> 批量查询
            </button>
            <button class="secondary sm" @click="doOrdersBatchVoid" :disabled="bizLoading || selectedIds.size === 0" style="color:#dc2626">
              <MinusCircle :size="13" /> 批量作废
            </button>
            <button class="secondary sm" @click="doExport" :disabled="bizLoading">
              <Download :size="13" /> 导出结果
            </button>
            <button class="secondary sm" @click="doExportSelected" :disabled="bizLoading || selectedIds.size === 0">
              <Download :size="13" /> 导出选中({{ selectedIds.size }})
            </button>
          </template>
          <!-- 导入快件 (在 orders-import tab 显示) -->
          <template v-if="accTab === 'orders-import'">
            <button class="secondary sm" @click="doDownloadImportTemplate" :disabled="bizLoading">
              <Download :size="13" /> 下载模板
            </button>
            <label class="secondary sm" style="cursor:pointer;display:inline-flex;align-items:center;gap:4px">
              <Upload :size="13" />
              <span>导入 Excel/CSV</span>
              <input type="file" accept=".csv,.xlsx,.xls" @change="doImportExcel" style="display:none" />
            </label>
          </template>
          <button class="secondary sm" v-if="canExport && !canOrdersBatch" @click="doExport" :disabled="bizLoading">
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
          <span class="biz-message" :class="bizMessageType" v-if="bizMessage" @click="clearBizMessage" title="点击关闭">
            {{ bizMessage }}
            <span v-if="bizMessageType === 'error'" style="margin-left:8px; opacity:0.7;">✕</span>
          </span>
        </div>

        <!-- 多条件筛选面板（对齐 ACC 大货运单页：grid 多 filter 同时生效 + 每个下拉多选） -->
        <div v-if="showAdvancedFilter && showAdvFilterButton && !batchPagePanel && !isBatchPrintPage && !isApiDocsPage"
             class="adv-filter-panel">
          <div class="adv-filter-grid">
            <!-- ▼ 多选 ▼ -->
            <div v-if="advFilterFieldsForTab.has('customers')" class="ff">
              <label>客户（多选）</label>
              <MultiSelect v-model="advFilters.customers"
                           :options="(selectOptions['customers'] ?? []).map((c: any) => ({ value: c.id, label: c.name }))"
                           placeholder="选择客户" />
            </div>
            <div v-if="advFilterFieldsForTab.has('channels')" class="ff">
              <label>产品/渠道（多选）</label>
              <MultiSelect v-model="advFilters.channels"
                           :options="(selectOptions['channels'] ?? []).map((c: any) => ({ value: c.code || c.name, label: c.name }))"
                           placeholder="选择渠道" />
            </div>
            <div v-if="advFilterFieldsForTab.has('countries')" class="ff">
              <label>目的国（多选）</label>
              <MultiSelect v-model="advFilters.countries"
                           :options="(selectOptions['countries'] ?? []).map((c: any) => ({ value: c.code || c.name, label: c.name }))"
                           placeholder="选择国家" />
            </div>
            <div v-if="advFilterFieldsForTab.has('statuses')" class="ff">
              <label>订单状态（多选）</label>
              <MultiSelect v-model="advFilters.statuses"
                           :options="[
                             { value: 'DRAFT', label: '草稿' },
                             { value: 'SUBMITTED', label: '已提交' },
                             { value: 'ACCEPTED', label: '已受理' },
                             { value: 'FULFILLING', label: '履约中' },
                             { value: 'COMPLETED', label: '已完成' },
                             { value: 'CANCELLED', label: '已取消' },
                             { value: 'EXCEPTION', label: '异常' },
                           ]" placeholder="选择状态" />
            </div>
            <div v-if="advFilterFieldsForTab.has('auditStatuses')" class="ff">
              <label>审核状态（多选）</label>
              <MultiSelect v-model="advFilters.auditStatuses"
                           :options="[
                             { value: 'PENDING', label: '待审核' },
                             { value: 'AUDITED', label: '已审核' },
                             { value: 'UNAUDITED', label: '未审核' },
                           ]" placeholder="选择审核状态" />
            </div>
            <div v-if="advFilterFieldsForTab.has('settlementStatuses')" class="ff">
              <label>结算状态（多选）</label>
              <MultiSelect v-model="advFilters.settlementStatuses"
                           :options="[
                             { value: 'UNSETTLED', label: '未结算' },
                             { value: 'SETTLED', label: '已结算' },
                             { value: 'PARTIAL', label: '部分结算' },
                             { value: 'VOID', label: '已作废' },
                           ]" placeholder="选择结算状态" />
            </div>
            <div v-if="advFilterFieldsForTab.has('sides')" class="ff">
              <label>方向（多选）</label>
              <MultiSelect v-model="advFilters.sides"
                           :options="[{ value: 'AR', label: '应收 AR' }, { value: 'AP', label: '应付 AP' }]"
                           placeholder="选择方向" />
            </div>
            <div v-if="advFilterFieldsForTab.has('currencies')" class="ff">
              <label>币种（多选）</label>
              <MultiSelect v-model="advFilters.currencies"
                           :options="(selectOptions['currencies'] ?? []).map((c: any) => ({ value: c.code || c.name, label: c.code || c.name }))"
                           placeholder="选择币种" />
            </div>
            <div v-if="advFilterFieldsForTab.has('branches')" class="ff">
              <label>分公司/分支（多选）</label>
              <MultiSelect v-model="advFilters.branches"
                           :options="(selectOptions['branches'] ?? []).map((c: any) => ({ value: c.id, label: c.name }))"
                           placeholder="选择分公司" />
            </div>
            <div v-if="advFilterFieldsForTab.has('sellers')" class="ff">
              <label>销售代表（多选）</label>
              <MultiSelect v-model="advFilters.sellers"
                           :options="(selectOptions['employees'] ?? []).map((c: any) => ({ value: c.id, label: c.name }))"
                           placeholder="选择销售代表" />
            </div>
            <div v-if="advFilterFieldsForTab.has('servicers')" class="ff">
              <label>客服代表（多选）</label>
              <MultiSelect v-model="advFilters.servicers"
                           :options="(selectOptions['employees'] ?? []).map((c: any) => ({ value: c.id, label: c.name }))"
                           placeholder="选择客服代表" />
            </div>
            <div v-if="advFilterFieldsForTab.has('warehouses')" class="ff">
              <label>仓库/站点（多选）</label>
              <MultiSelect v-model="advFilters.warehouses"
                           :options="(selectOptions['warehouses'] ?? []).map((c: any) => ({ value: c.code || c.id, label: c.name }))"
                           placeholder="选择仓库" />
            </div>
            <div v-if="advFilterFieldsForTab.has('carriers')" class="ff">
              <label>承运快递（多选）</label>
              <MultiSelect v-model="advFilters.carriers"
                           :options="[
                             { value: 'UPS', label: 'UPS' },
                             { value: 'FEDEX', label: 'FedEx' },
                             { value: 'DHL', label: 'DHL' },
                             { value: 'USPS', label: 'USPS' },
                             { value: 'TNT', label: 'TNT' },
                           ]" placeholder="选择承运" />
            </div>
            <div v-if="advFilterFieldsForTab.has('addNames')" class="ff">
              <label>添加人（多选）</label>
              <MultiSelect v-model="advFilters.addNames"
                           :options="(selectOptions['employees'] ?? []).map((c: any) => ({ value: c.name, label: c.name }))"
                           placeholder="选择添加人" />
            </div>

            <!-- ▼ 文本单值 ▼ -->
            <div v-if="advFilterFieldsForTab.has('postcode')" class="ff">
              <label>邮编</label>
              <input type="text" v-model="advFilters.postcode" placeholder="收件邮编" />
            </div>
            <div v-if="advFilterFieldsForTab.has('trackingNo')" class="ff">
              <label>跟踪号</label>
              <input type="text" v-model="advFilters.trackingNo" placeholder="UPS/FedEx 单号" />
            </div>
            <div v-if="advFilterFieldsForTab.has('recipientName')" class="ff">
              <label>收件人</label>
              <input type="text" v-model="advFilters.recipientName" placeholder="收件人姓名" />
            </div>
            <div v-if="advFilterFieldsForTab.has('vatNo')" class="ff">
              <label>VAT 号</label>
              <input type="text" v-model="advFilters.vatNo" placeholder="增值税号" />
            </div>
            <div v-if="advFilterFieldsForTab.has('poNumber')" class="ff">
              <label>PO 编号</label>
              <input type="text" v-model="advFilters.poNumber" placeholder="客户订单号" />
            </div>
            <div v-if="advFilterFieldsForTab.has('amazonRef')" class="ff">
              <label>亚马逊虚拟单号</label>
              <input type="text" v-model="advFilters.amazonRef" placeholder="亚马逊参考号" />
            </div>
            <div v-if="advFilterFieldsForTab.has('binLocation')" class="ff">
              <label>库位</label>
              <input type="text" v-model="advFilters.binLocation" placeholder="货架/库位" />
            </div>
            <div v-if="advFilterFieldsForTab.has('mainItem')" class="ff">
              <label>主品名</label>
              <input type="text" v-model="advFilters.mainItem" placeholder="货物主品名关键字" />
            </div>

            <!-- ▼ 时间/金额区间 ▼ -->
            <div v-if="advFilterFieldsForTab.has('createdFrom')" class="ff">
              <label>创建时间</label>
              <div style="display:flex;gap:4px;align-items:center">
                <input type="date" v-model="advFilters.createdFrom" />
                <span style="font-size:11px">至</span>
                <input type="date" v-model="advFilters.createdTo" />
              </div>
            </div>
            <div v-if="advFilterFieldsForTab.has('submittedFrom')" class="ff">
              <label>提交时间</label>
              <div style="display:flex;gap:4px;align-items:center">
                <input type="date" v-model="advFilters.submittedFrom" />
                <span style="font-size:11px">至</span>
                <input type="date" v-model="advFilters.submittedTo" />
              </div>
            </div>
            <div v-if="advFilterFieldsForTab.has('deliveredFrom')" class="ff">
              <label>签收时间</label>
              <div style="display:flex;gap:4px;align-items:center">
                <input type="date" v-model="advFilters.deliveredFrom" />
                <span style="font-size:11px">至</span>
                <input type="date" v-model="advFilters.deliveredTo" />
              </div>
            </div>
            <div v-if="advFilterFieldsForTab.has('amountFrom')" class="ff">
              <label>金额区间</label>
              <div style="display:flex;gap:4px;align-items:center">
                <input type="number" step="0.01" v-model="advFilters.amountFrom" placeholder="最小" />
                <span style="font-size:11px">~</span>
                <input type="number" step="0.01" v-model="advFilters.amountTo" placeholder="最大" />
              </div>
            </div>
          </div>
          <div class="adv-filter-actions">
            <button class="primary sm" @click="accSearch" :disabled="accLoading">
              <Search :size="13" /> 应用筛选
            </button>
            <button class="secondary sm" @click="clearAdvFilters(); accSearch()">
              清空所有
            </button>
            <button class="secondary sm" @click="saveCurrentAsPreset"
                    :disabled="activeAdvFilterCount() === 0"
                    title="把当前筛选条件保存为预设方案，下次一键加载">
              💾 保存为方案
            </button>
            <span style="position:relative" v-if="presetsForCurrentTab.length">
              <button class="secondary sm" @click="showPresetMenu = !showPresetMenu">
                📂 加载方案 ({{ presetsForCurrentTab.length }}) ▾
              </button>
              <div v-if="showPresetMenu" class="preset-menu" @click.stop>
                <div v-for="p in presetsForCurrentTab" :key="p.name" class="preset-item">
                  <span class="preset-star" :class="{ active: p.isDefault }"
                        @click="toggleDefaultPreset(p)"
                        :title="p.isDefault ? '取消默认（打开 tab 不再自动套用）' : '设为默认（打开 tab 自动套用）'">
                    {{ p.isDefault ? '★' : '☆' }}
                  </span>
                  <span class="preset-name" @click="applyPreset(p)">{{ p.name }}</span>
                  <span class="preset-del" @click="deletePreset(p)" title="删除">✕</span>
                </div>
              </div>
            </span>
            <span class="adv-filter-hint">当前生效 {{ activeAdvFilterCount() }} 个条件</span>
          </div>
        </div>

        <!-- ACC 5 个批量操作页面的专用面板（在表格上方） -->
        <!-- ACC 批量打印页面（扫描式 UI） -->
        <div v-if="isBatchPrintPage" class="acc-batch-print" style="background:#fff;border:1px solid #cbd5e1;border-radius:4px;margin-top:8px">
          <!-- 顶部扫描区 -->
          <div style="padding:14px 16px;background:#f8fafc;border-bottom:1px solid #cbd5e1;display:flex;align-items:center;gap:12px">
            <strong style="font-size:14px">扫描快件：</strong>
            <input type="text" v-model="printScanInput" @keyup.enter="doScanLookup"
                   placeholder="输入单号后回车（条码枪自动回车）" autofocus
                   style="flex:1;max-width:380px;padding:6px 10px;border:1px solid #93c5fd;border-radius:3px;font-size:14px" />
            <button class="primary sm" @click="doScanLookup" :disabled="bizLoading || !printScanInput">检索快件</button>
            <span style="margin-left:auto;font-size:13px">
              <strong>扫描数量：</strong>
              扫描:<span style="color:#2563eb">【{{ printScanCount }}】</span> 个 /
              成功:<span style="color:#059669">【{{ printScanSuccess }}】</span> 个
            </span>
          </div>
          <!-- 打印按钮组 -->
          <div style="padding:8px 16px;border-bottom:1px solid #e2e8f0;display:flex;gap:6px;flex-wrap:wrap">
            <button class="secondary sm" @click="printScanList('a4-all')" :disabled="!printScanRows.length">A4所有文档</button>
            <button class="secondary sm" @click="printScanList('label')" :disabled="!printScanRows.length">打印标签</button>
            <button class="secondary sm" @click="printScanList('invoice')" :disabled="!printScanRows.length">打印发票</button>
            <button class="secondary sm" @click="printScanList('battery-letter')" :disabled="!printScanRows.length">打印电池信</button>
            <button class="secondary sm" @click="printScanList('master-label')" :disabled="!printScanRows.length">多主单标签</button>
            <button class="secondary sm" @click="printScanList('invoice2-battery2')" :disabled="!printScanRows.length">2发票和2电池信</button>
            <button class="secondary sm" @click="printScanList('simple-label')" :disabled="!printScanRows.length">简易标签</button>
            <button class="secondary sm" @click="printScanList('handover')" :disabled="!printScanRows.length">交接清单</button>
            <button class="secondary sm" @click="clearScanList" :disabled="!printScanRows.length" style="margin-left:auto;color:#dc2626">清空列表</button>
          </div>
          <!-- 打印清单 -->
          <div style="background:#e0f2fe;border-bottom:1px solid #93c5fd;padding:6px 16px;font-weight:bold;text-align:center;font-size:13px">
            打印清单
          </div>
          <table class="data-table" style="width:100%;font-size:13px">
            <thead>
              <tr>
                <th style="width:50px">序号</th>
                <th>扫描单号</th>
                <th>运单号</th>
                <th>主单号</th>
                <th style="width:50px">选页</th>
                <th>客户</th>
                <th>重量</th>
                <th>件数</th>
                <th>目的地</th>
                <th>发货渠道</th>
                <th>参数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="printScanRows.length === 0">
                <td colspan="11" class="empty-cell">扫描或输入单号后回车开始</td>
              </tr>
              <tr v-for="(row, i) in printScanRows" :key="row.id">
                <td>{{ i + 1 }}</td>
                <td>{{ row.scanNo }}</td>
                <td>{{ row.orderNo }}</td>
                <td>{{ row.masterNo }}</td>
                <td><input type="number" v-model.number="row.pageNo" min="1" style="width:50px" /></td>
                <td>{{ row.customerName }}</td>
                <td>{{ row.weight }}</td>
                <td>{{ row.pieces ?? '-' }}</td>
                <td>{{ row.country }}</td>
                <td>{{ row.product }}</td>
                <td>{{ zhStatus(row.status) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ACC 批量操作页面（textarea 粘贴 + 预览 + 确认两步） -->
        <div v-if="batchPagePanel" class="acc-batch-page" style="background:#fff;border:1px solid #cbd5e1;border-radius:4px;margin-top:8px">
          <div style="background:#e0f2fe;border-bottom:1px solid #93c5fd;padding:10px 16px;font-weight:bold;text-align:center">
            {{ batchPageMeta?.title }}
          </div>
          <!-- 输入区 -->
          <div style="padding:16px;border-bottom:1px solid #e2e8f0">
            <div style="display:flex;align-items:flex-start;gap:12px;margin-bottom:10px">
              <label style="width:80px;text-align:right;padding-top:4px;font-size:13px">{{ accTab === 'orders-batch-track' ? '单号/转单号：' : '转单号：' }}</label>
              <div style="flex:1">
                <textarea v-model="batchInputText"
                          :placeholder="batchPageMeta?.placeholder"
                          rows="8"
                          style="width:100%;font-family:monospace;font-size:13px;padding:6px;border:1px solid #cbd5e1;border-radius:3px"></textarea>
                <div style="font-size:12px;color:#64748b;margin-top:4px">{{ batchPageMeta?.hint }}</div>
              </div>
            </div>
            <!-- 变更客户专属：新客户下拉 -->
            <div v-if="batchPageMeta?.needCustomer" style="display:flex;align-items:center;gap:12px;margin-bottom:10px">
              <label style="width:80px;text-align:right;font-size:13px">
                <span style="color:#dc2626">(*)</span>新客户：
              </label>
              <select v-model="batchTargetCustomerId" style="padding:4px 8px;min-width:220px">
                <option value="">请选择客户</option>
                <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
              </select>
            </div>
            <!-- 选项区 -->
            <div v-if="batchPageMeta?.showApplyCurrent || batchPageMeta?.showOverride" style="display:flex;align-items:center;gap:24px;margin-bottom:10px">
              <label style="width:80px;text-align:right;font-size:13px">其它选项：</label>
              <label v-if="batchPageMeta?.showApplyCurrent" style="font-size:13px">
                <input type="checkbox" v-model="batchApplyCurrentTime" /> 以当前时间计费
              </label>
              <label v-if="batchPageMeta?.showOverride" style="font-size:13px">
                <input type="checkbox" v-model="batchOverrideAudited" /> 覆盖已审费用
              </label>
            </div>
            <div style="margin-left:92px">
              <button class="primary sm" @click="doBatchPreview" :disabled="bizLoading">
                {{ batchPageMeta?.previewLabel ?? '更新预览' }}
              </button>
            </div>
          </div>

          <!-- 预览表格 -->
          <table class="data-table" style="width:100%">
            <thead>
              <tr>
                <th class="check-col">
                  <input type="checkbox"
                         :checked="batchPreviewRows.length > 0 && batchPreviewSelected.size === batchPreviewRows.length"
                         @change="batchPreviewSelected.size === batchPreviewRows.length ? batchPreviewSelectNone() : batchPreviewSelectAll()" />
                </th>
                <th>序号</th>
                <th>单号</th>
                <th>重量</th>
                <th>目的地</th>
                <th>销售产品</th>
                <th>状态</th>
                <template v-if="accTab === 'orders-update-tracking'">
                  <th>转单号</th>
                  <th>新转单号</th>
                </template>
                <template v-else-if="accTab === 'orders-update-weight'">
                  <th>原重量</th>
                  <th>新计费重</th>
                </template>
                <template v-else-if="accTab === 'orders-change-customer'">
                  <th>原客户</th>
                </template>
                <template v-else-if="accTab === 'orders-batch-track'">
                  <th>转单号</th>
                  <th>渠道类型</th>
                  <th>追踪轨迹</th>
                </template>
              </tr>
            </thead>
            <tbody>
              <tr v-if="batchPreviewRows.length === 0">
                <td :colspan="accTab === 'orders-batch-track' ? 10 : 9" class="empty-cell">输入单号后点击「更新预览」</td>
              </tr>
              <tr v-for="(row, i) in batchPreviewRows" :key="row.id">
                <td class="check-col">
                  <input type="checkbox"
                         :checked="batchPreviewSelected.has(row.id)"
                         @change="toggleBatchPreviewRow(row.id)" />
                </td>
                <td>{{ i + 1 }}</td>
                <td>{{ row.orderNo }}</td>
                <td>{{ row.weight }}</td>
                <td>{{ row.country }}</td>
                <td>{{ row.product }}</td>
                <td>{{ zhStatus(row.status) }}</td>
                <template v-if="accTab === 'orders-update-tracking'">
                  <td>{{ row.trackNo }}</td>
                  <td style="color:#dc2626;font-weight:bold">{{ row.newValue }}</td>
                </template>
                <template v-else-if="accTab === 'orders-update-weight'">
                  <td>{{ row.weight }}</td>
                  <td style="color:#dc2626;font-weight:bold">{{ row.newValue }}</td>
                </template>
                <template v-else-if="accTab === 'orders-change-customer'">
                  <td>{{ row.customerName }}</td>
                </template>
                <template v-else-if="accTab === 'orders-batch-track'">
                  <td>{{ row.trackNo }}</td>
                  <td>{{ row.product }}</td>
                  <td style="max-width:300px">
                    <div v-if="row.events && row.events.length > 0" style="font-size:11px;color:#64748b">
                      <div v-for="(ev, j) in row.events.slice(0, 3)" :key="j">
                        {{ ev.event_time }} - {{ ev.event_code }} ({{ ev.location }})
                      </div>
                    </div>
                    <span v-else style="color:#94a3b8">-</span>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>

          <!-- 底部 -->
          <div style="padding:12px 16px;border-top:1px solid #e2e8f0;display:flex;align-items:center;gap:16px">
            <span style="font-size:13px">
              <strong>选择：</strong>
              <a href="#" @click.prevent="batchPreviewSelectAll" style="color:#2563eb;margin:0 4px">全选</a> -
              <a href="#" @click.prevent="batchPreviewInvert" style="color:#2563eb;margin:0 4px">反选</a> -
              <a href="#" @click.prevent="batchPreviewSelectNone" style="color:#2563eb;margin:0 4px">不选</a>
            </span>
            <div style="flex:1;text-align:center">
              <button class="primary" @click="doBatchConfirm" :disabled="bizLoading || batchPreviewRows.length === 0">
                {{ batchPageMeta?.confirmLabel ?? '确认更新' }}
              </button>
              <button class="secondary" @click="cancelBatchPage" style="margin-left:8px">
                取消返回
              </button>
            </div>
          </div>
        </div>

        <!-- API 文档：内嵌 Swagger UI -->
        <div v-if="isApiDocsPage" style="background:#fff;border:1px solid #cbd5e1;border-radius:4px;margin-top:8px;height:calc(100vh - 220px);overflow:hidden">
          <iframe :src="apiDocsUrl" style="width:100%;height:100%;border:0" title="API 文档"></iframe>
        </div>

        <!-- 财务工作台 - 总览 dashboard (所有 fwb tab 都展示) -->
        <div v-if="accTab.startsWith('fwb-') && fwbDashboard"
             style="margin: 12px 0; display:flex; gap:10px; flex-wrap:wrap;">
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #ef4444; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">总应收(未付)</div>
            <div style="font-size:16px; font-weight:600; color:#ef4444;">{{ fwbDashboard.totalReceivable }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #10b981; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">本月已收</div>
            <div style="font-size:16px; font-weight:600; color:#10b981;">{{ fwbDashboard.paidThisMonth }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #f59e0b; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">预扣未对账</div>
            <div style="font-size:16px; font-weight:600; color:#f59e0b;">{{ fwbDashboard.prepayPending }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #dc2626; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">逾期 30 天+</div>
            <div style="font-size:16px; font-weight:600; color:#dc2626;">{{ fwbDashboard.overdueAmount }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #6366f1; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">待二审账单</div>
            <div style="font-size:16px; font-weight:600; color:#6366f1;">{{ fwbDashboard.pendingVerifyCount }} 张</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #0ea5e9; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">活跃客户</div>
            <div style="font-size:16px; font-weight:600; color:#0ea5e9;">{{ fwbDashboard.activeCustomers }}</div>
          </div>
        </div>

        <!-- 核算工作台 - 利润 dashboard (所有 swb tab 都展示) -->
        <div v-if="accTab.startsWith('swb-') && swbDashboard"
             style="margin: 12px 0; display:flex; gap:10px; flex-wrap:wrap;">
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #10b981; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">总应收 AR</div>
            <div style="font-size:16px; font-weight:600; color:#10b981;">{{ swbDashboard.totalAr }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #ef4444; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">总应付 AP</div>
            <div style="font-size:16px; font-weight:600; color:#ef4444;">{{ swbDashboard.totalAp }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #6366f1; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">总利润</div>
            <div style="font-size:16px; font-weight:600; color:#6366f1;">{{ swbDashboard.totalProfit }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #f59e0b; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">利润率</div>
            <div style="font-size:16px; font-weight:600; color:#f59e0b;">{{ swbDashboard.profitRate }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #10b981; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">本月 AR</div>
            <div style="font-size:16px; font-weight:600; color:#10b981;">{{ swbDashboard.monthAr }}</div>
          </div>
          <div style="background:#f1f5f9; padding:8px 12px; border-radius:6px; border-left:3px solid #6366f1; min-width:140px;">
            <div style="font-size:11px; color:#64748b;">本月利润</div>
            <div style="font-size:16px; font-weight:600; color:#6366f1;">{{ swbDashboard.monthProfit }}</div>
          </div>
        </div>

        <!-- 财务工作台 - 客户余额三段卡 (只在 fwb-prepay tab 选了客户时显示) -->
        <div v-if="accTab === 'fwb-prepay' && fwbBalance"
             style="margin: 12px 0; display:flex; gap:12px; flex-wrap:wrap;">
          <div style="background:#f8fafc; padding:10px 14px; border-radius:6px; border-left:4px solid #10b981; min-width:180px;">
            <div style="font-size:11px; color:#64748b;">可打单余额</div>
            <div style="font-size:18px; font-weight:600; color:#10b981; margin-top:2px;">{{ fwbBalance.usableBalance }} {{ fwbBalance.currency }}</div>
          </div>
          <div style="background:#f8fafc; padding:10px 14px; border-radius:6px; border-left:4px solid #f59e0b; min-width:180px;">
            <div style="font-size:11px; color:#64748b;">预扣明细余额</div>
            <div style="font-size:18px; font-weight:600; color:#f59e0b; margin-top:2px;">{{ fwbBalance.prepayDeductions }} {{ fwbBalance.currency }}</div>
          </div>
          <div style="background:#f8fafc; padding:10px 14px; border-radius:6px; border-left:4px solid #6366f1; min-width:180px;">
            <div style="font-size:11px; color:#64748b;">总账户 = 可打单 + 预扣</div>
            <div style="font-size:18px; font-weight:600; color:#6366f1; margin-top:2px;">{{ fwbBalance.totalAccount }} {{ fwbBalance.currency }}</div>
          </div>
          <div style="background:#f8fafc; padding:10px 14px; border-radius:6px; border-left:4px solid #ef4444; min-width:180px;">
            <div style="font-size:11px; color:#64748b;">已出账未付</div>
            <div style="font-size:18px; font-weight:600; color:#ef4444; margin-top:2px;">{{ fwbBalance.invoicedUnpaid }} {{ fwbBalance.currency }}</div>
          </div>
        </div>

        <!-- Data table (非批量页/打印页/API文档页才显示) -->
        <div class="acc-table-wrap" v-if="!batchPagePanel && !isBatchPrintPage && !isApiDocsPage">
          <table class="data-table" v-if="accColumns[accTab]">
            <thead>
              <tr>
                <th v-if="canSelect" class="check-col">
                  <input type="checkbox" @change="toggleSelectAll()" :checked="selectedIds.size > 0 && selectedIds.size === accData.length" />
                </th>
                <th v-for="col in accColumns[accTab]" :key="col.key">{{ col.label }}</th>
                <th v-if="accTab !== 'stowage-plans'" class="audit-status-col">审核状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="accLoading">
                <td :colspan="accColumns[accTab].length + (accTab === 'stowage-plans' ? 1 : 2) + (canSelect ? 1 : 0)" class="loading-cell">
                  <RefreshCw :size="16" class="spinning" /> 加载中...
                </td>
              </tr>
              <tr v-else-if="accData.length === 0">
                <td :colspan="accColumns[accTab].length + (accTab === 'stowage-plans' ? 1 : 2) + (canSelect ? 1 : 0)" class="empty-cell">暂无数据</td>
              </tr>
              <tr v-for="row in accData" :key="row.id ?? row.code ?? row.no"
                  :class="{
                    'audited-row': row.auditStatus === 'AUDITED',
                    'missing-cost-row': ordersTabSet.has(accTab) && Number(row.sellCharge||0) > 0 && Number(row.costCharge||0) === 0,
                  }" v-else>
                <td v-if="canSelect" class="check-col">
                  <input type="checkbox" :checked="selectedIds.has(row.id)" @change="toggleSelect(row.id)" />
                </td>
                <td v-for="col in accColumns[accTab]" :key="col.key"
                    :class="{ 'money-cell': col.fmt === 'money' || col.key === 'amount' }">
                  <!-- 应付 (costCharge) 列：缺成本时显示红色 ⚠ -->
                  <template v-if="col.key === 'costCharge' && Number(row.sellCharge||0) > 0 && Number(row.costCharge||0) === 0">
                    <span class="missing-cost-badge" title="此单有应收但无应付，请点击右侧「添加成本」">⚠ 缺成本</span>
                  </template>
                  <!-- amount 列：如有汇率快照，渲染 ¥X -> €Y 双币种 -->
                  <template v-else-if="col.key === 'amount' && row.targetCurrency && row.targetAmount != null">
                    {{ fmtMoney(row.amount, row.currency) }}
                    <span class="dual-currency"> → {{ fmtMoney(row.targetAmount, row.targetCurrency) }}</span>
                  </template>
                  <!-- 客户单号/订单号 列：可点击打开详情 -->
                  <template v-else-if="(col.key === 'orderNo' || col.key === 'order_no') && row[col.key]">
                    <a class="row-link" @click.prevent="viewDetail(row)">{{ row[col.key] }}</a>
                  </template>
                  <template v-else>{{ fmtCell(row[col.key], col.fmt) }}</template>
                </td>
                <td v-if="accTab !== 'stowage-plans'" class="audit-status-cell">
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
                  <!-- 3D 配载方案行内: 装柜单 PDF 下载 -->
                  <button class="action-btn fwb fwb-label"
                          v-if="accTab === 'stowage-plans' && row.id"
                          @click="downloadStowageSheet(row.id)"
                          title="下载装柜单 PDF">
                    <FileText :size="13" /> 装柜单
                  </button>
                  <button class="action-btn" v-if="accTab === 'stowages'" @click="doSyncStowage(row.id)" title="同步" :disabled="bizLoading">
                    <RefreshCw :size="12" />
                  </button>
                  <button class="action-btn" v-if="accTab === 'bills'" @click="doReloadBill(row.id)" title="重算" :disabled="bizLoading">
                    <Calculator :size="12" />
                  </button>
                  <!-- 下载面单：订单已提交（有 tracking）即可下载 -->
                  <button class="action-btn fwb fwb-label"
                          v-if="(accTab === 'orders' || accTab === 'orders-history') && row.status === 'SUBMITTED'"
                          @click="doDownloadLabel(row)" :disabled="bizLoading">
                    <FileText :size="13" /> 下载面单
                  </button>
                  <!-- 看订单财务：charges + ledger + 客户余额 -->
                  <button class="action-btn fwb fwb-finance"
                          v-if="(accTab === 'orders' || accTab === 'orders-history' || accTab === 'orders-cancelled') && row.id"
                          @click="doViewOrderFinance(row)" :disabled="bizLoading">
                    <Wallet :size="13" /> 看财务
                  </button>
                  <!-- 添加成本（缺 AP 时醒目；任何 SUBMITTED 后的订单都可补） -->
                  <button class="action-btn add-cost-btn"
                          v-if="ordersTabSet.has(accTab) && row.id && row.status !== 'DRAFT'"
                          :class="{ 'urgent': Number(row.sellCharge||0) > 0 && Number(row.costCharge||0) === 0 }"
                          @click="openAddCostDialog(row)" :disabled="bizLoading"
                          :title="Number(row.sellCharge||0) > 0 && Number(row.costCharge||0) === 0 ? '⚠ 此单缺应付成本，立即补录' : '为此单添加应付成本'">
                    <CreditCard :size="13" /> {{ Number(row.sellCharge||0) > 0 && Number(row.costCharge||0) === 0 ? '⚠ 添加成本' : '添加成本' }}
                  </button>
                  <!-- 财务工作台 预扣明细：调整金额 -->
                  <button class="action-btn fwb fwb-adjust"
                          v-if="accTab === 'fwb-prepay'"
                          @click="doAdjustCharge(row)" :disabled="bizLoading"
                          title="把报价金额改为实际成本（CSV 也可批量导入）">
                    <Calculator :size="13" /> 调整金额
                  </button>
                  <!-- 财务工作台 已出账：标记已付（手动核销扣减）-->
                  <button class="action-btn fwb fwb-pay"
                          v-if="accTab === 'fwb-invoiced' && row.invoice_status !== 'PAID'"
                          @click="doMarkInvoicePaid(row)" :disabled="bizLoading"
                          :title="row.invoice_status === 'PARTIAL_PAID' ? '继续付款（剩余 ' + row.invoice_unpaid + ')' : '收到客户付款 → 标记已付'">
                    <CheckCircle :size="13" />
                    {{ row.invoice_status === 'PARTIAL_PAID' ? '续付款' : '标记已付' }}
                  </button>
                  <!-- 财务工作台 已出账：反核销（PAID/PARTIAL → PENDING） -->
                  <button class="action-btn fwb fwb-unsettle"
                          v-if="accTab === 'fwb-invoiced' && (row.invoice_status === 'PAID' || row.invoice_status === 'PARTIAL_PAID')"
                          @click="doUnsettleInvoice(row)" :disabled="bizLoading"
                          title="撤销已付状态（标错了用）">
                    <Undo2 :size="13" /> 反核销
                  </button>
                  <!-- 财务工作台 已出账：退款（PAID/PARTIAL → 减 paid_amount + ledger REFUND）-->
                  <button class="action-btn fwb fwb-refund"
                          v-if="accTab === 'fwb-invoiced' && (row.invoice_status === 'PAID' || row.invoice_status === 'PARTIAL_PAID')"
                          @click="doRefundInvoice(row)" :disabled="bizLoading"
                          title="退钱给客户（写 ledger REFUND）">
                    <Coins :size="13" /> 退款
                  </button>
                  <!-- 财务工作台 待二审：二审通过（>5w 强制） -->
                  <button class="action-btn fwb fwb-verify"
                          v-if="accTab === 'fwb-needs-verify' && row.verify_status !== 'VERIFIED'"
                          @click="doVerifyOneBill(row)" :disabled="bizLoading"
                          title="主管二审通过（>5w 账单收款前必须）">
                    <CheckCircle :size="13" /> 二审通过
                  </button>
                  <!-- 财务工作台 已出账：作废账单 -->
                  <button class="action-btn fwb fwb-void"
                          v-if="accTab === 'fwb-invoiced' && row.invoice_status !== 'PAID' && row.invoice_status !== 'PARTIAL_PAID'"
                          @click="doVoidInvoice(row)" :disabled="bizLoading"
                          title="作废账单 → charges 回到待审核">
                    <XCircle :size="13" /> 作废
                  </button>
                  <!-- 财务工作台 已出账：打印/PDF -->
                  <button class="action-btn fwb fwb-print"
                          v-if="accTab === 'fwb-invoiced' && row.invoice_id"
                          @click="doPrintInvoice(row)" :disabled="bizLoading"
                          title="打开打印页 → Ctrl+P 另存 PDF 发给客户">
                    <FileText :size="13" /> 打印对账单
                  </button>
                  <!-- 财务工作台 待审核：撤销调整 -->
                  <button class="action-btn fwb fwb-undo"
                          v-if="accTab === 'fwb-pending'"
                          @click="doUnadjustCharge(row)" :disabled="bizLoading"
                          title="撤销刚才的金额调整 → 回到 ESTIMATED">
                    <Undo2 :size="13" /> 撤销调整
                  </button>
                  <!-- 财务工作台：charge 审计时间线 -->
                  <button class="action-btn fwb fwb-history"
                          v-if="(accTab === 'fwb-prepay' || accTab === 'fwb-pending') && row.id"
                          @click="doViewChargeHistory(row)" :disabled="bizLoading"
                          title="查看本条 charge 的全部变更记录">
                    <Clock :size="13" /> 审计
                  </button>
                  <!-- ACC 制单中心：申请作废 + 恢复（仅订单 tab）-->
                  <button class="action-btn"
                          v-if="(accTab === 'orders' || accTab === 'orders-history') && row.status !== 'CANCELLED' && row.status !== 'DRAFT'"
                          @click="doRequestVoid(row)" title="申请作废" :disabled="bizLoading"
                          style="color:#dc2626">
                    <MinusCircle :size="12" />
                  </button>
                  <button class="action-btn"
                          v-if="accTab === 'orders-cancelled' && row.status === 'CANCELLED'"
                          @click="doRestoreVoid(row)" title="恢复" :disabled="bizLoading"
                          style="color:#059669">
                    <Undo2 :size="12" />
                  </button>
                  <button class="action-btn del" v-if="canCrud && row.auditStatus !== 'AUDITED'"
                          @click="confirmDeleteRow(row)" title="删除">
                    <Trash2 :size="12" />
                  </button>
                </td>
              </tr>
            </tbody>
            <!-- 合计行：后端返 aggregations 时才显示 -->
            <tfoot v-if="accAggregations && Object.keys(accAggregations).length > 0">
              <tr class="sum-row">
                <td v-if="canSelect"></td>
                <td v-for="(col, idx) in accColumns[accTab]" :key="col.key">
                  <strong v-if="idx === 0">合计</strong>
                  <strong v-else-if="accAggregations[col.key] != null" class="money-cell">
                    {{ fmtMoney(accAggregations[col.key], 'CNY') }}
                  </strong>
                </td>
                <td></td><td></td>
              </tr>
            </tfoot>
          </table>
        </div>

        <!-- Pagination -->
        <div class="pagination" v-if="!noPaginationTabs.has(accTab) && accTotal > 0 && !batchPagePanel && !isBatchPrintPage">
          <button @click="accPrev" :disabled="accPage <= 1">
            <ChevronLeft :size="14" /> 上一页
          </button>
          <span class="page-info">第 {{ accPage }} / {{ accTotalPages }} 页</span>
          <button @click="accNext" :disabled="accPage >= accTotalPages">
            下一页 <ChevronRight :size="14" />
          </button>
        </div>
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

    <!-- ════════ ACC 完整制单表单（对应 ACC「添加制单」）════════ -->
    <div class="modal-backdrop" v-if="showFullOrderForm" @click.self="showFullOrderForm = false">
      <div class="modal-dialog modal-xl">
        <div class="modal-header">
          <h3>新增 快件订单（完整制单）</h3>
          <button class="modal-close" @click="showFullOrderForm = false"><X :size="18" /></button>
        </div>
        <!-- 章节快速跳转条 (下方表单太长,先给用户指路) -->
        <div class="form-anchor-bar">
          <a href="#sec-basic" @click.prevent="scrollFormTo('sec-basic')">基本信息</a>
          <a href="#sec-channel" @click.prevent="scrollFormTo('sec-channel')">发货渠道</a>
          <a href="#sec-cargo" @click.prevent="scrollFormTo('sec-cargo')">货物信息</a>
          <a href="#sec-invoice" @click.prevent="scrollFormTo('sec-invoice')">发票信息</a>
          <a href="#sec-receiver" @click.prevent="scrollFormTo('sec-receiver')">收件人</a>
          <a href="#sec-shipper" @click.prevent="scrollFormTo('sec-shipper')">发件人</a>
          <a href="#sec-importer" @click.prevent="scrollFormTo('sec-importer')">进口商</a>
          <a href="#sec-declare" @click.prevent="scrollFormTo('sec-declare')" class="anchor-warn">⚠ 申报明细 (HS 编码/数量/货值)</a>
          <a href="#sec-package" @click.prevent="scrollFormTo('sec-package')">装箱单</a>
        </div>
        <div class="modal-body" ref="fullOrderModalBody">
          <div class="error-bar" v-if="fullOrderError">{{ fullOrderError }}</div>

          <!-- 基本信息 -->
          <a id="sec-basic"></a>
          <div class="form-section">
            <h4>基本信息</h4>
            <div class="form-grid">
              <div class="form-field"><label>客户单号 <span class="required">*</span></label><input type="text" v-model="fullOrderData.orderNo" /></div>
              <div class="form-field"><label>日期 <span class="required">*</span></label><input type="date" v-model="fullOrderData.orderDate" /></div>
              <div class="form-field">
                <label>客户 <span class="required">*</span></label>
                <select v-model="fullOrderData.customerId">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
            </div>
          </div>

          <!-- 发货渠道 -->
          <a id="sec-channel"></a>
          <div class="form-section">
            <h4>发货渠道</h4>
            <div class="form-grid">
              <div class="form-field">
                <label>发货产品</label>
                <select v-model="fullOrderData.product">
                  <option value="">请选择产品</option>
                  <option v-for="opt in (selectOptions['channels'] ?? [])" :key="opt.id" :value="opt.code || opt.name">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>制单账号</label>
                <select v-model="fullOrderData.channelAccount">
                  <option value="">请选择制单账号</option>
                  <option v-for="opt in (selectOptions['channel-accounts'] ?? [])" :key="opt.id" :value="opt.code || opt.name">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>包裹类型</label>
                <div class="radio-row">
                  <label><input type="radio" value="DOCUMENT" v-model="fullOrderData.packageType" /> 文件</label>
                  <label><input type="radio" value="PARCEL" v-model="fullOrderData.packageType" /> 包裹</label>
                </div>
              </div>
              <div class="form-field">
                <label>电池选项</label>
                <select v-model.number="fullOrderData.batteryType">
                  <option :value="0">不带电</option>
                  <option :value="1">内置电池</option>
                  <option :value="2">干电池</option>
                </select>
              </div>
              <div class="form-field full-width">
                <label>特殊货物</label>
                <div class="radio-row">
                  <label><input type="radio" :value="0" v-model.number="fullOrderData.specialType" /> 普货</label>
                  <label><input type="radio" :value="5" v-model.number="fullOrderData.specialType" /> 仿牌</label>
                  <label><input type="radio" :value="1" v-model.number="fullOrderData.specialType" /> 特殊产品</label>
                  <label><input type="radio" :value="2" v-model.number="fullOrderData.specialType" /> 港发件</label>
                  <label><input type="radio" :value="3" v-model.number="fullOrderData.specialType" /> 报关件</label>
                  <label><input type="radio" :value="4" v-model.number="fullOrderData.specialType" /> 纺织品</label>
                </div>
              </div>
              <div class="form-field">
                <label>标签类型</label>
                <div class="radio-row">
                  <label><input type="radio" value="PDF" v-model="fullOrderData.labelType" /> PDF</label>
                  <label><input type="radio" value="ZPL" v-model="fullOrderData.labelType" /> ZPL</label>
                </div>
              </div>
            </div>
          </div>

          <!-- 货物信息 -->
          <a id="sec-cargo"></a>
          <div class="form-section">
            <h4>货物信息 <span class="form-hint">— 海关编码 / 申报数量 / 货值 在下方 <a class="row-link" @click.prevent="scrollFormTo('sec-declare')">⚠ 申报明细</a> 表格里填</span></h4>
            <div class="form-grid">
              <div class="form-field full-width"><label>英文品名 <span class="required">*</span></label><input type="text" v-model="fullOrderData.materialsEn" placeholder="英文品名 (ACC MaterialsEN)" /></div>
              <div class="form-field full-width"><label>中文品名</label><input type="text" v-model="fullOrderData.materialsCn" placeholder="中文品名 (ACC MaterialsCN)" /></div>
              <div class="form-field"><label>件数</label><input type="number" v-model.number="fullOrderData.piece" /></div>
              <div class="form-field"><label>重量 (kg)</label><input type="number" step="any" v-model.number="fullOrderData.weight" /></div>
              <div class="form-field"><label>体积 (m³)</label><input type="number" step="any" v-model.number="fullOrderData.volume" /></div>
              <div class="form-field">
                <label>电池代码</label>
                <select v-model="fullOrderData.batteryCode">
                  <option value="">无电池</option>
                  <option value="UN3480">UN3480 锂电池单独</option>
                  <option value="UN3481">UN3481 锂电池随附</option>
                  <option value="UN3090">UN3090 锂金属电池</option>
                  <option value="UN3091">UN3091 锂金属电池随附</option>
                </select>
              </div>
            </div>
          </div>

          <!-- 发票信息 -->
          <a id="sec-invoice"></a>
          <div class="form-section">
            <h4>发票信息</h4>
            <div class="form-grid">
              <div class="form-field full-width">
                <label>附加服务</label>
                <div class="checkbox-row">
                  <label><input type="checkbox" value="PREPAID_DUTY" v-model="fullOrderData.services" /> 预付关税</label>
                  <label><input type="checkbox" value="SIGN_CONFIRM" v-model="fullOrderData.services" /> 签收确认</label>
                  <label><input type="checkbox" value="ADULT_SIGN" v-model="fullOrderData.services" /> 成人签收确认</label>
                </div>
              </div>
              <div class="form-field">
                <label>货币</label>
                <select v-model="fullOrderData.currency">
                  <option value="USD">USD</option>
                  <option value="CNY">CNY</option>
                  <option value="EUR">EUR</option>
                  <option value="HKD">HKD</option>
                  <option value="GBP">GBP</option>
                  <option value="JPY">JPY</option>
                </select>
              </div>
              <div class="form-field"><label>货物金额</label><input type="number" step="any" v-model.number="fullOrderData.declaredValue" /></div>
              <div class="form-field"><label>运费 (Freight)</label><input type="number" step="any" v-model.number="fullOrderData.freight" /></div>
              <div class="form-field"><label>保险 (Insurance)</label><input type="number" step="any" v-model.number="fullOrderData.insurance" /></div>
              <div class="form-field full-width"><label>备注</label><textarea v-model="fullOrderData.remark" rows="2" /></div>
            </div>
          </div>

          <!-- 收件人 -->
          <a id="sec-receiver"></a>
          <div class="form-section">
            <h4>收件人</h4>
            <div class="form-grid">
              <div class="form-field">
                <label>仓库地址</label>
                <select v-model="fullOrderData.receiver.warehouseCode">
                  <option value="">请选择仓库地址</option>
                  <option v-for="opt in (selectOptions['warehouses'] ?? [])" :key="opt.id" :value="opt.code || opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>目的地 <span class="required">*</span></label>
                <select v-model="fullOrderData.country">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['countries'] ?? [])" :key="opt.id" :value="opt.code || opt.name">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field"><label>目的地代码</label><input type="text" v-model="fullOrderData.receiver.areaCode" placeholder="输入后自动填充国家邮编" /></div>
              <div class="form-field"><label>公司 <span class="required">*</span></label><input type="text" v-model="fullOrderData.receiver.company" /></div>
              <div class="form-field"><label>收件人 <span class="required">*</span></label><input type="text" v-model="fullOrderData.receiver.name" /></div>
              <div class="form-field"><label>电话 <span class="required">*</span></label><input type="text" v-model="fullOrderData.receiver.phone" /></div>
              <div class="form-field"><label>省/洲</label><input type="text" v-model="fullOrderData.receiver.province" /></div>
              <div class="form-field"><label>邮编</label><input type="text" v-model="fullOrderData.receiver.postcode" /></div>
              <div class="form-field"><label>城市</label><input type="text" v-model="fullOrderData.receiver.city" /></div>
              <div class="form-field"><label>税号 (VAT)</label><input type="text" v-model="fullOrderData.receiver.vat" /></div>
              <div class="form-field full-width"><label>地址 <span class="required">*</span></label><input type="text" v-model="fullOrderData.receiver.address" /></div>
              <div class="form-field"><label>门牌号</label><input type="text" v-model="fullOrderData.receiver.houseNo" /></div>
            </div>
          </div>

          <!-- 发件人 -->
          <a id="sec-shipper"></a>
          <div class="form-section">
            <h4>发件人 <span style="color:#c00;font-size:12px;font-weight:normal">（为空则按默认）</span></h4>
            <div class="form-grid">
              <div class="form-field"><label>发件公司</label><input type="text" v-model="fullOrderData.shipper.company" /></div>
              <div class="form-field"><label>发件人</label><input type="text" v-model="fullOrderData.shipper.name" /></div>
              <div class="form-field"><label>电话</label><input type="text" v-model="fullOrderData.shipper.phone" /></div>
              <div class="form-field"><label>省/洲</label><input type="text" v-model="fullOrderData.shipper.province" /></div>
              <div class="form-field"><label>邮编</label><input type="text" v-model="fullOrderData.shipper.postcode" /></div>
              <div class="form-field"><label>城市</label><input type="text" v-model="fullOrderData.shipper.city" /></div>
              <div class="form-field"><label>税号</label><input type="text" v-model="fullOrderData.shipper.vat" /></div>
              <div class="form-field full-width"><label>发件地址</label><input type="text" v-model="fullOrderData.shipper.address" /></div>
            </div>
          </div>

          <!-- 进口商 -->
          <a id="sec-importer"></a>
          <div class="form-section">
            <h4>进口商 (IOR)</h4>
            <div class="form-grid">
              <div class="form-field">
                <label>预设模板</label>
                <select v-model="fullOrderData.shipTo.templateId" @change="applyImporterTemplate">
                  <option value="">请选择预设模板</option>
                  <option v-for="opt in (selectOptions['importer-templates'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field"><label>公司名称</label><input type="text" v-model="fullOrderData.shipTo.company" /></div>
              <div class="form-field"><label>联系人</label><input type="text" v-model="fullOrderData.shipTo.name" /></div>
              <div class="form-field"><label>电话</label><input type="text" v-model="fullOrderData.shipTo.phone" /></div>
              <div class="form-field"><label>洲/省</label><input type="text" v-model="fullOrderData.shipTo.province" /></div>
              <div class="form-field"><label>邮编</label><input type="text" v-model="fullOrderData.shipTo.postcode" /></div>
              <div class="form-field"><label>城市</label><input type="text" v-model="fullOrderData.shipTo.city" /></div>
              <div class="form-field"><label>税号</label><input type="text" v-model="fullOrderData.shipTo.vat" /></div>
              <div class="form-field full-width"><label>地址</label><input type="text" v-model="fullOrderData.shipTo.address" /></div>
            </div>
          </div>

          <!-- 申报明细 -->
          <a id="sec-declare"></a>
          <div class="form-section">
            <h4>申报明细 <button class="primary sm" type="button" @click="addDeclareRow"><Plus :size="13" /> 添加商品</button></h4>
            <table class="inline-table">
              <thead><tr>
                <th>序号</th><th>英文品名</th><th>中文品名</th><th>原产地</th>
                <th>数量</th><th>单价</th><th>小计</th><th>海关编码</th><th>备注</th><th></th>
              </tr></thead>
              <tbody>
                <tr v-for="(row, i) in fullOrderData.declare" :key="i">
                  <td>{{ i + 1 }}</td>
                  <td><input type="text" v-model="row.name" /></td>
                  <td><input type="text" v-model="row.cnName" /></td>
                  <td><input type="text" v-model="row.origin" /></td>
                  <td><input type="number" v-model.number="row.quantity" /></td>
                  <td><input type="number" step="any" v-model.number="row.price" /></td>
                  <td>{{ ((row.quantity || 0) * (row.price || 0)).toFixed(2) }}</td>
                  <td><input type="text" v-model="row.hsCode" /></td>
                  <td><input type="text" v-model="row.remark" /></td>
                  <td><button class="danger-btn sm" type="button" @click="removeDeclareRow(i)" v-if="fullOrderData.declare.length > 1"><X :size="13" /></button></td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 装箱单明细 -->
          <a id="sec-package"></a>
          <div class="form-section">
            <h4>装箱单明细 <button class="primary sm" type="button" @click="addPackageRow"><Plus :size="13" /> 添加装箱</button></h4>
            <table class="inline-table">
              <thead><tr>
                <th>序号</th><th>装箱单号</th><th>货箱重量</th><th>英文品名</th><th>中文品名</th>
                <th>海关编码</th><th>商品毛重</th><th>长</th><th>宽</th><th>高</th>
                <th>数量</th><th>单价</th><th>材质</th><th></th>
              </tr></thead>
              <tbody>
                <tr v-for="(row, i) in fullOrderData.packageList" :key="i">
                  <td>{{ i + 1 }}</td>
                  <td><input type="text" v-model="row.no" /></td>
                  <td><input type="number" step="any" v-model.number="row.weight" /></td>
                  <td><input type="text" v-model="row.name" /></td>
                  <td><input type="text" v-model="row.cnName" /></td>
                  <td><input type="text" v-model="row.hsCode" /></td>
                  <td><input type="number" step="any" v-model.number="row.grossWeight" /></td>
                  <td><input type="number" step="any" v-model.number="row.length" /></td>
                  <td><input type="number" step="any" v-model.number="row.width" /></td>
                  <td><input type="number" step="any" v-model.number="row.height" /></td>
                  <td><input type="number" v-model.number="row.quantity" /></td>
                  <td><input type="number" step="any" v-model.number="row.price" /></td>
                  <td><input type="text" v-model="row.material" /></td>
                  <td><button class="danger-btn sm" type="button" @click="removePackageRow(i)" v-if="fullOrderData.packageList.length > 1"><X :size="13" /></button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showFullOrderForm = false">取消</button>
          <button class="primary" @click="saveFullOrder" :disabled="fullOrderSaving">
            <RefreshCw v-if="fullOrderSaving" :size="14" class="spinning" />
            <Save v-else :size="14" />
            {{ fullOrderSaving ? '保存中...' : '确认添加' }}
          </button>
        </div>
      </div>
    </div>

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
            <template v-for="field in currentFormFields" :key="field.col">
              <!-- ACC section header（type='section' 用作分组分隔）-->
              <div v-if="field.type === 'section'" class="form-section-header full-width">
                {{ field.label }}
              </div>
              <div v-else class="form-field"
                   :class="{ 'full-width': field.type === 'textarea' || field.type === 'radio' }">
                <label>
                  <span v-if="field.required" class="required-star">*</span>
                  {{ field.label }}
                </label>
                <input v-if="field.type === 'text'" type="text" v-model="formData[field.col]" />
                <input v-else-if="field.type === 'number'" type="number" step="any" v-model.number="formData[field.col]" />
                <input v-else-if="field.type === 'date'" type="date" v-model="formData[field.col]" />
                <textarea v-else-if="field.type === 'textarea'" v-model="formData[field.col]" rows="3" />
                <!-- ACC 风格 radio buttons：与 select+opts 同结构但视觉是 radio -->
                <div v-else-if="field.type === 'radio' && field.opts" class="radio-group">
                  <label v-for="opt in field.opts" :key="opt.v" class="radio-item"
                         :class="{ active: formData[field.col] === opt.v }">
                    <input type="radio" :name="'r_' + field.col" :value="opt.v" v-model="formData[field.col]" />
                    <span>{{ opt.l }}</span>
                  </label>
                </div>
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
            </template>
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
                <select v-model="bizDialogData.customer_id">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>币种</label>
                <select v-model="bizDialogData.currency">
                  <option v-for="opt in (selectOptions['currencies'] ?? [])" :key="opt.id" :value="opt.code">{{ opt.code }}</option>
                </select>
              </div>
              <div class="form-field"><label>起始日期</label><input type="date" v-model="bizDialogData.date_from" /></div>
              <div class="form-field"><label>截止日期</label><input type="date" v-model="bizDialogData.date_to" /></div>
            </template>
            <template v-if="bizDialogType === 'quick-payment'">
              <div class="form-field">
                <label>客户 <span class="required">*</span></label>
                <select v-model="bizDialogData.customer_id">
                  <option value="">请选择</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>币种</label>
                <select v-model="bizDialogData.currency">
                  <option v-for="opt in (selectOptions['currencies'] ?? [])" :key="opt.id" :value="opt.code">{{ opt.code }}</option>
                </select>
              </div>
              <div class="form-field"><label>收款金额 <span class="required">*</span></label><input type="number" step="any" v-model.number="bizDialogData.settled_amount" /></div>
              <div class="form-field">
                <label>银行账户</label>
                <select v-model="bizDialogData.bank_account_id">
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
      <div class="modal-dialog" :style="detailType === 'order-detail' ? 'max-width: 1200px;' : detailType === 'stowage-3d' ? 'max-width: 1400px;' : 'max-width: 900px;'">
        <div class="modal-header">
          <h3>{{ detailType === 'shipment-items' ? '出货明细' : detailType === 'bill-items' ? '账单明细' : detailType === 'stowage-packages' ? '配载包裹' : detailType === 'commission-result' ? '提成计算结果' : detailType === 'profit-summary' ? '利润汇总报表' : detailType === 'order-detail' ? '订单详情' : detailType === 'stowage-3d' ? '3D 配载方案立体视图' : '记录详情' }}</h3>
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
          <div v-if="detailType === 'profit-summary' && detailData && detailData.groups">
            <div v-if="!detailData.adjustmentsSupported" class="profit-summary-hint">
              ⚠️ 当前维度（{{ detailData.groupBy }}）的调整项（finance_txns / fines）无可分摊键，
              利润仅包含 AR/AP/赔偿。如需含调整项，请切到 客户 或 日 维度。
            </div>
            <table class="data-table">
              <thead><tr>
                <th>分组</th><th>票数</th>
                <th>营收</th><th>成本</th><th>调整项</th><th>赔偿</th><th>利润</th>
              </tr></thead>
              <tbody>
                <tr v-for="item in detailData.groups" :key="item.label">
                  <td>{{ item.label }}</td>
                  <td>{{ item.count }}</td>
                  <td class="money-cell">¥{{ fmt(item.revenue) }}</td>
                  <td class="money-cell">¥{{ fmt(item.cost) }}</td>
                  <td class="money-cell" :class="Number(item.adjustments) >= 0 ? 'positive' : 'negative'">
                    {{ Number(item.adjustments) >= 0 ? '+' : '' }}¥{{ fmt(item.adjustments) }}
                  </td>
                  <td class="money-cell negative">-¥{{ fmt(item.reparation) }}</td>
                  <td class="money-cell" :class="Number(item.profit) >= 0 ? 'positive' : 'negative'">
                    ¥{{ fmt(item.profit) }}
                  </td>
                </tr>
                <tr v-if="detailData.groups.length === 0">
                  <td colspan="7" class="empty-cell">无数据</td>
                </tr>
              </tbody>
            </table>
          </div>
          <!-- ACC 风格订单详情 (9 个分区) -->
          <div v-if="detailType === 'order-detail' && detailData" class="order-detail">
            <!-- 1. 基本信息 -->
            <section class="od-section">
              <h4 class="od-title">基本信息</h4>
              <div class="od-grid">
                <div><label>订单号</label><span>{{ detailData.basic?.order_no || '-' }}</span></div>
                <div><label>状态</label><span>{{ zhStatus(detailData.basic?.status) }}</span></div>
                <div><label>客户</label><span>{{ detailData.basic?.customer_name || '-' }} ({{ detailData.basic?.customer_code || '-' }})</span></div>
                <div><label>客户单号</label><span>{{ detailData.basic?.customer_ref || '-' }}</span></div>
                <div><label>来源</label><span>{{ ({LOCAL:'本地',ACC:'ACC',XQT:'XQT',API:'API',IMPORT:'导入'} as any)[detailData.basic?.source] || (detailData.basic?.source || '-') }}</span></div>
                <div><label>分店</label><span>{{ detailData.basic?.branch_name || '-' }}</span></div>
                <div><label>创建时间</label><span>{{ (detailData.basic?.created_at || '').slice(0,19).replace('T',' ') }}</span></div>
                <div><label>提交时间</label><span>{{ (detailData.basic?.submitted_at || '').slice(0,19).replace('T',' ') || '-' }}</span></div>
                <div><label>受理时间</label><span>{{ (detailData.basic?.accepted_at || '').slice(0,19).replace('T',' ') || '-' }}</span></div>
                <div><label>完成时间</label><span>{{ (detailData.basic?.completed_at || '').slice(0,19).replace('T',' ') || '-' }}</span></div>
              </div>
            </section>

            <!-- 2. 货物/发货信息 (metadata) -->
            <section class="od-section" v-if="detailData.metadata">
              <h4 class="od-title">货物 / 发货信息</h4>
              <div class="od-grid">
                <div><label>英文品名</label><span>{{ detailData.metadata.materialsEn || detailData.metadata.materials_en || '-' }}</span></div>
                <div><label>中文品名</label><span>{{ detailData.metadata.materialsCn || detailData.metadata.materials_cn || '-' }}</span></div>
                <div><label>件数</label><span>{{ detailData.metadata.piece || '-' }}</span></div>
                <div><label>重量(kg)</label><span>{{ detailData.metadata.weight || '-' }}</span></div>
                <div><label>体积(m³)</label><span>{{ detailData.metadata.volume || '-' }}</span></div>
                <div><label>货物金额</label><span>{{ detailData.metadata.declaredValue || detailData.metadata.declared_value || '-' }} {{ detailData.metadata.currency || '' }}</span></div>
                <div><label>运费</label><span>{{ detailData.metadata.freight || '-' }}</span></div>
                <div><label>保险</label><span>{{ detailData.metadata.insurance || '-' }}</span></div>
                <div><label>发货产品</label><span>{{ detailData.metadata.product || '-' }}</span></div>
                <div><label>制单账号</label><span>{{ detailData.metadata.channelAccount || detailData.metadata.channel_account || '-' }}</span></div>
                <div><label>包裹类型</label><span>{{ detailData.metadata.packageType || '-' }}</span></div>
                <div><label>电池</label><span>{{ detailData.metadata.batteryCode || '-' }} ({{ ['不带电','内置电池','干电池'][detailData.metadata.batteryType || 0] }})</span></div>
                <div><label>特殊货物</label><span>{{ ['普货','特殊产品','港发件','报关件','纺织品','仿牌'][detailData.metadata.specialType || 0] }}</span></div>
                <div><label>标签</label><span>{{ detailData.metadata.labelType || '-' }}</span></div>
                <div class="full-width" v-if="detailData.metadata.services?.length"><label>附加服务</label><span>{{ (detailData.metadata.services || []).join(', ') }}</span></div>
                <div class="full-width" v-if="detailData.metadata.remark"><label>备注</label><span>{{ detailData.metadata.remark }}</span></div>
              </div>
            </section>

            <!-- 3/4/5. 收件人/发件人/进口商 -->
            <section class="od-section" v-if="detailData.metadata?.receiver">
              <h4 class="od-title">收件人</h4>
              <div class="od-grid">
                <div><label>公司</label><span>{{ detailData.metadata.receiver.company || '-' }}</span></div>
                <div><label>姓名</label><span>{{ detailData.metadata.receiver.name || '-' }}</span></div>
                <div><label>电话</label><span>{{ detailData.metadata.receiver.phone || '-' }}</span></div>
                <div><label>国家</label><span>{{ detailData.metadata.country || '-' }}</span></div>
                <div><label>邮编</label><span>{{ detailData.metadata.receiver.postcode || '-' }}</span></div>
                <div><label>城市</label><span>{{ detailData.metadata.receiver.city || '-' }}</span></div>
                <div><label>省/洲</label><span>{{ detailData.metadata.receiver.province || '-' }}</span></div>
                <div><label>VAT</label><span>{{ detailData.metadata.receiver.vat || '-' }}</span></div>
                <div class="full-width"><label>地址</label><span>{{ detailData.metadata.receiver.address || '-' }}</span></div>
              </div>
            </section>

            <section class="od-section" v-if="detailData.metadata?.shipper && detailData.metadata.shipper.company">
              <h4 class="od-title">发件人</h4>
              <div class="od-grid">
                <div><label>公司</label><span>{{ detailData.metadata.shipper.company || '-' }}</span></div>
                <div><label>姓名</label><span>{{ detailData.metadata.shipper.name || '-' }}</span></div>
                <div><label>电话</label><span>{{ detailData.metadata.shipper.phone || '-' }}</span></div>
                <div><label>邮编</label><span>{{ detailData.metadata.shipper.postcode || '-' }}</span></div>
                <div><label>城市</label><span>{{ detailData.metadata.shipper.city || '-' }}</span></div>
                <div><label>VAT</label><span>{{ detailData.metadata.shipper.vat || '-' }}</span></div>
                <div class="full-width"><label>地址</label><span>{{ detailData.metadata.shipper.address || '-' }}</span></div>
              </div>
            </section>

            <section class="od-section" v-if="detailData.metadata?.shipTo && detailData.metadata.shipTo.company">
              <h4 class="od-title">进口商 (IOR)</h4>
              <div class="od-grid">
                <div><label>公司</label><span>{{ detailData.metadata.shipTo.company || '-' }}</span></div>
                <div><label>联系人</label><span>{{ detailData.metadata.shipTo.name || '-' }}</span></div>
                <div><label>电话</label><span>{{ detailData.metadata.shipTo.phone || '-' }}</span></div>
                <div><label>税号</label><span>{{ detailData.metadata.shipTo.vat || '-' }}</span></div>
                <div class="full-width"><label>地址</label><span>{{ detailData.metadata.shipTo.address || '-' }}</span></div>
              </div>
            </section>

            <!-- 6. 申报明细 -->
            <section class="od-section">
              <h4 class="od-title">申报明细 ({{ (detailData.declarations || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>运单</th><th>品名</th><th>材质</th><th>HS编码</th><th>数量</th><th>申报价值</th></tr></thead>
                <tbody>
                  <tr v-for="(d, i) in (detailData.declarations || [])" :key="'d'+i">
                    <td>{{ d.shipment_no }}</td><td>{{ d.item_name }}</td><td>{{ d.material || '-' }}</td>
                    <td>{{ d.hs_code || '-' }}</td><td>{{ d.quantity }}</td>
                    <td class="money-cell">{{ d.value_amount }}</td>
                  </tr>
                  <tr v-if="!(detailData.declarations || []).length"><td colspan="6" class="empty-cell">无申报明细</td></tr>
                </tbody>
              </table>
            </section>

            <!-- 7. 装箱单 -->
            <section class="od-section">
              <h4 class="od-title">装箱明细 ({{ (detailData.cartons || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>运单</th><th>箱号</th><th>子单号</th><th>主单号</th><th>实重(kg)</th><th>计费重</th><th>CBM</th><th>尺寸(cm)</th></tr></thead>
                <tbody>
                  <tr v-for="(c, i) in (detailData.cartons || [])" :key="'c'+i">
                    <td>{{ c.shipment_no }}</td><td>{{ c.carton_no }}</td>
                    <td>{{ c.tracking_no || '-' }}</td>
                    <td>{{ c.carrier_master_tracking_no || '-' }}</td>
                    <td>{{ c.actual_weight_kg }}</td>
                    <td>{{ c.chargeable_weight_kg || '-' }}</td>
                    <td>{{ c.cbm || '-' }}</td>
                    <td>{{ c.length_cm }}×{{ c.width_cm }}×{{ c.height_cm }}</td>
                  </tr>
                  <tr v-if="!(detailData.cartons || []).length"><td colspan="8" class="empty-cell">无装箱</td></tr>
                </tbody>
              </table>
            </section>

            <!-- 8. 财务: charges + ledger -->
            <section class="od-section">
              <h4 class="od-title">财务 — Charges ({{ (detailData.charges || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>方向</th><th>金额</th><th>币种</th><th>状态</th><th>审核</th><th>结算</th><th>已付</th><th>账单号</th></tr></thead>
                <tbody>
                  <tr v-for="(c, i) in (detailData.charges || [])" :key="'ch'+i">
                    <td>{{ zhStatus(c.side) }}</td>
                    <td class="money-cell">{{ c.amount }}</td>
                    <td>{{ c.currency }}</td>
                    <td>{{ zhStatus(c.status) }}</td>
                    <td>{{ c.audit_status || '-' }}</td>
                    <td>{{ c.settlement_status || '-' }}</td>
                    <td class="money-cell">{{ c.paid_amount || '0' }}</td>
                    <td>{{ c.invoice_no || '未出账' }}</td>
                  </tr>
                  <tr v-if="!(detailData.charges || []).length"><td colspan="8" class="empty-cell">无 charges</td></tr>
                </tbody>
              </table>
              <h4 class="od-title" style="margin-top:12px">客户余额账本 (Ledger {{ (detailData.ledger || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>时间</th><th>类型</th><th>方向</th><th>金额</th><th>变动前</th><th>变动后</th><th>备注</th></tr></thead>
                <tbody>
                  <tr v-for="(l, i) in (detailData.ledger || [])" :key="'l'+i">
                    <td>{{ (l.created_at || '').slice(0,19).replace('T',' ') }}</td>
                    <td>{{ zhStatus(l.biz_type) }}</td>
                    <td>{{ zhStatus(l.direction) }}</td>
                    <td class="money-cell">{{ l.amount }}</td>
                    <td class="money-cell">{{ l.balance_before }}</td>
                    <td class="money-cell">{{ l.balance_after }}</td>
                    <td>{{ l.remark || '-' }}</td>
                  </tr>
                  <tr v-if="!(detailData.ledger || []).length"><td colspan="7" class="empty-cell">无 ledger 记录</td></tr>
                </tbody>
              </table>
            </section>

            <!-- 9. 物流跟踪 -->
            <section class="od-section">
              <h4 class="od-title">物流跟踪 ({{ (detailData.trackingEvents || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>时间</th><th>状态</th><th>原始状态</th><th>地点</th><th>跟踪号</th><th>来源</th></tr></thead>
                <tbody>
                  <tr v-for="(t, i) in (detailData.trackingEvents || [])" :key="'t'+i">
                    <td>{{ (t.event_time || '').slice(0,19).replace('T',' ') }}</td>
                    <td>{{ t.normalized_status }}</td>
                    <td>{{ t.raw_status }}</td>
                    <td>{{ t.location || '-' }}</td>
                    <td>{{ t.tracking_no }}</td>
                    <td>{{ t.source || '-' }}</td>
                  </tr>
                  <tr v-if="!(detailData.trackingEvents || []).length"><td colspan="6" class="empty-cell">无跟踪事件</td></tr>
                </tbody>
              </table>
            </section>

            <!-- 10. 审计日志 -->
            <section class="od-section">
              <h4 class="od-title">操作历史 ({{ (detailData.auditHistory || []).length }})</h4>
              <table class="data-table">
                <thead><tr><th>时间</th><th>动作</th><th>操作人</th><th>备注</th></tr></thead>
                <tbody>
                  <tr v-for="(a, i) in (detailData.auditHistory || [])" :key="'a'+i">
                    <td>{{ (a.occurred_at || '').slice(0,19).replace('T',' ') }}</td>
                    <td>{{ zhStatus(a.action) }}</td>
                    <td>{{ a.actor_name || '-' }}</td>
                    <td>{{ a.remark || '-' }}</td>
                  </tr>
                  <tr v-if="!(detailData.auditHistory || []).length"><td colspan="4" class="empty-cell">无审计记录</td></tr>
                </tbody>
              </table>
            </section>
          </div>

          <!-- Raw record -->
          <!-- 3D 配载立体视图 -->
          <StowagePlan3D
            v-if="detailType === 'stowage-3d' && detailData"
            :plan="detailData" />

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

    <!-- 3D 配载方案对话框 -->
    <div class="modal-backdrop" v-if="showStowageDialog" @click.self="showStowageDialog = false">
      <div class="modal-dialog" style="max-width:560px">
        <div class="modal-header">
          <h3>自动求解配载方案</h3>
          <button class="modal-close" @click="showStowageDialog = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div style="background:#dbeafe;color:#1e3a8a;padding:8px 12px;border-radius:4px;margin-bottom:12px;font-size:12px;line-height:1.6">
            💡 输入要装柜的 shipment IDs（系统会自动拉每个 shipment 下的 cartons），
            按柜规格用 3D Bin Packing + LIFO 求解，落 stowage_plans 表。
            <br/>客户路线写"A,B,C"表示 A 先卸 (LIFO 自动反向排进柜)。
          </div>
          <div class="form-grid">
            <div class="form-field full-width">
              <label>求解模式</label>
              <div class="radio-row">
                <label><input type="radio" value="single" v-model="stowageData.mode" /> 单柜 (一票货装一柜)</label>
                <label><input type="radio" value="multi" v-model="stowageData.mode" /> 多柜分配 (自动决定用几个柜)</label>
              </div>
            </div>
            <div class="form-field" v-if="stowageData.mode === 'single'">
              <label>柜号</label>
              <input type="text" v-model="stowageData.containerCode" />
            </div>
            <div class="form-field">
              <label>柜长(cm) <span class="required">*</span></label>
              <input type="number" v-model.number="stowageData.length_cm" />
            </div>
            <div class="form-field">
              <label>柜宽(cm) <span class="required">*</span></label>
              <input type="number" v-model.number="stowageData.width_cm" />
            </div>
            <div class="form-field">
              <label>柜高(cm) <span class="required">*</span></label>
              <input type="number" v-model.number="stowageData.height_cm" />
            </div>
            <div class="form-field">
              <label>载重上限(kg) <span class="required">*</span></label>
              <input type="number" v-model.number="stowageData.max_weight_kg" />
            </div>
            <div class="form-field">
              <label>启用 LIFO</label>
              <select v-model="stowageData.enableLifo">
                <option :value="true">是 (按 route 倒序装)</option>
                <option :value="false">否</option>
              </select>
            </div>
            <template v-if="stowageData.mode === 'multi'">
              <div class="form-field">
                <label>40HC 最多用几个</label>
                <input type="number" v-model.number="stowageData.multi40hcCount" min="0" max="20" />
              </div>
              <div class="form-field">
                <label>20GP 最多用几个</label>
                <input type="number" v-model.number="stowageData.multi20gpCount" min="0" max="20" />
              </div>
              <div class="form-field">
                <label>装柜余量系数 (0.5-1.0)</label>
                <input type="number" v-model.number="stowageData.packingFactor" min="0.5" max="1.0" step="0.05" />
              </div>
            </template>
            <div class="form-field full-width">
              <label>选 Shipments（推荐） <span class="required">*</span></label>
              <MultiSelect v-model="stowageSelectedShipments"
                           :options="stowageShipmentOptions"
                           placeholder="点击选择运单（支持搜索）" />
            </div>
            <div class="form-field full-width">
              <label>或手动输入 ID（备用）</label>
              <textarea v-model="stowageData.shipmentIds" rows="2"
                placeholder="可填: shipment.id / shipment_no / order.id / order_no, 多个用逗号/空格/换行分隔" />
            </div>
            <div class="form-field full-width">
              <label>客户路线 (customer_id 数组,逗号分隔；可选)</label>
              <input type="text" v-model="stowageData.route" placeholder="A,B,C — A 最先卸" />
            </div>
          </div>
        </div>
        <!-- 求解前提示行 - 若按钮 disabled 显示原因 -->
        <div v-if="stowageSolveBlockedReason" class="stowage-block-reason">
          ⚠ {{ stowageSolveBlockedReason }}
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showStowageDialog = false">取消</button>
          <button class="secondary" @click="doSmartGroupStowage" :disabled="bizLoading"
                  title="按「客户 + 目的国」自动分组，每组生成 1 个配载方案"
                  style="background:#fef3c7;color:#854d0e;border-color:#fde68a">
            ⚡ 智能分柜 (按客户+目的国分组)
          </button>
          <button class="primary" @click="doStowageSolve"
                  :disabled="!!stowageSolveBlockedReason"
                  :title="stowageSolveBlockedReason || '求解当前选中的运单'">
            <Boxes :size="14" /> 求解并落库
          </button>
        </div>
      </div>
    </div>

    <!-- 添加成本对话框 (AP 应付) -->
    <div class="modal-backdrop" v-if="showAddCostDialog" @click.self="showAddCostDialog = false">
      <div class="modal-dialog" style="max-width:560px">
        <div class="modal-header">
          <h3>{{ addCostStandalone ? '新增成本（自选订单）' : '添加应付成本 — ' + addCostOrderNo }}</h3>
          <button class="modal-close" @click="showAddCostDialog = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div style="background:#fef3c7;color:#854d0e;padding:8px 12px;border-radius:4px;margin-bottom:12px;font-size:12px;line-height:1.6">
            💡 添加的成本会落到「核算中心 → 待核成本」, 由财务一审通过后再走付款流程。
            <br/>常用费用类型: FREIGHT (运费) / FUEL (燃油) / REMOTE (偏远) / TAX (关税) / OTHER (其他)
          </div>
          <div class="form-grid">
            <!-- 财务中心独立入口：先选客户 → 选订单 -->
            <template v-if="addCostStandalone">
              <div class="form-field">
                <label>客户 <span class="required">*</span></label>
                <select v-model="addCostCustomerId" @change="loadOrdersForCustomer">
                  <option value="">请选择客户</option>
                  <option v-for="opt in (selectOptions['customers'] ?? [])"
                          :key="opt.id" :value="opt.id">{{ opt.name }}</option>
                </select>
              </div>
              <div class="form-field">
                <label>订单 <span class="required">*</span></label>
                <select v-model="addCostOrderId" @change="pickAddCostOrder(addCostOrderId)"
                        :disabled="!addCostCustomerId || !addCostOrderOptions.length">
                  <option value="">{{ addCostCustomerId ? (addCostOrderOptions.length ? '请选择订单' : '该客户无可补成本的订单') : '请先选客户' }}</option>
                  <option v-for="o in addCostOrderOptions" :key="o.id" :value="o.id">
                    {{ o.orderNo }} ({{ o.status }})
                  </option>
                </select>
              </div>
            </template>

            <div class="form-field">
              <label>成本金额 <span class="required">*</span></label>
              <input type="number" step="0.01" v-model="addCostData.amount" placeholder="如 35.50" />
            </div>
            <div class="form-field">
              <label>币种</label>
              <select v-model="addCostData.currency">
                <option value="CNY">CNY 人民币</option>
                <option value="USD">USD 美元</option>
                <option value="EUR">EUR 欧元</option>
                <option value="HKD">HKD 港币</option>
                <option value="GBP">GBP 英镑</option>
              </select>
            </div>
            <div class="form-field">
              <label>费用类型代码（可选）</label>
              <input type="text" v-model="addCostData.chargeItemCode" placeholder="FREIGHT / FUEL / REMOTE / TAX..." />
            </div>
            <div class="form-field full-width">
              <label>备注（可选）</label>
              <textarea v-model="addCostData.remark" rows="2" placeholder="对账说明，如「补录 UPS 7 月账单」" />
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showAddCostDialog = false">取消</button>
          <button class="primary" @click="doAddCost"
                  :disabled="bizLoading || !addCostData.amount || (addCostStandalone && !addCostOrderId)">
            <CreditCard :size="14" /> 落到「待核成本」
          </button>
        </div>
      </div>
    </div>

    <!-- 批量汇款对话框 -->
    <div class="modal-backdrop" v-if="showBatchRemitDialog" @click.self="showBatchRemitDialog = false">
      <div class="modal-dialog" style="max-width:480px">
        <div class="modal-header">
          <h3>批量汇款（{{ selectedIds.size }} 条）</h3>
          <button class="modal-close" @click="showBatchRemitDialog = false"><X :size="18" /></button>
        </div>
        <div class="modal-body">
          <div class="form-row" style="margin-bottom:12px">
            <label style="display:block;font-size:13px;margin-bottom:4px">汇出资金账户 *</label>
            <select v-model="batchRemitAccountId" style="width:100%;padding:6px">
              <option value="">请选择...</option>
              <option v-for="b in remitBanks" :key="b.id" :value="b.id">
                {{ b.name ?? b.accountName ?? b.bank_name }} - {{ b.accountNo ?? b.account_no ?? '' }}
              </option>
            </select>
          </div>
          <div class="form-row" style="margin-bottom:12px">
            <label style="display:block;font-size:13px;margin-bottom:4px">汇款经手人</label>
            <input v-model="batchRemitName" type="text" style="width:100%;padding:6px" />
          </div>
          <p style="color:#666;font-size:12px;line-height:1.5;margin:0">
            仅已审核且未汇款的记录会被处理；未审核或已 PAID 的会跳过。
          </p>
        </div>
        <div class="modal-footer">
          <button class="secondary" @click="showBatchRemitDialog = false">取消</button>
          <button class="primary" @click="doBatchRemit" :disabled="bizLoading || !batchRemitAccountId">
            <Landmark :size="13" /> 确认汇款
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
