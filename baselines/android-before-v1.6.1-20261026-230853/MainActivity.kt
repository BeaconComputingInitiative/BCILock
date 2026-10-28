// BEACON COMPUTING INITIATIVE
// Component: BCILock Android Dashboard v1.6 (staging)
// Developer Seal: CJB-DEV-2026
// SHA-256: recorded in release SHA256SUMS
package org.beaconcomputing.bcilock

import android.app.Activity
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val onyx = Color.rgb(7, 10, 15)
    private val cyan = Color.rgb(34, 224, 245)
    private val crimson = Color.rgb(242, 42, 59)
    private val silver = Color.rgb(220, 234, 240)
    private val muted = Color.rgb(151, 168, 178)
    private lateinit var content: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun font(name: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(assets, "fonts/$name") } catch (_: Exception) { fallback }
    private fun text(value: String, size: Float, tint: Int, face: String = "rajdhani.ttf") =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(tint)
            typeface = font(face, Typeface.DEFAULT); includeFontPadding = false
        }
    private fun surface(accent: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(41, 51, 62), Color.rgb(16, 23, 32), onyx)
    ).apply { cornerRadius = dp(16).toFloat(); setStroke(dp(1), accent) }
    private fun add(view: android.view.View, margin: Int = 10) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) })
    }
    private fun card(title: String, status: String, detail: String, accent: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = surface(accent)
            setPadding(dp(18), dp(16), dp(18), dp(17)); elevation = dp(5).toFloat()
        }
        card.addView(text(title, 14f, accent, "oxanium.ttf"))
        card.addView(text(status, 22f, silver, "oxanium.ttf"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        card.addView(text(detail, 14f, muted, "rajdhani.ttf"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        add(card)
    }
    @Suppress("MissingPermission")
    override fun onResume() { super.onResume(); if (::content.isInitialized) render() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = onyx; window.navigationBarColor = onyx
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28)); setBackgroundColor(onyx)
        }
        val scroller = ScrollView(this).apply { isFillViewport = true; addView(content) }
        setContentView(scroller)
        render()
    }
    private fun render() {
        content.removeAllViews()
        add(text("BEACON COMPUTING INITIATIVE", 13f, cyan, "oxanium.ttf"), 0)
        add(text("BCI LOCK", 41f, silver, "oxanium.ttf"), 18)
        add(text("ENTERPRISE ENDPOINT SECURITY  //  v1.6 STAGING", 12f, cyan, "jetbrainsmono.ttf"), 0)
        val brand = ImageView(this).apply {
            try { assets.open("branding/bcilock_eye_lighthouse.png").use { setImageDrawable(android.graphics.drawable.Drawable.createFromStream(it, null)) } }
            catch (_: Exception) { setImageResource(android.R.drawable.ic_lock_lock) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = surface(crimson); setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        content.addView(brand, LinearLayout.LayoutParams(-1, dp(180)).apply { topMargin = dp(16) })
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothEnabled = manager.adapter?.isEnabled == true
        val prefs = getSharedPreferences("bci_auth_status", MODE_PRIVATE)
        val keyReady = prefs.getBoolean("key_ready", false)
        val signed = prefs.getLong("last_signed_at", 0L)
        card("ENDPOINT", "Enterprise · Kali Linux", "Endpoint identity configured locally; remote telemetry not connected", cyan)
        card("BLUETOOTH", if (bluetoothEnabled) "ADAPTER ENABLED" else "ADAPTER OFF",
            "Adapter status only — not proof that Kali is connected", if (bluetoothEnabled) cyan else crimson)
        card("PHONE AUTHENTICATOR", if (keyReady) "KEY AVAILABLE" else "NOT INITIALIZED",
            if (signed > 0) "Last local signature: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(signed))}" else "Open authenticator to initialize and approve challenges", cyan)
        card("NETWORK GUARD", "REMOTE STATUS UNVERIFIED",
            "Kali v1.5 enforces egress; app telemetry / signed egress integration pending", crimson)
        card("DESKTOP LOCK", "INTEGRATION PENDING", "No remote desktop unlock is performed by this app", crimson)
        val action = Button(this).apply {
            text = "OPEN SECURE AUTHENTICATOR"; textSize = 16f; setTextColor(silver)
            typeface = font("oxanium.ttf", Typeface.DEFAULT_BOLD)
            background = surface(crimson)
            setOnClickListener { startActivity(Intent(this@MainActivity, BciBridgeActivity::class.java)) }
        }
        add(action, 20)
        val refresh = Button(this).apply {
            text = "REFRESH LOCAL STATUS"; setTextColor(cyan); background = surface(cyan)
            setOnClickListener { render() }
        }
        add(refresh)
        add(text("Developer Seal: CJB-DEV-2026  |  Local status only", 12f, muted, "jetbrainsmono.ttf"), 17)
    }
}
