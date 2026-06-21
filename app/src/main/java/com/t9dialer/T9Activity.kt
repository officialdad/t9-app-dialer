package com.t9dialer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.view.LayoutInflater
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import android.view.MotionEvent
import android.graphics.Rect

class T9Activity : Activity() {

    private lateinit var appsContainer: LinearLayout
    private lateinit var mainContainer: LinearLayout
    private var allApps: List<AppInfo> = emptyList()
    private var appsLoaded = false
    private var currentQuery = ""
    private var isLightTheme = false
    private var iconPackPackageName: String? = null
    private var iconPackResources: Resources? = null
    private val iconPackMappings: MutableMap<String, String> = ConcurrentHashMap()

    // Performance optimizations
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val viewPool = mutableListOf<LinearLayout>()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cachedIconPacks: List<IconPackInfo>? = null

    // Debounce job for search
    private var searchDebounceJob: Job? = null
    // Loading state for first key press
    private var appsLoading = false

    // Move mode state for repositioning the dialog
    private var isMoveModeActive = false
    private var pendingMoveMode = false  // Wait for finger release before activating
    private var dialogX = 0
    private var dialogY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialDialogX = 0
    private var initialDialogY = 0
    private var hasCustomPosition = false
    private var savedStrokeColor = 0
    private var savedStrokeWidth = 0

    // Container size state
    private var containerWidth = DEFAULT_CONTAINER_WIDTH
    private var hasCustomSize = false

    companion object {
        private const val DEFAULT_CONTAINER_WIDTH = 320
        private const val MIN_CONTAINER_WIDTH = 240
        private const val MAX_CONTAINER_WIDTH = 400
        private const val CONTAINER_WIDTH_STEP = 20
        private const val DEFAULT_NUMBER_SIZE_SP = 20
        private const val DEFAULT_ALPHABET_SIZE_SP = 14
        private const val DEFAULT_APPS_CONTAINER_HEIGHT = 120
        private const val DEFAULT_BUTTON_HEIGHT = 76
        private const val DEFAULT_CONTAINER_PADDING = 6
        private const val DEFAULT_APPS_MARGIN_TOP = 19
        private const val DEFAULT_APPS_MARGIN_BOTTOM = 10
        private const val DEFAULT_BUTTON_MARGIN = 2
        private const val DEFAULT_APP_WIDTH = 102
        private const val DEFAULT_APP_ICON_SIZE = 72
        private const val DEFAULT_APP_LABEL_SIZE_SP = 12
        private const val VIEW_POOL_MAX_SIZE = 10
        private const val SEARCH_DEBOUNCE_MS = 50L
        private const val MAX_RECENT_APPS = 3
        private const val PREF_RECENT_APPS = "recent_apps"
        private const val PREF_CACHED_APPS = "cached_apps"

        // Pre-computed T9 character map — allocated once, used for all lookups
        private val T9_MAP = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
        )
    }

    // Get scale factor based on container width
    private fun getScaleFactor(): Float {
        return containerWidth.toFloat() / DEFAULT_CONTAINER_WIDTH.toFloat()
    }

    // Get scaled number font size in sp
    private fun getScaledNumberSize(): Int {
        return (DEFAULT_NUMBER_SIZE_SP * getScaleFactor()).toInt()
    }

    // Get scaled alphabet font size in sp
    private fun getScaledAlphabetSize(): Int {
        return (DEFAULT_ALPHABET_SIZE_SP * getScaleFactor()).toInt()
    }

    // Get scaled app icon size in dp
    private fun getScaledAppIconSize(): Int {
        return (DEFAULT_APP_ICON_SIZE * getScaleFactor()).toInt()
    }

    // Get scaled app label size in sp
    private fun getScaledAppLabelSize(): Float {
        return DEFAULT_APP_LABEL_SIZE_SP * getScaleFactor()
    }

    // Get scaled app width in dp
    private fun getScaledAppWidth(): Int {
        return (DEFAULT_APP_WIDTH * getScaleFactor()).toInt()
    }

    // Helper to get orientation suffix for preference keys
    private fun getOrientationSuffix(): String {
        return if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            "_landscape"
        } else {
            "_portrait"
        }
    }

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

        // Load position preference first
        loadPositionPreference()

        // Load size preference
        loadSizePreference()

        // Apply custom position or default gravity
        if (hasCustomPosition) {
            applyDialogPosition()
        } else {
            // Position dialog at bottom of screen for one-handed use
            // In landscape, position at bottom-right corner
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            window?.setGravity(if (isLandscape) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM)
        }

        setContentView(R.layout.activity_t9)

        // Apply container size after view is created
        applyContainerSize()

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

        // Load apps eagerly so recent apps can be shown on startup
        loadInstalledApps()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Handle pending move mode - activate on finger release
        if (pendingMoveMode && ev.action == MotionEvent.ACTION_UP) {
            pendingMoveMode = false
            enterMoveMode()
            return true
        }

        // Handle move mode - free dragging, long-press button 3 to save
        if (isMoveModeActive) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    initialDialogX = dialogX
                    initialDialogY = dialogY
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = initialDialogX + (ev.rawX - initialTouchX).toInt()
                    val newY = initialDialogY + (ev.rawY - initialTouchY).toInt()

                    // Get screen dimensions and dialog size for bounds clamping
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
                    val cardWidth = mainCard.width
                    val cardHeight = mainCard.height

                    // Clamp to keep at least 25% of dialog visible on screen
                    val minVisible = 0.25
                    val minX = -(cardWidth * (1 - minVisible)).toInt()
                    val maxX = (screenWidth - cardWidth * minVisible).toInt()
                    val minY = 0  // Don't go above status bar
                    val maxY = (screenHeight - cardHeight * minVisible).toInt()

                    dialogX = newX.coerceIn(minX, maxX)
                    dialogY = newY.coerceIn(minY, maxY)
                    applyDialogPosition()
                }
                // ACTION_UP: Stay in move mode, user saves by long-pressing button 3
            }
            // Let touch events pass through to buttons so long-press works
            return super.dispatchTouchEvent(ev)
        }

        // Check if touch is outside the main card
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
            val outRect = IntArray(2)
            mainCard.getLocationOnScreen(outRect)

            val cardLeft = outRect[0]
            val cardTop = outRect[1]
            val cardRight = cardLeft + mainCard.width
            val cardBottom = cardTop + mainCard.height

            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()

            // If touch is outside the card bounds, finish the activity
            if (x < cardLeft || x > cardRight || y < cardTop || y > cardBottom) {
                finish()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        searchDebounceJob?.cancel()
        mainScope.cancel()  // Clean up coroutines
    }

    // Convert string to T9 digit sequence for fast matching
    private fun stringToT9(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.lowercase()) {
            sb.append(T9_MAP[c] ?: ' ')
        }
        return sb.toString()
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
                // Parse icon pack XML in background to avoid blocking startup
                mainScope.launch(Dispatchers.IO) {
                    loadIconPackMappings(packageName)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Icon pack uninstalled, clear preference
                iconPackPackageName = null
                iconPackResources = null
                iconPackMappings.clear()
                prefs.edit().remove("icon_pack").apply()
            }
        }
    }

    private fun loadPositionPreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        hasCustomPosition = prefs.getBoolean("has_custom_position$suffix", false)
        if (hasCustomPosition) {
            dialogX = prefs.getInt("dialog_x$suffix", 0)
            dialogY = prefs.getInt("dialog_y$suffix", 0)

            // Validate position is within reasonable screen bounds
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // If position would place dialog mostly off-screen, reset to default
            if (dialogX < -screenWidth / 2 || dialogX > screenWidth ||
                dialogY < 0 || dialogY > screenHeight) {
                hasCustomPosition = false
                dialogX = 0
                dialogY = 0
                // Clear invalid saved position for this orientation
                prefs.edit()
                    .remove("has_custom_position$suffix")
                    .remove("dialog_x$suffix")
                    .remove("dialog_y$suffix")
                    .apply()
            }
        }
    }

    private fun savePositionPreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        prefs.edit()
            .putBoolean("has_custom_position$suffix", true)
            .putInt("dialog_x$suffix", dialogX)
            .putInt("dialog_y$suffix", dialogY)
            .apply()
        hasCustomPosition = true
    }

    private fun applyDialogPosition() {
        window?.let { win ->
            val params = win.attributes
            params.gravity = Gravity.TOP or Gravity.START
            params.x = dialogX
            params.y = dialogY
            win.attributes = params
        }
    }

    private fun loadSizePreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        hasCustomSize = prefs.getBoolean("has_custom_size$suffix", false)
        if (hasCustomSize) {
            containerWidth = prefs.getInt("container_width$suffix", DEFAULT_CONTAINER_WIDTH)
            // Validate bounds
            containerWidth = containerWidth.coerceIn(MIN_CONTAINER_WIDTH, MAX_CONTAINER_WIDTH)
        } else {
            containerWidth = DEFAULT_CONTAINER_WIDTH
        }
    }

    private fun saveSizePreference() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        prefs.edit()
            .putBoolean("has_custom_size$suffix", true)
            .putInt("container_width$suffix", containerWidth)
            .apply()
        hasCustomSize = true
    }

    private fun applyContainerSize() {
        val scale = getScaleFactor()

        // Scale main card width
        val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
        val cardParams = mainCard.layoutParams
        cardParams.width = dpToPx(containerWidth)
        mainCard.layoutParams = cardParams

        // Scale main container padding
        val mainContainerView = findViewById<LinearLayout>(R.id.mainContainer)
        val scaledPadding = dpToPx((DEFAULT_CONTAINER_PADDING * scale).toInt())
        mainContainerView.setPadding(scaledPadding, scaledPadding, scaledPadding, scaledPadding)

        // Scale apps container height and margins
        val appsContainerView = findViewById<LinearLayout>(R.id.appsContainer)
        val appsParams = appsContainerView.layoutParams as LinearLayout.LayoutParams
        appsParams.height = dpToPx((DEFAULT_APPS_CONTAINER_HEIGHT * scale).toInt())
        appsParams.topMargin = dpToPx((DEFAULT_APPS_MARGIN_TOP * scale).toInt())
        appsParams.bottomMargin = dpToPx((DEFAULT_APPS_MARGIN_BOTTOM * scale).toInt())
        appsContainerView.layoutParams = appsParams

        // Scale all button heights and margins
        val scaledButtonHeight = dpToPx((DEFAULT_BUTTON_HEIGHT * scale).toInt())
        val scaledButtonMargin = dpToPx((DEFAULT_BUTTON_MARGIN * scale).toInt())

        for (btnId in listOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                             R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)) {
            val button = findViewById<MaterialButton>(btnId)
            val btnParams = button.layoutParams as LinearLayout.LayoutParams
            btnParams.height = scaledButtonHeight
            btnParams.setMargins(scaledButtonMargin, scaledButtonMargin, scaledButtonMargin, scaledButtonMargin)
            button.layoutParams = btnParams
        }

        // Update button text sizes based on new container width
        updateButtonColors()

        // Refresh app list to scale icons
        if (currentQuery.isNotEmpty()) {
            updateAppsList()
        }
    }

    private fun resizeContainer(increase: Boolean) {
        val newWidth = if (increase) {
            containerWidth + CONTAINER_WIDTH_STEP
        } else {
            containerWidth - CONTAINER_WIDTH_STEP
        }

        // Check bounds
        if (newWidth < MIN_CONTAINER_WIDTH || newWidth > MAX_CONTAINER_WIDTH) {
            val message = if (increase) "Maximum size reached" else "Minimum size reached"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        containerWidth = newWidth
        applyContainerSize()
        saveSizePreference()
    }

    private fun resetContainerSize() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        prefs.edit()
            .remove("has_custom_size$suffix")
            .remove("container_width$suffix")
            .apply()

        hasCustomSize = false
        containerWidth = DEFAULT_CONTAINER_WIDTH
        applyContainerSize()
        Toast.makeText(this, "Size reset to default", Toast.LENGTH_SHORT).show()
    }

    private fun enterPendingMoveMode() {
        pendingMoveMode = true

        // Visual feedback: highlight border immediately
        val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
        savedStrokeColor = mainCard.strokeColor
        savedStrokeWidth = mainCard.strokeWidth
        mainCard.strokeColor = getColor(R.color.move_mode_highlight)
        mainCard.strokeWidth = dpToPx(3)

        // Show instruction to release
        showMoveStatus("Release to move")
    }

    private fun enterMoveMode() {
        // Capture actual window position before entering move mode
        val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
        val location = IntArray(2)
        mainCard.getLocationOnScreen(location)

        // Set dialogX/Y to current actual position so drag starts from here
        dialogX = location[0]
        dialogY = location[1]

        // Store initial position for cancel/revert
        initialDialogX = dialogX
        initialDialogY = dialogY

        // Visual feedback already applied in enterPendingMoveMode, ensure it's set
        if (savedStrokeColor == 0) {
            savedStrokeColor = mainCard.strokeColor
            savedStrokeWidth = mainCard.strokeWidth
            mainCard.strokeColor = getColor(R.color.move_mode_highlight)
            mainCard.strokeWidth = dpToPx(3)
        }

        isMoveModeActive = true
        updateButton3Icon()
        showMoveStatus("Move mode • Hold [3] to save")
    }

    private fun exitMoveMode(save: Boolean) {
        isMoveModeActive = false

        // Restore visual feedback
        val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
        mainCard.strokeColor = savedStrokeColor
        mainCard.strokeWidth = savedStrokeWidth

        // Update icon back to move
        updateButton3Icon()

        if (save) {
            savePositionPreference()
            showMoveStatus("Position saved")
            // Clear status after a short delay and restore app list
            mainScope.launch {
                delay(800)
                if (!isMoveModeActive) {
                    updateAppsList()
                }
            }
        } else {
            // Revert to previous position
            dialogX = initialDialogX
            dialogY = initialDialogY
            applyDialogPosition()
            updateAppsList()
        }
    }

    private fun showMoveStatus(message: String) {
        // Clear appsContainer and show status message
        appsContainer.removeAllViews()

        val textColor = if (isLightTheme) {
            getColor(R.color.light_app_text)
        } else {
            getColor(R.color.dark_app_text)
        }

        val statusView = TextView(this).apply {
            text = message
            textSize = 16f
            setTextColor(getColor(R.color.move_mode_highlight))
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        appsContainer.addView(statusView)
    }

    private fun resetPosition() {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val suffix = getOrientationSuffix()

        prefs.edit()
            .remove("has_custom_position$suffix")
            .remove("dialog_x$suffix")
            .remove("dialog_y$suffix")
            .apply()
        hasCustomPosition = false
        dialogX = 0
        dialogY = 0

        // Restore default gravity
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        window?.setGravity(if (isLandscape) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM)

        Toast.makeText(this, "Position reset to default", Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        // Cancel pending move mode on orientation change
        if (pendingMoveMode) {
            pendingMoveMode = false
            // Restore visual feedback
            val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
            mainCard.strokeColor = savedStrokeColor
            mainCard.strokeWidth = savedStrokeWidth
            updateAppsList()
        }

        // Exit move mode if active during orientation change (cancel without saving)
        if (isMoveModeActive) {
            // Restore visual feedback
            val mainCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.mainCard)
            mainCard.strokeColor = savedStrokeColor
            mainCard.strokeWidth = savedStrokeWidth
            isMoveModeActive = false
            updateButton3Icon()
        }

        // Load position preference for new orientation
        loadPositionPreference()

        // Apply custom position or default gravity for new orientation
        if (hasCustomPosition) {
            applyDialogPosition()
        } else {
            val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            window?.setGravity(if (isLandscape) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM)
        }

        // Load and apply size preference for new orientation
        loadSizePreference()
        applyContainerSize()
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

        // Update theme toggle icon
        updateThemeIcon()
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

    private fun updateThemeIcon() {
        val btn2 = findViewById<MaterialButton>(R.id.btn2)
        // Dark theme shows sun (to switch to light), light theme shows moon (to switch to dark)
        val iconRes = if (isLightTheme) R.drawable.ic_moon else R.drawable.ic_sun
        val themeIcon = getDrawable(iconRes)
        themeIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn2.setCompoundDrawables(null, null, themeIcon, null)
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
                AbsoluteSizeSpan(getScaledNumberSize(), true),
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
                AbsoluteSizeSpan(getScaledAlphabetSize(), true),
                number.length + 1, text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            button.text = spannable
        }
    }

    private fun loadIconPackMappings(packageName: String) {
        iconPackMappings.clear()
        val res = iconPackResources ?: return

        try {
            // Try to find appfilter in xml or raw resources
            var appfilterId = res.getIdentifier("appfilter", "xml", packageName)
            var isRawResource = false

            if (appfilterId == 0) {
                appfilterId = res.getIdentifier("appfilter", "raw", packageName)
                isRawResource = true
            }

            if (appfilterId != 0) {
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
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            // Icon pack parse failed — fall back to default system icons
        }
    }

    private fun setKeyText(button: MaterialButton, number: String, letters: String, isRed: Boolean = false) {
        val text = "$number\n$letters"
        val spannable = SpannableString(text)

        // Number: scaled size, white (or red for button 1)
        val numberColor = if (isRed) getColor(R.color.key_clear) else getColor(R.color.key_number)
        spannable.setSpan(
            ForegroundColorSpan(numberColor),
            0, number.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(getScaledNumberSize(), true),
            0, number.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Alphabet: scaled size, gray
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.key_alphabet)),
            number.length + 1, text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            AbsoluteSizeSpan(getScaledAlphabetSize(), true),
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

        // Button 1: Add icon pack (palette) icon
        val clearButton = findViewById<MaterialButton>(R.id.btn1)
        val paletteIcon = getDrawable(R.drawable.ic_palette)
        paletteIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        clearButton.setCompoundDrawables(null, null, paletteIcon, null)
        clearButton.compoundDrawablePadding = dpToPx(4)

        // Button 2: Add theme toggle icon (will be set in updateThemeIcon)
        val btn2 = findViewById<MaterialButton>(R.id.btn2)
        btn2.compoundDrawablePadding = dpToPx(4)

        // Button 4: Add arrow collapse icon for resize (shrink)
        val btn4 = findViewById<MaterialButton>(R.id.btn4)
        val collapseIcon = getDrawable(R.drawable.ic_arrow_collapse)
        collapseIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn4.setCompoundDrawables(null, null, collapseIcon, null)
        btn4.compoundDrawablePadding = dpToPx(4)

        // Button 5: Add reset icon for size reset
        val btn5 = findViewById<MaterialButton>(R.id.btn5)
        val resetIcon = getDrawable(R.drawable.ic_reset)
        resetIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn5.setCompoundDrawables(null, null, resetIcon, null)
        btn5.compoundDrawablePadding = dpToPx(4)

        // Button 6: Add arrow expand icon for resize (grow)
        val btn6 = findViewById<MaterialButton>(R.id.btn6)
        val expandIcon = getDrawable(R.drawable.ic_arrow_expand)
        expandIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn6.setCompoundDrawables(null, null, expandIcon, null)
        btn6.compoundDrawablePadding = dpToPx(4)

        // Number buttons 2-9
        btn2.setOnClickListener { addDigit('2') }
        findViewById<MaterialButton>(R.id.btn3).setOnClickListener { addDigit('3') }
        btn4.setOnClickListener { addDigit('4') }
        findViewById<MaterialButton>(R.id.btn5).setOnClickListener { addDigit('5') }
        btn6.setOnClickListener { addDigit('6') }
        findViewById<MaterialButton>(R.id.btn7).setOnClickListener { addDigit('7') }
        findViewById<MaterialButton>(R.id.btn8).setOnClickListener { addDigit('8') }
        findViewById<MaterialButton>(R.id.btn9).setOnClickListener { addDigit('9') }

        // Long-press button 4 to shrink container
        btn4.setOnLongClickListener {
            resizeContainer(false)  // decrease
            true
        }

        // Long-press button 5 to reset container size
        btn5.setOnLongClickListener {
            resetContainerSize()
            true
        }

        // Long-press button 6 to expand container
        btn6.setOnLongClickListener {
            resizeContainer(true)  // increase
            true
        }

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

        // Button 3: Add move icon and long-press to toggle move mode
        val btn3 = findViewById<MaterialButton>(R.id.btn3)
        updateButton3Icon()
        btn3.compoundDrawablePadding = dpToPx(4)

        // Long-press button 3 to toggle move mode (enter/save)
        btn3.setOnLongClickListener {
            if (isMoveModeActive) {
                // Already in move mode, save position and exit
                exitMoveMode(true)
            } else {
                // Enter pending state with visual feedback
                // Move mode fully activates when finger is released
                enterPendingMoveMode()
            }
            true
        }

        // Button 9: Add info icon for About dialog
        val btn9 = findViewById<MaterialButton>(R.id.btn9)
        val infoIcon = getDrawable(R.drawable.ic_info)
        infoIcon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn9.setCompoundDrawables(null, null, infoIcon, null)
        btn9.compoundDrawablePadding = dpToPx(4)

        // Long-press button 9 to show About dialog
        btn9.setOnLongClickListener {
            showAboutDialog()
            true
        }
    }

    private fun updateButton3Icon() {
        val btn3 = findViewById<MaterialButton>(R.id.btn3)
        val icon = if (isMoveModeActive) {
            getDrawable(R.drawable.ic_save)
        } else {
            getDrawable(R.drawable.ic_move)
        }
        icon?.setBounds(0, 0, dpToPx(16), dpToPx(16))
        btn3.setCompoundDrawables(null, null, icon, null)
    }

    private fun addDigit(digit: Char) {
        // Block input during move mode
        if (isMoveModeActive) return

        currentQuery += digit

        // Debounce rapid key presses
        searchDebounceJob?.cancel()
        searchDebounceJob = mainScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            updateAppsList()
        }
    }

    private fun showLoadingState() {
        appsContainer.removeAllViews()
        val textColor = if (isLightTheme) {
            getColor(R.color.light_app_text)
        } else {
            getColor(R.color.dark_app_text)
        }
        val loadingView = TextView(this).apply {
            text = "Loading..."
            textSize = 14f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        appsContainer.addView(loadingView)
    }

    private fun loadInstalledApps() {
        // Try loading from cache first for instant startup
        if (!appsLoaded) {
            val cached = loadAppsFromCache()
            if (cached.isNotEmpty()) {
                allApps = cached
                appsLoaded = true
                appsLoading = false
                updateAppsList()
            }
        }

        // Always refresh from PackageManager in background
        mainScope.launch(Dispatchers.IO) {
            val pm = packageManager

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
                        t9Sequence = stringToT9(appName),
                        icon = null
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name }

            // Save to cache for next cold start
            saveAppsToCache(apps)

            withContext(Dispatchers.Main) {
                allApps = apps
                appsLoaded = true
                appsLoading = false
                updateAppsList()
            }
        }
    }

    private fun saveAppsToCache(apps: List<AppInfo>) {
        try {
            val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
            // Store as "name\tpackage" lines — lightweight, no JSON dependency
            val data = apps.joinToString("\n") { "${it.name}\t${it.packageName}" }
            prefs.edit().putString(PREF_CACHED_APPS, data).apply()
        } catch (_: Exception) {
            // Non-critical — cache miss just means slower next startup
        }
    }

    private fun loadAppsFromCache(): List<AppInfo> {
        return try {
            val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
            val data = prefs.getString(PREF_CACHED_APPS, "") ?: ""
            if (data.isEmpty()) {
                return emptyList()
            }
            val apps = data.split("\n").mapNotNull { line ->
                val parts = line.split("\t", limit = 2)
                if (parts.size == 2) {
                    AppInfo(
                        name = parts[0],
                        packageName = parts[1],
                        t9Sequence = stringToT9(parts[0]),
                        icon = null
                    )
                } else null
            }
            apps
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recycleViews() {
        for (i in 0 until appsContainer.childCount) {
            val child = appsContainer.getChildAt(i)
            if (child is LinearLayout && child.tag is MatchInfo && viewPool.size < VIEW_POOL_MAX_SIZE) {
                viewPool.add(child)
            }
        }
        appsContainer.removeAllViews()
    }

    private fun updateAppsList() {
        // Show recent apps when no search query
        if (currentQuery.isEmpty()) {
            showRecentApps()
            return
        }

        // Full scan — collect all matches across all priorities
        val matchedApps = mutableListOf<MatchInfo>()
        for (app in allApps) {
            val matchInfo = getMatchInfo(app, currentQuery)
            if (matchInfo != null) {
                matchedApps.add(matchInfo)
            }
        }

        // Sort matched apps by priority, then name length, then name
        val sortedApps = matchedApps
            .sortedWith(compareBy<MatchInfo> { it.matchPriority }
                .thenBy { it.app.name.length }
                .thenBy { it.app.name })
            .take(3)

        // Immediately show views with cached icons or placeholders
        recycleViews()
        val appWidth = dpToPx(getScaledAppWidth())
        for (matchInfo in sortedApps) {
            // Use cached icon if available
            val cacheKey = "${iconPackPackageName ?: "default"}_${matchInfo.app.packageName}"
            val cachedIcon = iconCache[cacheKey]
            if (cachedIcon != null) {
                matchInfo.app.icon = cachedIcon
            }

            val appView = getOrCreateAppView(matchInfo)
            val params = LinearLayout.LayoutParams(appWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            appView.layoutParams = params
            appsContainer.addView(appView)
        }

        // Load missing icons asynchronously
        for (matchInfo in sortedApps) {
            if (matchInfo.app.icon == null) {
                val cacheKey = "${iconPackPackageName ?: "default"}_${matchInfo.app.packageName}"
                mainScope.launch(Dispatchers.IO) {
                    val icon = try {
                        getIconFromPack(matchInfo.app.packageName)
                            ?: packageManager.getApplicationIcon(matchInfo.app.packageName)
                    } catch (e: Exception) {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        val resolvedIcon = icon ?: getDrawable(android.R.drawable.sym_def_app_icon)!!
                        iconCache[cacheKey] = resolvedIcon
                        matchInfo.app.icon = resolvedIcon
                        // Update the ImageView if still visible
                        for (i in 0 until appsContainer.childCount) {
                            val child = appsContainer.getChildAt(i)
                            if (child is LinearLayout && (child.tag as? MatchInfo)?.app?.packageName == matchInfo.app.packageName) {
                                child.findViewById<ImageView>(android.R.id.icon)?.setImageDrawable(resolvedIcon)
                                break
                            }
                        }
                    }
                }
            }
        }

        // Center apps when fewer than 3 results
        appsContainer.gravity = Gravity.CENTER

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
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            appsContainer.addView(noMatchView)
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
        val iconSize = dpToPx(72)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))

            // Create icon view
            val icon = ImageView(this@T9Activity).apply {
                id = android.R.id.icon
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            }

            // Create label view
            val label = TextView(this@T9Activity).apply {
                id = android.R.id.text1
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(4)
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

        // Scale icon size
        val scaledIconSize = dpToPx(getScaledAppIconSize())
        val iconParams = icon.layoutParams
        iconParams.width = scaledIconSize
        iconParams.height = scaledIconSize
        icon.layoutParams = iconParams

        // Set icon (should always be loaded at this point)
        icon.setImageDrawable(matchInfo.app.icon)
        icon.visibility = android.view.View.VISIBLE

        // Highlight the matched portion of the app name
        val spannable = SpannableString(matchInfo.app.name)
        // Clamp to name bounds — app names with emoji/multi-codepoint chars can desync the T9 index
        val matchStart = matchInfo.matchPosition.coerceIn(0, matchInfo.app.name.length)
        val matchEnd = (matchStart + currentQuery.length).coerceAtMost(matchInfo.app.name.length)

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
        label.textSize = getScaledAppLabelSize()

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
            recordAppLaunch(packageName)
            startActivity(intent)
            finish()
        }
    }

    private fun recordAppLaunch(packageName: String) {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        val recent = prefs.getString(PREF_RECENT_APPS, "")!!
            .split(",")
            .filter { it.isNotEmpty() && it != packageName }
            .toMutableList()
        recent.add(0, packageName)
        prefs.edit()
            .putString(PREF_RECENT_APPS, recent.take(MAX_RECENT_APPS).joinToString(","))
            .apply()
    }

    private fun getRecentApps(): List<String> {
        val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
        return prefs.getString(PREF_RECENT_APPS, "")!!
            .split(",")
            .filter { it.isNotEmpty() }
    }

    private fun showRecentApps() {
        val recentPackages = getRecentApps()
        if (recentPackages.isEmpty()) return

        // Need apps loaded to resolve package names
        if (!appsLoaded) return

        recycleViews()
        val appWidth = dpToPx(getScaledAppWidth())

        for (pkg in recentPackages) {
            val app = allApps.find { it.packageName == pkg } ?: continue

            // Use cached icon if available
            val cacheKey = "${iconPackPackageName ?: "default"}_${app.packageName}"
            val cachedIcon = iconCache[cacheKey]
            if (cachedIcon != null) {
                app.icon = cachedIcon
            }

            val matchInfo = MatchInfo(app, 0, 0)
            val appView = getOrCreateAppView(matchInfo)
            val params = LinearLayout.LayoutParams(appWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            appView.layoutParams = params
            appsContainer.addView(appView)

            // Load icon async if not cached
            if (app.icon == null) {
                mainScope.launch(Dispatchers.IO) {
                    val icon = try {
                        getIconFromPack(app.packageName)
                            ?: packageManager.getApplicationIcon(app.packageName)
                    } catch (e: Exception) {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        val resolvedIcon = icon ?: getDrawable(android.R.drawable.sym_def_app_icon)!!
                        iconCache[cacheKey] = resolvedIcon
                        app.icon = resolvedIcon
                        for (i in 0 until appsContainer.childCount) {
                            val child = appsContainer.getChildAt(i)
                            if (child is LinearLayout && (child.tag as? MatchInfo)?.app?.packageName == app.packageName) {
                                child.findViewById<ImageView>(android.R.id.icon)?.setImageDrawable(resolvedIcon)
                                break
                            }
                        }
                    }
                }
            }
        }

        appsContainer.gravity = Gravity.CENTER
    }

    private fun getInstalledIconPacks(): List<IconPackInfo> {
        // Return cached list if available
        cachedIconPacks?.let { return it }

        val iconPacks = mutableListOf<IconPackInfo>()

        // Add "Default" option
        iconPacks.add(IconPackInfo(
            "Default (System Icons)",
            "",
            getDrawable(android.R.drawable.sym_def_app_icon) ?: ColorDrawable(0)
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

    private fun createCustomDialog(): Pair<Dialog, android.view.View> {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_custom, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        val scale = getScaleFactor()

        // Apply theme colors
        val card = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.dialogCard)
        val container = view.findViewById<LinearLayout>(R.id.dialogContainer)
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val cancelButton = view.findViewById<TextView>(R.id.dialogCancelButton)

        val bgColor = if (isLightTheme) getColor(R.color.light_dialog_background) else getColor(R.color.dark_dialog_background)
        val textColor = if (isLightTheme) getColor(R.color.light_dialog_text) else getColor(R.color.dark_dialog_text)
        val titleColor = if (isLightTheme) getColor(R.color.light_dialog_title) else getColor(R.color.dark_dialog_title)
        val borderColor = if (isLightTheme) getColor(R.color.light_border) else getColor(R.color.dark_border)
        val buttonColor = if (isLightTheme) getColor(R.color.light_dialog_button) else getColor(R.color.dark_dialog_button)

        // Scale dialog card width
        val cardParams = card.layoutParams
        cardParams.width = dpToPx((280 * scale).toInt())
        card.layoutParams = cardParams
        card.radius = dpToPx((16 * scale).toInt()).toFloat()

        card.setCardBackgroundColor(bgColor)
        card.strokeColor = borderColor
        container.setBackgroundColor(bgColor)

        // Scale title
        val scaledPad = dpToPx((20 * scale).toInt())
        title.setPadding(scaledPad, scaledPad, scaledPad, dpToPx((12 * scale).toInt()))
        title.textSize = 18 * scale
        title.setTextColor(titleColor)

        // Scale cancel button
        cancelButton.setPadding(scaledPad, dpToPx((16 * scale).toInt()), scaledPad, dpToPx((16 * scale).toInt()))
        cancelButton.textSize = 14 * scale
        cancelButton.setTextColor(buttonColor)

        // Dismiss on background tap
        view.setOnClickListener { dialog.dismiss() }

        // Cancel button
        cancelButton.setOnClickListener { dialog.dismiss() }

        // Apply ripple to cancel button
        val rippleColor = if (isLightTheme) getColor(R.color.light_ripple) else getColor(R.color.dark_ripple)
        cancelButton.foreground = createRippleDrawable(rippleColor)

        return Pair(dialog, view)
    }

    private fun addDialogItem(
        container: LinearLayout,
        text: String,
        icon: Drawable? = null,
        isSelected: Boolean = false,
        onClick: () -> Unit
    ) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.dialog_item, container, false)
        val scale = getScaleFactor()

        val itemIcon = itemView.findViewById<ImageView>(R.id.itemIcon)
        val itemText = itemView.findViewById<TextView>(R.id.itemText)
        val itemCheck = itemView.findViewById<ImageView>(R.id.itemCheck)

        // Scale item padding
        val hPad = dpToPx((20 * scale).toInt())
        val vPad = dpToPx((14 * scale).toInt())
        itemView.setPadding(hPad, vPad, hPad, vPad)

        // Apply theme colors
        val textColor = if (isLightTheme) getColor(R.color.light_dialog_text) else getColor(R.color.dark_dialog_text)
        val checkColor = if (isLightTheme) getColor(R.color.light_dialog_button) else getColor(R.color.dark_dialog_button)

        itemText.text = text
        itemText.setTextColor(textColor)
        itemText.textSize = 16 * scale

        if (icon != null) {
            itemIcon.setImageDrawable(icon)
            itemIcon.visibility = android.view.View.VISIBLE
            // Scale icon size
            val iconSize = dpToPx((32 * scale).toInt())
            val iconParams = itemIcon.layoutParams
            iconParams.width = iconSize
            iconParams.height = iconSize
            itemIcon.layoutParams = iconParams
            (iconParams as? LinearLayout.LayoutParams)?.marginEnd = dpToPx((16 * scale).toInt())
        } else {
            itemIcon.visibility = android.view.View.GONE
        }

        if (isSelected) {
            val checkDrawable = getDrawable(R.drawable.ic_check)
            checkDrawable?.setTint(checkColor)
            itemCheck.setImageDrawable(checkDrawable)
            itemCheck.visibility = android.view.View.VISIBLE
            // Scale check icon
            val checkSize = dpToPx((24 * scale).toInt())
            val checkParams = itemCheck.layoutParams
            checkParams.width = checkSize
            checkParams.height = checkSize
            itemCheck.layoutParams = checkParams
        } else {
            itemCheck.visibility = android.view.View.GONE
        }

        // Apply ripple effect
        val rippleColor = if (isLightTheme) getColor(R.color.light_ripple) else getColor(R.color.dark_ripple)
        itemView.foreground = createRippleDrawable(rippleColor)

        itemView.setOnClickListener { onClick() }
        container.addView(itemView)
    }

    private fun showIconPackSelector() {
        val iconPacks = getInstalledIconPacks()

        // Find currently selected icon pack index
        val currentSelection = iconPacks.indexOfFirst {
            it.packageName == (iconPackPackageName ?: "")
        }

        val (dialog, view) = createCustomDialog()
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.dialogItemsContainer)

        title.text = "Select Icon Pack"

        iconPacks.forEachIndexed { index, pack ->
            addDialogItem(
                container = itemsContainer,
                text = pack.name,
                icon = pack.icon,
                isSelected = index == currentSelection
            ) {
                // Save preference
                val prefs = getSharedPreferences("T9Dialer", Context.MODE_PRIVATE)
                if (pack.packageName.isEmpty()) {
                    // Default selected — no XML parsing needed
                    prefs.edit().remove("icon_pack").apply()
                    iconPackPackageName = null
                    iconPackResources = null
                    iconPackMappings.clear()
                    iconCache.clear()
                    for (app in allApps) app.icon = null
                    updateAppsList()
                    Toast.makeText(this, "Using default icons", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    prefs.edit().putString("icon_pack", pack.packageName).apply()
                    iconPackPackageName = pack.packageName
                    iconPackResources = try {
                        packageManager.getResourcesForApplication(pack.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                    dialog.dismiss()
                    // Parse the icon pack's appfilter off the main thread — it can be large (ANR risk)
                    mainScope.launch(Dispatchers.IO) {
                        loadIconPackMappings(pack.packageName)
                        withContext(Dispatchers.Main) {
                            iconCache.clear()
                            for (app in allApps) app.icon = null
                            updateAppsList()
                            val msg = if (iconPackMappings.isNotEmpty()) "Icon pack applied" else "No icons found in pack"
                            Toast.makeText(this@T9Activity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        val (dialog, view) = createCustomDialog()
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.dialogItemsContainer)
        val scale = getScaleFactor()

        title.text = "T9 App Dialer"

        // Get theme-aware icon color
        val iconColor = if (isLightTheme) getColor(R.color.light_dialog_text) else getColor(R.color.dark_dialog_text)

        // App icon centered
        val appIconLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = dpToPx((20 * scale).toInt())
            val vPad = dpToPx((8 * scale).toInt())
            setPadding(pad, vPad, pad, vPad)
        }

        val iconSize = dpToPx((72 * scale).toInt())
        val appIcon = ImageView(this).apply {
            setImageDrawable(getDrawable(R.drawable.ic_launcher))
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }
        appIconLayout.addView(appIcon)
        itemsContainer.addView(appIconLayout)

        // Version as dialog item - opens app info
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: "?"
        val settingsIcon = getDrawable(R.drawable.ic_settings)?.apply { setTint(iconColor) }
        addDialogItem(itemsContainer, "Version $versionName", icon = settingsIcon) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            dialog.dismiss()
        }

        // GitHub link
        val githubIcon = getDrawable(R.drawable.ic_github)?.apply { setTint(iconColor) }
        addDialogItem(itemsContainer, "View on GitHub", icon = githubIcon) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/officialdad/t9-app-dialer"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAppContextMenu(app: AppInfo) {
        val (dialog, view) = createCustomDialog()
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.dialogItemsContainer)

        title.text = app.name

        // Get theme-aware icon color
        val iconColor = if (isLightTheme) getColor(R.color.light_dialog_text) else getColor(R.color.dark_dialog_text)

        // App Info option
        val infoIcon = getDrawable(R.drawable.ic_info)?.apply { setTint(iconColor) }
        addDialogItem(itemsContainer, "App Info", icon = infoIcon) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${app.packageName}")
            startActivity(intent)
            dialog.dismiss()
        }

        // Open in Play Store option
        val playStoreIcon = getDrawable(R.drawable.ic_play_store)?.apply { setTint(iconColor) }
        addDialogItem(itemsContainer, "Open in Play Store", icon = playStoreIcon) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                startActivity(intent)
            }
            dialog.dismiss()
        }

        dialog.show()
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
