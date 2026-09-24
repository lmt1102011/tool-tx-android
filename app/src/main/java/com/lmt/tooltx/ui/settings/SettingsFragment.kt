package com.lmt.tooltx.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.chaquo.python.PyObject
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.R
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentSettingsBinding
import com.lmt.tooltx.ui.auth.SignInFragment
import com.lmt.tooltx.ui.home.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            (requireActivity() as MainActivity).showFragment(HomeFragment::class.java, "home", push = false)
        }

        binding.btnLogout.setOnClickListener { confirmLogout() }

        binding.switchDark.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }

        binding.switchAuto.setOnCheckedChangeListener { _, isChecked ->
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("auto_connect", isChecked).apply()
            toggleAutoConnect(isChecked)
        }

        binding.llBuglogHeader.setOnClickListener {
            toggleBugLog()
        }

        binding.btnBuglogSend.setOnClickListener { shareBugLog() }
        binding.btnBuglogClear.setOnClickListener { clearBugLog() }
    }

    override fun onResume() {
        super.onResume()
        loadDarkMode()
        loadAutoConnect()
        loadProfile()
    }

    private fun loadDarkMode() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        binding.switchDark.setOnCheckedChangeListener(null)
        binding.switchDark.isChecked = prefs.getBoolean("dark_mode", true)
        binding.switchDark.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }
    }

    private fun loadAutoConnect() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        binding.switchAuto.setOnCheckedChangeListener(null)
        binding.switchAuto.isChecked = prefs.getBoolean("auto_connect", true)
        binding.switchAuto.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_connect", isChecked).apply()
            toggleAutoConnect(isChecked)
        }
    }

    private fun toggleAutoConnect(enabled: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            if (enabled) {
                val url = bridge.discoverServer()
                if (!url.isNullOrEmpty()) {
                    val token = bridge.getSession()?.get("idToken")?.toString().orEmpty()
                    bridge.connectSocket(url, token)
                }
            } else {
                bridge.disconnectSocket()
            }
        }
    }

    private fun loadProfile() {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val session = bridge.getSession()
            val user = bridge.getUserData()
            val picks = if (user.isEmpty() || user.containsKey("error")) -1 else pickCount(user)
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                val name = session?.get("displayName")?.toString()
                    ?: session?.get("username")?.toString()
                    ?: "Khách"
                val uid = session?.get("uid")?.toString().orEmpty()
                val role = (user["role"] ?: session?.get("role"))?.toString().orEmpty()
                val picksTxt = when {
                    role == "admin" -> "vô hạn"
                    picks >= 0 -> picks.toString()
                    else -> "--"
                }
                if (role.equals("admin", ignoreCase = true)) {
                    (requireActivity() as MainActivity).showAdmin(true)
                } else {
                    (requireActivity() as MainActivity).showAdmin(false)
                }
                profile(name, uid, role, picksTxt)
                picksText(picksTxt)
            }
        }
    }

    private fun profile(name: String, uid: String, role: String, picks: String) {
        binding.tvProfileName.text = name
        val roleTxt = if (role.isNotEmpty()) " . " + role else ""
        binding.tvProfileUid.text = (if (uid.isEmpty()) "Chưa đăng nhập" else uid) + roleTxt
        picksText(picks)
    }

    private fun picksText(txt: String) {
        binding.tvPicks.text = txt
    }

    private fun pickCount(user: Map<String, Any?>): Int {
        val bal = user["balanceFields"]
        if (bal != null) {
            val n = pickNum(bal)
            if (n != null) return maxOf(0, n)
        }
        val sec = user["balanceSeconds"]?.let { pickNum(it) } ?: 0
        return if (sec > 0) maxOf(1, sec / 60) else 0
    }

    private fun pickNum(v: Any?): Int? = when (v) {
        is PyObject -> try { v.toFloat().toInt() } catch (_: Exception) { null }
        is Number -> v.toInt()
        else -> v?.toString()?.trim()?.toFloatOrNull()?.toInt()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton("OK") { _, _ -> doLogout() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun doLogout() {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            bridge.disconnectSocket()
            bridge.logout()
            withContext(Dispatchers.Main) {
                (requireActivity() as MainActivity).showAdmin(false)
                (requireActivity() as MainActivity)
                    .showFragment(SignInFragment::class.java, "signin")
            }
        }
    }

    private var bugLogExpanded = false
    private var bugLogLoaded = false

    private fun toggleBugLog() {
        bugLogExpanded = !bugLogExpanded
        val body = binding.llBuglogBody
        val arrow = binding.ivBuglogArrow
        if (bugLogExpanded) {
            body.visibility = View.VISIBLE
            val anim = RotateAnimation(270f, 90f, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f)
            anim.duration = 200
            anim.fillAfter = true
            arrow.startAnimation(anim)
            if (!bugLogLoaded) {
                bugLogLoaded = true
                loadBugLog()
            }
        } else {
            body.visibility = View.GONE
            val anim = RotateAnimation(90f, 270f, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f)
            anim.duration = 200
            anim.fillAfter = true
            arrow.startAnimation(anim)
        }
    }

    private fun loadBugLog() {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val logText = bridge.getBugLog()
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.tvBuglog.text = if (logText.isEmpty())
                    "Chưa có log lỗi nào trong lần chạy này.\n\nGồm: lỗi kết nối server, lỗi agent (websocket), lỗi socket, lỗi từ chối mã liên kết."
                else logText
            }
        }
    }

    private fun clearBugLog() {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            bridge.clearBugLog()
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.tvBuglog.text = "Đã dọn log."
            }
        }
    }

    private fun shareBugLog() {
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val body = bridge.getBugLog()
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                if (body.isEmpty()) {
                    Toast.makeText(requireContext(), "Không có bug log.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("ToolTX Bug Log", body.takeLast(5000)))
                Toast.makeText(requireContext(), "Đã sao chép bug log.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}