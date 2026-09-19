package com.spiderv2ray.spv.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.core.SpvVpnService
import com.spiderv2ray.spv.data.ConfigGroup
import com.spiderv2ray.spv.data.VpnConfig
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GroupWithConfigs(
    val group: ConfigGroup,
    val configs: List<VpnConfig>
)

class GroupAdapter(
    private var items: List<GroupWithConfigs>,
    private var activeId: String?,
    private val onDeleteConfig: (VpnConfig) -> Unit,
    private val onSelectConfig: (VpnConfig) -> Unit,
    private val onRefreshGroup: (ConfigGroup) -> Unit,
    private val onPingGroup: (GroupWithConfigs) -> Unit,
    private val onDeleteGroup: (ConfigGroup) -> Unit,
    private val onExportConfig: (VpnConfig) -> Unit = {}
) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

    private val nestedAdapters = mutableMapOf<String, ConfigAdapter>()
    private val collapsedGroups = mutableSetOf<String>()

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivGroupIcon: ImageView = view.findViewById(R.id.ivGroupIcon)
        val tvGroupName: TextView = view.findViewById(R.id.tvGroupName)
        val tvGroupMeta: TextView = view.findViewById(R.id.tvGroupMeta)
        val btnGroupRefresh: ImageButton = view.findViewById(R.id.btnGroupRefresh)
        val btnGroupCopy: ImageButton = view.findViewById(R.id.btnGroupCopy)
        val btnGroupPing: ImageButton = view.findViewById(R.id.btnGroupPing)
        val btnGroupDelete: ImageButton = view.findViewById(R.id.btnGroupDelete)
        val rvGroupConfigs: RecyclerView = view.findViewById(R.id.rvGroupConfigs)
        val ivGroupExpand: ImageView = view.findViewById(R.id.tvGroupExpand)
        val layoutGroupUsage: View = view.findViewById(R.id.layoutGroupUsage)
        val progressGroupUsage: ProgressBar = view.findViewById(R.id.progressGroupUsage)
        val tvGroupUsageText: TextView = view.findViewById(R.id.tvGroupUsageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val item = items[position]
        val group = item.group

        holder.ivGroupIcon.setImageResource(
            if (group.isSubscription) R.drawable.ic_subscription else R.drawable.ic_local
        )
        holder.tvGroupName.text = group.name
        val typeLabel = if (group.isSubscription) "Subscription" else "Local"
        holder.tvGroupMeta.text = "${item.configs.size} configs • $typeLabel"

        holder.btnGroupRefresh.visibility = if (group.isSubscription) View.VISIBLE else View.GONE
        holder.btnGroupRefresh.setOnClickListener { onRefreshGroup(group) }

        // libXray فقط یه هسته داره؛ وقتی تونل وصله نمیشه هندشیک پروتکل زد.
        val connected = SpvVpnService.isRunning
        holder.btnGroupPing.isEnabled = !connected
        holder.btnGroupPing.alpha = if (connected) 0.4f else 1f
        holder.btnGroupPing.setOnClickListener { onPingGroup(item) }

        // Copy the raw subscription URL itself (not the individual configs
        // inside it) so the user can re-share/re-import the sub link.
        holder.btnGroupCopy.visibility = if (group.isSubscription) View.VISIBLE else View.GONE
        holder.btnGroupCopy.setOnClickListener {
            showCopyShareMenu(holder.btnGroupCopy, group.subUrl ?: "", "subscription_url", "لینک ساب کپی شد")
        }

        // The built-in "Local" bucket can't be deleted (it's the default home
        // for manually pasted single configs); every other group — including
        // subscriptions — can be removed entirely, link and all.
        holder.btnGroupDelete.visibility = if (group.id == ConfigGroup.LOCAL_ID) View.GONE else View.VISIBLE
        holder.btnGroupDelete.setOnClickListener { onDeleteGroup(group) }

        bindUsage(holder, group)

        // Reuse the existing ConfigAdapter for this group instead of recreating it,
        // so pingMap (and any other per-item state) survives rebinds/scrolls.
        val existingAdapter = nestedAdapters[group.id]
        if (existingAdapter != null) {
            existingAdapter.updateData(item.configs, activeId)
            if (holder.rvGroupConfigs.adapter !== existingAdapter) {
                holder.rvGroupConfigs.layoutManager = LinearLayoutManager(holder.itemView.context)
                holder.rvGroupConfigs.adapter = existingAdapter
            }
        } else {
            val nestedAdapter = ConfigAdapter(
                items = item.configs,
                activeId = activeId,
                onDelete = onDeleteConfig,
                onSelect = onSelectConfig,
                onExport = onExportConfig
            )
            nestedAdapters[group.id] = nestedAdapter
            holder.rvGroupConfigs.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.rvGroupConfigs.adapter = nestedAdapter
        }

        val isCollapsed = collapsedGroups.contains(group.id)
        holder.rvGroupConfigs.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        holder.ivGroupExpand.rotation = if (isCollapsed) -90f else 0f
        holder.ivGroupExpand.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(holder.itemView.context, if (isCollapsed) R.color.ping_bad else R.color.ping_good)
        )

        val toggleListener = View.OnClickListener {
            if (collapsedGroups.contains(group.id)) collapsedGroups.remove(group.id) else collapsedGroups.add(group.id)
            notifyItemChanged(holder.adapterPosition)
        }
        holder.ivGroupExpand.setOnClickListener(toggleListener)
        holder.tvGroupName.setOnClickListener(toggleListener)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<GroupWithConfigs>, newActiveId: String?) {
        items = newItems
        activeId = newActiveId
        notifyDataSetChanged()
    }

    fun setConfigPing(groupId: String, configId: String, pingText: String) {
        nestedAdapters[groupId]?.setPing(configId, pingText)
    }

    fun applyGroupPingResults(groupId: String, results: Map<String, Long>) {
        val idx = items.indexOfFirst { it.group.id == groupId }
        if (idx == -1) return
        val group = items[idx]
        val sortedConfigs = group.configs.sortedBy { cfg ->
            val ms = results[cfg.id]
            if (ms != null && ms >= 0) ms else Long.MAX_VALUE
        }
        items = items.toMutableList().also { it[idx] = group.copy(configs = sortedConfigs) }
        val texts = results.mapValues { (_, ms) -> if (ms >= 0) "${ms}ms" else "timeout" }
        nestedAdapters[groupId]?.updateDataWithPings(sortedConfigs, activeId, texts)
    }

    // Draws the "used / total" usage bar for subscription groups whose panel
    // sent a Subscription-Userinfo header. Groups without traffic info (local
    // group, or a sub whose panel doesn't send the header) keep this hidden.
    private fun bindUsage(holder: GroupViewHolder, group: ConfigGroup) {
        val total = group.trafficTotal
        if (total == null || total <= 0) {
            holder.layoutGroupUsage.visibility = View.GONE
            return
        }
        holder.layoutGroupUsage.visibility = View.VISIBLE
        val used = (group.trafficUpload ?: 0L) + (group.trafficDownload ?: 0L)
        val percent = ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        holder.progressGroupUsage.progress = percent

        val color = when {
            percent >= 90 -> R.color.ping_bad
            percent >= 70 -> R.color.ping_mid
            else -> R.color.purple_primary
        }
        holder.progressGroupUsage.progressTintList =
            ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.context, color))

        val expireText = group.trafficExpire?.let { expireEpochSeconds ->
            if (expireEpochSeconds <= 0) null else {
                val daysLeft = TimeUnit.MILLISECONDS.toDays(
                    (expireEpochSeconds * 1000L) - System.currentTimeMillis()
                )
                when {
                    daysLeft < 0 -> "  •  منقضی شده"
                    daysLeft == 0L -> "  •  امروز منقضی می‌شود"
                    else -> "  •  $daysLeft روز تا انقضا"
                }
            }
        } ?: ""

        holder.tvGroupUsageText.text =
            "${formatBytes(used)} از ${formatBytes(total)} مصرف شده$expireText"
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
