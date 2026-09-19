package com.spiderv2ray.spv.util

import java.net.HttpURLConnection
import java.net.URL

/**
 * Used ONLY on Home while the VPN tunnel is connected. libXray's core is
 * busy serving the active tunnel (only one Xray instance can run at a time),
 * so a real protocol ping isn't possible there. Instead this fires a real
 * HTTP GET through the tunnel itself — an ordinary, unprotected socket, so
 * the OS transparently routes it through the active VPN — and times the
 * full round trip. A genuine test of the live path, not a synthetic one.
 */
object NetworkPing {

    fun liveTunnelPingMs(timeoutMs: Int = 5000): Long {
        var conn: HttpURLConnection? = null
        return try {
            val start = System.currentTimeMillis()
            conn = (URL("https://cp.cloudflare.com/").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            conn.responseCode // forces the request to actually happen
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
        }
    }
}
