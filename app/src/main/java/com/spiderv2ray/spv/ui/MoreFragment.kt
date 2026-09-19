package com.spiderv2ray.spv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.SpvApplication
import com.spiderv2ray.spv.data.BypassAppsStore
import com.spiderv2ray.spv.data.ConfigGroup
import com.spiderv2ray.spv.data.ConfigRepository
import com.spiderv2ray.spv.data.GroupRepository
import com.spiderv2ray.spv.data.VpnConfig
import org.json.JSONArray
import org.json.JSONObject

class MoreFragment : Fragment(R.layout.fragment_more) {

    companion object {
        private const val PREFS = "spv_settings"
        private const val KEY_BYPASS_LAN = "bypass_lan"

        fun isBypassLanEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_BYPASS_LAN, true)

        fun setBypassLanEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BYPASS_LAN, enabled).apply()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val telegramUrl = "https://t.me/spiderv2ray"

        view.findViewById<CardView>(R.id.cardTelegram).setOnClickListener {
            openLink(telegramUrl)
        }
        view.findViewById<CardView>(R.id.cardSubscribe).setOnClickListener {
            openLink(telegramUrl)
        }

        // Theme
        val switchDarkTheme = view.findViewById<Switch>(R.id.switchDarkTheme)
        switchDarkTheme.isChecked = SpvApplication.isDarkModeEnabled(requireContext())
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            SpvApplication.setDarkModeEnabled(requireContext(), isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // Recreate right away so every screen (and the system bars) switch together.
            view.post {
                activity?.takeIf { !it.isFinishing && !it.isDestroyed }?.recreate()
            }
        }

        // Bypass LAN
        val switchBypass = view.findViewById<Switch>(R.id.switchBypassLan)
        switchBypass.isChecked = isBypassLanEnabled(requireContext())
        switchBypass.setOnCheckedChangeListener { _, isChecked ->
            setBypassLanEnabled(requireContext(), isChecked)
            Toast.makeText(
                requireContext(),
                if (isChecked) "Bypass LAN فعال شد (بعد از وصل مجدد اعمال می‌شود)"
                else "Bypass LAN غیرفعال شد (بعد از وصل مجدد اعمال می‌شود)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Split apps
        view.findViewById<CardView>(R.id.cardSplitApps)?.setOnClickListener {
            showAppBypassPicker()
        }

        // Backup
        view.findViewById<CardView>(R.id.cardBackup).setOnClickListener {
            doBackup()
        }

        // Restore
        view.findViewById<CardView>(R.id.cardRestore).setOnClickListener {
            doRestore()
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "امکان باز کردن لینک وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppBypassPicker() {
        val pm = requireContext().packageManager
        val selected = BypassAppsStore.getPackages(requireContext()).toMutableSet()

        // User-installed + launchable apps
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .filter { it.packageName != requireContext().packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(requireContext(), "لیست اپ‌ها خالی است", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = apps.map { pm.getApplicationLabel(it).toString() + "\n" + it.packageName }.toTypedArray()
        val checked = BooleanArray(apps.size) { i -> selected.contains(apps[i].packageName) }

        AlertDialog.Builder(requireContext())
            .setTitle("اپ‌هایی که از VPN رد نشوند")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setPositiveButton("ذخیره") { _, _ ->
                BypassAppsStore.setPackages(requireContext(), selected)
                Toast.makeText(
                    requireContext(),
                    "${selected.size} اپ برای Bypass ذخیره شد (بعد از وصل مجدد اعمال می‌شود)",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("انصراف", null)
            .setNeutralButton("پاک کردن همه") { _, _ ->
                BypassAppsStore.setPackages(requireContext(), emptySet())
                Toast.makeText(requireContext(), "لیست Bypass پاک شد", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun doBackup() {
        try {
            val configRepo = ConfigRepository(requireContext())
            val groupRepo = GroupRepository(requireContext())
            val configs = configRepo.getAll()
            val groups = groupRepo.getAll()
            val activeId = configRepo.getActiveId()

            val root = JSONObject().apply {
                put("version", 2)
                put("exportedAt", System.currentTimeMillis())
                put("activeConfigId", activeId)
                put("bypassPackages", JSONArray(BypassAppsStore.getPackages(requireContext()).toList()))
                put("groups", JSONArray().apply {
                    groups.forEach { put(it.toJson()) }
                })
                put("configs", JSONArray().apply {
                    configs.forEach { put(it.toJson()) }
                })
            }

            val json = root.toString(2)
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("SPV Backup", json))
            Toast.makeText(
                requireContext(),
                "بکاپ در کلیپ‌بورد کپی شد (${configs.size} کانفیگ، ${groups.size} گروه)",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "خطا در بکاپ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun doRestore() {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(requireContext())?.toString()?.trim()
        if (text.isNullOrBlank()) {
            Toast.makeText(requireContext(), "کلیپ‌بورد خالی است. اول بکاپ را کپی کنید.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("بازیابی بکاپ")
            .setMessage("تمام کانفیگ‌ها و گروه‌های فعلی جایگزین می‌شوند. ادامه می‌دهید؟")
            .setPositiveButton("بازیابی") { _, _ ->
                try {
                    val root = JSONObject(text)
                    val configRepo = ConfigRepository(requireContext())
                    val groupRepo = GroupRepository(requireContext())

                    val groupsArr = root.optJSONArray("groups") ?: JSONArray()
                    val configsArr = root.optJSONArray("configs") ?: JSONArray()

                    val existingGroups = groupRepo.getAll().filter { it.id != ConfigGroup.LOCAL_ID }
                    existingGroups.forEach { groupRepo.remove(it.id) }
                    configRepo.getAll().forEach { configRepo.remove(it.id) }

                    for (i in 0 until groupsArr.length()) {
                        val g = ConfigGroup.fromJson(groupsArr.getJSONObject(i))
                        if (g.id != ConfigGroup.LOCAL_ID) {
                            groupRepo.add(g)
                        }
                    }
                    for (i in 0 until configsArr.length()) {
                        val c = VpnConfig.fromJson(configsArr.getJSONObject(i))
                        configRepo.add(c)
                    }

                    // restore bypass packages if present
                    root.optJSONArray("bypassPackages")?.let { arr ->
                        val pkgs = (0 until arr.length()).map { arr.getString(it) }.toSet()
                        BypassAppsStore.setPackages(requireContext(), pkgs)
                    }

                    val activeId = root.optString("activeConfigId", null)
                    if (!activeId.isNullOrBlank()) {
                        configRepo.setActiveId(activeId)
                    }

                    Toast.makeText(
                        requireContext(),
                        "بازیابی موفق: ${configsArr.length()} کانفیگ",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "خطا در بازیابی: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
