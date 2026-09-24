package com.lmt.tooltx

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.ui.admin.AdminFragment
import com.lmt.tooltx.ui.auth.SignInFragment
import com.lmt.tooltx.ui.auth.SignUpFragment
import com.lmt.tooltx.ui.browser.BrowserFragment
import com.lmt.tooltx.ui.home.HomeFragment
import com.lmt.tooltx.ui.settings.SettingsFragment
import com.lmt.tooltx.ui.tool.ToolFragment
import com.lmt.tooltx.ui.topup.TopUpFragment
import java.util.ArrayDeque
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var navPill: View
    private lateinit var navWrap: View
    private lateinit var fragmentContainer: View
    private lateinit var navItems: List<View>
    private val pythonBridge by lazy { PythonBridge(this) }
    private val navStack = ArrayDeque<String>()

    private val navItemIds = listOf(
        R.id.nav_item_home, R.id.nav_item_topup, R.id.nav_item_tool,
        R.id.nav_item_settings, R.id.nav_item_admin
    )
    private val navIconIds = listOf(
        R.id.nav_icon_home, R.id.nav_icon_topup, R.id.nav_icon_tool,
        R.id.nav_icon_settings, R.id.nav_icon_admin
    )
    private val navLabelIds = listOf(
        R.id.nav_label_home, R.id.nav_label_topup, R.id.nav_label_tool,
        R.id.nav_label_settings, R.id.nav_label_admin
    )
    private val navTags = listOf("home", "topup", "tool", "settings", "admin")

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_mode", true)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pythonBridge.setSessionPath(filesDir.absolutePath + "/session.json")

        navPill = findViewById(R.id.nav_pill)
        navWrap = findViewById(R.id.nav_wrap)
        fragmentContainer = findViewById(R.id.fragment_container)
        navItems = navItemIds.map { findViewById<View>(it) }
        for (i in navItems.indices) {
            navItems[i].setOnClickListener { _ ->
                val tag = navTags[i]
                showFragment(fragmentClassFor(tag), tag)
            }
        }
        setAdminVisible(false)
        setupImmersive()

        if (savedInstanceState == null) {
            val session = pythonBridge.getSession()
            if (session != null) {
                showFragment(HomeFragment::class.java, "home")
                selectNavIndex(navTags.indexOf("home"), animate = false)
            } else {
                showFragment(SignInFragment::class.java, "signin")
                navWrap.visibility = View.GONE
            }
        }

        startAutoConnect()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Volatile
    private var gameFullscreen = false

    private fun setupImmersive() {
        val root = findViewById<View>(R.id.main_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            // Khi đang fullscreen game thì KHÔNG pad gì cả — game phải chạm kín màn hình
            if (!gameFullscreen) {
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                fragmentContainer.setPadding(0, sys.top, 0, 0)
            } else {
                fragmentContainer.setPadding(0, 0, 0, 0)
            }
            insets
        }
        hideSystemBars()
    }

    fun setGameFullscreen(on: Boolean) {
        gameFullscreen = on
        if (on) {
            fragmentContainer.setPadding(0, 0, 0, 0)
            hideSystemBars()
        } else {
            // Trả lại padding theo insets hiện tại
            val root = findViewById<View>(R.id.main_root)
            root.post { root.requestApplyInsets() }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val pos = IntArray(2)
                focused.getLocationOnScreen(pos)
                val x = ev.rawX
                val y = ev.rawY
                val inside = x >= pos[0] && x <= pos[0] + focused.width &&
                    y >= pos[1] && y <= pos[1] + focused.height
                if (!inside) {
                    try {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(focused.windowToken, 0)
                        focused.clearFocus()
                    } catch (_: Exception) {}
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                // Khi đang fullscreen game thì ẩn CẢ status lẫn navigation (tránh "thanh ngang" trên cùng)
                if (gameFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or (if (gameFullscreen) View.SYSTEM_UI_FLAG_FULLSCREEN else 0)
                    )
            }
        } catch (_: Exception) {}
    }

    fun showFragment(cls: Class<out Fragment>, tag: String) {
        showFragment(cls, tag, push = true)
    }

    fun showFragment(cls: Class<out Fragment>, tag: String, push: Boolean) {
        val fm = supportFragmentManager
        val current = fm.primaryNavigationFragment
        if (current != null && current.tag == tag) return

        val ft = fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                R.anim.nav_enter, R.anim.nav_exit,
                R.anim.nav_enter, R.anim.nav_exit
            )
        if (current != null) {
            ft.hide(current)
        }

        val shown = fm.findFragmentByTag(tag)
        if (shown != null) {
            ft.show(shown)
            ft.setPrimaryNavigationFragment(shown)
        } else {
            val frag = cls.getDeclaredConstructor().newInstance()
            ft.add(R.id.fragment_container, frag, tag)
            ft.setPrimaryNavigationFragment(frag)
        }
        ft.commitAllowingStateLoss()
        fm.executePendingTransactions()

        val isAuth = tag == "signin" || tag == "signup"
        navWrap.visibility = if (tag == "tool") {
            View.GONE
        } else if (isAuth) {
            View.GONE
        } else {
            View.VISIBLE
        }
        if (isAuth) {
            if (tag == "signin") {
                navStack.clear()
                navStack.addLast(tag)
            } else {
                navStack.removeAll { it == tag }
                navStack.addLast(tag)
            }
        } else if (push) {
            navStack.removeAll { it == "signin" || it == "signup" }
            navStack.removeAll { it == tag }
            navStack.addLast(tag)
        }
        updateNavSelection(tag)
    }

    private fun updateNavSelection(tag: String) {
        val idx = navTags.indexOf(tag)
        if (idx >= 0) {
            selectNavIndex(idx, animate = true)
        }
    }

    private fun selectNavIndex(idx: Int, animate: Boolean) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val secondary = ContextCompat.getColor(this, R.color.onSurfaceVariant)
        for (i in navItems.indices) {
            val selected = i == idx
            val icon = findViewById<ImageView>(navIconIds[i])
            val label = findViewById<TextView>(navLabelIds[i])
            if (selected) {
                icon.setColorFilter(primary)
                label.setTextColor(primary)
            } else {
                icon.setColorFilter(secondary)
                label.setTextColor(secondary)
            }
        }
        if (animate) animatePill(idx) else positionPill(idx)
    }

    fun showNav(show: Boolean) {
        navWrap.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun showAdmin(show: Boolean) {
        setAdminVisible(show)
    }

    private fun setAdminVisible(show: Boolean) {
        val adminItem = findViewById<View>(R.id.nav_item_admin)
        adminItem.visibility = if (show) View.VISIBLE else View.GONE
        navPill.post { if (navPill.width > 0) navPill.translationX = pillTargetFor(activeIndex()) }
    }

    private fun activeIndex(): Int {
        val cur = supportFragmentManager.primaryNavigationFragment
        val tag = cur?.tag
        return navTags.indexOf(tag).let { if (it >= 0) it else 0 }
    }

    private fun visibleBefore(idx: Int): Int {
        var v = 0
        for (i in 0 until idx) {
            if (i < navItems.size && navItems[i].visibility == View.VISIBLE) v++
        }
        return v
    }

    private fun pillTargetFor(idx: Int): Float {
        val colWidth = navWrap.width.toFloat() / visibleCount()
        if (colWidth <= 0f) return navPill.translationX
        val center = (visibleBefore(idx) + 0.5f) * colWidth
        return center - navPill.width / 2f
    }

    private fun visibleCount(): Int {
        var c = 0
        for (i in navItems.indices) {
            if (navItems[i].visibility == View.VISIBLE) c++
        }
        return c
    }

    private fun positionPill(idx: Int) {
        navItems.firstOrNull()?.post {
            if (navPill.width > 0) {
                navPill.translationX = pillTargetFor(idx)
            }
        }
    }

    private fun animatePill(idx: Int) {
        navItems.firstOrNull()?.post {
            if (navPill.width <= 0) return@post
            val target = pillTargetFor(idx)
            if (navPill.translationX == target) return@post
            val anim = ValueAnimator.ofFloat(navPill.translationX, target).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { a -> navPill.translationX = a.animatedValue as Float }
            }
            anim.start()
        }
    }

    fun openBrowser() {
        showFragment(BrowserFragment::class.java, "browser")
    }

    private fun startAutoConnect() {
        GlobalScope.launch {
            while (isActive) {
                try {
                    val onToolPage = supportFragmentManager.primaryNavigationFragment is ToolFragment
                    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                    if (!onToolPage &&
                        prefs.getBoolean("auto_connect", true) && !pythonBridge.isSocketConnected()
                    ) {
                        val url = pythonBridge.discoverServer()
                        if (!url.isNullOrEmpty()) {
                            val token = pythonBridge.refreshToken()
                                ?: pythonBridge.getSession()?.get("idToken")?.toString().orEmpty()
                            pythonBridge.connectSocket(url, token)
                        }
                    }
                } catch (_: Exception) {}
                delay(10_000)
            }
        }
    }

    fun getBridge(): PythonBridge = pythonBridge

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val fm = supportFragmentManager
        val current = fm.primaryNavigationFragment
        if (current is SignUpFragment) {
            if (navStack.size > 1) {
                navStack.removeLast()
                val prev = navStack.last
                showFragment(fragmentClassFor(prev), prev, push = false)
            } else {
                moveTaskToBack(true)
            }
            return
        }
        if (current is SignInFragment) {
            moveTaskToBack(true)
            return
        }
        if (navStack.size > 1) {
            navStack.removeLast()
            val prev = navStack.last
            showFragment(fragmentClassFor(prev), prev, push = false)
        } else {
            moveTaskToBack(true)
        }
    }

    private fun fragmentClassFor(tag: String): Class<out Fragment> = when (tag) {
        "home" -> HomeFragment::class.java
        "topup" -> TopUpFragment::class.java
        "tool" -> ToolFragment::class.java
        "settings" -> SettingsFragment::class.java
        "admin" -> AdminFragment::class.java
        "browser" -> BrowserFragment::class.java
        "signin" -> SignInFragment::class.java
        "signup" -> SignUpFragment::class.java
        else -> HomeFragment::class.java
    }
}