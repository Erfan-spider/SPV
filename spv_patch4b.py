#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SPV patch v4b — تکمیل ۴ موردی که در patch4 به‌خاطر فاصله‌گذاری واقعی فایل match نشدن.
اجرا کن روی همون پروژه‌ای که patch4 رو قبلاً روش زدی (بعد از patch4، قبل از اینکه
دوباره چیزی دستی عوض کنی):

    cd ~/SPV_v3/SPV
    python spv_patch4b.py
    gradle assembleDebug
"""
import os
import shutil
import sys
import time

ROOT = os.path.abspath(".")
JAVA = os.path.join(ROOT, "app/src/main/java/com/spiderv2ray/spv")
RES = os.path.join(ROOT, "app/src/main/res")
BACKUP_DIR = os.path.expanduser("~/spv_backups/patch4b_" + time.strftime("%Y%m%d_%H%M%S"))

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


TOUCHED = [
    os.path.join(JAVA, "core/SpvVpnService.kt"),
    os.path.join(JAVA, "ui/ConfigsFragment.kt"),
    os.path.join(RES, "layout/item_config.xml"),
]
for p in TOUCHED:
    backup(p)
print(f"backups -> {BACKUP_DIR}")

# ---------------------------------------------------------------------------
# 1) SpvVpnService.kt — accumulate gift usage + auto-stop at 5GB
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "core/SpvVpnService.kt")

replace_once(
    p,
    "                lastUplink = up\n                lastDownlink = down\n            } catch (e: Exception) {",
    '''                lastUplink = up
                lastDownlink = down

                if (isGiftActive) {
                    val deltaUp = (up - giftLastUp).coerceAtLeast(0)
                    val deltaDown = (down - giftLastDown).coerceAtLeast(0)
                    giftLastUp = up
                    giftLastDown = down
                    com.spiderv2ray.spv.util.GiftConfigManager.addUsedBytes(applicationContext, deltaUp + deltaDown)
                    if (com.spiderv2ray.spv.util.GiftConfigManager.isExhausted(applicationContext)) {
                        isGiftActive = false
                        mainHandler.post {
                            toast("\\u06f5 \\u06af\\u06cc\\u06af \\u0647\\u062f\\u06cc\\u0647 \\u062a\\u0645\\u0627\\u0645 \\u0634\\u062f")
                            stopVpn()
                        }
                    }
                }
            } catch (e: Exception) {''',
    "SpvVpnService: accumulate gift usage + auto-stop at 5GB",
)

# ---------------------------------------------------------------------------
# 2) ConfigsFragment.kt — typed name becomes both filename and imported tag
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "ui/ConfigsFragment.kt")

replace_once(
    p,
    '''                val fileName = safeFileName(etName.text.toString()).ifEmpty { "config" }
                val owner = etOwner.text.toString().trim()
                val cfg = JSONObject().apply {
                    put("tag", config.tag)
                    put("protocol", config.protocol)''',
    '''                val nameInput = etName.text.toString().trim().ifEmpty { config.tag }
                val fileName = safeFileName(nameInput).ifEmpty { "config" }
                val owner = etOwner.text.toString().trim()
                val cfg = JSONObject().apply {
                    put("tag", nameInput)
                    put("protocol", config.protocol)''',
    "ConfigsFragment: use typed name as both filename and imported tag",
)

# ---------------------------------------------------------------------------
# 3) ConfigsFragment.kt — plain-link-share button inside Export dialog
# ---------------------------------------------------------------------------
replace_once(
    p,
    '''            .setNegativeButton("انصراف", null)
            .create()''',
    '''            .setNegativeButton("انصراف", null)
            .setNeutralButton("ارسال لینک ساده") { _, _ ->
                val link = config.rawLink
                if (link.isNullOrBlank()) {
                    Toast.makeText(ctx, "لینک اصلی این کانفیگ ذخیره نشده", Toast.LENGTH_LONG).show()
                } else {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, link)
                    }
                    try {
                        ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "اشتراک‌گذاری ممکن نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()''',
    "ConfigsFragment: add plain-link-share button inside Export dialog",
)

# ---------------------------------------------------------------------------
# 4) item_config.xml — add btnShareConfig
# ---------------------------------------------------------------------------
p = os.path.join(RES, "layout/item_config.xml")

replace_once(
    p,
    '''            app:layout_constraintEnd_toStartOf="@id/btnDelete" />

        <ImageButton
            android:id="@+id/btnDelete"''',
    '''            app:layout_constraintEnd_toStartOf="@id/btnShareConfig" />

        <ImageButton
            android:id="@+id/btnShareConfig"
            android:layout_width="30dp"
            android:layout_height="30dp"
            android:padding="5dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_share"
            app:tint="@color/text_gray"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toStartOf="@id/btnDelete" />

        <ImageButton
            android:id="@+id/btnDelete"''',
    "item_config.xml: add btnShareConfig button",
)

print()
print(f"DONE: {OK} patch(es) applied successfully.")
if FAILED:
    print(f"WARNING: {len(FAILED)} patch(es) FAILED:")
    for label, path, info in FAILED:
        print(f"  - {label}  ({os.path.relpath(path, ROOT)}, info={info})")
sys.exit(1 if FAILED else 0)
