// BEACON COMPUTING INITIATIVE
// Component: BCILock Android Dashboard v1.6.1 (icon and launch hotfix)
// Developer Seal: CJB-DEV-2026
// SHA-256: recorded in release SHA256SUMS
package org.beaconcomputing.bcilock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val onyx = Color.rgb(7, 10, 15)
    private val cyan = Color.rgb(34, 224, 245)
    private val crimson = Color.rgb(242, 42, 59)
    private val silver = Color.rgb(220, 234, 240)
    private val muted = Color.rgb(151, 168, 178)
    private lateinit var content: LinearLayout
    private val btPermissionRequest = 161

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    // Read adapter state iff the runtime permission is granted; fail safely otherwise.
    private fun bluetoothStatus(): Pair<String, String> {
        if (!hasBluetoothPermission()) {
            return Pair("PERMISSION REQUIRED", "Grant Nearby devices to read the local Bluetooth adapter state")
        }
        return try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            when {
                adapter == null -> Pair("UNAVAILABLE", "No Bluetooth adapter reported by Android")
                adapter.isEnabled -> Pair("ADAPTER ENABLED", "Local adapter only — not proof of Kali connection")
                else -> Pair("ADAPTER OFF", "Enable Bluetooth to use the phone authenticator")
            }
        } catch (_: SecurityException) {
            Pair("PERMISSION REQUIRED", "Android denied Bluetooth status access")
        } catch (_: RuntimeException) {
            Pair("STATUS UNAVAILABLE", "Android Bluetooth subsystem could not report its state")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == btPermissionRequest && ::content.isInitialized) render()
    }
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
        val bluetooth = bluetoothStatus()
        val prefs = getSharedPreferences("bci_auth_status", MODE_PRIVATE)
        val keyReady = prefs.getBoolean("key_ready", false)
        val signed = prefs.getLong("last_signed_at", 0L)
        card("ENDPOINT", "Enterprise · Kali Linux", "Endpoint identity configured locally; remote telemetry not connected", cyan)
        card("BLUETOOTH", bluetooth.first, bluetooth.second,
            if (bluetooth.first == "ADAPTER ENABLED") cyan else crimson)
        if (!hasBluetoothPermission()) {
            val permission = Button(this).apply {
                text = "AUTHORIZE BLUETOOTH STATUS"
                textSize = 14f
                setTextColor(cyan)
                background = surface(cyan)
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), btPermissionRequest)
                    }
                }
            }
            add(permission, 8)
        }
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
