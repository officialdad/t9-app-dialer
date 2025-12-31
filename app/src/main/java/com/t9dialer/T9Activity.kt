package com.t9dialer

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

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

        // Position dialog at bottom of screen for one-handed use
        window?.setGravity(Gravity.BOTTOM)

        setContentView(R.layout.activity_t9)

        appsContainer = findViewById(R.id.appsContainer)

        // Load all installed apps
        loadInstalledApps()

        // Set up T9 keyboard buttons
        setupKeyboard()

        // Show initial top 3 apps
        updateAppsList()
    }

    private fun setKeyText(button: MaterialButton, number: String, letters: String, isRed: Boolean = false) {
        val text = "$number\n$letters"
        val spannable = SpannableString(text)

        // Number: 20sp size, white (or red for button 1)
        val numberColor = if (isRed) getColor(R.color.key_clear) else getColor(R.color.key_number)
        spannable.setSpan(
            ForegroundColorSpan(numberColor),
            0, number.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(20, true), // 20sp
            0, number.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Alphabet: 14sp size, gray
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.key_alphabet)),
            number.length + 1, text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(14, true), // 14sp
            number.length + 1, text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        button.text = spannable
    }

    private fun setupKeyboard() {
        // Set dual-color text for all buttons
        setKeyText(findViewById(R.id.btn1), "1", "CLEAR", isRed = true)
        setKeyText(findViewById(R.id.btn2), "2", "ABC")
        setKeyText(findViewById(R.id.btn3), "3", "DEF")
        setKeyText(findViewById(R.id.btn4), "4", "GHI")
        setKeyText(findViewById(R.id.btn5), "5", "JKL")
        setKeyText(findViewById(R.id.btn6), "6", "MNO")
        setKeyText(findViewById(R.id.btn7), "7", "PQRS")
        setKeyText(findViewById(R.id.btn8), "8", "TUV")
        setKeyText(findViewById(R.id.btn9), "9", "WXYZ")

        // Number buttons 2-9
        findViewById<MaterialButton>(R.id.btn2).setOnClickListener { addDigit('2') }
        findViewById<MaterialButton>(R.id.btn3).setOnClickListener { addDigit('3') }
        findViewById<MaterialButton>(R.id.btn4).setOnClickListener { addDigit('4') }
        findViewById<MaterialButton>(R.id.btn5).setOnClickListener { addDigit('5') }
        findViewById<MaterialButton>(R.id.btn6).setOnClickListener { addDigit('6') }
        findViewById<MaterialButton>(R.id.btn7).setOnClickListener { addDigit('7') }
        findViewById<MaterialButton>(R.id.btn8).setOnClickListener { addDigit('8') }
        findViewById<MaterialButton>(R.id.btn9).setOnClickListener { addDigit('9') }

        // Button 1: Clear/Reset
        findViewById<MaterialButton>(R.id.btn1).setOnClickListener {
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
                textSize = 14f
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                gravity = Gravity.CENTER
            }
            appsContainer.addView(noMatchView)
        }
    }

    private fun createAppView(app: AppInfo): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))

            val iconSize = dpToPx(75)
            val icon = ImageView(this@T9Activity).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            }

            val label = TextView(this@T9Activity).apply {
                text = app.name
                textSize = 12f
                setTextColor(getColor(R.color.app_text)) // Light gray text for dark theme
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(85),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(6)
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
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
