package com.doczis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.doczis.app.databinding.ActivityMainBinding
import com.doczis.app.ui.home.HomeViewModel
import com.doczis.app.util.UpdateChecker

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var searchJob: Job? = null

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.pageChip.text = destination.label
            val isHome = destination.id == R.id.homeFragment
            val isTool = destination.id == R.id.toolsFragment
            binding.searchButton.isVisible = isHome

            if (!isHome && !isTool) {
                showNormalHeader()
            }
        }

        binding.settingsButton.setOnClickListener {
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false)
                .build()
            navController.navigate(R.id.settingsFragment, null, options)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navController.popBackStack(R.id.homeFragment, false)
            if (item.itemId != R.id.homeFragment) {
                navController.navigate(item.itemId)
            }
            true
        }

        binding.searchButton.setOnClickListener {
            showSearchHeader()
        }

        binding.searchBackButton.setOnClickListener {
            showNormalHeader()
            getHomeViewModel()?.search("")
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    getHomeViewModel()?.search(s?.toString() ?: "")
                }
            }
        })

        requestNotificationPermission()
        UpdateChecker.check(this, this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showNormalHeader() {
        binding.normalContent.isVisible = true
        binding.searchContent.isVisible = false
        binding.searchEditText.setText("")
        binding.searchEditText.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun showSearchHeader() {
        binding.normalContent.isVisible = false
        binding.searchContent.isVisible = true
        binding.searchEditText.post {
            binding.searchEditText.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun getHomeViewModel(): HomeViewModel? {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return null
        val navController = navHostFragment.navController
        val backStackEntry = navController.getBackStackEntry(R.id.homeFragment)
        return ViewModelProvider(backStackEntry)[HomeViewModel::class.java]
    }
}
