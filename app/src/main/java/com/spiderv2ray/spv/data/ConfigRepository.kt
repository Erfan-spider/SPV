package com.spiderv2ray.spv.data

import android.content.Context
import org.json.JSONArray

class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("spv_configs", Context.MODE_PRIVATE)
    private val KEY_CONFIGS = "configs"
    private val KEY_ACTIVE_ID = "active_config_id"

    fun getAll(): List<VpnConfig> {
        val raw = prefs.getString(KEY_CONFIGS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val list = mutableListOf<VpnConfig>()
        for (i in 0 until arr.length()) {
            list.add(VpnConfig.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun getByGroup(groupId: String): List<VpnConfig> = getAll().filter { it.groupId == groupId }

    private fun saveAll(configs: List<VpnConfig>) {
        val arr = JSONArray()
        configs.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_CONFIGS, arr.toString()).apply()
    }

    fun add(config: VpnConfig) {
        val list = getAll().toMutableList()
        list.add(config)
        saveAll(list)
    }

    fun addAll(configs: List<VpnConfig>) {
        val list = getAll().toMutableList()
        list.addAll(configs)
        saveAll(list)
    }

    fun remove(id: String) {
        val list = getAll().filterNot { it.id == id }
        saveAll(list)
        if (getActiveId() == id) setActiveId(null)
    }

    fun removeGroupConfigs(groupId: String) {
        val activeWasInGroup = getActive()?.groupId == groupId
        val list = getAll().filterNot { it.groupId == groupId }
        saveAll(list)
        if (activeWasInGroup) setActiveId(null)
    }

    fun getActiveId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActiveId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActive(): VpnConfig? {
        val id = getActiveId() ?: return null
        return getAll().find { it.id == id }
    }
}
