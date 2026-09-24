package com.lmt.tooltx.ui.browser

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentBrowserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartBrowser.setOnClickListener {
            startBrowser(which = "fork")
        }
        binding.btnStartChrome.visibility =
            if (isDesktop()) View.VISIBLE else View.GONE
        binding.btnStartChrome.setOnClickListener {
            startBrowser(which = "chrome")
        }
    }

    private fun startBrowser(which: String) {
        setStatus("Đang khởi động...")

        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val server = bridge.discoverServer() ?: "http://localhost:8787"
            val code = bridge.getAgentPair(server)
            if (code.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    setStatus("Chưa lấy được mã liên kết.")
                    setCode("---")
                }
                return@launch
            }

            val ok = if (which == "chrome") {
                bridge.startAgent(server, code)
            } else {
                bridge.startFork(server, code)
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                setCode(code)
                if (ok) {
                    setStatus("Đang kết nối browser...")
                    setAgent("Agent đang chạy.")
                } else {
                    setStatus("Khởi động thất bại.")
                    setAgent("Chưa có agent nào chạy.")
                }
            }
        }
    }

    private fun isDesktop(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("Emulator") ||
            Build.PRODUCT.contains("sdk")

    private fun setStatus(text: String) {
        binding.tvBrowserStatus.text = text
    }

    private fun setCode(text: String) {
        binding.tvBrowserCode.text = "Mã liên kết: " + text
    }

    private fun setAgent(text: String) {
        binding.tvAgentLine.text = text
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}