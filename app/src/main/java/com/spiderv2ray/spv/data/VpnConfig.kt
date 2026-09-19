package com.spiderv2ray.spv.data

import org.json.JSONObject
import java.util.UUID

data class VpnConfig(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val protocol: String,
    val address: String,
    val outboundJson: String,
    val source: String = "manual",
    val groupId: String = "local",
    val createdAt: Long = System.currentTimeMillis(),
    // The original share link this config came from (vless://, vmess://, trojan://, ...),
    // kept verbatim so the user can copy the exact same link back out. Configs saved
    // before this field existed will have rawLink == null.
    val rawLink: String? = null,
    // Optional info from whoever exported this config (.spvt): owner display name and
    // Telegram channel id (no @). Home shows a button for it while connected.
    val ownerName: String? = null,
    val telegramId: String? = null,
    val isGift: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("tag", tag)
        put("protocol", protocol)
        put("address", address)
        put("outboundJson", outboundJson)
        put("source", source)
        put("groupId", groupId)
        put("createdAt", createdAt)
        put("rawLink", rawLink)
        put("ownerName", ownerName)
        put("telegramId", telegramId)
        put("isGift", isGift)
    }

    companion object {
        fun fromJson(o: JSONObject): VpnConfig = VpnConfig(
            id = o.getString("id"),
            tag = o.getString("tag"),
            protocol = o.optString("protocol", ""),
            address = o.optString("address", ""),
            outboundJson = o.getString("outboundJson"),
            source = o.optString("source", "manual"),
            groupId = o.optString("groupId", "local"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            rawLink = if (o.isNull("rawLink")) null else o.optString("rawLink", null),
            ownerName = if (o.isNull("ownerName")) null
                else o.optString("ownerName", "").ifBlank { null },
            telegramId = if (o.isNull("telegramId")) null
                else o.optString("telegramId", "").ifBlank { null },
            isGift = o.optBoolean("isGift", false)
        )
    }
}
