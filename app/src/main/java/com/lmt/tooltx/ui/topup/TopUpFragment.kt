package com.lmt.tooltx.ui.topup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lmt.tooltx.MainActivity
import com.lmt.tooltx.R
import com.lmt.tooltx.bridge.PythonBridge
import com.lmt.tooltx.databinding.FragmentTopupBinding
import com.lmt.tooltx.ui.home.HomeFragment

class TopUpFragment : Fragment() {

    private var _binding: FragmentTopupBinding? = null
    private val binding get() = _binding!!

    private val banks = listOf("Vietcombank", "MB Bank", "Techcombank", "BIDV", "VPBank")
    private val topUpUrl = "https://lmt1102011.github.io/tool-tx/user.html"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            (requireActivity() as MainActivity).showFragment(HomeFragment::class.java, "home", push = false)
        }

        binding.btnOpenWeb.setOnClickListener { openWeb() }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, banks)
        binding.spinnerBank.setAdapter(adapter)
        binding.spinnerBank.setOnItemClickListener { _, _, position, _ ->
            if (position in banks.indices) {
                showBankDialog(banks[position])
            }
        }
    }

    private fun showBankDialog(bank: String) {
        val message = getString(R.string.transfer_desc) + "\n\nNgân hàng: $bank"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.transfer_title)
            .setMessage(message)
            .setPositiveButton(R.string.open_topup_web) { _, _ -> openWeb() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openWeb() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(topUpUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không mở được trang nạp tiền", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}