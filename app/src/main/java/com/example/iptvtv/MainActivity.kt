package com.iptv.jktivi

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Base64
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

private const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

@OptIn(UnstableApi::class)
class MainActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var toast: TextView
    private lateinit var panel: LinearLayout
    private lateinit var groupTitle: TextView
    private lateinit var list: ListView

    // thanh thông tin kênh
    private lateinit var bar: LinearLayout
    private lateinit var barLogo: ImageView
    private lateinit var barTitle: TextView
    private lateinit var barNow: TextView
    private lateinit var barNext: TextView
    private lateinit var barClock: TextView
    private lateinit var barDate: TextView
    private lateinit var barTech: TextView

    private val ui = Handler(Looper.getMainLooper())
    private val hideToastR = Runnable { toast.visibility = View.GONE }
    private val hidePanelR = Runnable { hidePanel() }
    private val hideBarR = Runnable { hideBar() }
    private val commitR = Runnable { commitDigits() }
    private val tick = object : Runnable {
        override fun run() { refreshBar(); ui.postDelayed(this, 1000) }
    }

    private val meter by lazy { DefaultBandwidthMeter.Builder(this).build() }
    private val vn = Locale.forLanguageTag("vi-VN")
    private val tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private val fmtClock = SimpleDateFormat("HH:mm:ss", vn).apply { timeZone = tz }
    private val fmtDate = SimpleDateFormat("EEEE, dd/MM/yyyy", vn).apply { timeZone = tz }
    private val fmtHm = SimpleDateFormat("HH:mm", vn).apply { timeZone = tz }

    private var all: List<Channel> = emptyList()
    private var groups: List<String> = listOf("Tất cả")
    private var groupIndex = 0
    private var shownIdx: List<Int> = emptyList()
    private var cur = -1
    private val digits = StringBuilder()
    private var pendingKey: String? = null

    // cảm ứng
    private lateinit var touchBar: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var gestures: GestureDetector
    private var query = ""
    private var resizeIdx = 0
    private val hideTouchR = Runnable { hideTouchControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        pendingKey = intent?.getStringExtra(HomeChannel.EXTRA_KEY)
        lowThread(4000) { HomeChannel.publish(applicationContext) }

        // Đệm nhỏ để vào kênh và chuyển kênh nhanh
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 15000, 1000, 1500)
            .build()
        player = ExoPlayer.Builder(this).setLoadControl(load).setBandwidthMeter(meter).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val c = error.cause
                val http = if (c is HttpDataSource.InvalidResponseCodeException) " (HTTP ${c.responseCode})" else ""
                Log.e("JKTivi", "Playback error: ${error.errorCodeName}", error)
                showToast("Lỗi phát: ${error.errorCodeName}$http", 8000)
            }
        })

        val onEpg = { ui.post { if (!isDestroyed) onEpgUpdated() }; Unit }
        thread {
            val cached = Playlist.cached(this)
            ui.post { if (!isDestroyed) setChannels(cached.channels) }
            if (cached.epgUrl != null) lowThread { Epg.load(applicationContext, cached.epgUrl, onEpg) }

            val fresh = runCatching { Playlist.download(this) }.getOrNull()
            if (fresh != null && fresh.channels.isNotEmpty()) {
                ui.post { if (!isDestroyed) setChannels(fresh.channels) }
                if (fresh.epgUrl != null && fresh.epgUrl != cached.epgUrl)
                    lowThread { Epg.load(applicationContext, fresh.epgUrl, onEpg) }
            }
        }
    }

    // ---------- UI ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
    }

    private val gold = Glass.GOLD
    private val goldLight = Glass.GOLD_LIGHT
    private val muted = Glass.MUTED
    private val density get() = resources.displayMetrics.density

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false; keepScreenOn = true; isFocusable = false
        }
        root.addView(playerView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // Lớp bắt cử chỉ nằm trên video, dưới mọi thành phần giao diện khác
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (panel.visibility == View.VISIBLE) hidePanel() else toggleTouchControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean { togglePause(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) < dp(80)) return false
                    if (dx > 0) showPanel() else hidePanel()
                } else {
                    if (Math.abs(dy) < dp(80)) return false
                    if (dy < 0) play(cur + 1) else play(cur - 1)
                }
                return true
            }
        })
        val touchLayer = View(this)
        touchLayer.setOnTouchListener { _, e -> gestures.onTouchEvent(e); true }
        root.addView(touchLayer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // thông báo nhỏ (số kênh đang nhập, lỗi) – viên kính vàng
        toast = tv(30f, gold, true).apply {
            background = Glass.card(density, 18)
            setPadding(dp(28), dp(12), dp(28), dp(12)); visibility = View.GONE
        }
        root.addView(toast, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dp(32), dp(40), 0) })

        // thẻ kính thông tin kênh nổi ở đáy màn hình
        barLogo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = Glass.logoTile(density); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        barTitle = tv(30f, Color.WHITE, true)
        barNow = tv(22f, gold)
        barNext = tv(18f, muted)
        barClock = tv(34f, Color.WHITE, true).apply { gravity = Gravity.END }
        barDate = tv(16f, muted).apply { gravity = Gravity.END }
        barTech = tv(18f, goldLight).apply { gravity = Gravity.END }
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(barTitle); addView(barNow); addView(barNext)
        }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            addView(barClock); addView(barDate); addView(barTech)
        }
        val barDivider = View(this).apply { setBackgroundColor(0x55FFC83D) }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Glass.card(density, 28)
            setPadding(dp(28), dp(18), dp(32), dp(18)); visibility = View.GONE
            addView(barLogo, LinearLayout.LayoutParams(dp(120), dp(68)).apply { rightMargin = dp(24) })
            addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(barDivider, LinearLayout.LayoutParams(dp(1), dp(60)).apply { setMargins(dp(16), 0, dp(20), 0) })
            addView(right, LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { setMargins(dp(48), 0, dp(48), dp(32)) })

        // bảng kính danh sách kênh nổi bên trái
        groupTitle = tv(22f, gold, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(18), dp(8), dp(14))
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_UP && query.isEmpty()) {
                    resetPanelTimer()
                    if (e.x < v.width / 3f) changeGroup(-1) else if (e.x > v.width * 2 / 3f) changeGroup(1)
                }
                true
            }
        }
        val line = View(this).apply { setBackgroundColor(0x55FFC83D) }
        list = ListView(this).apply {
            this.divider = null
            this.selector = Glass.rowSelector(density)
            setPadding(dp(8), dp(6), dp(8), dp(10)); clipToPadding = false
            setOnItemClickListener { _, _, pos, _ -> play(shownIdx[pos]); hidePanel() }
            setOnTouchListener { _, _ -> resetPanelTimer(); false }
        }
        searchBox = EditText(this).apply {
            hint = "Tìm kênh…"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 18f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setBackgroundColor(0x22FFFFFF)
            setPadding(dp(18), dp(10), dp(18), dp(10)); visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty().trim()
                    resetPanelTimer()
                    fillList()
                }
            })
            setOnEditorActionListener { v, _, _ -> hideKeyboard(v); true }
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.card(density, 28); visibility = View.GONE
            addView(groupTitle, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(24), 0, dp(24), 0)
            })
            addView(searchBox, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(4)) })
            addView(list, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        root.addView(panel, FrameLayout.LayoutParams(
            minOf(dp(540), (resources.displayMetrics.widthPixels * 0.9f).toInt()),
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
            .apply { setMargins(dp(24), dp(24), 0, dp(24)) })

        // Hàng nút điều khiển cảm ứng (hiện khi chạm vào màn hình)
        touchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(touchBtn("☰") { showPanel() })
            addView(touchBtn("🔍") { openSearch() })
            addView(touchBtn("⏮") { play(cur - 1) })
            addView(touchBtn("⏯") { togglePause() })
            addView(touchBtn("⏭") { play(cur + 1) })
            addView(touchBtn("⛶") { cycleResize() })
        }
        root.addView(touchBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { setMargins(0, dp(24), 0, 0) })
        setContentView(root)
        applyResize(getSharedPreferences("s", MODE_PRIVATE).getInt("resize", 0))
    }

    private fun touchBtn(label: String, onClick: () -> Unit): TextView {
        val b = tv(26f, Color.WHITE, true)
        b.text = label
        b.gravity = Gravity.CENTER
        b.background = Glass.card(density, 22)
        b.setPadding(dp(18), dp(10), dp(18), dp(10))
        b.isClickable = true
        b.setOnClickListener { showTouchControls(); onClick() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(5), 0, dp(5), 0)
        b.layoutParams = lp
        return b
    }

    private fun showToast(text: String, durationMs: Long = 3000) {
        toast.text = text; toast.visibility = View.VISIBLE
        ui.removeCallbacks(hideToastR); ui.postDelayed(hideToastR, durationMs)
    }

    // ---------- Thanh thông tin kênh ----------
    private fun showBar() {
        if (cur < 0 || panel.visibility == View.VISIBLE) return
        val ch = all[cur]
        barTitle.text = "${cur + 1}   ${ch.name}"
        Logos.load(this, ch.logo, barLogo)
        bar.visibility = View.VISIBLE
        ui.removeCallbacks(tick); tick.run()
        ui.removeCallbacks(hideBarR); ui.postDelayed(hideBarR, 6000)
    }

    private fun hideBar() {
        ui.removeCallbacks(tick); ui.removeCallbacks(hideBarR)
        bar.visibility = View.GONE
    }

    private fun refreshBar() {
        if (cur < 0 || cur >= all.size) return
        val ch = all[cur]
        val now = System.currentTimeMillis()
        val np = Epg.now(ch, now)
        val nx = Epg.next(ch, now)
        barNow.text = if (np != null) "▶ ${np.title}   ${fmtHm.format(Date(np.start))} – ${fmtHm.format(Date(np.end))}"
        else "Chưa có thông tin lịch phát"
        barNext.text = if (nx != null) "Tiếp theo: ${nx.title}   ${fmtHm.format(Date(nx.start))}" else ""
        barNext.visibility = if (nx != null) View.VISIBLE else View.GONE
        barClock.text = fmtClock.format(Date(now))
        barDate.text = fmtDate.format(Date(now))
        val size = player.videoSize
        val res = if (size.width > 0) "${size.width}×${size.height}" else "đang tải…"
        val mbps = meter.bitrateEstimate / 1_000_000.0
        barTech.text = String.format(Locale.US, "%s · %.1f Mbps", res, mbps)
    }

    private fun onEpgUpdated() {
        if (panel.visibility == View.VISIBLE) (list.adapter as? BaseAdapter)?.notifyDataSetChanged()
        if (bar.visibility == View.VISIBLE) refreshBar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val key = intent.getStringExtra(HomeChannel.EXTRA_KEY) ?: return
        if (all.isEmpty()) { pendingKey = key; return }
        val i = HomeChannel.indexOf(all, key)
        if (i >= 0) play(i) else showToast("Chưa có kênh này trong danh sách", 3000)
    }

    // ---------- Dữ liệu ----------
    private fun setChannels(newList: List<Channel>) {
        if (newList.isEmpty() || newList == all) return
        val currentUrl = all.getOrNull(cur)?.url
        val currentName = all.getOrNull(cur)?.name
        val first = all.isEmpty()
        all = newList
        groups = listOf("Tất cả") + newList.map { it.group }.distinct()
        groupIndex = 0
        if (first) {
            // Nhớ kênh cũ khi thoát ứng dụng
            val p = getSharedPreferences("s", MODE_PRIVATE)
            val lu = p.getString("last", null); val ln = p.getString("lastName", null)
            val idx = newList.indexOfFirst { it.url == lu && it.name == ln }
                .takeIf { it >= 0 } ?: newList.indexOfFirst { it.url == lu }
            val fromHome = pendingKey?.let { HomeChannel.indexOf(newList, it) } ?: -1
            pendingKey = null
            play(if (fromHome >= 0) fromHome else idx.coerceAtLeast(0))
        } else {
            // Cập nhật danh sách ngầm, không làm gián đoạn kênh đang xem
            cur = newList.indexOfFirst { it.url == currentUrl && it.name == currentName }
                .takeIf { it >= 0 } ?: newList.indexOfFirst { it.url == currentUrl }.coerceAtLeast(0)
        }
    }

    // Kodi M3U ClearKey format: KID:KEY (both hexadecimal, 16 bytes each).
    // Media3 LocalMediaDrmCallback expects a ClearKey JSON license response.
    private fun clearKeyLicenseResponse(value: String): ByteArray {
        val pair = value.trim().substringBefore('|').split(':', limit = 2)
        require(pair.size == 2) { "license_key phải có dạng KID:KEY" }
        val kid = pair[0].trim()
        val key = pair[1].trim()
        fun decodeHex(hex: String): ByteArray {
            require(hex.length == 32 && hex.all { it in "0123456789abcdefABCDEF" }) {
                "KID/KEY ClearKey phải là chuỗi hex 32 ký tự"
            }
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        fun base64Url(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val kid64 = base64Url(decodeHex(kid))
        val key64 = base64Url(decodeHex(key))
        val json = """{"keys":[{"kty":"oct","kid":"$kid64","k":"$key64"}],"type":"temporary"}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    // ---------- Phát kênh ----------
    private fun play(index: Int) {
        if (all.isEmpty()) return
        cur = (index % all.size + all.size) % all.size
        val ch = all[cur]
        val u = ch.url.lowercase()
        val isClearKey = ch.drm && (
            ch.licenseType.equals("clearkey", ignoreCase = true) ||
            (ch.licenseType.isBlank() && ch.licKey.isNotBlank())
        )
        if (ch.drm && !isClearKey) {
            player.stop()
            player.clearMediaItems()
            showBar()
            showToast("DRM ${ch.licenseType.ifBlank { "không xác định" }} chưa được cấu hình", 7000)
            return
        }
        if (isClearKey && ch.licKey.isBlank()) {
            showToast("Kênh ClearKey thiếu license_key trong M3U", 7000)
            return
        }
        val dash = u.contains(".mpd") || ch.manifestType == "mpd" || ch.manifestType == "dash"
        val hls = !dash && (u.contains(".m3u8") || ch.manifestType == "hls")
        val item = MediaItem.Builder().setUri(ch.url).apply {
            if (dash) setMimeType(MimeTypes.APPLICATION_MPD)
            else if (hls) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (isClearKey) {
                setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build())
            }
        }.build()
        // Header riêng cho từng kênh (User-Agent, Referer...) nếu playlist có khai báo
        val headers = HashMap<String, String>(ch.headers)
        val uaKey = headers.keys.firstOrNull { it.equals("User-Agent", ignoreCase = true) }
        val ua = uaKey?.let { headers.remove(it) } ?: DEFAULT_UA
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
        // Với ClearKey, tạo phản hồi license cục bộ từ cặp KID:KEY trong M3U.
        // Các kênh thường vẫn dùng pipeline HLS/DASH mặc định.
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(http)
        if (isClearKey) {
            val drmResponse = clearKeyLicenseResponse(ch.licKey)
            val drmManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(LocalMediaDrmCallback(drmResponse))
            mediaSourceFactory.setDrmSessionManagerProvider { drmManager }
        }
        val source = if (hls && !isClearKey)
            // Bắt đầu phát mà không cần tải trước một đoạn video khi playlist có đủ thông tin codec
            HlsMediaSource.Factory(http).setAllowChunklessPreparation(true).createMediaSource(item)
        else mediaSourceFactory.createMediaSource(item)
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        getSharedPreferences("s", MODE_PRIVATE).edit()
            .putString("last", ch.url).putString("lastName", ch.name).apply()
        showBar()
    }

    // ---------- Danh sách kênh ----------
    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = shownIdx.size
        override fun getItem(p: Int) = shownIdx[p]
        override fun getItemId(p: Int) = p.toLong()

        private fun makeRow(): LinearLayout {
            val num = tv(20f, muted).apply { gravity = Gravity.CENTER }
            val logo = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = Glass.logoTile(density); setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            val name = tv(20f, Color.WHITE)
            val epg = tv(15f, Glass.MUTED_DIM)
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; addView(name); addView(epg)
            }
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                addView(num, LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(logo, LinearLayout.LayoutParams(dp(76), dp(44)).apply { rightMargin = dp(14) })
                addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val row = (cv as? LinearLayout) ?: makeRow()
            val i = shownIdx[p]
            val ch = all[i]
            val num = row.getChildAt(0) as TextView
            num.text = "${i + 1}"
            // Kênh đang phát: số màu vàng, in đậm
            num.setTextColor(if (i == cur) gold else muted)
            num.setTypeface(null, if (i == cur) Typeface.BOLD else Typeface.NORMAL)
            Logos.load(this@MainActivity, ch.logo, row.getChildAt(1) as ImageView)
            val col = row.getChildAt(2) as LinearLayout
            (col.getChildAt(0) as TextView).text = ch.name
            val np = Epg.now(ch, System.currentTimeMillis())
            val e = col.getChildAt(1) as TextView
            if (np != null) {
                e.text = "▶ ${np.title}  ${fmtHm.format(Date(np.start))}–${fmtHm.format(Date(np.end))}"
                e.visibility = View.VISIBLE
            } else e.visibility = View.GONE
            return row
        }
    }

    private fun showPanel() {
        if (all.isEmpty()) return
        hideBar()
        hideTouchControls()
        groupIndex = groups.indexOf(all[cur].group).coerceAtLeast(0)
        fillList()
        panel.visibility = View.VISIBLE
        list.requestFocus()
        list.setSelection(shownIdx.indexOf(cur).coerceAtLeast(0))
        resetPanelTimer()
    }

    private fun hidePanel() {
        ui.removeCallbacks(hidePanelR)
        hideKeyboard(searchBox)
        searchBox.visibility = View.GONE
        if (query.isNotEmpty()) searchBox.setText("")
        panel.visibility = View.GONE
    }

    private fun resetPanelTimer() {
        ui.removeCallbacks(hidePanelR)
        ui.postDelayed(hidePanelR, if (searchBox.visibility == View.VISIBLE) 30000L else 10000L)
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun openSearch() {
        showPanel()
        searchBox.visibility = View.VISIBLE
        searchBox.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        resetPanelTimer()
    }

    private fun changeGroup(d: Int) {
        groupIndex = (groupIndex + d + groups.size) % groups.size
        fillList()
        list.setSelection(0)
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    private fun fillList() {
        val g = groups[groupIndex]
        val q = fold(query)
        shownIdx = all.indices.filter {
            if (q.isNotEmpty()) fold(all[it].name).contains(q)
            else groupIndex == 0 || all[it].group == g
        }
        groupTitle.text = if (q.isNotEmpty()) "Kết quả tìm kiếm (${shownIdx.size})"
        else "◀  $g (${shownIdx.size})  ▶"
        list.adapter = RowAdapter()
    }

    // ---------- Phím số ----------
    private fun commitDigits() {
        ui.removeCallbacks(commitR)
        val n = digits.toString().toIntOrNull()
        digits.setLength(0)
        if (n != null && n in 1..all.size) play(n - 1) else showToast("Không có kênh ${n ?: ""}")
    }

    private fun onDigit(d: Int) {
        digits.append(d)
        showToast(digits.toString())
        ui.removeCallbacks(commitR)
        if (digits.length >= all.size.toString().length) commitDigits() else ui.postDelayed(commitR, 1500)
    }

    // ---------- Cảm ứng ----------
    private fun lowThread(delayMs: Long = 2500, block: () -> Unit) =
        thread(priority = Thread.MIN_PRIORITY) { Thread.sleep(delayMs); block() }

    private fun showTouchControls() {
        touchBar.visibility = View.VISIBLE
        ui.removeCallbacks(hideTouchR); ui.postDelayed(hideTouchR, 6000)
    }

    private fun hideTouchControls() {
        ui.removeCallbacks(hideTouchR)
        touchBar.visibility = View.GONE
    }

    private fun toggleTouchControls() {
        if (touchBar.visibility == View.VISIBLE) { hideTouchControls(); hideBar() }
        else { showTouchControls(); showBar() }
    }

    private fun togglePause() {
        player.playWhenReady = !player.playWhenReady
        if (player.playWhenReady) {
            player.seekToDefaultPosition() // quay về điểm trực tiếp sau khi tạm dừng
            showToast("▶ Đang phát")
        } else showToast("⏸ Tạm dừng")
    }

    private fun applyResize(i: Int) {
        resizeIdx = i
        playerView.resizeMode = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        )[i]
    }

    private fun cycleResize() {
        applyResize((resizeIdx + 1) % 3)
        getSharedPreferences("s", MODE_PRIVATE).edit().putInt("resize", resizeIdx).apply()
        showToast(arrayOf("Vừa khung hình", "Phóng to (cắt viền)", "Kéo giãn")[resizeIdx])
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    // ---------- Điều khiển remote ----------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        val k = e.keyCode
        if (touchBar.visibility == View.VISIBLE) hideTouchControls()

        if (panel.visibility == View.VISIBLE) {
            resetPanelTimer()
            if (searchBox.hasFocus() && k != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)
            when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { changeGroup(-1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { changeGroup(1); return true }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> { hidePanel(); return true }
            }
            return super.dispatchKeyEvent(e) // danh sách tự xử lý lên/xuống/OK
        }

        val digit = when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> k - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> k - KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (digit >= 0) { onDigit(digit); return true }

        when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> play(cur + 1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> play(cur - 1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> showPanel()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                if (digits.isNotEmpty()) commitDigits() else showBar()
            }
            else -> return super.dispatchKeyEvent(e)
        }
        return true
    }

    override fun onStart() { super.onStart(); if (all.isNotEmpty()) player.playWhenReady = true }
    override fun onStop() { super.onStop(); player.playWhenReady = false }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); player.release(); super.onDestroy() }
}
