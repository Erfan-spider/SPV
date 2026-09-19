package com.spiderv2ray.spv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.data.VpnConfig

class ConfigAdapter(
    private var items: List<VpnConfig>,
    private var activeId: String?,
    private val onDelete: (VpnConfig) -> Unit,
    private val onSelect: (VpnConfig) -> Unit,
    private val onExport: (VpnConfig) -> Unit = {}
) : RecyclerView.Adapter<ConfigAdapter.ConfigViewHolder>() {

    private val pingMap = mutableMapOf<String, String>()

    class ConfigViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTag: TextView = view.findViewById(R.id.tvTag)
        val tvProtocol: TextView = view.findViewById(R.id.tvProtocol)
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val tvPing: TextView = view.findViewById(R.id.tvPing)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnCopyConfig: ImageButton = view.findViewById(R.id.btnCopyConfig)
        val btnShareConfig: ImageButton = view.findViewById(R.id.btnShareConfig)
        val viewActiveDot: View = view.findViewById(R.id.viewActiveDot)
        val progressPing: ProgressBar = view.findViewById(R.id.progressPing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config, parent, false)
        return ConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        val item = items[position]
        holder.tvTag.text = item.tag
        holder.tvProtocol.text = item.protocol
        holder.tvAddress.text = if (item.isGift) "🔒 مخفی" else item.address

        val pingText = pingMap[item.id]
            if (pingText == "…") {
                holder.tvPing.visibility = View.GONE
                holder.progressPing.visibility = View.VISIBLE
            } else {
                holder.progressPing.visibility = View.GONE
                holder.tvPing.visibility = View.VISIBLE

        holder.tvPing.text = pingText ?: ""
        holder.tvPing.setTextColor(
            when {
                pingText == null -> ContextCompat.getColor(holder.itemView.context, R.color.text_gray)
                pingText == "timeout" || pingText == "error" -> ContextCompat.getColor(holder.itemView.context, R.color.ping_bad)
                pingText.isNotEmpty() && !pingText.first().isDigit() ->
                    ContextCompat.getColor(holder.itemView.context, R.color.text_gray)
                else -> {
                    // Single ping methodology everywhere now (see NetworkPing.pingTcp:
                    // raw TCP-connect, median of 3 samples) - so one consistent set
                    // of thresholds, no more "(tcp)" vs "full protocol" distinction.
                    val ms = Regex("""^\d+""").find(pingText)?.value?.toLongOrNull() ?: 9999L
                    when {
                        ms < 120 -> ContextCompat.getColor(holder.itemView.context, R.color.ping_good)
                        ms < 300 -> ContextCompat.getColor(holder.itemView.context, R.color.ping_good)
                        else -> ContextCompat.getColor(holder.itemView.context, R.color.ping_good)
                    }
                }
            }
        )

                    }

            holder.viewActiveDot.alpha = if (item.id == activeId) 1f else 0f

        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener { onSelect(item) }

        // Copy this single config's original share link (vless://, vmess://, ...).
        // Configs imported before this feature existed have no stored rawLink;
        // for those we say so instead of copying an empty/wrong string.
        if (item.isGift) {
            holder.btnCopyConfig.visibility = View.GONE
            holder.btnShareConfig.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnCopyConfig.visibility = View.VISIBLE
            holder.btnShareConfig.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        }

        holder.btnCopyConfig.setOnClickListener {
            val ctx = holder.itemView.context
            val link = item.rawLink
            if (link.isNullOrBlank()) {
                android.widget.Toast.makeText(
                    ctx,
                    "لینک اصلی این کانفیگ ذخیره نشده؛ ساب را یک‌بار بروزرسانی کن",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("config_link", link))
                android.widget.Toast.makeText(ctx, "لینک کانفیگ کپی شد", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        holder.btnShareConfig.setOnClickListener { onExport(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<VpnConfig>, newActiveId: String?) {
        items = newItems
        activeId = newActiveId
        notifyDataSetChanged()
    }

    fun updateDataWithPings(newItems: List<VpnConfig>, newActiveId: String?, pings: Map<String, String>) {
        items = newItems
        activeId = newActiveId
        pingMap.putAll(pings)
        notifyDataSetChanged()
    }

    fun setPing(configId: String, pingText: String) {
        pingMap[configId] = pingText
        val index = items.indexOfFirst { it.id == configId }
        if (index != -1) notifyItemChanged(index)
    }
}

// Small menu on the copy button: Copy or Share (system share sheet).
internal fun showCopyShareMenu(
    anchor: View,
    text: String,
    clipLabel: String,
    copiedMsg: String,
    onExport: (() -> Unit)? = null
) {
    val ctx = anchor.context
    val popup = android.widget.PopupMenu(ctx, anchor)
    popup.menu.add(0, 1, 0, "Copy  •  کپی")
    popup.menu.add(0, 2, 1, "Share  •  اشتراک‌گذاری")
    if (onExport != null) popup.menu.add(0, 3, 2, "Export file  •  فایل با پیام")
    popup.setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
            1 -> {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText(clipLabel, text))
                android.widget.Toast.makeText(ctx, copiedMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
            3 -> onExport?.invoke()
            2 -> {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                try {
                    ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(ctx, "اشتراک‌گذاری ممکن نشد", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        true
    }
    popup.show()
}
