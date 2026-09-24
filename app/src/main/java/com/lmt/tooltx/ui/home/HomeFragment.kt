package com.lmt.tooltx.ui.home

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chaquo.python.PyObject
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.R
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentHomeBinding
import com.lmt.tooltx.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var lastRefresh = 0L
    private var consecutiveFail = 0
    private val serverFailThreshold = 3

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnProfile.setOnClickListener {
            (requireActivity() as MainActivity).showFragment(SettingsFragment::class.java, "settings")
        }

        binding.swipeRefresh.setOnRefreshListener { refresh(force = true) }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastRefresh != 0L && now - lastRefresh < 6000) return
        val first = lastRefresh == 0L
        lastRefresh = now
        if (first) {
            try {
                binding.skeleton.visibility = View.VISIBLE
                binding.skeleton.startShimmer()
            } catch (_: Exception) {}
        }
        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val session = bridge.getSession()
            val user = bridge.getUserData()
            val picks = if (user.isEmpty() || user.containsKey("error")) -1 else pickCount(user)
            val connected = bridge.isSocketConnected()
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.swipeRefresh.isRefreshing = false
                b.skeleton.stopShimmer()
                b.skeleton.visibility = View.GONE
                val name = session?.get("displayName")?.toString()
                    ?: session?.get("username")?.toString()
                    ?: "Name"
                greet(name)

                val role = (user["role"] ?: session?.get("role"))?.toString().orEmpty()
                if (role.equals("admin", ignoreCase = true)) {
                    (requireActivity() as MainActivity).showAdmin(true)
                    credit("vô hạn", warn = false)
                    gate(null)
                } else {
                    (requireActivity() as MainActivity).showAdmin(false)
                    if (picks >= 0) {
                        val warn = picks <= 0
                        credit(picks.toString(), warn)
                        if (warn) {
                            gate("BẠN ĐÃ HẾT LƯỢT SỬ DỤNG TOOL — nạp thêm tại trang web để tiếp tục.")
                        } else {
                            gate(null)
                        }
                    } else {
                        credit("--", warn = false)
                        gate(null)
                    }
                }

                if (connected) {
                    consecutiveFail = 0
                    server("Máy chủ đang hoạt động", ok = true)
                } else {
                    consecutiveFail++
                    if (consecutiveFail >= 3) {
                        server("Máy chủ đang tắt", ok = false)
                    } else {
                        server("Đã kết nối server — chờ dữ liệu phiên…", ok = false)
                    }
                }
            }
        }
    }

    private fun greet(name: String) {
        binding.tvName.text = name
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

    private fun credit(txt: String, warn: Boolean) {
        binding.tvCredit.text = "Tín dụng: $txt"
        binding.tvCredit.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (warn) R.color.error else R.color.onSecondaryContainer
            )
        )
    }

    private fun server(text: String, ok: Boolean) {
        binding.tvServer.text = text
        binding.tvServer.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (ok) R.color.primary else R.color.onSurfaceVariant
            )
        )
    }

    private fun gate(msg: String?) {
        if (msg.isNullOrEmpty()) {
            binding.tvGate.visibility = View.GONE
        } else {
            binding.tvGate.text = msg
            binding.tvGate.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}