#!/bin/bash
set -euo pipefail
BACKUP_DIR=/opt/xqt-saas/backups
TS=$(date +%Y%m%d_%H%M)
mkdir -p "$BACKUP_DIR"
PGPASSWORD=Xqt_prod_DB_2026_change_me /usr/bin/pg_dump -h localhost -p 15432 -U xqt -d xqt_saas --format=custom --no-owner --compress=9 -f "$BACKUP_DIR/xqt_saas_${TS}.dump"
echo "[$(date)] backup ok: $(du -h $BACKUP_DIR/xqt_saas_${TS}.dump | cut -f1)"
find "$BACKUP_DIR" -name "xqt_saas_*.dump" -mtime +7 -delete -print
# === 阿里云 OSS 占位 (开启需配 /etc/ossutilconfig + ossutil 二进制) ===
# /usr/local/bin/ossutil cp "$BACKUP_DIR/xqt_saas_${TS}.dump" oss://your-bucket/xqt-saas/ --config-file /etc/ossutilconfig
