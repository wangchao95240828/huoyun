-- Legacy prototype tables left over from the retired Fastify apps/api adapter.
-- They are not part of the canonical schema under db/migrations 001–014.
-- Idempotent; safe against missing tables.

DROP TABLE IF EXISTS sys_service_messages CASCADE;
DROP TABLE IF EXISTS sys_operation_logs CASCADE;
DROP TABLE IF EXISTS sys_customer_accounts CASCADE;
DROP TABLE IF EXISTS sys_users CASCADE;
DROP TABLE IF EXISTS sys_menus CASCADE;
DROP TABLE IF EXISTS sys_error_logs CASCADE;
DROP TABLE IF EXISTS sys_configs CASCADE;
DROP TABLE IF EXISTS sys_hardware_configs CASCADE;
DROP TABLE IF EXISTS sys_sms_logs CASCADE;
DROP TABLE IF EXISTS sys_roles CASCADE;
