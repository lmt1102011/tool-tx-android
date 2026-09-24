package com.lmt.tooltx.ui.auth

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.R
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentSignupBinding
import com.lmt.tooltx.ui.home.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignUpFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSubmit.setOnClickListener { doSignUp() }
        binding.btnSwitch.setOnClickListener {
            (requireActivity() as MainActivity).showFragment(SignInFragment::class.java, "signin")
        }

        listOf(binding.etUsername, binding.etPassword, binding.etConfirm).forEach { field ->
            field.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) bringIntoView(v)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(0, 0, 0, ime)
            v.post {
                val focused = v.findFocus()
                if (focused != null) bringIntoView(focused)
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun bringIntoView(focused: View) {
        focused.post {
            if (focused.height > 0) {
                val rect = Rect(0, 0, focused.width, focused.height)
                focused.requestRectangleOnScreen(rect, true)
            }
        }
    }

    private fun doSignUp() {
        val username = (binding.etUsername.text?.toString() ?: "").trim()
        val password = binding.etPassword.text?.toString() ?: ""
        val confirm = binding.etConfirm.text?.toString() ?: ""

        if (username.length < 3) {
            setStatus("Tên đăng nhập ít nhất 3 ký tự.", true)
            return
        }
        if (password.length < 6) {
            setStatus("Mật khẩu ít nhất 6 ký tự.", true)
            return
        }
        if (password != confirm) {
            setStatus("Mật khẩu nhập lại không khớp.", true)
            return
        }

        binding.btnSubmit.isEnabled = false
        setStatus("Đang tạo tài khoản...", false)

        GlobalScope.launch(Dispatchers.IO) {
            val bridge: PythonBridge = (requireActivity() as MainActivity).getBridge()
            val registered = bridge.register(username, password, username)
            if (registered["ok"] != true) {
                val error = registered["error"]?.toString() ?: "Đăng ký thất bại."
                withContext(Dispatchers.Main) {
                    val b = _binding ?: return@withContext
                    b.btnSubmit.isEnabled = true
                    setStatus(error, true)
                }
                return@launch
            }
            val loggedIn = bridge.login(username, password)
            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.btnSubmit.isEnabled = true
                if (loggedIn["ok"] == true) {
                    (requireActivity() as MainActivity)
                        .showFragment(HomeFragment::class.java, "home")
                } else {
                    setStatus("Đã tạo tài khoản — vui lòng đăng nhập lại.", true)
                }
            }
        }
    }

    private fun setStatus(msg: String, isError: Boolean) {
        binding.tvStatus.text = msg
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isError) R.color.error else R.color.onSurfaceVariant
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}