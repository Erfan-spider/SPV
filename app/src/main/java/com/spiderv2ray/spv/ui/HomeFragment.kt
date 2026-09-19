package com.spiderv2ray.spv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.LinearLayout
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.core.SpvVpnService
import com.spiderv2ray.spv.data.ConfigRepository
import com.spiderv2ray.spv.util.LibXrayPing
import com.spiderv2ray.spv.util.NetworkPing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var repo: ConfigRepository
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsRunnable: Runnable? = null
    private var lastUp: Long = 0
    private var lastDown: Long = 0
    private var lastSampleTime: Long = 0
    private var pingInFlight = false

    private var locationFetchedForThisSession = false
    private var lastGeoError: String = ""

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == androidx.appcompat.app.AppCompatActivity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(requireContext(), "اجازه‌ی VPN رد شد", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // even if denied we continue; notification is best-effort
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireView().findViewById<android.view.View>(R.id.ivSpider).setOnClickListener {
            SpiderWebView.tap(requireActivity(), it, requireView().findViewById(R.id.viewSpiderGlow))
        }
        repo = ConfigRepository(requireContext())

        // Android 13+ notification permission (needed for foreground VPN service)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        view.findViewById<MaterialButton>(R.id.btnPower).setOnClickListener {
            if (SpvVpnService.isRunning) {
                disconnectVpn()
            } else {
                connectVpn()
            }
        }

        // پینگ کاملاً دستیه: فقط با تپ روی خودِ عدد پینگ اجرا می‌شه، هیچ رفرش خودکاری نیست.
        view.findViewById<TextView>(R.id.tvActiveConfigPing).setOnClickListener {
            refreshPing()
        }

        view.findViewById<LinearLayout>(R.id.layoutTelegramBanner).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spiderV2ray"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        if (isHidden) return
        updateUi()
        startStatsLoop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopStatsLoop()
        } else {
            updateUi()
            startStatsLoop()
        }
    }

    override fun onPause() {
        super.onPause()
        stopStatsLoop()
    }

    private fun connectVpn() {
        val active = repo.getActive()
        if (active == null) {
            Toast.makeText(requireContext(), "اول یک کانفیگ را از تب Configs انتخاب کن", Toast.LENGTH_LONG).show()
            return
        }
        val intent = VpnService.prepare(requireContext())
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val active = repo.getActive() ?: return
        val serviceIntent = Intent(requireContext(), SpvVpnService::class.java).apply {
            action = SpvVpnService.ACTION_CONNECT
            putExtra(SpvVpnService.EXTRA_CONFIG_ID, active.id)
        }
        requireContext().startService(serviceIntent)
        view?.findViewById<TextView>(R.id.tvStatus)?.text = "Connecting..."
        view?.postDelayed({ updateUi() }, 2000)
    }

    private fun disconnectVpn() {
        val serviceIntent = Intent(requireContext(), SpvVpnService::class.java).apply {
            action = SpvVpnService.ACTION_DISCONNECT
        }
        requireContext().startService(serviceIntent)
        lastUp = 0
        lastDown = 0
        locationFetchedForThisSession = false
        view?.findViewById<View>(R.id.layoutRealLocation)?.visibility = View.GONE
        view?.postDelayed({ updateUi() }, 400)
    }

    private fun updateUi() {
        val view = view ?: return
        val active = repo.getActive()

        view.findViewById<TextView>(R.id.tvActiveConfigName).text =
            active?.tag ?: "کانفیگی انتخاب نشده"
        view.findViewById<TextView>(R.id.tvActiveConfigProtocol).text = active?.protocol ?: ""
        view.findViewById<TextView>(R.id.tvActiveConfigAddress).text = active?.address ?: ""

        val tvPing = view.findViewById<TextView>(R.id.tvActiveConfigPing)
        // پینگ خودکار عمداً حذف شد — فقط با تپ روی این TextView اجرا می‌شه.
        tvPing.text = if (active == null) "" else "برای پینگ ضربه بزن"
        tvPing.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))

        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvHomeDesc = view.findViewById<TextView>(R.id.tvHomeDesc)
        val btnPower = view.findViewById<MaterialButton>(R.id.btnPower)
        if (SpvVpnService.isRunning) {
            tvStatus.text = "Connected"
            tvHomeDesc.text = "ترافیک از طریق تونل عبور می‌کند"
            ownerBtnKey = null
            applyOwnerButton()
            btnPower.alpha = 1f
            btnPower.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.status_connected))
            fetchRealLocationIfNeeded()
        } else {
            tvStatus.text = "Disconnected"
            tvHomeDesc.text = "برای اتصال روی دکمه بزن"
            ownerBtnKey = null
            applyOwnerButton()
            btnPower.alpha = 1f
            btnPower.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.status_disconnected))
            view.findViewById<TextView>(R.id.tvUplink)?.text = "0 KB/s"
            view.findViewById<TextView>(R.id.tvDownlink)?.text = "0 KB/s"
        }
    }

    // Owner button: while connected to a config that came from an .spvt file with a
    // Telegram id, the line under the status turns into a button that opens the channel.
    private var ownerBtnKey: String? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applyOwnerButton() {
        val v = view ?: return
        val tv = v.findViewById<TextView>(R.id.tvHomeDesc) ?: return
        val active = repo.getActive()
        val tg = active?.telegramId
        val name = active?.ownerName
        val showOwner = SpvVpnService.isRunning && !tg.isNullOrBlank()
        val giftState = com.spiderv2ray.spv.util.GiftConfigManager.getState(requireContext())
        val showGift = !SpvVpnService.isRunning && active == null && !showOwner
        val key = when {
            showOwner -> "owner|$tg|$name"
            showGift -> "gift|$giftState"
            else -> "off"
        }
        if (key == ownerBtnKey) return
        ownerBtnKey = key
        when {
            showOwner -> {
                val id = tg.orEmpty()
                tv.text = if (name.isNullOrBlank()) "Telegram  •  @$id" else "$name  •  @$id"
                tv.setBackgroundResource(R.drawable.bg_glass_chip)
                tv.setPadding(dp(16), dp(7), dp(16), dp(7))
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                tv.setOnClickListener { openTelegram(id) }
            }
            showGift -> {
                tv.text = if (giftState == "done") "۵ گیگ تموم شد  •  خرید از ربات" else "🎁 دریافت ۵ گیگ هدیه"
                tv.setBackgroundResource(R.drawable.bg_glass_chip)
                tv.setPadding(dp(16), dp(7), dp(16), dp(7))
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                tv.setOnClickListener {
                    val ctx = requireContext()
                    if (giftState == "done") {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spdray_bot")))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val existingId = com.spiderv2ray.spv.util.GiftConfigManager.getConfigId(ctx)
                        val existing = existingId?.let { id -> repo.getAll().find { c -> c.id == id } }
                        if (existing != null) {
                            repo.setActiveId(existing.id)
                        } else {
                            com.spiderv2ray.spv.util.GiftConfigManager.activateGift(ctx)
                        }
                        ownerBtnKey = null
                        updateUi()
                    }
                }
            }
            else -> {
                tv.background = null
                tv.setPadding(0, 0, 0, 0)
                tv.setOnClickListener(null)
                tv.isClickable = false
                tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
            }
        }
    }

    private fun openTelegram(id: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$id")))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchRealLocationIfNeeded() {
        if (locationFetchedForThisSession) return
        locationFetchedForThisSession = true
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            if (!SpvVpnService.isRunning) return@launch
            val info = withContext(Dispatchers.IO) { fetchGeoLocation() }
            if (!SpvVpnService.isRunning) return@launch
            val layoutLocation = view?.findViewById<View>(R.id.layoutRealLocation) ?: return@launch
            val tvFlag = view?.findViewById<TextView>(R.id.tvRealLocationFlag) ?: return@launch
            val tvPlace = view?.findViewById<TextView>(R.id.tvRealLocationPlace) ?: return@launch
            val tvIp = view?.findViewById<TextView>(R.id.tvRealLocationIp) ?: return@launch
            if (info == null) {
                layoutLocation.visibility = View.GONE
                return@launch
            }
            val flag = CountryFlags.flagFromCountryCode(info.countryCode)
            tvFlag.text = flag
            tvPlace.text = if (info.city.isNullOrBlank()) {
                info.country
            } else {
                "${info.country}, ${info.city}"
            }
            tvIp.text = maskIp(info.ip)
            layoutLocation.alpha = 0f
            layoutLocation.scaleX = 0.85f
            layoutLocation.scaleY = 0.85f
            layoutLocation.visibility = View.VISIBLE
            layoutLocation.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start()
        }
    }

    private data class GeoInfo(val country: String, val countryCode: String?, val city: String?, val ip: String?)

        private fun fetchGeoLocation(): GeoInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("http://ip-api.com/json/?fields=status,message,country,countryCode,city,query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                lastGeoError = "HTTP $code: $text"
                return null
            }
            val json = JSONObject(text)
            if (json.optString("status") != "success") {
                lastGeoError = json.optString("message", "unknown geo error")
                return null
            }
            val country = json.optString("country").ifBlank { null } ?: return null
            GeoInfo(
                country = country,
                countryCode = json.optString("countryCode", null),
                city = json.optString("city", null),
                ip = json.optString("query", null)
            )
        } catch (e: Exception) {
            lastGeoError = e.toString()
            null
        } finally {
            conn?.disconnect()
        }
    }

private fun maskIp(ip: String?): String {
    if (ip.isNullOrBlank()) return ""
    val parts = ip.split(".")
    return if (parts.size == 4) "${parts[0]}.${parts[1]}.xx.xx" else ip
}

    // پینگ کاملاً دستیه (فقط با تپ). دو مسیر جدا:
    //  - قطع: هندشیک کامل پروتکل روی کانفیگ فعال، از طریق LibXrayPing (pingBatch).
    //  - وصل: هسته‌ی libXray مشغول تونله، پس هندشیک پروتکل ممکن نیست؛ به‌جاش یه
    //    درخواست HTTP واقعی از داخل تونل زده می‌شه (NetworkPing.liveTunnelPingMs).
    private fun refreshPing() {
        val view = view ?: return
        if (pingInFlight) return

        val connected = SpvVpnService.isRunning
        val active = repo.getActive()
        val tv = view.findViewById<TextView>(R.id.tvActiveConfigPing)

        if (!connected && active == null) {
            tv?.text = "کانفیگی انتخاب نشده"
            return
        }

        pingInFlight = true
        tv?.text = "…"

        lifecycleScope.launch {
            val ms = withContext(Dispatchers.IO) {
                if (connected) {
                    NetworkPing.liveTunnelPingMs()
                } else {
                    val target = LibXrayPing.PingTarget(active!!.id, active.outboundJson)
                    LibXrayPing.pingBatch(listOf(target))[active.id] ?: -1L
                }
            }
            pingInFlight = false
            if (!isAdded || connected != SpvVpnService.isRunning) return@launch
            val tvNow = this@HomeFragment.view?.findViewById<TextView>(R.id.tvActiveConfigPing) ?: return@launch
            tvNow.text = if (ms < 0) "timeout" else "${ms}ms"
            tvNow.setTextColor(ContextCompat.getColor(requireContext(), com.spiderv2ray.spv.util.PingColors.colorResFor(ms)))
        }
    }

    private fun startStatsLoop() {
        stopStatsLoop()
        lastUp = SpvVpnService.lastUplink
        lastDown = SpvVpnService.lastDownlink
        lastSampleTime = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                updateStatsUi()
                statsHandler.postDelayed(this, 1000)
            }
        }
        statsRunnable = runnable
        statsHandler.postDelayed(runnable, 1500)
    }

    private fun stopStatsLoop() {
        statsRunnable?.let { statsHandler.removeCallbacks(it) }
        statsRunnable = null
    }

    private fun updateStatsUi() {
        val view = view ?: return
        applyOwnerButton()
        if (!SpvVpnService.isRunning) {
            view.findViewById<TextView>(R.id.tvUplink)?.text = "0 KB/s"
            view.findViewById<TextView>(R.id.tvDownlink)?.text = "0 KB/s"
            view.findViewById<TextView>(R.id.tvSessionTotal)?.text = "Total: 0 KB"
            return
        }

        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastSampleTime).coerceAtLeast(1)) / 1000.0

        val curUp = SpvVpnService.lastUplink
        val curDown = SpvVpnService.lastDownlink

        val upRate = ((curUp - lastUp).coerceAtLeast(0) / elapsedSec) / 1024.0
        val downRate = ((curDown - lastDown).coerceAtLeast(0) / elapsedSec) / 1024.0

        lastUp = curUp
        lastDown = curDown
        lastSampleTime = now

        view.findViewById<TextView>(R.id.tvUplink)?.text = String.format("%.1f KB/s", upRate)
        view.findViewById<TextView>(R.id.tvDownlink)?.text = String.format("%.1f KB/s", downRate)

        val totalBytes = curUp + curDown
        view.findViewById<TextView>(R.id.tvSessionTotal)?.text = "Total: ${formatBytes(totalBytes)}"
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
