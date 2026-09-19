package com.spiderv2ray.spv.data

import org.json.JSONObject
import java.util.UUID

data class ConfigGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Parsed from the subscription server's "Subscription-Userinfo" response
    // header (upload/download/total in bytes, expire in epoch seconds). Null
    // when the panel doesn't send this header, or before the first refresh.
    val trafficUpload: Long? = null,
    val trafficDownload: Long? = null,
    val trafficTotal: Long? = null,
    val trafficExpire: Long? = null
) {
    val isSubscription: Boolean get() = subUrl != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        if (subUrl != null) put("subUrl", subUrl)
        put("createdAt", createdAt)
        if (trafficUpload != null) put("trafficUpload", trafficUpload)
        if (trafficDownload != null) put("trafficDownload", trafficDownload)
        if (trafficTotal != null) put("trafficTotal", trafficTotal)
        if (trafficExpire != null) put("trafficExpire", trafficExpire)
    }

    companion object {
        const val LOCAL_ID = "local"

        fun fromJson(o: JSONObject): ConfigGroup = ConfigGroup(
            id = o.getString("id"),
            name = o.getString("name"),
            subUrl = if (o.has("subUrl")) o.optString("subUrl") else null,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            trafficUpload = if (o.has("trafficUpload")) o.optLong("trafficUpload") else null,
            trafficDownload = if (o.has("trafficDownload")) o.optLong("trafficDownload") else null,
            trafficTotal = if (o.has("trafficTotal")) o.optLong("trafficTotal") else null,
            trafficExpire = if (o.has("trafficExpire")) o.optLong("trafficExpire") else null
        )

        fun defaultLocal(): ConfigGroup = ConfigGroup(id = LOCAL_ID, name = "Local", subUrl = null)
    }
}
