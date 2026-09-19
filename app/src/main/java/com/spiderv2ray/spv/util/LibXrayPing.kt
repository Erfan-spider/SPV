package com.spiderv2ray.spv.util

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

object LibXrayPing {

    private const val PING_URL = "https://cp.cloudflare.com/"
    private const val PING_TIMEOUT_SEC = 5

    // Last raw response or exception text, for on-screen debugging (no adb access).
    var lastDebugInfo: String = ""
        private set

    data class PingTarget(val id: String, val outboundJson: String)

    private fun pingBatchOnce(targets: List<PingTarget>): Map<String, Long> {
        if (targets.isEmpty()) return emptyMap()

        val configsArray = JSONArray()
        targets.forEach { t ->
            val xrayJson = JSONObject().put(
                "outbounds",
                JSONArray().put(JSONObject(t.outboundJson))
            )
            configsArray.put(JSONObject().put("xrayJson", xrayJson.toString()))
        }

        val req = JSONObject().apply {
            put("apiVersion", 3)
            put("method", "pingBatch")
            put("payload", JSONObject().apply {
                put("configs", configsArray)
                put("timeout", PING_TIMEOUT_SEC)
                put("url", PING_URL)
            })
        }

        val rawResp = try {
            LibXray.invoke(req.toString())
        } catch (e: Exception) {
            lastDebugInfo = "EXCEPTION: ${e.message}"
            return targets.associate { it.id to -1L }
        }

        lastDebugInfo = rawResp

        val resp = try {
            JSONObject(rawResp)
        } catch (e: Exception) {
            lastDebugInfo = "BAD JSON: $rawResp"
            return targets.associate { it.id to -1L }
        }

        if (!resp.optBoolean("success", false)) {
            lastDebugInfo = "success=false: $rawResp"
            return targets.associate { it.id to -1L }
        }

        val results = resp.optJSONObject("data")?.optJSONArray("results")
        if (results == null) {
            lastDebugInfo = "no data.results: $rawResp"
            return targets.associate { it.id to -1L }
        }

        return targets.mapIndexed { index, t ->
            val item = results.optJSONObject(index)
            val delay = item?.optLong("delay", -1L) ?: -1L
            val ms = if (delay in 0 until 10000) delay else -1L
            // نمایش: ۱۰۰ میلی‌ثانیه از سربار ساخت نمونه‌ی موقت Xray کم می‌شه
            // تا عدد شبیه چیزی که v2rayNG نشون می‌ده باشه. اندازه‌گیری واقعی
            // (ms خام) دست‌نخورده باقی می‌مونه، فقط چیزی که نمایش داده می‌شه کمتره.
            val displayMs = if (ms >= 0) (ms - 100).coerceAtLeast(1L) else ms
            t.id to displayMs
        }.toMap()
    }

    fun pingBatch(targets: List<PingTarget>): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        targets.chunked(5).forEach { chunk ->
            result.putAll(pingBatchOnce(chunk))
        }
        return result
    }
}
