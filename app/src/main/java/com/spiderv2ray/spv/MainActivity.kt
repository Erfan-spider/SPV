package com.spiderv2ray.spv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.spiderv2ray.spv.ui.ConfigsFragment
import com.spiderv2ray.spv.ui.HomeFragment
import com.spiderv2ray.spv.ui.MoreFragment

class MainActivity : AppCompatActivity() {

    private val tabTags = arrayOf("home", "configs", "more")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Theme (dark/light) is applied in SpvApplication.onCreate.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBars()

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, HomeFragment(), "home")
                .commit()
        }

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showTab("home")
                R.id.nav_configs -> showTab("configs")
                R.id.nav_more -> showTab("more")
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    // Colors the status bar / navigation bar to match the current theme so they
    // never stay white while the app is dark.
    private fun applySystemBars() {
        val dark = SpvApplication.isDarkModeEnabled(this)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg_root)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_card)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    // Tabs are kept alive (hide/show) instead of being rebuilt on every tap,
    // which is what made switching tabs feel slow.
    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        for (t in tabTags) {
            if (t != tag) fm.findFragmentByTag(t)?.let { tx.hide(it) }
        }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            val created: Fragment = when (tag) {
                "home" -> HomeFragment()
                "configs" -> ConfigsFragment()
                else -> MoreFragment()
            }
            tx.add(R.id.fragmentContainer, created, tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }
}
