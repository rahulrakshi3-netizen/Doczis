package com.doczis.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.doczis.app.DoczisApp
import com.doczis.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: com.doczis.app.util.SettingsManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> updateNotificationSwitch() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = (requireActivity().application as DoczisApp).settingsManager

        binding.darkModeSwitch.isChecked = settingsManager.isDarkMode
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isDarkMode = isChecked
            requireActivity().recreate()
            requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        updateNotificationSwitch()
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
            }
            settingsManager.isNotificationsEnabled = isChecked
        }

        updateStorageBar()
        binding.clearCacheButton.setOnClickListener {
            clearCache()
        }

        binding.licenseButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("App License")
                .setMessage("DOCZIS PDF Utility\n\nLicensed under the MIT License.\n\nCopyright 2026 DOCZIS\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files...")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun updateStorageBar() {
        val cacheSize = calculateCacheSize()
        val maxCache = 100L * 1024 * 1024
        val progress = ((cacheSize * 100) / maxCache).toInt().coerceIn(0, 100)
        binding.storageBar.progress = progress
        binding.storageBar.contentDescription = "Cache: ${formatSize(cacheSize)} used"
    }

    private fun calculateCacheSize(): Long {
        var size = 0L
        requireContext().cacheDir.listFiles()?.forEach { size += it.length() }
        requireContext().externalCacheDir?.listFiles()?.forEach { size += it.length() }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    private fun clearCache() {
        val before = calculateCacheSize()
        var deleted = 0
        requireContext().cacheDir.listFiles()?.forEach { file ->
            if (file.delete()) deleted++
        }
        requireContext().externalCacheDir?.listFiles()?.forEach { file ->
            if (file.delete()) deleted++
        }
        updateStorageBar()
        Toast.makeText(requireContext(), "Freed ${formatSize(before)} ($deleted files)", Toast.LENGTH_SHORT).show()
    }

    private fun updateNotificationSwitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            binding.notificationSwitch.isChecked = granted && settingsManager.isNotificationsEnabled
            binding.notificationSwitch.isEnabled = granted
        } else {
            binding.notificationSwitch.isChecked = settingsManager.isNotificationsEnabled
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
