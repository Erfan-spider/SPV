#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SPV patch v5 — حذف IPv6 از تون.

مشکل: هندشیک TLS روی IPv6 داخل تونل نصفه می‌مونه (TLS alert / unexpected eof)،
در حالی که همون آدرس روی IPv4 مشکلی نداره. اپ‌هایی که اول IPv6 رو امتحان می‌کنن
(Happy Eyeballs - مثل آپلود عکس تلگرام، باز کردن مستقیم لینک t.me) گیر می‌کنن تا
تایم‌اوت بخوره یا ارور بدن. با حذف آدرس/روت IPv6 از تون، سیستم و اپ‌ها اصلاً
IPv6 رو نمی‌بینن و مستقیم می‌رن سراغ IPv4 که سالمه.

اجرا:
    cd ~/SPV_v3/SPV
    python spv_patch5.py
    gradle assembleDebug
"""
import os
import shutil
import sys
import time

ROOT = os.path.abspath(".")
JAVA = os.path.join(ROOT, "app/src/main/java/com/spiderv2ray/spv")
BACKUP_DIR = os.path.expanduser("~/spv_backups/patch5_" + time.strftime("%Y%m%d_%H%M%S"))

FAILED = []
OK = 0


def backup(path):
    if not os.path.exists(path):
        return
    rel = os.path.relpath(path, ROOT)
    dest = os.path.join(BACKUP_DIR, rel)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copy2(path, dest)


def load(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def save(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def replace_once(path, old, new, label):
    global OK
    content = load(path)
    count = content.count(old)
    if count != 1:
        FAILED.append((label, path, count))
        print(f"[SKIP] {label}: {count} match(es) instead of 1 in {os.path.relpath(path, ROOT)}")
        return
    content = content.replace(old, new, 1)
    save(path, content)
    OK += 1
    print(f"[OK]   {label}")


p = os.path.join(JAVA, "core/SpvVpnService.kt")
backup(p)
print(f"backup -> {BACKUP_DIR}")

replace_once(
    p,
    '''            // IPv6 support
            try {
                builder.addAddress("fd00:10:10:10::2", 64)
                builder.addRoute("::", 0)
            } catch (e: Exception) {
                android.util.Log.w("SPV", "IPv6 not supported on this device", e)
            }''',
    '''            // IPv6 عمداً غیرفعال شد: هندشیک TLS روی IPv6 داخل این تون نصفه می‌موند
            // (TLS alert / unexpected eof)، در حالی که IPv4 سالمه. با حذف آدرس/روت
            // IPv6، اپ‌هایی که اول IPv6 رو امتحان می‌کنن (Happy Eyeballs) بلافاصله
            // می‌رن سراغ IPv4 به‌جای گیر کردن تا تایم‌اوت.''',
    "SpvVpnService: remove broken IPv6 route from tunnel",
)

print()
print(f"DONE: {OK} patch(es) applied successfully.")
if FAILED:
    print(f"WARNING: {len(FAILED)} patch(es) FAILED:")
    for label, path, info in FAILED:
        print(f"  - {label}  ({os.path.relpath(path, ROOT)}, info={info})")
sys.exit(1 if FAILED else 0)
