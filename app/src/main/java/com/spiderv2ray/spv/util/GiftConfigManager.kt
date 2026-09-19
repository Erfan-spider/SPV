package com.spiderv2ray.spv.util

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
                tag = "\uD83C\uDF81 \u06f5 \u06af\u06cc\u06af \u0647\u062f\u06cc\u0647 \u0627\u0633\u067e\u0627\u06cc\u062f\u0631",
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
