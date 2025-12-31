package com.t9dialer

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
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
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class T9Activity : Activity() {

    private lateinit var appsContainer: LinearLayout
    private lateinit var allApps: List<AppInfo>
    private var currentQuery = ""
    private var iconPackPackageName: String? = null
    private var iconPackResources: Resources? = null
    private var iconPackMappings: MutableMap<String, String> = mutableMapOf()
    private val debugLog = StringBuilder()

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    data class IconPackInfo(
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

        // Load icon pack preference
        loadIconPackPreference()

        // Load all installed apps
        loadInstalledApps()

        // Set up T9 keyboard buttons
        setupKeyboard()

        // Show initial top 3 apps
        updateAppsList()
    }

    private fun loadIconPackPreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        iconPackPackageName = prefs.getString("icon_pack", null)

        iconPackPackageName?.let { packageName ->
            try {
                iconPackResources = packageManager.getResourcesForApplication(packageName)
                loadIconPackMappings(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Icon pack uninstalled, clear preference
                iconPackPackageName = null
                iconPackResources = null
                iconPackMappings.clear()
                prefs.edit().remove("icon_pack").apply()
            }
        }
    }

    private fun loadIconPackMappings(packageName: String) {
        iconPackMappings.clear()
        debugLog.clear()
        val res = iconPackResources ?: return

        debugLog.append("=== ICON PACK DEBUG ===\n")
        debugLog.append("Package: $packageName\n\n")

        try {
            // Try to find appfilter in xml or raw resources
            var appfilterId = res.getIdentifier("appfilter", "xml", packageName)
            var isRawResource = false

            if (appfilterId == 0) {
                debugLog.append("appfilter not found in xml, trying raw...\n")
                appfilterId = res.getIdentifier("appfilter", "raw", packageName)
                isRawResource = true
            }

            if (appfilterId != 0) {
                debugLog.append("✓ Found appfilter (ID: $appfilterId)\n")
                debugLog.append("Type: ${if (isRawResource) "raw" else "xml"}\n\n")

                val parser: XmlPullParser

                if (isRawResource) {
                    // For raw resources, open as input stream
                    val inputStream = res.openRawResource(appfilterId)
                    val factory = XmlPullParserFactory.newInstance()
                    parser = factory.newPullParser()
                    parser.setInput(inputStream, "UTF-8")
                } else {
                    // For xml resources, use getXml
                    parser = res.getXml(appfilterId)
                }

                var eventType = parser.eventType
                var itemCount = 0
                val sampleMappings = mutableListOf<String>()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")

                        if (component != null && drawable != null) {
                            // Handle multiple component name formats
                            var componentPackage = component

                            if (componentPackage.contains("ComponentInfo{")) {
                                componentPackage = componentPackage
                                    .substringAfter("ComponentInfo{")
                                    .substringBefore("}")
                            }

                            if (componentPackage.contains("/")) {
                                componentPackage = componentPackage.substringBefore("/")
                            }

                            iconPackMappings[componentPackage] = drawable
                            itemCount++

                            if (itemCount <= 5) {
                                sampleMappings.add("$componentPackage -> $drawable")
                            }
                        }
                    }
                    eventType = parser.next()
                }

                debugLog.append("✓ Parsed successfully!\n")
                debugLog.append("Total mappings: ${iconPackMappings.size}\n\n")
                if (sampleMappings.isNotEmpty()) {
                    debugLog.append("Sample mappings:\n")
                    sampleMappings.forEach { debugLog.append("  $it\n") }
                }
            } else {
                debugLog.append("✗ No appfilter found!\n\n")

                // List all available XML resources
                try {
                    val fields = Class.forName("${packageName}.R\$xml").declaredFields
                    if (fields.isNotEmpty()) {
                        debugLog.append("Available XML resources:\n")
                        fields.forEach { debugLog.append("  - ${it.name}\n") }
                    } else {
                        debugLog.append("No XML resources found\n")
                    }
                } catch (e: Exception) {
                    debugLog.append("Could not list XML: ${e.message}\n")
                }
            }
        } catch (e: Exception) {
            debugLog.append("\n✗ ERROR: ${e.message}\n")
            debugLog.append("${e.stackTraceToString()}\n")
        }
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
        val clearButton = findViewById<MaterialButton>(R.id.btn1)
        clearButton.setOnClickListener {
            currentQuery = ""
            updateAppsList()
        }

        // Long-press to open icon pack selector
        clearButton.setOnLongClickListener {
            showIconPackSelector()
            true
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
                val defaultIcon = pm.getApplicationIcon(it)
                val icon = getIconFromPack(it.packageName) ?: defaultIcon

                AppInfo(
                    name = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = icon
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

    private fun getInstalledIconPacks(): List<IconPackInfo> {
        val iconPacks = mutableListOf<IconPackInfo>()

        // Add "Default" option
        iconPacks.add(IconPackInfo(
            "Default (System Icons)",
            "",
            getDrawable(android.R.drawable.sym_def_app_icon)!!
        ))

        val pm = packageManager
        val processedPackages = mutableSetOf<String>()

        // Check for ADW/Nova launcher icon packs
        val adwIntent = Intent("org.adw.launcher.THEMES")
        adwIntent.addCategory("com.anddoes.launcher.THEME")
        val adwPackages = pm.queryIntentActivities(adwIntent, 0)
        for (info in adwPackages) {
            val packageName = info.activityInfo.packageName
            if (processedPackages.add(packageName)) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    iconPacks.add(IconPackInfo(
                        pm.getApplicationLabel(appInfo).toString(),
                        packageName,
                        pm.getApplicationIcon(appInfo)
                    ))
                } catch (e: Exception) {
                    // Skip this icon pack
                }
            }
        }

        // Check for Icon Pack Studio / GO Launcher icon packs
        val goIntent = Intent("com.gau.go.launcherex.theme")
        val goPackages = pm.queryIntentActivities(goIntent, 0)
        for (info in goPackages) {
            val packageName = info.activityInfo.packageName
            if (processedPackages.add(packageName)) {
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    iconPacks.add(IconPackInfo(
                        pm.getApplicationLabel(appInfo).toString(),
                        packageName,
                        pm.getApplicationIcon(appInfo)
                    ))
                } catch (e: Exception) {
                    // Skip this icon pack
                }
            }
        }

        // Check all apps for icon pack metadata (Icon Pack Studio apps)
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in allApps) {
            val packageName = appInfo.packageName
            if (processedPackages.contains(packageName)) continue

            try {
                val res = pm.getResourcesForApplication(packageName)
                // Check if it has appfilter.xml (common in icon packs)
                val appfilterId = res.getIdentifier("appfilter", "xml", packageName)
                if (appfilterId != 0 && processedPackages.add(packageName)) {
                    iconPacks.add(IconPackInfo(
                        pm.getApplicationLabel(appInfo).toString(),
                        packageName,
                        pm.getApplicationIcon(appInfo)
                    ))
                }
            } catch (e: Exception) {
                // Skip this package
            }
        }

        return iconPacks
    }

    private fun showIconPackSelector() {
        val iconPacks = getInstalledIconPacks()
        val packNames = iconPacks.map { it.name }.toTypedArray()

        // Find currently selected icon pack index
        val currentSelection = iconPacks.indexOfFirst {
            it.packageName == (iconPackPackageName ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Select Icon Pack")
            .setSingleChoiceItems(packNames, currentSelection) { dialog, which ->
                val selectedPack = iconPacks[which]

                // Save preference
                val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
                if (selectedPack.packageName.isEmpty()) {
                    // Default selected
                    prefs.edit().remove("icon_pack").apply()
                    iconPackPackageName = null
                    iconPackResources = null
                    iconPackMappings.clear()
                } else {
                    prefs.edit().putString("icon_pack", selectedPack.packageName).apply()
                    iconPackPackageName = selectedPack.packageName
                    try {
                        iconPackResources = packageManager.getResourcesForApplication(selectedPack.packageName)
                        loadIconPackMappings(selectedPack.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        iconPackResources = null
                        iconPackMappings.clear()
                    }
                }

                // Reload apps with new icons
                loadInstalledApps()
                updateAppsList()

                // Show feedback message
                if (selectedPack.packageName.isEmpty()) {
                    Toast.makeText(this, "Using default system icons", Toast.LENGTH_SHORT).show()
                } else {
                    val iconCount = iconPackMappings.size
                    val message = if (iconCount > 0) {
                        "${selectedPack.name}\n$iconCount icons loaded successfully"
                    } else {
                        "${selectedPack.name}\nNo icon mappings found"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getIconFromPack(packageName: String): Drawable? {
        if (iconPackResources == null || iconPackPackageName == null) {
            return null
        }

        try {
            // First, try to use appfilter.xml mapping (Icon Pack Studio format)
            val mappedDrawable = iconPackMappings[packageName]
            if (mappedDrawable != null) {
                val resId = iconPackResources?.getIdentifier(
                    mappedDrawable,
                    "drawable",
                    iconPackPackageName
                ) ?: 0

                if (resId != 0) {
                    return iconPackResources?.getDrawable(resId, null)
                }
            }

            // Fallback: Try common icon pack naming schemes
            val drawableName = packageName.replace(".", "_")

            // Try various naming patterns
            val patterns = listOf(
                drawableName,
                "${drawableName}_icon",
                "ic_${drawableName}",
                packageName.substringAfterLast("."),
                "ic_${packageName.substringAfterLast(".")}"
            )

            for (pattern in patterns) {
                val resId = iconPackResources?.getIdentifier(
                    pattern,
                    "drawable",
                    iconPackPackageName
                ) ?: 0

                if (resId != 0) {
                    return iconPackResources?.getDrawable(resId, null)
                }
            }
        } catch (e: Exception) {
            // Silent fail, return null for fallback to system icon
        }

        return null
    }
}
