create or replace function app_current_tenant_id()
returns uuid
language sql
stable
as $$
  select nullif(current_setting('app.current_tenant_id', true), '')::uuid
$$;

create or replace function app_is_service_role()
returns boolean
language sql
stable
as $$
  select coalesce(current_setting('app.service_role', true), '') = 'true'
$$;

create or replace function app_tenant_matches(row_tenant_id uuid)
returns boolean
language sql
stable
as $$
  select app_is_service_role() or row_tenant_id = app_current_tenant_id()
$$;

alter table users enable row level security;
alter table users force row level security;
create policy users_tenant_isolation on users
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customers enable row level security;
alter table customers force row level security;
create policy customers_tenant_isolation on customers
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table carriers enable row level security;
alter table carriers force row level security;
create policy carriers_tenant_isolation on carriers
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table channels enable row level security;
alter table channels force row level security;
create policy channels_tenant_isolation on channels
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table contracts enable row level security;
alter table contracts force row level security;
create policy contracts_tenant_isolation on contracts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table rate_cards enable row level security;
alter table rate_cards force row level security;
create policy rate_cards_tenant_isolation on rate_cards
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table rate_card_lines enable row level security;
alter table rate_card_lines force row level security;
create policy rate_card_lines_tenant_isolation on rate_card_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table charge_items enable row level security;
alter table charge_items force row level security;
create policy charge_items_tenant_isolation on charge_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table rule_versions enable row level security;
alter table rule_versions force row level security;
create policy rule_versions_tenant_isolation on rule_versions
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table charge_rules enable row level security;
alter table charge_rules force row level security;
create policy charge_rules_tenant_isolation on charge_rules
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table fuel_surcharge_rates enable row level security;
alter table fuel_surcharge_rates force row level security;
create policy fuel_surcharge_rates_tenant_isolation on fuel_surcharge_rates
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table remote_zones enable row level security;
alter table remote_zones force row level security;
create policy remote_zones_tenant_isolation on remote_zones
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table shipments enable row level security;
alter table shipments force row level security;
create policy shipments_tenant_isolation on shipments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table cartons enable row level security;
alter table cartons force row level security;
create policy cartons_tenant_isolation on cartons
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table declarations enable row level security;
alter table declarations force row level security;
create policy declarations_tenant_isolation on declarations
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customs_groups enable row level security;
alter table customs_groups force row level security;
create policy customs_groups_tenant_isolation on customs_groups
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customs_group_shipments enable row level security;
alter table customs_group_shipments force row level security;
create policy customs_group_shipments_tenant_isolation on customs_group_shipments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table charges enable row level security;
alter table charges force row level security;
create policy charges_tenant_isolation on charges
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table channel_cost_policies enable row level security;
alter table channel_cost_policies force row level security;
create policy channel_cost_policies_tenant_isolation on channel_cost_policies
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table delivery_quote_options enable row level security;
alter table delivery_quote_options force row level security;
create policy delivery_quote_options_tenant_isolation on delivery_quote_options
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table insurance_policies enable row level security;
alter table insurance_policies force row level security;
create policy insurance_policies_tenant_isolation on insurance_policies
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table insurance_events enable row level security;
alter table insurance_events force row level security;
create policy insurance_events_tenant_isolation on insurance_events
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table carrier_bill_imports enable row level security;
alter table carrier_bill_imports force row level security;
create policy carrier_bill_imports_tenant_isolation on carrier_bill_imports
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table carrier_bill_lines enable row level security;
alter table carrier_bill_lines force row level security;
create policy carrier_bill_lines_tenant_isolation on carrier_bill_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table reconciliation_results enable row level security;
alter table reconciliation_results force row level security;
create policy reconciliation_results_tenant_isolation on reconciliation_results
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customer_invoices enable row level security;
alter table customer_invoices force row level security;
create policy customer_invoices_tenant_isolation on customer_invoices
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table customer_invoice_lines enable row level security;
alter table customer_invoice_lines force row level security;
create policy customer_invoice_lines_tenant_isolation on customer_invoice_lines
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table payments enable row level security;
alter table payments force row level security;
create policy payments_tenant_isolation on payments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table bls enable row level security;
alter table bls force row level security;
create policy bls_tenant_isolation on bls
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table bl_shipments enable row level security;
alter table bl_shipments force row level security;
create policy bl_shipments_tenant_isolation on bl_shipments
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table cost_allocation_batches enable row level security;
alter table cost_allocation_batches force row level security;
create policy cost_allocation_batches_tenant_isolation on cost_allocation_batches
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table commission_plans enable row level security;
alter table commission_plans force row level security;
create policy commission_plans_tenant_isolation on commission_plans
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

alter table audit_logs enable row level security;
alter table audit_logs force row level security;
create policy audit_logs_tenant_isolation on audit_logs
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
