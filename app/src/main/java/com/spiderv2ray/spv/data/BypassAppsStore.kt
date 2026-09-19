package com.spiderv2ray.spv.data

import android.content.Context
import org.json.JSONArray

/**
 * Stores package names that should bypass the VPN (disallowed applications).
 */
object BypassAppsStore {
    private const val PREFS = "spv_settings"
    private const val KEY = "bypass_packages"

    fun getPackages(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setPackages(context: Context, packages: Set<String>) {
        val arr = JSONArray()
        packages.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun toggle(context: Context, packageName: String): Boolean {
        val set = getPackages(context).toMutableSet()
        val nowBypass = if (set.contains(packageName)) {
            set.remove(packageName)
            false
        } else {
            set.add(packageName)
            true
        }
        setPackages(context, set)
        return nowBypass
    }
}
