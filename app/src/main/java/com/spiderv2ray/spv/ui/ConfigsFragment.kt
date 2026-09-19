package com.spiderv2ray.spv.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.Typeface
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.core.SpvVpnService
import com.spiderv2ray.spv.data.ConfigGroup
import com.spiderv2ray.spv.data.ConfigRepository
import com.spiderv2ray.spv.data.GroupRepository
import com.spiderv2ray.spv.data.VpnConfig
import com.spiderv2ray.spv.util.LibXrayPing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ConfigsFragment : Fragment(R.layout.fragment_configs) {

    private lateinit var repo: ConfigRepository
    private lateinit var groupRepo: GroupRepository
    private lateinit var adapter: GroupAdapter
    private var currentQuery: String = ""
    private var allGrouped: List<GroupWithConfigs> = emptyList()

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val content = result.contents.trim()
            if (content.isNotEmpty()) {
                processInput(content)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScanner()
        } else {
            Toast.makeText(requireContext(), "مجوز دوربین لازم است", Toast.LENGTH_SHORT).show()
        }
    }

    // Import from a file (txt / json / later spvt & npvt)
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importFromFile(uri) }

    // Import from a QR image (gallery or file manager)
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) importFromQrImage(uri) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = ConfigRepository(requireContext())
        groupRepo = GroupRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvGroups)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddConfig)

        adapter = GroupAdapter(
            items = emptyList(),
            activeId = repo.getActiveId(),
            onDeleteConfig = { config ->
                repo.remove(config.id)
                refreshList()
            },
            onSelectConfig = { config ->
                repo.setActiveId(config.id)
                refreshList()
                Toast.makeText(requireContext(), "فعال شد: ${config.tag}", Toast.LENGTH_SHORT).show()
            },
            onRefreshGroup = { group -> refreshGroup(group) },
            onPingGroup = { groupWithConfigs -> pingGroup(groupWithConfigs) },
            onDeleteGroup = { group -> confirmDeleteGroup(group) },
            onExportConfig = { config -> showExportDialog(config) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        fab.setOnClickListener { showImportSheet() }

        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
            }
        })

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) refreshList()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshList()
    }

    private fun refreshList() {
        val view = view ?: return
        val groups = groupRepo.getAll()
        val allConfigs = repo.getAll()
        allGrouped = groups.map { g -> GroupWithConfigs(g, allConfigs.filter { it.groupId == g.id }) }

        val emptyState = view.findViewById<View>(R.id.tvEmptyState)
        emptyState.visibility = if (allConfigs.isEmpty()) View.VISIBLE else View.GONE

        applyFilter()
        // پینگ خودکار عمداً حذف شد — پینگ فقط با زدن دکمه‌ی پینگ هر گروه اتفاق می‌افته.
        updateGiftCard()
    }

    private fun applyFilter() {
        val q = currentQuery
        val filtered = if (q.isEmpty()) {
            allGrouped
        } else {
            allGrouped.mapNotNull { gwc ->
                val matching = gwc.configs.filter { c ->
                    c.tag.lowercase().contains(q) ||
                    c.address.lowercase().contains(q) ||
                    c.protocol.lowercase().contains(q) ||
                    (c.rawLink?.lowercase()?.contains(q) == true)
                }
                // also match group name
                if (matching.isNotEmpty() || gwc.group.name.lowercase().contains(q)) {
                    GroupWithConfigs(gwc.group, if (gwc.group.name.lowercase().contains(q) && matching.isEmpty()) gwc.configs else matching)
                } else null
            }
        }
        adapter.updateData(filtered, repo.getActiveId())
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // Import sheet: English title with small Persian text under it.
    private fun showImportSheet() {
        val ctx = requireContext()
        val sheet = BottomSheetDialog(ctx)
        val radius = dp(22).toFloat()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(20), dp(20), dp(28))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.bg_card))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }

        fun label(en: String, fa: String, enSize: Float, faSize: Float, enColor: Int): LinearLayout {
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            box.addView(TextView(ctx).apply {
                text = en
                textSize = enSize
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(enColor)
            })
            box.addView(TextView(ctx).apply {
                text = fa
                textSize = faSize
                setTextColor(ContextCompat.getColor(ctx, R.color.text_gray))
            })
            return box
        }

        val header = label("Import Config", "وارد کردن کانفیگ", 20f, 12f,
            ContextCompat.getColor(ctx, R.color.text_primary))
        root.addView(header)

        fun row(en: String, fa: String, action: () -> Unit) {
            val item = label(en, fa, 16f, 11f, ContextCompat.getColor(ctx, R.color.text_primary)).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(ctx, R.color.bg_card_alt))
                    cornerRadius = dp(14).toFloat()
                }
                isClickable = true
                setOnClickListener {
                    sheet.dismiss()
                    action()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            root.addView(item, lp)
        }

        row("Import Config Link", "وارد کردن لینک کانفیگ (vless / vmess / trojan / ss)") { showConfigLinkDialog() }
        row("Import Subscription Link", "وارد کردن لینک ساب") { showSubLinkDialog() }
        row("Import File", "وارد کردن فایل (txt / json)") { filePicker.launch(arrayOf("*/*")) }
        row("Import via QR", "وارد کردن با QR (دوربین یا گالری/فایل)") { showQrChooser() }

        sheet.setContentView(root)
        sheet.setOnShowListener {
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        sheet.show()
    }

    private fun showConfigLinkDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "vless:// vmess:// trojan:// ss:// ... (یک یا چند لینک، هر خط یکی)"
            setPadding(48, 32, 48, 32)
            minLines = 3
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(editText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Import Config Link")
            .setView(container)
            .setPositiveButton("افزودن") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) processInput(input)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showSubLinkDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "https://..."
            setPadding(48, 32, 48, 32)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(editText)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Import Subscription Link")
            .setView(container)
            .setPositiveButton("ادامه") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    askSubscriptionNameAndAdd(url)
                } else {
                    Toast.makeText(requireContext(), "لینک باید با http یا https شروع شود", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showQrChooser() {
        val items = arrayOf(
            "Scan with Camera  •  اسکن با دوربین",
            "Choose Image  •  انتخاب عکس از گالری / فایل‌ها"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Import via QR")
            .setItems(items) { _, which ->
                if (which == 0) startQrScan() else imagePicker.launch("image/*")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---------------------------------------------------------------
    // .spvt export / import: one config + an optional creator message
    // File format: first line "SPVT1", then a JSON object:
    //   { "owner": "...", "telegram": "...", "configs": [ {tag, protocol, address, outboundJson, rawLink} ] }
    // The file is plain text (NOT encrypted).
    // ---------------------------------------------------------------
    private var pendingExportBytes: ByteArray? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val out = requireContext().contentResolver.openOutputStream(uri, "wt")
                            ?: throw Exception("فایل ساخته نشد")
                        out.use { it.write(bytes) }
                    }
                    Toast.makeText(requireContext(), "فایل ذخیره شد", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "خطا در ذخیره: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun safeFileName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_").trim().trim('.').take(60)

    // Accepts "spiderV2ray", "@spiderV2ray" or "https://t.me/spiderV2ray".
    // Returns the bare id, or null if it is not a valid Telegram username.
    private val tgIdRegex = Regex("^[A-Za-z][A-Za-z0-9_]{4,31}$")

    private fun normalizeTelegramId(raw: String): String? {
        var s = raw.trim()
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.removePrefix("t.me/").removePrefix("telegram.me/")
        s = s.removePrefix("@").trim().trimEnd('/')
        return if (tgIdRegex.matches(s)) s else null
    }

    private fun showExportDialog(config: VpnConfig) {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val etName = EditText(ctx).apply {
            hint = "اسم کانفیگ (هم اسم فایل هم اسم داخل لیست بعد از Import)"
            setText(config.tag.take(40))
            setSingleLine()
        }
        val etOwner = EditText(ctx).apply {
            hint = "اسم صاحب کانفیگ (روی دکمه نمایش داده می‌شود)"
            setText(config.ownerName ?: "")
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(20))
        }
        val etTg = EditText(ctx).apply {
            hint = "آیدی کانال تلگرام بدون @ (مثلاً spiderV2ray)"
            setText(config.telegramId ?: "")
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        box.addView(etName)
        box.addView(etOwner)
        box.addView(etTg)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Export  •  ساخت فایل")
            .setView(box)
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("انصراف", null)
            .setNeutralButton("ارسال لینک ساده") { _, _ ->
                val link = config.rawLink
                if (link.isNullOrBlank()) {
                    Toast.makeText(ctx, "لینک اصلی این کانفیگ ذخیره نشده", Toast.LENGTH_LONG).show()
                } else {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, link)
                    }
                    try {
                        ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "اشتراک‌گذاری ممکن نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val tgRaw = etTg.text.toString().trim()
                val tg = if (tgRaw.isEmpty()) null else normalizeTelegramId(tgRaw)
                if (tgRaw.isNotEmpty() && tg == null) {
                    etTg.error = "آیدی معتبر نیست (۵ تا ۳۲ حرف انگلیسی، عدد یا _)"
                    return@setOnClickListener
                }
                val nameInput = etName.text.toString().trim().ifEmpty { config.tag }
                val fileName = safeFileName(nameInput).ifEmpty { "config" }
                val owner = etOwner.text.toString().trim()
                val cfg = JSONObject().apply {
                    put("tag", nameInput)
                    put("protocol", config.protocol)
                    put("address", config.address)
                    put("outboundJson", config.outboundJson)
                    put("rawLink", config.rawLink)
                }
                val root = JSONObject().apply {
                    put("owner", owner)
                    put("telegram", tg.orEmpty())
                    put("configs", JSONArray().put(cfg))
                }
                pendingExportBytes = ("SPVT1\n" + root.toString()).toByteArray(Charsets.UTF_8)
                dialog.dismiss()
                exportLauncher.launch("$fileName.spvt")
            }
        }
        dialog.show()
    }

    private fun importSpvt(text: String) {
        try {
            val root = JSONObject(text.removePrefix("SPVT1").trim())
            val owner = root.optString("owner", "")
                .replace(Regex("""\s+"""), " ").trim().take(20).ifBlank { null }
            val tgRaw = root.optString("telegram", "").trim()
            // Only a validated username is ever kept, since it is later used to build a link.
            val telegram = if (tgRaw.isEmpty()) null else normalizeTelegramId(tgRaw)
            val arr = root.optJSONArray("configs") ?: throw Exception("کانفیگی داخل فایل نیست")
            val list = mutableListOf<VpnConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val outbound = o.getString("outboundJson")
                JSONObject(outbound) // must be valid JSON
                val fallbackTag = "Config ${i + 1}"
                list.add(
                    VpnConfig(
                        tag = o.optString("tag", fallbackTag).ifBlank { fallbackTag },
                        protocol = o.optString("protocol", "unknown"),
                        address = o.optString("address", ""),
                        outboundJson = outbound,
                        source = "spvt",
                        groupId = ConfigGroup.LOCAL_ID,
                        rawLink = o.optString("rawLink", "").ifBlank { null },
                        ownerName = owner,
                        telegramId = telegram
                    )
                )
            }
            if (list.isEmpty()) throw Exception("کانفیگی داخل فایل نیست")
            repo.addAll(list)
            Toast.makeText(requireContext(), "${list.size} کانفیگ اضافه شد", Toast.LENGTH_LONG).show()
            refreshList()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "فایل spvt معتبر نیست: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val bytes = requireContext().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: throw Exception("فایل خوانده نشد")
                    if (bytes.size > 2 * 1024 * 1024) throw Exception("فایل خیلی بزرگ است")
                    String(bytes, Charsets.UTF_8).trimStart('\uFEFF').trim()
                }
                if (text.isEmpty()) {
                    Toast.makeText(requireContext(), "فایل خالی است", Toast.LENGTH_LONG).show()
                } else {
                    when {
                        text.startsWith("SPVT1") -> importSpvt(text)
                        text.startsWith("NPVT1") -> Toast.makeText(
                            requireContext(),
                            "فرمت npvt پشتیبانی نمی‌شود؛ فقط فایل spvt",
                            Toast.LENGTH_LONG
                        ).show()
                        else -> processInput(text)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importFromQrImage(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { decodeQrFromUri(uri) } catch (e: Exception) { null }
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(requireContext(), "QR توی این عکس پیدا نشد", Toast.LENGTH_LONG).show()
            } else {
                processInput(text.trim())
            }
        }
    }

    private fun decodeQrFromUri(uri: Uri): String? {
        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px)))
        val hints = mapOf<DecodeHintType, Any>(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        return try {
            MultiFormatReader().decode(bitmap, hints).text
        } catch (e: Exception) {
            null
        }
    }

    private fun startQrScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("QR کد کانفیگ را مقابل دوربین بگیرید")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
        }
        qrLauncher.launch(options)
    }

    private fun processInput(input: String) {
        val isSingleUrl = (input.startsWith("http://") || input.startsWith("https://")) &&
            input.none { it.isWhitespace() }
        if (isSingleUrl) {
            askSubscriptionNameAndAdd(input)
        } else {
            Toast.makeText(requireContext(), "در حال پردازش...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val configs = withContext(Dispatchers.Default) {
                        convertToConfigs(input, ConfigGroup.LOCAL_ID)
                    }
                    if (configs.isEmpty()) {
                        Toast.makeText(requireContext(), "هیچ کانفیگ معتبری پیدا نشد", Toast.LENGTH_LONG).show()
                    } else {
                        repo.addAll(configs)
                        Toast.makeText(requireContext(), "${configs.size} کانفیگ اضافه شد", Toast.LENGTH_LONG).show()
                        refreshList()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun askSubscriptionNameAndAdd(url: String) {
        val editText = EditText(requireContext()).apply {
            hint = "اسم این لینک ساب (مثلاً Turkey)"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("افزودن Subscription")
            .setView(editText)
            .setPositiveButton("افزودن") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "Subscription" }
                val group = ConfigGroup(name = name, subUrl = url)
                groupRepo.add(group)
                fetchAndFillGroup(group)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun refreshGroup(group: ConfigGroup) {
        fetchAndFillGroup(group)
    }

    private fun confirmDeleteGroup(group: ConfigGroup) {
        val message = if (group.isSubscription)
            "لینک Subscription «${group.name}» و همه کانفیگ‌های داخلش حذف بشه؟"
        else
            "گروه «${group.name}» و همه کانفیگ‌های داخلش حذف بشه؟"
        AlertDialog.Builder(requireContext())
            .setTitle("حذف گروه")
            .setMessage(message)
            .setPositiveButton("حذف") { _, _ ->
                if (repo.getActiveId()?.let { activeId -> repo.getAll().find { it.id == activeId }?.groupId == group.id } == true) {
                    repo.setActiveId(null)
                }
                repo.removeGroupConfigs(group.id)
                groupRepo.remove(group.id)
                refreshList()
                Toast.makeText(requireContext(), "«${group.name}» حذف شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun fetchAndFillGroup(group: ConfigGroup) {
        val url = group.subUrl ?: return
        Toast.makeText(requireContext(), "در حال بروزرسانی ${group.name}...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { fetchSubscriptionText(url) }
                val configs = withContext(Dispatchers.Default) { convertToConfigs(fetched.text, group.id) }
                if (configs.isEmpty()) {
                    Toast.makeText(requireContext(), "هیچ کانفیگ معتبری تو این ساب پیدا نشد", Toast.LENGTH_LONG).show()
                } else {
                    repo.removeGroupConfigs(group.id)
                    repo.addAll(configs)
                    groupRepo.update(applyUserinfo(group, fetched.userinfo))
                    Toast.makeText(requireContext(), "${group.name}: ${configs.size} کانفیگ بروز شد", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "خطا در بروزرسانی: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyUserinfo(group: ConfigGroup, header: String?): ConfigGroup {
        if (header.isNullOrBlank()) return group
        val map = header.split(";").mapNotNull { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim().toLongOrNull() else null
        }.toMap()
        if (map.isEmpty()) return group
        return group.copy(
            trafficUpload = map["upload"] ?: group.trafficUpload,
            trafficDownload = map["download"] ?: group.trafficDownload,
            trafficTotal = map["total"] ?: group.trafficTotal,
            trafficExpire = map["expire"] ?: group.trafficExpire
        )
    }

    private data class SubFetchResult(val text: String, val userinfo: String?)

    private fun fetchSubscriptionText(input: String): SubFetchResult {
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            return SubFetchResult(input, null)
        }
        val url = URL(input)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        val userinfo = conn.getHeaderField("Subscription-Userinfo")
            ?: conn.getHeaderField("subscription-userinfo")
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val text = decodeBase64Text(raw) ?: raw
        return SubFetchResult(text, userinfo)
    }

    // Tolerant base64 decode (standard + url-safe, with or without padding).
    // Returns the decoded text only if it looks like links or a JSON config.
    private fun decodeBase64Text(raw: String): String? {
        var s = raw.trim().filter { !it.isWhitespace() }
        if (s.isEmpty() || s.contains("://") || s.startsWith("{") || s.startsWith("[")) return null
        s += "=".repeat((4 - s.length % 4) % 4)
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val decoded = String(Base64.decode(s, flags), Charsets.UTF_8).trim()
                if (decoded.contains("://") || decoded.startsWith("{") || decoded.startsWith("[")) {
                    return decoded
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun convertShareLinks(text: String): JSONArray {
        val req = JSONObject().apply {
            put("apiVersion", 3)
            put("method", "convertShareLinksToXrayJson")
            put("payload", JSONObject().put("text", text))
        }
        val resp = JSONObject(LibXray.invoke(req.toString()))
        if (!resp.optBoolean("success", false)) {
            throw Exception(resp.optString("error", "convertShareLinksToXrayJson failed"))
        }
        return resp.getJSONObject("data").getJSONArray("outbounds")
    }

    private fun buildConfig(outbound: JSONObject, groupId: String, rawLink: String?, n: Int, name: String? = null): VpnConfig {
        val tag = (name ?: outbound.optString("tag", "")).ifBlank { "Config $n" }
        return VpnConfig(
            tag = tag,
            protocol = outbound.optString("protocol", "unknown"),
            address = extractAddress(outbound),
            outboundJson = outbound.toString(),
            source = "import",
            groupId = groupId,
            rawLink = rawLink
        )
    }

    private fun convertToConfigs(linksText: String, groupId: String): List<VpnConfig> {
        val text = decodeBase64Text(linksText) ?: linksText.trim()
        if (text.startsWith("{") || text.startsWith("[")) return configsFromJson(text, groupId)

        val schemeRe = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && schemeRe.containsMatchIn(it) }
        val result = mutableListOf<VpnConfig>()
        var lastError: String? = null

        // One link at a time: a single bad link no longer breaks the whole batch,
        // and every config keeps its own exact rawLink.
        for (line in lines) {
            try {
                val outbounds = convertShareLinks(line)
                for (j in 0 until outbounds.length()) {
                    result.add(buildConfig(outbounds.getJSONObject(j), groupId, line, result.size + 1))
                }
            } catch (e: Exception) {
                lastError = e.message
            }
        }

        if (result.isEmpty() && lines.isEmpty()) {
            val outbounds = convertShareLinks(text)
            for (j in 0 until outbounds.length()) {
                result.add(buildConfig(outbounds.getJSONObject(j), groupId, null, result.size + 1))
            }
        }
        if (result.isEmpty() && lastError != null) throw Exception(lastError)
        return result
    }

    // Xray JSON: a full config, an outbound object, or an array of them.
    private fun configsFromJson(text: String, groupId: String): List<VpnConfig> {
        val skip = setOf("freedom", "blackhole", "dns", "loopback")
        val result = mutableListOf<VpnConfig>()

        fun collect(obj: JSONObject) {
            val remarks = obj.optString("remarks", "").ifBlank { obj.optString("ps", "") }
            val outs = obj.optJSONArray("outbounds")
            if (outs != null) {
                for (i in 0 until outs.length()) {
                    val o = outs.optJSONObject(i) ?: continue
                    if (o.optString("protocol") in skip || o.optString("protocol").isEmpty()) continue
                    val name = if (outs.length() == 1 || i == 0) remarks.ifBlank { null } else null
                    result.add(buildConfig(o, groupId, null, result.size + 1, name))
                }
            } else if (obj.has("protocol") && obj.optString("protocol") !in skip) {
                result.add(buildConfig(obj, groupId, null, result.size + 1, remarks.ifBlank { null }))
            }
        }

        val root: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        if (root is JSONArray) {
            for (i in 0 until root.length()) {
                root.optJSONObject(i)?.let { collect(it) }
            }
        } else if (root is JSONObject) {
            collect(root)
        }
        if (result.isEmpty()) throw Exception("در JSON هیچ outbound قابل استفاده‌ای نبود")
        return result
    }

    private fun extractAddress(outbound: JSONObject): String {
        return try {
            val settings = outbound.optJSONObject("settings") ?: return ""
            val vnext = settings.optJSONArray("vnext")
            if (vnext != null && vnext.length() > 0) {
                val node = vnext.getJSONObject(0)
                return "${node.optString("address")}:${node.optInt("port")}"
            }
            val servers = settings.optJSONArray("servers")
            if (servers != null && servers.length() > 0) {
                val node = servers.getJSONObject(0)
                return "${node.optString("address")}:${node.optInt("port")}"
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    // Config ping = full protocol handshake via libXray's pingBatch (real TLS/
    // VLESS/VMess handshake), chunked internally into groups of 5 by
    // LibXrayPing. Manual only — this only runs when the user taps the ping
    // button for a group.
    //
    // libXray can only run ONE Xray core at a time. If the tunnel is
    // connected, that core is busy serving it and pingBatch (which spins up
    // its own temporary instance) can disrupt the running tunnel — so we
    // refuse to ping while connected and tell the user to disconnect first.
    private fun pingGroup(groupWithConfigs: GroupWithConfigs) {
        val configs = groupWithConfigs.configs
        if (configs.isEmpty()) return

        if (SpvVpnService.isRunning) {
            Toast.makeText(requireContext(), "برای پینگ گرفتن، اول تونل رو قطع کن", Toast.LENGTH_LONG).show()
            return
        }

        configs.forEach { adapter.setConfigPing(groupWithConfigs.group.id, it.id, "…") }

        lifecycleScope.launch {
            val targets = configs.map { LibXrayPing.PingTarget(it.id, it.outboundJson) }
            val results = withContext(Dispatchers.IO) { LibXrayPing.pingBatch(targets) }
            val adjusted = results.mapValues { (_, ms) -> if (ms >= 0) (ms - 150).coerceAtLeast(1) else -1L }
            adapter.applyGroupPingResults(groupWithConfigs.group.id, adjusted)
            if (results.values.all { it < 0 }) {
                AlertDialog.Builder(requireContext())
                    .setTitle("خروجی خام پینگ (دیباگ)")
                    .setMessage(com.spiderv2ray.spv.util.LibXrayPing.lastDebugInfo)
                    .setPositiveButton("باشه", null)
                    .show()
            }
        }
    }

    // --- 5GB gift config card (Configs tab) ---

    private fun formatGiftBytes(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.2f GB", gb)
    }

    private fun openTelegramBot() {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://t.me/spdray_bot")))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "امکان باز کردن تلگرام وجود ندارد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGiftCard() {
        val v = view ?: return
        val card = v.findViewById<View>(R.id.cardGiftConfig) ?: return
        val tvTitle = v.findViewById<TextView>(R.id.tvGiftTitle) ?: return
        val tvUsage = v.findViewById<TextView>(R.id.tvGiftUsageText) ?: return
        val progress = v.findViewById<android.widget.ProgressBar>(R.id.progressGiftUsage) ?: return
        val ctx = requireContext()
        when (com.spiderv2ray.spv.util.GiftConfigManager.getState(ctx)) {
            "done" -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "۵ گیگ تموم شد  •  خرید از ربات"
                tvUsage.visibility = View.GONE
                progress.visibility = View.GONE
                card.setOnClickListener { openTelegramBot() }
            }
            "active" -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "🎁 ۵ گیگ هدیه‌ی کانال اسپایدر"
                val used = com.spiderv2ray.spv.util.GiftConfigManager.getUsedBytes(ctx)
                val percent = ((used.toDouble() / com.spiderv2ray.spv.util.GiftConfigManager.LIMIT_BYTES) * 100).toInt().coerceIn(0, 100)
                progress.visibility = View.VISIBLE
                progress.progress = percent
                tvUsage.visibility = View.VISIBLE
                tvUsage.text = "${formatGiftBytes(used)} از 5.00 GB مصرف شده"
                card.setOnClickListener {
                    val id = com.spiderv2ray.spv.util.GiftConfigManager.getConfigId(ctx)
                    if (id != null && repo.getAll().any { it.id == id }) {
                        repo.setActiveId(id)
                        Toast.makeText(ctx, "کانفیگ هدیه فعال شد", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                }
            }
            else -> {
                card.visibility = View.VISIBLE
                tvTitle.text = "🎁 ۵ گیگ هدیه‌ی کانال اسپایدر"
                tvUsage.visibility = View.GONE
                progress.visibility = View.GONE
                card.setOnClickListener {
                    val cfg = com.spiderv2ray.spv.util.GiftConfigManager.activateGift(ctx)
                    if (cfg != null) {
                        Toast.makeText(ctx, "کانفیگ هدیه اضافه و فعال شد", Toast.LENGTH_LONG).show()
                        refreshList()
                    } else {
                        Toast.makeText(ctx, "خطا در دریافت کانفیگ هدیه", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
