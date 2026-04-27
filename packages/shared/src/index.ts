export type ChargeSide = "AR" | "AP";
export type ChargeStatus = "DRAFT" | "ESTIMATED" | "LOCKED" | "ADJUSTED" | "VOID";
export type ReconcileStatus = "MATCHED" | "DIFFERENT" | "UNMATCHED" | "PENDING_REVIEW";
export type RemoteLevel = "NONE" | "REMOTE" | "SUPER_REMOTE" | "EMBARGO";
export type BillingUom = "KG" | "LB" | "CBM" | "PIECE" | "SHIPMENT" | "CARTON" | "PERCENT";
export type CombinationStrategy = "STACK" | "MAX";
export type LedgerAccountType = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
export type LedgerEntryDirection = "DEBIT" | "CREDIT";
export type LedgerTransactionStatus = "DRAFT" | "POSTED" | "REVERSED";
export type BackgroundJobStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "RETRYING" | "DEAD";

export const financeModules = [
  "rate-engine",
  "receivable",
  "payable-reconciliation",
  "insurance",
  "allocation",
  "ledger",
  "authorization",
  "profit",
  "commission"
] as const;
