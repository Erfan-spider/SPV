package com.spiderv2ray.spv.data

import android.content.Context
import org.json.JSONArray

class GroupRepository(context: Context) {
    private val prefs = context.getSharedPreferences("spv_groups", Context.MODE_PRIVATE)
    private val KEY_GROUPS = "groups"

    fun getAll(): List<ConfigGroup> {
        val raw = prefs.getString(KEY_GROUPS, null)
        val list = mutableListOf<ConfigGroup>()
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(ConfigGroup.fromJson(arr.getJSONObject(i)))
            }
        }
        if (list.none { it.id == ConfigGroup.LOCAL_ID }) {
            list.add(0, ConfigGroup.defaultLocal())
            saveAll(list)
        }
        return list.sortedBy { if (it.id == ConfigGroup.LOCAL_ID) 0L else it.createdAt }
    }

    private fun saveAll(groups: List<ConfigGroup>) {
        val arr = JSONArray()
        groups.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    fun add(group: ConfigGroup) {
        val list = getAll().toMutableList()
        list.add(group)
        saveAll(list)
    }

    fun update(group: ConfigGroup) {
        val list = getAll().map { if (it.id == group.id) group else it }
        saveAll(list)
    }

    fun remove(id: String) {
        if (id == ConfigGroup.LOCAL_ID) return
        val list = getAll().filterNot { it.id == id }
        saveAll(list)
    }

    fun findById(id: String): ConfigGroup? = getAll().find { it.id == id }
}
