// BEACON COMPUTING INITIATIVE
// Component: BCILock Android Live Link Dashboard v1.6.2
// Developer Seal: CJB-DEV-2026
// SHA-256: see release SHA256SUMS
package org.beaconcomputing.bcilock

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.*

class MainActivity : Activity() {
    private val onyx = Color.rgb(7, 10, 15)
    private val cyan = Color.rgb(34, 224, 245)
    private val crimson = Color.rgb(242, 42, 59)
    private val silver = Color.rgb(220, 234, 240)
    private val muted = Color.rgb(151, 168, 178)
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var adapterValue: TextView
    private lateinit var adapterDetails: TextView
    private lateinit var endpointValue: TextView
    private lateinit var endpointDetails: TextView
    private lateinit var authenticatorValue: TextView
    private lateinit var authenticatorDetails: TextView
    private val refresh = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun font(name: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(assets, "fonts/$name") } catch (_: Exception) { fallback }
    private fun label(value: String, size: Float, tint: Int, face: String = "rajdhani.ttf") =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(tint)
            typeface = font(face, Typeface.DEFAULT); includeFontPadding = false
        }
    private fun surface(accent: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(41, 51, 62), Color.rgb(16, 23, 32), onyx)
    ).apply { cornerRadius = dp(15).toFloat(); setStroke(dp(1), accent) }
    private fun add(view: View, margin: Int = 10) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) })
    }
    private fun card(title: String, accent: Int): Pair<TextView, TextView> {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = surface(accent)
            setPadding(dp(16), dp(13), dp(16), dp(14)); elevation = dp(4).toFloat()
        }
        outer.addView(label(title, 13f, accent, "oxanium.ttf"))
        val current = label("CHECKING", 19f, silver, "oxanium.ttf")
        val details = label("Waiting for local status", 13f, muted)
        outer.addView(current, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        outer.addView(details, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        add(outer)
        return current to details
    }
    private fun hasBluetoothPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private fun bluetooth(): Pair<String, String> {
        if (!hasBluetoothPermission()) return "PERMISSION REQUIRED" to "Grant Nearby Devices for live adapter state"
        return try {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            when {
                adapter == null -> "NOT AVAILABLE" to "No Android Bluetooth adapter detected"
                adapter.isEnabled -> "ADAPTER ENABLED" to "Android Bluetooth radio is on"
                else -> "ADAPTER DISABLED" to "Turn on Bluetooth to authenticate"
            }
        } catch (_: SecurityException) {
            "PERMISSION REQUIRED" to "Android denied adapter access"
        } catch (_: RuntimeException) {
            "STATUS UNAVAILABLE" to "Bluetooth subsystem unavailable"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = onyx; window.navigationBarColor = onyx
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28)); setBackgroundColor(onyx)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(onyx)
            addView(content)
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { v, insets ->
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(0, bars.top, 0, bars.bottom)
                    insets
                }
            }
        }
        setContentView(scroll)
        add(label("BEACON COMPUTING INITIATIVE", 12f, cyan, "oxanium.ttf"), 0)
        add(label("BCI LOCK", 36f, silver, "oxanium.ttf"), 13)
        add(label("ENDPOINT AUTHORIZATION  //  v1.6.2", 11f, cyan, "jetbrainsmono.ttf"), 2)
        val image = ImageView(this).apply {
            try {
                assets.open("branding/bcilock_eye_lighthouse.png").use {
                    setImageDrawable(android.graphics.drawable.Drawable.createFromStream(it, null))
                }
            } catch (_: Exception) { setImageResource(android.R.drawable.ic_lock_lock) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = surface(crimson)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        content.addView(image, LinearLayout.LayoutParams(-1, dp(145)).apply { topMargin = dp(12) })
        val endpointHeader = card("ASSIGNED ENDPOINT", cyan)
        endpointHeader.first.text = "Enterprise · Kali Linux"
        endpointHeader.second.text = "Locally configured identity; BLE peer must still be authenticated"
        val a = card("BLUETOOTH ADAPTER", cyan); adapterValue = a.first; adapterDetails = a.second
        val e = card("ENDPOINT BLE LINK", cyan); endpointValue = e.first; endpointDetails = e.second
        val k = card("PHONE AUTHENTICATOR", cyan); authenticatorValue = k.first; authenticatorDetails = k.second
        val network = card("NETWORK GUARD", crimson)
        network.first.text = "REMOTE STATUS UNVERIFIED"
        network.second.text = "Tailscale telemetry requires authenticated Android client configuration"
        val open = Button(this).apply {
            text = "OPEN SECURE AUTHENTICATOR"; textSize = 14f
            setTextColor(silver); typeface = font("oxanium.ttf", Typeface.DEFAULT_BOLD)
            background = surface(crimson)
            setOnClickListener { startActivity(Intent(this@MainActivity, BciBridgeActivity::class.java)) }
        }
        add(open, 13)
        if (!hasBluetoothPermission() && Build.VERSION.SDK_INT >= 31) {
            val permission = Button(this).apply {
                text = "GRANT NEARBY DEVICES PERMISSION"
                setTextColor(cyan); background = surface(cyan)
                setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE), 161)
                }
            }
            add(permission)
        }
        add(label("Developer Seal: CJB-DEV-2026 | GATT connection ≠ verified endpoint", 11f, muted,
            "jetbrainsmono.ttf"), 14)
        refreshStatus()
    }
    private fun refreshStatus() {
        if (!::adapterValue.isInitialized) return
        val adapter = bluetooth()
        adapterValue.text = adapter.first; adapterDetails.text = adapter.second
        val s = BciBleLinkState.snapshot()
        endpointValue.text = when {
            adapter.first != "ADAPTER ENABLED" -> "NO RADIO LINK"
            s.connected -> "GATT PEER CONNECTED"
            s.advertising -> "AWAITING KALI CONNECTION"
            s.running -> "STARTING AUTHENTICATOR"
            else -> "AUTHENTICATOR OFFLINE"
        }
        endpointDetails.text = if (s.connected)
            "Active BLE GATT link · endpoint identity NOT YET VERIFIED"
        else if (s.running) s.label
        else "Open Authenticator and tap START to establish a live link"
        val prefs = getSharedPreferences("bci_auth_status", MODE_PRIVATE)
        val key = prefs.getBoolean("key_ready", false)
        authenticatorValue.text = when {
            s.challengePending -> "APPROVAL REQUIRED"
            s.signedResponseReady -> "SIGNATURE READY"
            key -> "KEY AVAILABLE"
            else -> "KEY NOT INITIALIZED"
        }
        authenticatorDetails.text = when {
            s.challengePending -> "Kali challenge received; open Authenticator to approve"
            s.signedResponseReady -> "Signed locally; remote verification not acknowledged"
            key -> "Enrolled key available; awaiting a new challenge"
            else -> "Open Authenticator to create the Android Keystore key"
        }
    }
    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        refreshStatus()
        handler.postDelayed(refresh, 1000)
    }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(refresh); super.onDestroy() }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        refreshStatus()
        // Restart screen to display permission action correctly if granted.
        if (code == 161 && grants.isNotEmpty() && grants.all { it == PackageManager.PERMISSION_GRANTED }) recreate()
    }
}
