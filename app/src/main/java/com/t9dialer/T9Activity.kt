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
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.content.res.ColorStateList
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.net.Uri
import android.content.ActivityNotFoundException

class T9Activity : Activity() {

    private lateinit var appsContainer: LinearLayout
    private lateinit var mainContainer: LinearLayout
    private var allApps: List<AppInfo> = emptyList()
    private var appsLoaded = false
    private var currentQuery = ""
    private var isLightTheme = false
    private var iconPackPackageName: String? = null
    private var iconPackResources: Resources? = null
    private var iconPackMappings: MutableMap<String, String> = mutableMapOf()
    private val debugLog = StringBuilder()

    // Performance optimizations
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val viewPool = mutableListOf<LinearLayout>()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cachedIconPacks: List<IconPackInfo>? = null

    data class AppInfo(
        val name: String,
        val packageName: String,
        val t9Sequence: String,  // Pre-computed T9 sequence for fast matching
        var icon: Drawable? = null  // Lazy-loaded icon
    )

    data class IconPackInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    data class MatchInfo(
        val app: AppInfo,
        val matchPosition: Int,      // Position where match was found
        val matchPriority: Int        // 0 = beginning, 1 = word start, 2 = substring
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Position dialog at bottom of screen for one-handed use
        window?.setGravity(Gravity.BOTTOM)

        setContentView(R.layout.activity_t9)

        appsContainer = findViewById(R.id.appsContainer)
        mainContainer = findViewById(R.id.mainContainer)

        // Load theme preference
        loadThemePreference()

        // Load icon pack preference
        loadIconPackPreference()

        // Set up T9 keyboard buttons
        setupKeyboard()

        // Apply theme
        applyTheme()

        // Preload icon pack list in background for faster access
        mainScope.launch(Dispatchers.IO) {
            getInstalledIconPacks()
        }

        // Apps will be loaded on first key press for faster startup
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()  // Clean up coroutines
    }

    // Convert string to T9 digit sequence for fast matching
    private fun stringToT9(text: String): String {
        val t9Map = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
        )
        return text.lowercase().map { t9Map[it] ?: ' ' }.joinToString("")
    }

    private fun loadThemePreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        isLightTheme = prefs.getBoolean("light_theme", false)
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

    private fun applyTheme() {
        val bgColor = if (isLightTheme) {
            getColor(R.color.light_background)
        } else {
            getColor(R.color.dark_background)
        }

        mainContainer.setBackgroundColor(bgColor)

        // Update card stroke color
        val cardView = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
        val borderColor = if (isLightTheme) {
            getColor(R.color.light_border)
        } else {
            getColor(R.color.dark_border)
        }
        cardView?.strokeColor = borderColor
        cardView?.setCardBackgroundColor(bgColor)

        // Update button text colors
        updateButtonColors()
    }

    private fun toggleTheme() {
        isLightTheme = !isLightTheme

        // Save preference
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("light_theme", isLightTheme).apply()

        // Apply theme
        applyTheme()

        // Refresh app list to update text colors
        updateAppsList()

        // Show toast
        val message = if (isLightTheme) "Light theme" else "Dark theme"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonColors() {
        val keyNumberColor = if (isLightTheme) {
            getColor(R.color.light_key_number)
        } else {
            getColor(R.color.dark_key_number)
        }

        val keyAlphabetColor = if (isLightTheme) {
            getColor(R.color.light_key_alphabet)
        } else {
            getColor(R.color.dark_key_alphabet)
        }

        val rippleColor = if (isLightTheme) {
            getColor(R.color.light_ripple)
        } else {
            getColor(R.color.dark_ripple)
        }

        val rippleColorRed = if (isLightTheme) {
            getColor(R.color.light_ripple_red)
        } else {
            getColor(R.color.dark_ripple_red)
        }

        // Update all buttons
        for (btnId in listOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                             R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)) {
            val button = findViewById<MaterialButton>(btnId)
            val isBtn1 = btnId == R.id.btn1
            updateButtonText(button, isBtn1, keyNumberColor, keyAlphabetColor)

            // Create ripple drawable for foreground
            val color = if (isBtn1) rippleColorRed else rippleColor
            val ripple = createRippleDrawable(color)
            button.foreground = ripple
        }
    }

    private fun createRippleDrawable(color: Int): StateListDrawable {
        // Create state list drawable for instant press feedback
        val stateList = StateListDrawable()

        // Pressed state - show highlight instantly
        val cornerRadius = dpToPx(8).toFloat()
        val radii = floatArrayOf(
            cornerRadius, cornerRadius,  // top-left
            cornerRadius, cornerRadius,  // top-right
            cornerRadius, cornerRadius,  // bottom-right
            cornerRadius, cornerRadius   // bottom-left
        )
        val pressedShape = RoundRectShape(radii, null, null)
        val pressedDrawable = ShapeDrawable(pressedShape).apply {
            paint.color = color
        }

        // Add pressed state
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)

        // Default state - transparent (no highlight)
        val defaultShape = RoundRectShape(radii, null, null)
        val defaultDrawable = ShapeDrawable(defaultShape).apply {
            paint.color = android.graphics.Color.TRANSPARENT
        }
        stateList.addState(intArrayOf(), defaultDrawable)

        // No exit fade - instant appearance and disappearance
        stateList.setEnterFadeDuration(0)
        stateList.setExitFadeDuration(0)

        return stateList
    }

    private fun updateButtonText(button: MaterialButton, isBtn1: Boolean,
                                  numberColor: Int, alphabetColor: Int) {
        val text = button.text.toString()
        val parts = text.split("\n")
        if (parts.size == 2) {
            val spannable = SpannableString(text)
            val number = parts[0]

            // Number color (red for button 1, otherwise theme color)
            val numberColorToUse = if (isBtn1) getColor(R.color.key_clear) else numberColor
            spannable.setSpan(
                ForegroundColorSpan(numberColorToUse),
                0, number.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                AbsoluteSizeSpan(20, true),
                0, number.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Alphabet color
            spannable.setSpan(
                ForegroundColorSpan(alphabetColor),
                number.length + 1, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                AbsoluteSizeSpan(14, true),
                number.length + 1, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            button.text = spannable
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

        // Button 1: Add settings icon
        val clearButton = findViewById<MaterialButton>(R.id.btn1)
        val settingsIcon = getDrawable(R.drawable.ic_settings)
        settingsIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        clearButton.setCompoundDrawables(null, null, settingsIcon, null)
        clearButton.compoundDrawablePadding = dpToPx(4)

        // Button 2: Add theme toggle icon
        val btn2 = findViewById<MaterialButton>(R.id.btn2)
        val themeIcon = getDrawable(R.drawable.ic_theme)
        themeIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn2.setCompoundDrawables(null, null, themeIcon, null)
        btn2.compoundDrawablePadding = dpToPx(4)

        // Number buttons 2-9
        btn2.setOnClickListener { addDigit('2') }
        findViewById<MaterialButton>(R.id.btn3).setOnClickListener { addDigit('3') }
        findViewById<MaterialButton>(R.id.btn4).setOnClickListener { addDigit('4') }
        findViewById<MaterialButton>(R.id.btn5).setOnClickListener { addDigit('5') }
        findViewById<MaterialButton>(R.id.btn6).setOnClickListener { addDigit('6') }
        findViewById<MaterialButton>(R.id.btn7).setOnClickListener { addDigit('7') }
        findViewById<MaterialButton>(R.id.btn8).setOnClickListener { addDigit('8') }
        findViewById<MaterialButton>(R.id.btn9).setOnClickListener { addDigit('9') }

        // Button 1: Clear/Reset
        clearButton.setOnClickListener {
            currentQuery = ""
            updateAppsList()
        }

        // Long-press button 1 to open icon pack selector
        clearButton.setOnLongClickListener {
            showIconPackSelector()
            true
        }

        // Long-press button 2 to toggle theme
        btn2.setOnLongClickListener {
            toggleTheme()
            true
        }
    }

    private fun addDigit(digit: Char) {
        // Load apps on first key press
        if (!appsLoaded) {
            loadInstalledApps()
        }

        currentQuery += digit
        updateAppsList()
    }

    private fun loadInstalledApps() {
        // Load apps in background for better performance
        mainScope.launch(Dispatchers.IO) {
            val pm = packageManager

            // Query only apps with LAUNCHER intent (same as default launcher)
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val launcherActivities = pm.queryIntentActivities(mainIntent, 0)

            val apps = launcherActivities
                .map { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    val appName = resolveInfo.loadLabel(pm).toString()

                    AppInfo(
                        name = appName,
                        packageName = packageName,
                        t9Sequence = stringToT9(appName),  // Pre-compute T9 sequence
                        icon = null  // Icons loaded lazily when needed
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name }

            withContext(Dispatchers.Main) {
                allApps = apps
                appsLoaded = true  // Mark as loaded after apps are ready
                updateAppsList()  // Refresh UI if search is active
            }
        }
    }

    private fun updateAppsList() {
        // Only show apps when there's a search query
        if (currentQuery.isEmpty()) {
            // Store old views for recycling
            for (i in 0 until appsContainer.childCount) {
                val child = appsContainer.getChildAt(i)
                if (child is LinearLayout && child.tag is MatchInfo) {
                    viewPool.add(child)
                }
            }
            appsContainer.removeAllViews()
            return
        }

        // Optimized search with early termination
        val matchedApps = mutableListOf<MatchInfo>()
        var foundPriorityZero = 0

        // Early termination: stop after finding 3 priority-0 (beginning) matches
        for (app in allApps) {
            // Stop if we have 3 apps starting with the query (priority 0)
            if (foundPriorityZero >= 3) break

            val matchInfo = getMatchInfo(app, currentQuery)
            if (matchInfo != null) {
                matchedApps.add(matchInfo)
                if (matchInfo.matchPriority == 0) {
                    foundPriorityZero++
                }
            }
        }

        // Sort matched apps
        val sortedApps = matchedApps
            .sortedWith(compareBy<MatchInfo> { it.matchPriority }
                .thenBy { it.app.name.length }
                .thenBy { it.app.name })
            .take(3)

        // Load icons first, then prepare views
        for (matchInfo in sortedApps) {
            // Ensure icon is loaded before displaying view
            if (matchInfo.app.icon == null) {
                val cacheKey = "${iconPackPackageName ?: "default"}_${matchInfo.app.packageName}"
                val cachedIcon = iconCache[cacheKey]
                if (cachedIcon != null) {
                    matchInfo.app.icon = cachedIcon
                } else {
                    // Load icon synchronously for smooth appearance
                    try {
                        val icon = getIconFromPack(matchInfo.app.packageName)
                            ?: packageManager.getApplicationIcon(matchInfo.app.packageName)
                        iconCache[cacheKey] = icon
                        matchInfo.app.icon = icon
                    } catch (e: Exception) {
                        // Use default if loading fails
                        matchInfo.app.icon = getDrawable(android.R.drawable.sym_def_app_icon)
                    }
                }
            }
        }

        // Now update container after all icons are loaded
        // Store old views for recycling
        for (i in 0 until appsContainer.childCount) {
            val child = appsContainer.getChildAt(i)
            if (child is LinearLayout && child.tag is MatchInfo) {
                viewPool.add(child)
            }
        }
        appsContainer.removeAllViews()

        // Add views with icons ready
        for (matchInfo in sortedApps) {
            val appView = getOrCreateAppView(matchInfo)
            appsContainer.addView(appView)
        }

        // Show message if no matches (but only if apps have finished loading)
        if (sortedApps.isEmpty() && currentQuery.isNotEmpty() && appsLoaded) {
            val textColor = if (isLightTheme) {
                getColor(R.color.light_app_text)
            } else {
                getColor(R.color.dark_app_text)
            }

            val noMatchView = TextView(this).apply {
                text = "No matches"
                textSize = 14f
                setTextColor(textColor)
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                gravity = Gravity.CENTER
            }
            appsContainer.addView(noMatchView)
        }
    }

    // Lazy load icon with caching
    private fun loadIconForApp(app: AppInfo, imageView: ImageView? = null) {
        if (app.icon != null) return  // Already loaded

        // Check cache first
        val cacheKey = "${iconPackPackageName ?: "default"}_${app.packageName}"
        val cachedIcon = iconCache[cacheKey]
        if (cachedIcon != null) {
            app.icon = cachedIcon
            imageView?.setImageDrawable(cachedIcon)
            return
        }

        // Load icon in background
        mainScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val icon = try {
                val iconPackIcon = getIconFromPack(app.packageName)
                iconPackIcon ?: pm.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                pm.getApplicationIcon(app.packageName)
            }

            iconCache[cacheKey] = icon
            app.icon = icon

            // Update the ImageView on main thread if provided
            withContext(Dispatchers.Main) {
                imageView?.setImageDrawable(icon)
            }
        }
    }

    // Get or create app view (with view recycling)
    private fun getOrCreateAppView(matchInfo: MatchInfo): LinearLayout {
        val view = if (viewPool.isNotEmpty()) {
            viewPool.removeAt(viewPool.size - 1)
        } else {
            createAppView()
        }

        updateAppView(view, matchInfo)
        return view
    }

    private fun createAppView(): LinearLayout {
        val iconSize = dpToPx(90)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))

            // Create icon view
            val icon = ImageView(this@T9Activity).apply {
                id = android.R.id.icon
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            }

            // Create label view
            val label = TextView(this@T9Activity).apply {
                id = android.R.id.text1
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(102),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(6)
                }
            }

            addView(icon)
            addView(label)

            // Make it look clickable
            isClickable = true
            isFocusable = true
        }
    }

    private fun updateAppView(view: LinearLayout, matchInfo: MatchInfo) {
        view.tag = matchInfo

        val icon = view.findViewById<ImageView>(android.R.id.icon)
        val label = view.findViewById<TextView>(android.R.id.text1)

        // Set icon (should always be loaded at this point)
        icon.setImageDrawable(matchInfo.app.icon)
        icon.visibility = android.view.View.VISIBLE

        // Highlight the matched portion of the app name
        val spannable = SpannableString(matchInfo.app.name)
        val matchStart = matchInfo.matchPosition
        val matchEnd = matchStart + currentQuery.length

        // Get theme-aware colors
        val highlightColor = if (isLightTheme) {
            getColor(R.color.light_app_text_highlight)
        } else {
            getColor(R.color.dark_app_text_highlight)
        }

        val normalColor = if (isLightTheme) {
            getColor(R.color.light_app_text)
        } else {
            getColor(R.color.dark_app_text)
        }

        // Make matched text highlighted and bold
        spannable.setSpan(
            ForegroundColorSpan(highlightColor),
            matchStart,
            matchEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            matchStart,
            matchEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        label.text = spannable
        label.setTextColor(normalColor)

        // Apply theme-aware ripple effect (same as keyboard buttons)
        val rippleColor = if (isLightTheme) {
            getColor(R.color.light_ripple)
        } else {
            getColor(R.color.dark_ripple)
        }
        view.foreground = createRippleDrawable(rippleColor)

        // Launch app on click
        view.setOnClickListener {
            launchApp(matchInfo.app.packageName)
        }

        // Show context menu on long-press
        view.setOnLongClickListener {
            showAppContextMenu(matchInfo.app)
            true
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getMatchInfo(app: AppInfo, query: String): MatchInfo? {
        if (query.isEmpty()) return null

        val t9Seq = app.t9Sequence  // Use pre-computed sequence

        // 1. Try matching from the beginning (highest priority)
        if (t9Seq.startsWith(query)) {
            return MatchInfo(app, 0, 0)
        }

        val cleanName = app.name.lowercase()

        // 2. Try matching from the start of each word (medium priority)
        var i = 0
        while (i < cleanName.length) {
            if (i > 0 && (cleanName[i - 1] == ' ' || !cleanName[i - 1].isLetter())) {
                // Check if T9 sequence matches at this position
                if (i + query.length <= t9Seq.length) {
                    val segment = t9Seq.substring(i, i + query.length)
                    if (segment == query) {
                        return MatchInfo(app, i, 1)
                    }
                }
            }
            i++
        }

        // 3. Try matching anywhere in the name (lowest priority)
        val matchIndex = t9Seq.indexOf(query)
        if (matchIndex >= 0) {
            return MatchInfo(app, matchIndex, 2)
        }

        return null
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            finish()
        }
    }

    private fun getInstalledIconPacks(): List<IconPackInfo> {
        // Return cached list if available
        cachedIconPacks?.let { return it }

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

        // Cache the result for future use
        cachedIconPacks = iconPacks
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

                // Clear icon cache and reload
                iconCache.clear()
                for (app in allApps) {
                    app.icon = null  // Clear loaded icons
                }
                updateAppsList()

                // Show feedback message
                val message = if (selectedPack.packageName.isEmpty()) {
                    "Using default icons"
                } else {
                    val iconCount = iconPackMappings.size
                    if (iconCount > 0) {
                        "Icon pack applied"
                    } else {
                        "No icons found in pack"
                    }
                }

                // Create toast without icon
                val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
                val toastView = toast.view
                toastView?.findViewById<ImageView>(android.R.id.icon)?.visibility = android.view.View.GONE
                toast.show()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAppContextMenu(app: AppInfo) {
        val options = arrayOf("App Info", "Open in Play Store")

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        // App Info - Open system settings
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    }
                    1 -> {
                        // Open in Play Store
                        try {
                            // Try to open in Play Store app
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Fallback to Play Store web page if app not installed
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                            startActivity(intent)
                        }
                    }
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
