package com.t9dialer

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class T9Activity : Activity() {

    private lateinit var appsContainer: LinearLayout
    private lateinit var allApps: List<AppInfo>
    private var currentQuery = ""

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_t9)

        appsContainer = findViewById(R.id.appsContainer)

        // Load all installed apps
        loadInstalledApps()

        // Set up T9 keyboard buttons
        setupKeyboard()

        // Show initial top 3 apps
        updateAppsList()
    }

    private fun setupKeyboard() {
        // Number buttons 2-9
        findViewById<Button>(R.id.btn2).setOnClickListener { addDigit('2') }
        findViewById<Button>(R.id.btn3).setOnClickListener { addDigit('3') }
        findViewById<Button>(R.id.btn4).setOnClickListener { addDigit('4') }
        findViewById<Button>(R.id.btn5).setOnClickListener { addDigit('5') }
        findViewById<Button>(R.id.btn6).setOnClickListener { addDigit('6') }
        findViewById<Button>(R.id.btn7).setOnClickListener { addDigit('7') }
        findViewById<Button>(R.id.btn8).setOnClickListener { addDigit('8') }
        findViewById<Button>(R.id.btn9).setOnClickListener { addDigit('9') }

        // Button 1: Clear/Reset
        findViewById<Button>(R.id.btn1).setOnClickListener {
            currentQuery = ""
            updateAppsList()
        }
    }

    private fun addDigit(digit: Char) {
        currentQuery += digit
        updateAppsList()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)

        allApps = packages
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                     pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                AppInfo(
                    name = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it)
                )
            }
            .sortedBy { it.name }
    }

    private fun updateAppsList() {
        appsContainer.removeAllViews()

        // Filter apps based on T9 query
        val filteredApps = if (currentQuery.isEmpty()) {
            allApps.take(3)
        } else {
            allApps
                .filter { matchesT9(it.name, currentQuery) }
                .take(3)
        }

        // Add top 3 apps to horizontal container
        for (app in filteredApps) {
            val appView = createAppView(app)
            appsContainer.addView(appView)
        }

        // Show message if no matches
        if (filteredApps.isEmpty() && currentQuery.isNotEmpty()) {
            val noMatchView = TextView(this).apply {
                text = "No matches"
                textSize = 18f
                setPadding(40, 40, 40, 40)
                gravity = Gravity.CENTER
            }
            appsContainer.addView(noMatchView)
        }
    }

    private fun createAppView(app: AppInfo): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)

            val iconSize = 150
            val icon = ImageView(this@T9Activity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            }

            val label = TextView(this@T9Activity).apply {
                text = app.name
                textSize = 16f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    iconSize + 20,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12
                }
            }

            addView(icon)
            addView(label)

            // Launch app on click
            setOnClickListener {
                launchApp(app.packageName)
            }

            // Make it look clickable
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
    }

    private fun matchesT9(appName: String, query: String): Boolean {
        val t9Map = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi",
            '5' to "jkl", '6' to "mno", '7' to "pqrs",
            '8' to "tuv", '9' to "wxyz"
        )

        if (query.isEmpty()) return true

        val cleanName = appName.lowercase()

        // Check if query is longer than app name
        if (query.length > cleanName.length) {
            return false
        }

        // Match each digit position with corresponding character position
        for (i in query.indices) {
            val digit = query[i]
            val letters = t9Map[digit] ?: continue
            val char = cleanName[i]

            // The character at position i must be one of the letters for this digit
            if (!letters.contains(char)) {
                return false
            }
        }

        return true
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            finish()
        }
    }
}
