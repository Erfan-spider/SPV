#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SPV patch v4
- فیکس: اسم Export هم اسم فایل هم اسم داخل JSON (تگ کانفیگ ایمپورت‌شونده) می‌شه
- دکمه‌ی Copy مستقیم کپی می‌کنه (بدون منو) + دکمه‌ی Share جدا که Export رو باز می‌کنه
- داخل دیالوگ Export یه دکمه‌ی "ارسال لینک ساده" اضافه شد
- فیچر «۵ گیگ هدیه»: کارت ثابت بالای تب Configs + دکمه روی Home وقتی قطعی/بدون کانفیگ فعاله
  شمارش مصرف در SpvVpnService، سه حالت not_received / active / done

نحوه اجرا:
    cd ~/SPV_v3/SPV
    python spv_patch4.py
    gradle assembleDebug

اگر جایی از پچ‌ها match نشد، اسکریپت هشدار می‌ده و ادامه می‌ده؛ اون قسمت دستی
نمی‌خوره - دقیقاً همون خط رو از فایل واقعی برام بفرست تا اصلاح کنم.
"""
import os
import shutil
import sys
import time

ROOT = os.path.abspath(".")
JAVA = os.path.join(ROOT, "app/src/main/java/com/spiderv2ray/spv")
RES = os.path.join(ROOT, "app/src/main/res")

BACKUP_DIR = os.path.expanduser("~/spv_backups/patch4_" + time.strftime("%Y%m%d_%H%M%S"))

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


def create_new(path, content, label):
    global OK
    if os.path.exists(path):
        print(f"[SKIP] {label}: file already exists at {os.path.relpath(path, ROOT)} (not overwritten)")
        FAILED.append((label, path, "exists"))
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    save(path, content)
    OK += 1
    print(f"[OK]   {label} (new file)")


# ---------------------------------------------------------------------------
# Backup every file we are about to touch
# ---------------------------------------------------------------------------
TOUCHED = [
    os.path.join(JAVA, "data/VpnConfig.kt"),
    os.path.join(JAVA, "ui/ConfigsFragment.kt"),
    os.path.join(JAVA, "ui/ConfigAdapter.kt"),
    os.path.join(JAVA, "ui/HomeFragment.kt"),
    os.path.join(JAVA, "core/SpvVpnService.kt"),
    os.path.join(RES, "layout/item_config.xml"),
    os.path.join(RES, "layout/fragment_configs.xml"),
]
for p in TOUCHED:
    backup(p)
print(f"backups -> {BACKUP_DIR}")

# ---------------------------------------------------------------------------
# 1) VpnConfig.kt — add isGift field
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "data/VpnConfig.kt")

replace_once(
    p,
    'val telegramId: String? = null\n) {',
    'val telegramId: String? = null,\n    val isGift: Boolean = false\n) {',
    "VpnConfig: add isGift field to data class",
)

replace_once(
    p,
    'put("telegramId", telegramId)\n    }',
    'put("telegramId", telegramId)\n        put("isGift", isGift)\n    }',
    "VpnConfig: serialize isGift in toJson",
)

replace_once(
    p,
    'else o.optString("telegramId", "").ifBlank { null }',
    'else o.optString("telegramId", "").ifBlank { null },\n            isGift = o.optBoolean("isGift", false)',
    "VpnConfig: deserialize isGift in fromJson",
)

# ---------------------------------------------------------------------------
# 2) New file — GiftConfigManager.kt
# ---------------------------------------------------------------------------
gift_manager_code = '''package com.spiderv2ray.spv.util

import android.content.Context
import com.spiderv2ray.spv.data.ConfigGroup
import com.spiderv2ray.spv.data.ConfigRepository
import com.spiderv2ray.spv.data.VpnConfig
import libXray.LibXray
import org.json.JSONObject

// Manages the single shared "5GB gift" config: state (not_received / active / done),
// cumulative usage tracked only on this device (SharedPreferences), and turning the
// raw vless:// link into a real VpnConfig the app can connect to like any other.
object GiftConfigManager {

    private const val PREFS = "spv_gift_config"
    private const val KEY_USED_BYTES = "used_bytes"
    private const val KEY_STATE = "state"
    private const val KEY_CONFIG_ID = "config_id"

    const val LIMIT_BYTES = 5L * 1024 * 1024 * 1024

    // Replace this link from the panel whenever the gift config needs to change.
    private const val GIFT_VLESS_LINK =
        "vless://153b6187-5c46-43ac-9b7d-57a4564e35c4@202.133.90.179:8443?encryption=none&security=none&type=tcp#%F0%9F%95%B7%EF%B8%8F%F0%9F%87%B9%F0%9F%87%B7%F0%9D%93%88%F0%9D%93%85%F0%9D%92%BE%F0%9D%92%B9%F0%9D%91%92%F0%9D%93%87%F0%9D%93%8B%F0%9D%9F%A4%F0%9D%93%87%F0%9D%92%B6%F0%9D%93%8E%E2%93%82"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getState(ctx: Context): String = prefs(ctx).getString(KEY_STATE, "not_received") ?: "not_received"

    private fun setState(ctx: Context, state: String) {
        prefs(ctx).edit().putString(KEY_STATE, state).apply()
    }

    fun getUsedBytes(ctx: Context): Long = prefs(ctx).getLong(KEY_USED_BYTES, 0L)

    fun addUsedBytes(ctx: Context, delta: Long) {
        if (delta <= 0) return
        val p = prefs(ctx)
        val used = p.getLong(KEY_USED_BYTES, 0L) + delta
        p.edit().putLong(KEY_USED_BYTES, used).apply()
        if (used >= LIMIT_BYTES && getState(ctx) == "active") {
            setState(ctx, "done")
        }
    }

    fun getConfigId(ctx: Context): String? = prefs(ctx).getString(KEY_CONFIG_ID, null)

    fun isExhausted(ctx: Context): Boolean = getUsedBytes(ctx) >= LIMIT_BYTES

    private fun extractAddress(outbound: JSONObject): String = try {
        val settings = outbound.optJSONObject("settings")
        val vnext = settings?.optJSONArray("vnext")
        if (vnext != null && vnext.length() > 0) {
            val node = vnext.getJSONObject(0)
            "${node.optString("address")}:${node.optInt("port")}"
        } else ""
    } catch (e: Exception) {
        ""
    }

    // Adds the gift config to the local group and makes it the active one. Returns
    // null (and leaves state untouched) if the panel link can't be parsed right now.
    fun activateGift(context: Context): VpnConfig? {
        val ctx = context.applicationContext
        return try {
            val req = JSONObject().apply {
                put("apiVersion", 3)
                put("method", "convertShareLinksToXrayJson")
                put("payload", JSONObject().put("text", GIFT_VLESS_LINK))
            }
            val resp = JSONObject(LibXray.invoke(req.toString()))
            if (!resp.optBoolean("success", false)) return null
            val outbounds = resp.getJSONObject("data").getJSONArray("outbounds")
            if (outbounds.length() == 0) return null
            val outbound = outbounds.getJSONObject(0)

            val config = VpnConfig(
                tag = "\\uD83C\\uDF81 \\u06f5 \\u06af\\u06cc\\u06af \\u0647\\u062f\\u06cc\\u0647 \\u0627\\u0633\\u067e\\u0627\\u06cc\\u062f\\u0631",
                protocol = outbound.optString("protocol", "unknown"),
                address = extractAddress(outbound),
                outboundJson = outbound.toString(),
                source = "gift",
                groupId = ConfigGroup.LOCAL_ID,
                rawLink = GIFT_VLESS_LINK,
                isGift = true
            )
            val repo = ConfigRepository(ctx)
            repo.add(config)
            repo.setActiveId(config.id)
            prefs(ctx).edit().putString(KEY_CONFIG_ID, config.id).apply()
            setState(ctx, "active")
            config
        } catch (e: Exception) {
            null
        }
    }
}
'''
create_new(os.path.join(JAVA, "util/GiftConfigManager.kt"), gift_manager_code, "new file GiftConfigManager.kt")

# ---------------------------------------------------------------------------
# 3) SpvVpnService.kt — gift usage tracking
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "core/SpvVpnService.kt")

replace_once(
    p,
    "private var statsRunnable: Runnable? = null",
    "private var statsRunnable: Runnable? = null\n    private var isGiftActive: Boolean = false\n    private var giftLastUp: Long = 0\n    private var giftLastDown: Long = 0",
    "SpvVpnService: add gift tracking fields",
)

replace_once(
    p,
    'startVpn(config.outboundJson, config.tag)',
    'startVpn(config.outboundJson, config.tag, config.isGift)',
    "SpvVpnService: pass isGift on ACTION_CONNECT",
)

replace_once(
    p,
    'startVpn(active.outboundJson, active.tag)',
    'startVpn(active.outboundJson, active.tag, active.isGift)',
    "SpvVpnService: pass isGift on ACTION_TOGGLE",
)

replace_once(
    p,
    'private fun startVpn(outboundJson: String, configTag: String = "") {',
    'private fun startVpn(outboundJson: String, configTag: String = "", isGift: Boolean = false) {\n        isGiftActive = isGift\n        giftLastUp = 0\n        giftLastDown = 0',
    "SpvVpnService: startVpn resets gift counters",
)

replace_once(
    p,
    "                    lastUplink = up\n                    lastDownlink = down\n                } catch (e: Exception) {",
    '''                    lastUplink = up
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
# 4) VpnConfig.kt already patched above. Now ConfigsFragment.kt
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "ui/ConfigsFragment.kt")

replace_once(
    p,
    'hint = "اسم فایل"',
    'hint = "اسم کانفیگ (هم اسم فایل هم اسم داخل لیست بعد از Import)"',
    "ConfigsFragment: export dialog hint text",
)

replace_once(
    p,
    '''                    val fileName = safeFileName(etName.text.toString()).ifEmpty { "config" }
                    val owner = etOwner.text.toString().trim()
                    val cfg = JSONObject().apply {
                        put("tag", config.tag)
                        put("protocol", config.protocol)''',
    '''                    val nameInput = etName.text.toString().trim().ifEmpty { config.tag }
                    val fileName = safeFileName(nameInput).ifEmpty { "config" }
                    val owner = etOwner.text.toString().trim()
                    val cfg = JSONObject().apply {
                        put("tag", nameInput)
                        put("protocol", config.protocol)''',
    "ConfigsFragment: use typed name as both filename and imported tag",
)

replace_once(
    p,
    '''                .setNegativeButton("انصراف", null)
                .create()''',
    '''                .setNegativeButton("انصراف", null)
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

replace_once(
    p,
    '''        applyFilter()
        // پینگ خودکار عمداً حذف شد — پینگ فقط با زدن دکمه‌ی پینگ هر گروه اتفاق می‌افته.
    }''',
    '''        applyFilter()
        // پینگ خودکار عمداً حذف شد — پینگ فقط با زدن دکمه‌ی پینگ هر گروه اتفاق می‌افته.
        updateGiftCard()
    }''',
    "ConfigsFragment: refresh gift card whenever list refreshes",
)

replace_once(
    p,
    '''                    .setPositiveButton("باشه", null)
                    .show()
            }
        }
    }
}''',
    '''                    .setPositiveButton("باشه", null)
                    .show()
            }
        }
    }

    // --- 5GB gift config card (Configs tab) ---

    private fun formatGiftBytes(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.2f GB", gb)
    }

    private fun openTelegramBot() {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://t.me/spdray_bot")))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGiftCard() {
        val v = view ?: return
        val card = v.findViewById<View>(R.id.cardGiftConfig) ?: return
        val tvTitle = v.findViewById<TextView>(R.id.tvGiftTitle) ?: return
        val tvUsage = v.findViewById<TextView>(R.id.tvGiftUsageText) ?: return
        val progress = v.findViewById<android.widget.ProgressBar>(R.id.progressGiftUsage) ?: return
        val ctx = requireContext()
        when (com.spiderv2ray.spv.util.GiftConfigManager.getState(ctx)) {
            "done" -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "۵ گیگ تموم شد  •  خرید از ربات"
                tvUsage.visibility = View.GONE
                progress.visibility = View.GONE
                card.setOnClickListener { openTelegramBot() }
            }
            "active" -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "🎁 ۵ گیگ هدیه‌ی کانال اسپایدر"
                val used = com.spiderv2ray.spv.util.GiftConfigManager.getUsedBytes(ctx)
                val percent = ((used.toDouble() / com.spiderv2ray.spv.util.GiftConfigManager.LIMIT_BYTES) * 100).toInt().coerceIn(0, 100)
                progress.visibility = View.VISIBLE
                progress.progress = percent
                tvUsage.visibility = View.VISIBLE
                tvUsage.text = "${formatGiftBytes(used)} از 5.00 GB مصرف شده"
                card.setOnClickListener {
                    val id = com.spiderv2ray.spv.util.GiftConfigManager.getConfigId(ctx)
                    if (id != null && repo.getAll().any { it.id == id }) {
                        repo.setActiveId(id)
                        Toast.makeText(ctx, "کانفیگ هدیه فعال شد", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                }
            }
            else -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "🎁 ۵ گیگ هدیه‌ی کانال اسپایدر"
                tvUsage.visibility = View.GONE
                progress.visibility = View.GONE
                card.setOnClickListener {
                    val cfg = com.spiderv2ray.spv.util.GiftConfigManager.activateGift(ctx)
                    if (cfg != null) {
                        Toast.makeText(ctx, "کانفیگ هدیه اضافه و فعال شد", Toast.LENGTH_LONG).show()
                        refreshList()
                    } else {
                        Toast.makeText(ctx, "خطا در دریافت کانفیگ هدیه", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}''',
    "ConfigsFragment: add gift-card update/activation functions",
)

# ---------------------------------------------------------------------------
# 5) ConfigAdapter.kt — split copy/share, lock gift item
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "ui/ConfigAdapter.kt")

replace_once(
    p,
    "val btnCopyConfig: ImageButton = view.findViewById(R.id.btnCopyConfig)",
    "val btnCopyConfig: ImageButton = view.findViewById(R.id.btnCopyConfig)\n        val btnShareConfig: ImageButton = view.findViewById(R.id.btnShareConfig)",
    "ConfigAdapter: add btnShareConfig to ViewHolder",
)

replace_once(
    p,
    "holder.tvAddress.text = item.address",
    "holder.tvAddress.text = if (item.isGift) \"🔒 مخفی\" else item.address",
    "ConfigAdapter: hide address for gift config",
)

replace_once(
    p,
    '''        holder.btnCopyConfig.setOnClickListener {
            val ctx = holder.itemView.context
            val link = item.rawLink
            if (link.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    ctx,
                    "لینک اصلی این کانفیگ ذخیره نشده؛ ساب را یک‌بار بروزرسانی کن",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                showCopyShareMenu(holder.btnCopyConfig, link, "config_link", "لینک کانفیگ کپی شد",
                    onExport = { onExport(item) })
            }
        }''',
    '''        if (item.isGift) {
            holder.btnCopyConfig.visibility = View.GONE
            holder.btnShareConfig.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnCopyConfig.visibility = View.VISIBLE
            holder.btnShareConfig.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        }

        holder.btnCopyConfig.setOnClickListener {
            val ctx = holder.itemView.context
            val link = item.rawLink
            if (link.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    ctx,
                    "لینک اصلی این کانفیگ ذخیره نشده؛ ساب را یک‌بار بروزرسانی کن",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("config_link", link))
                android.widget.Toast.makeText(ctx, "لینک کانفیگ کپی شد", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        holder.btnShareConfig.setOnClickListener { onExport(item) }''',
    "ConfigAdapter: direct copy + separate share button + gift lock",
)

# ---------------------------------------------------------------------------
# 6) HomeFragment.kt — gift button on Home when disconnected/no config
# ---------------------------------------------------------------------------
p = os.path.join(JAVA, "ui/HomeFragment.kt")

replace_once(
    p,
    '''    private fun applyOwnerButton() {
        val v = view ?: return
        val tv = v.findViewById<TextView>(R.id.tvHomeDesc) ?: return
        val active = repo.getActive()
        val tg = active?.telegramId
        val name = active?.ownerName
        val show = SpvVpnService.isRunning && !tg.isNullOrBlank()
        val key = if (show) "$tg|$name" else "off"
        if (key == ownerBtnKey) return
        ownerBtnKey = key
        if (show) {
            val id = tg.orEmpty()
            tv.text = if (name.isNullOrBlank()) "Telegram  •  @$id" else "$name  •  @$id"
            tv.setBackgroundResource(R.drawable.bg_glass_chip)
            tv.setPadding(dp(16), dp(7), dp(16), dp(7))
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            tv.setOnClickListener { openTelegram(id) }
        } else {
            tv.background = null
            tv.setPadding(0, 0, 0, 0)
            tv.setOnClickListener(null)
            tv.isClickable = false
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
        }
    }''',
    '''    private fun applyOwnerButton() {
        val v = view ?: return
        val tv = v.findViewById<TextView>(R.id.tvHomeDesc) ?: return
        val active = repo.getActive()
        val tg = active?.telegramId
        val name = active?.ownerName
        val showOwner = SpvVpnService.isRunning && !tg.isNullOrBlank()
        val giftState = com.spiderv2ray.spv.util.GiftConfigManager.getState(requireContext())
        val showGift = !SpvVpnService.isRunning && active == null && !showOwner
        val key = when {
            showOwner -> "owner|$tg|$name"
            showGift -> "gift|$giftState"
            else -> "off"
        }
        if (key == ownerBtnKey) return
        ownerBtnKey = key
        when {
            showOwner -> {
                val id = tg.orEmpty()
                tv.text = if (name.isNullOrBlank()) "Telegram  •  @$id" else "$name  •  @$id"
                tv.setBackgroundResource(R.drawable.bg_glass_chip)
                tv.setPadding(dp(16), dp(7), dp(16), dp(7))
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                tv.setOnClickListener { openTelegram(id) }
            }
            showGift -> {
                tv.text = if (giftState == "done") "۵ گیگ تموم شد  •  خرید از ربات" else "🎁 دریافت ۵ گیگ هدیه"
                tv.setBackgroundResource(R.drawable.bg_glass_chip)
                tv.setPadding(dp(16), dp(7), dp(16), dp(7))
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                tv.setOnClickListener {
                    val ctx = requireContext()
                    if (giftState == "done") {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spdray_bot")))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val existingId = com.spiderv2ray.spv.util.GiftConfigManager.getConfigId(ctx)
                        val existing = existingId?.let { id -> repo.getAll().find { c -> c.id == id } }
                        if (existing != null) {
                            repo.setActiveId(existing.id)
                        } else {
                            com.spiderv2ray.spv.util.GiftConfigManager.activateGift(ctx)
                        }
                        ownerBtnKey = null
                        updateUi()
                    }
                }
            }
            else -> {
                tv.background = null
                tv.setPadding(0, 0, 0, 0)
                tv.setOnClickListener(null)
                tv.isClickable = false
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
            }
        }
    }''',
    "HomeFragment: gift button state on tvHomeDesc",
)

# ---------------------------------------------------------------------------
# 7) item_config.xml — add btnShareConfig
# ---------------------------------------------------------------------------
p = os.path.join(RES, "layout/item_config.xml")

replace_once(
    p,
    '''                app:layout_constraintEnd_toStartOf="@id/btnDelete" />

            <ImageButton
                android:id="@+id/btnDelete"''',
    '''                app:layout_constraintEnd_toStartOf="@id/btnShareConfig" />

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

# ---------------------------------------------------------------------------
# 8) fragment_configs.xml — add gift card
# ---------------------------------------------------------------------------
p = os.path.join(RES, "layout/fragment_configs.xml")

replace_once(
    p,
    '''        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvGroups"''',
    '''        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.cardview.widget.CardView
        android:id="@+id/cardGiftConfig"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="16dp"
        android:layout_marginTop="10dp"
        android:clickable="true"
        android:focusable="true"
        app:cardCornerRadius="14dp"
        app:cardElevation="0dp"
        app:cardBackgroundColor="@color/bg_card_alt"
        app:layout_constraintTop_toBottomOf="@id/etSearch"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="14dp">

            <TextView
                android:id="@+id/tvGiftTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textStyle="bold"
                android:textColor="@color/text_primary"
                tools:text="🎁 ۵ گیگ هدیه‌ی کانال اسپایدر" />

            <ProgressBar
                android:id="@+id/progressGiftUsage"
                style="?android:attr/progressBarStyleHorizontal"
                android:layout_width="match_parent"
                android:layout_height="6dp"
                android:layout_marginTop="8dp"
                android:max="100"
                android:visibility="gone"
                android:progressTint="@color/purple_primary" />

            <TextView
                android:id="@+id/tvGiftUsageText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textSize="11sp"
                android:textColor="@color/text_gray"
                android:visibility="gone"
                tools:text="2.28 GB از 5.00 GB مصرف شده" />

        </LinearLayout>
    </androidx.cardview.widget.CardView>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvGroups"''',
    "fragment_configs.xml: insert gift card",
)

replace_once(
    p,
    '''android:clipToPadding="false"
        app:layout_constraintTop_toBottomOf="@id/etSearch"''',
    '''android:clipToPadding="false"
        app:layout_constraintTop_toBottomOf="@id/cardGiftConfig"''',
    "fragment_configs.xml: rvGroups top now below gift card",
)

# ---------------------------------------------------------------------------
print()
print(f"DONE: {OK} patch(es) applied successfully.")
if FAILED:
    print(f"WARNING: {len(FAILED)} patch(es) FAILED (skipped, nothing written for those):")
    for label, path, info in FAILED:
        print(f"  - {label}  ({os.path.relpath(path, ROOT)}, info={info})")
    print()
    print("برای این موارد، دقیقاً همون خط/بلوک رو از فایل واقعی کپی کن و برام بفرست تا اصلاحش کنم.")
sys.exit(1 if FAILED else 0)
