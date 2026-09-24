package com.lmt.tooltx.ui.tool

import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.R
import com.lmt.tooltx.WebViewBridge
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentToolBinding
import com.lmt.tooltx.ui.home.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class ToolFragment : Fragment() {

    @Volatile
    private var running = false
    private var startJob: Job? = null
    private var lastCode: String? = null
    private var gameOpen = false
    private var gameUrl: String? = null
    private var pendingOpen = false

    private var _binding: FragmentToolBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOpenTool.setOnClickListener { onOpenGameClicked() }
        binding.btnResetToken.setOnClickListener { startTool(forceRefresh = true) }
        binding.btnExitGame.setOnClickListener { closeGame() }
        binding.btnMuteSpeaker.setOnClickListener { onMuteClicked() }
        binding.btnBack.setOnClickListener {
            if (gameOpen) {
                closeGame()
            } else {
                (requireActivity() as MainActivity).showFragment(HomeFragment::class.java, "home", push = false)
            }
        }
binding.predCard.setOnTouchListener(::onDragTouch)
        setupWebView()

        val bridge = (requireActivity() as MainActivity).getBridge()
        if (bridge.isLoggedIn()) startTool()

        backCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (gameOpen) {
                    closeGame()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)
    }

    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    private fun setSystemUiFullscreen(fullscreen: Boolean) {
        val window = requireActivity().window
        if (fullscreen) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                try {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                } catch (_: Exception) {}
            }
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                try {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                } catch (_: Exception) {}
            }
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    // ── Cửa sổ dự đoán kéo thả ───────────────────────────────
    private var downRawX = 0f
    private var downRawY = 0f
    private var startTransX = 0f
    private var startTransY = 0f
    private var isDragging = false

    private fun onDragTouch(v: View, e: MotionEvent): Boolean {
        val card = binding.predCard
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX; downRawY = e.rawY
                startTransX = card.translationX; startTransY = card.translationY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downRawX
                val dy = e.rawY - downRawY
                if (kotlin.math.abs(dx) > 6f || kotlin.math.abs(dy) > 6f) isDragging = true
                if (isDragging) card.translationX = startTransX + dx
                if (isDragging) card.translationY = startTransY + dy
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    private fun setupWebView() {
        val wv = binding.webView
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadsImagesAutomatically = true
        s.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.setSupportZoom(false)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?) {
                super.onPageStarted(view, url)
                WebViewBridge.installWsShim()
                setStatus("Game đang tải…")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                WebViewBridge.installWsShim()
                WebViewBridge.mutePage()
            }
        }
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                wv.post {
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                    transport?.webView = wv
                    resultMsg.sendToTarget()
                }
                return true
            }
        }
        WebViewBridge.attach(wv)
    }

    // ── Flow: nút MỞ TOOL → MỞ GAME → mở game fullscreen ngang ─
    private fun onOpenGameClicked() {
        if (gameOpen) {
            closeGame()
            return
        }
        val bridge = (requireActivity() as MainActivity).getBridge()
        if (bridge.isSocketConnected() && gameUrl != null) {
            openGame()
        } else if (!running) {
            startTool()
        } else {
            setStatus("Đang kết nối server — chờ 1 chút rồi tự mở game.")
        }
    }

    private fun openGame() {
        if (gameOpen) return
        gameOpen = true
        pendingOpen = false
        backCallback?.isEnabled = true
        binding.toolPage.visibility = View.GONE
        binding.gameOverlay.visibility = View.VISIBLE
        val wv = binding.webView
        wv.visibility = View.VISIBLE
        WebViewBridge.attach(wv)
        gameUrl?.let { WebViewBridge.navigate(it) }
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        (requireActivity() as MainActivity).setGameFullscreen(true)
        setSystemUiFullscreen(true)
        binding.btnOpenTool.text = getString(R.string.close_game)
        setStatus("Đang chơi — cửa sổ dự đoán nằm ở giữa dưới màn hình.")
    }

    private fun closeGame() {
        if (!gameOpen) return
        gameOpen = false
        backCallback?.isEnabled = false
        binding.gameOverlay.visibility = View.GONE
        binding.toolPage.visibility = View.VISIBLE
        val wv = binding.webView
        try { wv.stopLoading() } catch (_: Exception) {}
        wv.visibility = View.GONE
        try { wv.loadUrl("about:blank") } catch (_: Exception) {}
        try { wv.clearHistory() } catch (_: Exception) {}
        WebViewBridge.detach()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        (requireActivity() as MainActivity).setGameFullscreen(false)
        setSystemUiFullscreen(false)
        binding.btnOpenTool.text = if (running) getString(R.string.open_game) else getString(R.string.open_tool)
        if (running) setStatus("Server: đã kết nối — bấm MỞ GAME để chơi.")
        else setStatus("Chưa kết nối — bấm MỞ GAME để kết nối.")
    }

    private fun startTool(forceRefresh: Boolean = false) {
        startJob?.cancel()
        running = true
        if (forceRefresh && gameOpen) {
            gameOpen = false
            backCallback?.isEnabled = false
            pendingOpen = true
            binding.gameOverlay.visibility = View.GONE
            binding.toolPage.visibility = View.VISIBLE
            wvGone()
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        setStatus(if (forceRefresh) "Đang làm mới mã và kết nối lại server..." else "Đang kết nối server...")
        setCode(null)

        startJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
                val session = bridge.getSession()
                if (session == null) {
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        setStatus("Chưa đăng nhập — đăng nhập trước.")
                        stopToolButtons()
                    }
                    return@launch
                }
                val server = bridge.discoverServer() ?: "http://localhost:8787"
                bridge.writeBugLog("ui", "startTool: server=$server forceRefresh=$forceRefresh")
                if (forceRefresh) {
                    bridge.stopAgent()
                    bridge.disconnectSocket()
                }
                val token = if (forceRefresh) {
                    bridge.forceRefreshToken() ?: bridge.refreshToken()
                } else {
                    bridge.refreshToken()
                } ?: session["idToken"]?.toString().orEmpty()
                bridge.clearKick()

                for (attempt in 1..2) {
                    try {
                        bridge.disconnectSocket()
                        bridge.connectSocket(server, token)
                    } catch (_: Exception) {}
                    val connected = bridge.isSocketConnected()
                    bridge.writeBugLog("ui", "connect attempt=$attempt connected=$connected")
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        setStatus(
                            if (connected) "Server: $server — lấy mã liên kết..."
                            else "Đang thử kết nối server... ($attempt/2)"
                        )
                    }
                    if (connected) break
                    delay(2500)
                }

                if (!bridge.isSocketConnected()) {
                    bridge.writeBugLog("ui", "ket qua: KHONG ket noi duoc server (sau 2 lan)")
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        setStatus("Không kết nối được server: $server")
                        setAgent("Kiểm tra: server đã bật trên PC, điện thoại cùng mạng Wi-Fi với PC, và địa chỉ server đúng.")
                        stopToolButtons()
                    }
                    return@launch
                }

                val code = bridge.getAgentPair(server)
                bridge.writeBugLog("ui", "agent_code=" + (code ?: "NULL"))
                if (code.isNullOrEmpty()) {
                    val kick = bridge.getLastKick()
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        if (!kick.isNullOrEmpty()) {
                            setStatus("Server từ chối: $kick")
                            setAgent("Bấm RESET MÃ để làm mới mã đăng nhập rồi kết nối lại.")
                        } else {
                            setStatus("Không lấy được mã liên kết — bấm RESET MÃ rồi MỞ GAME lại.")
                            setAgent("Server bật nhưng không trả mã trong 10s.")
                        }
                        stopToolButtons()
                    }
                    return@launch
                }
                lastCode = code
                val ok = bridge.startAgent(server, code)
                bridge.writeBugLog("ui", "startAgent ok=" + ok)
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    setCode("Mã liên kết: $code")
                    if (ok) {
                        setStatus("Đã kết nối — bấm MỞ GAME để chơi.")
                        setAgent("Chờ game mở rồi chơi ngay trong app.")
                        binding.btnOpenTool.isEnabled = true
                        binding.btnOpenTool.text = getString(R.string.open_game)
                    } else {
                        setStatus("Không khởi động được agent.")
                        setAgent("Thử bấm RESET MÃ lại.")
                        stopToolButtons()
                    }
                }
                if (ok) pollAgentStatus(bridge, gameOpen)
            } finally {
                running = false
            }
        }
    }

    private fun stopToolButtons() {
        binding.btnOpenTool.isEnabled = false
        binding.btnOpenTool.text = getString(R.string.open_game)
    }

    private fun pollAgentStatus(bridge: PythonBridge, gameOpenAtStart: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            while (!Thread.currentThread().isInterrupted) {
                if (_binding == null) return@launch
                val st = bridge.agentStatus()
                val msg = st["message"]?.toString()?.takeIf { it.isNotEmpty() } ?: ""
                val connected = st["connected"]?.toString()?.toBooleanStrictOrNull() ?: false
                val url = st["url"]?.toString()?.takeIf { it.isNotEmpty() }
                if (url != null) gameUrl = url

                val panel = bridge.getLastPanel()
                if (panel.isNotEmpty()) {
                    val pick = panel["pick"]?.toString()?.trim().orEmpty()
                    val hist = (panel["hist"] as? List<*>)?.size ?: 0
                    bridge.writeBugLog("ui", "panel: pick=$pick hist=$hist")
                    withContext(Dispatchers.Main) {
                        if (_binding != null) prediction(panel)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (msg.isNotEmpty()) setAgent(msg)
                    if (connected) {
                        binding.btnOpenTool.isEnabled = true
                        binding.btnOpenTool.backgroundTintList =
                            ContextCompat.getColorStateList(requireContext(), R.color.panelGo)
                        if (gameOpen) {
                            binding.btnOpenTool.text = getString(R.string.close_game)
                        } else {
                            binding.btnOpenTool.text = getString(R.string.open_game)
                            if (gameUrl != null) {
                                setStatus("Đã kết nối — bấm MỞ GAME để chơi.")
                                if (pendingOpen && !gameOpen) openGame()
                            } else {
                                setStatus("Đã kết nối — đang chờ mã game...")
                            }
                        }
                    } else {
                        binding.btnOpenTool.isEnabled = true
                        binding.btnOpenTool.backgroundTintList =
                            ContextCompat.getColorStateList(requireContext(), R.color.primary)
                        binding.btnOpenTool.text = getString(R.string.open_tool)
                        if (msg.isNotEmpty()) setStatus(msg)
                    }
                }
                delay(2000)
            }
        }
    }

    private fun onMuteClicked() {
        val muted = WebViewBridge.toggleMute()
        binding.btnMuteSpeaker.setImageResource(if (muted) R.drawable.ic_speaker_off else R.drawable.ic_speaker_on)
        binding.btnMuteSpeaker.contentDescription = getString(if (muted) R.string.unmute_sound else R.string.mute_sound)
        setStatus(if (muted) "Âm thanh game đã TẮT (loa ẩn)." else "Âm thanh game đã BẬT — nghe lại bình thường.")
    }

    private fun wvGone() {
        val wv = binding.webView
        try { wv.stopLoading() } catch (_: Exception) {}
        wv.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (gameOpen) {
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            (requireActivity() as MainActivity).setGameFullscreen(true)
            setSystemUiFullscreen(true)
            WebViewBridge.resumeWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        WebViewBridge.pauseWebView()
    }

    override fun onStop() {
        super.onStop()
        WebViewBridge.pauseWebView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        WebViewBridge.detach()
        runCatching { (activity as? MainActivity)?.getBridge()?.stopAgent() }
        if (gameOpen) {
            gameOpen = false
            backCallback?.isEnabled = false
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            setSystemUiFullscreen(false)
        }
        _binding = null
    }

    private fun prediction(p: Map<*, *>) {
        if (p.isEmpty()) return
        val pick = p["pick"]?.toString()?.trim()
        if (pick.isNullOrEmpty()) return

        val isTai = pick.equals("T", ignoreCase = true)
        binding.tvPrediction.text = getString(if (isTai) R.string.tai else R.string.xiu)
        binding.tvPrediction.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isTai) R.color.error else R.color.primary
            )
        )

        val pT = p["pT"]?.toString()?.toDoubleOrNull() ?: (if (isTai) 60.0 else 40.0)
        val pX = p["pX"]?.toString()?.toDoubleOrNull() ?: (100.0 - pT)
        binding.tvPercentage.text = String.format(Locale.ROOT, "TÀI %02.0f  /  XỈU %02.0f", pT, pX)
        binding.progressBar.progress = pT.roundToInt().coerceIn(0, 100)

        val conf = (p["confidence"] ?: p["conf"])?.toString()?.toDoubleOrNull()
        val confTxt = conf?.let { getString(R.string.confidence, it) }.orEmpty()

        val hist = p["hist"] ?: p["history"]
        var histTxt = ""
        if (hist is List<*>) {
            histTxt = hist.takeLast(16).joinToString("   ") { item ->
                (item?.toString()?.take(1) ?: "").uppercase()
            }
        }
        binding.tvHistory.text = listOf(confTxt, histTxt)
            .filter { it.isNotEmpty() }
            .joinToString(" | ")
    }

    private fun setStatus(text: String) {
        binding.tvToolStatus.text = text
        binding.tvBottomStatus.text = text
    }

    private fun setAgent(text: String) {
        binding.tvAgentStatus.text = text
        binding.tvBottomStatus.text = text
    }

    private fun setCode(text: String?) {
        binding.tvCode.text = text.orEmpty()
        binding.tvCode.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
}