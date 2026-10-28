// BEACON COMPUTING INITIATIVE
// Component: BCILock Android Authenticator v1.6.2
// Developer Seal: CJB-DEV-2026
// SHA-256: see release SHA256SUMS
package org.beaconcomputing.bcilock

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
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
import android.view.WindowInsets
import android.widget.*
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Presentation and user-approved signing. BLE transport is owned by BciBleService. */
class BciBridgeActivity : Activity() {
    companion object {
        val SERVICE: UUID = UUID.fromString("d527a80f-7cfa-4ed9-85da-29b9fe088f61")
        val PUBLIC: UUID = UUID.fromString("d527a810-7cfa-4ed9-85da-29b9fe088f61")
        val CHALLENGE: UUID = UUID.fromString("d527a811-7cfa-4ed9-85da-29b9fe088f61")
        val RESPONSE: UUID = UUID.fromString("d527a812-7cfa-4ed9-85da-29b9fe088f61")
        const val KEY_ALIAS = "BCILock-v040-Enterprise"
        const val AUTH_REQUEST = 717
        const val PERMISSION_REQUEST = 718
    }
    private val onyx = Color.rgb(7, 10, 15)
    private val cyan = Color.rgb(34, 224, 245)
    private val crimson = Color.rgb(242, 42, 59)
    private val silver = Color.rgb(220, 234, 240)
    private val muted = Color.rgb(151, 168, 178)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stage: TextView
    private lateinit var link: TextView
    private lateinit var detail: TextView
    private lateinit var approve: Button
    private lateinit var deny: Button
    private lateinit var start: Button
    private lateinit var stop: Button
    private var credentialRequest: BciBleLinkState.Challenge? = null
    private val prefix = "BCILOCK-V1|UNLOCK|Enterprise|".toByteArray(Charsets.US_ASCII)
    private val refresh = object : Runnable {
        override fun run() { updateState(); handler.postDelayed(this, 600) }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun font(name: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(assets, "fonts/$name") } catch (_: Exception) { fallback }
    private fun text(value: String, size: Float, color: Int, family: String = "rajdhani.ttf") =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = font(family, Typeface.DEFAULT); includeFontPadding = false
        }
    private fun metal(color: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(43, 54, 65), Color.rgb(15, 23, 31), onyx)
    ).apply { cornerRadius = dp(15).toFloat(); setStroke(dp(1), color) }
    private fun panel(container: LinearLayout, title: String, body: String, accent: Int): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = metal(accent)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(text(title, 12f, accent, "oxanium.ttf"))
        val content = text(body, 18f, silver, "oxanium.ttf")
        box.addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        container.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return content
    }
    private fun action(name: String, accent: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = name; textSize = 13f; setTextColor(silver)
        typeface = font("oxanium.ttf", Typeface.DEFAULT_BOLD)
        background = metal(accent); setOnClickListener { onClick() }
    }
    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun publicKey(): ByteArray {
        var ks = keystore()
        if (!ks.containsAlias(KEY_ALIAS)) {
            val gen = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(30)
                .build()
            gen.initialize(spec); gen.generateKeyPair()
            ks = keystore()
        }
        val result = ks.getCertificate(KEY_ALIAS).publicKey.encoded
        getSharedPreferences("bci_auth_status", MODE_PRIVATE).edit().putBoolean("key_ready", true).apply()
        return result
    }
    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = onyx; window.navigationBarColor = onyx
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(30))
            setBackgroundColor(onyx)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(onyx); addView(content)
            if (Build.VERSION.SDK_INT >= 30) setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(0, bars.top, 0, bars.bottom); insets
            }
        }
        content.addView(text("BEACON COMPUTING INITIATIVE", 12f, cyan, "oxanium.ttf"))
        content.addView(text("AUTHENTICATOR", 31f, silver, "oxanium.ttf"),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(15) })
        content.addView(text("SECURE SIGNED APPROVAL  //  Enterprise", 12f, cyan, "jetbrainsmono.ttf"))
        stage = panel(content, "AUTHENTICATION STATE", "OFFLINE", crimson)
        link = panel(content, "ENDPOINT LINK", "WAITING", cyan)
        detail = panel(content, "ENROLLED KEY", "Initializing Android Keystore", cyan)
        val commandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        start = action("START GATT", cyan) { startOrRequest() }
        stop = action("STOP GATT", crimson) {
            startService(Intent(this, BciBleService::class.java).apply { action = BciBleService.STOP })
            updateState()
        }
        commandRow.addView(start, LinearLayout.LayoutParams(0, dp(62), 1f))
        commandRow.addView(stop, LinearLayout.LayoutParams(0, dp(62), 1f).apply { leftMargin = dp(8) })
        content.addView(commandRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        approve = action("APPROVE CHALLENGE", cyan) { approveChallenge() }
        deny = action("DENY CHALLENGE", crimson) {
            BciBleLinkState.claim()?.let { BciBleLinkState.deny(it.epoch) }
            updateState()
        }
        content.addView(approve, LinearLayout.LayoutParams(-1, dp(66)).apply { topMargin = dp(12) })
        content.addView(deny, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(7) })
        content.addView(text("A GATT link is not proof of endpoint identity. Signing does not unlock Kali or grant egress until the computer explicitly verifies and enforces it.",
            13f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        content.addView(text("Developer Seal: CJB-DEV-2026  |  v1.6.2", 11f, cyan,
            "jetbrainsmono.ttf"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(scroll)
        try { detail.text = "Enterprise key SHA-256\n${fingerprint(publicKey())}" }
        catch (e: Exception) { detail.text = "KEY ERROR: ${e.javaClass.simpleName}" }
        updateState()
    }
    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
    private fun startOrRequest() {
        if (!permitted()) {
            if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), PERMISSION_REQUEST)
            return
        }
        try {
            val intent = Intent(this, BciBleService::class.java).apply { action = BciBleService.START }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            stage.text = "SERVICE START FAILED: ${e.javaClass.simpleName}"
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == PERMISSION_REQUEST) {
            if (permitted()) startOrRequest() else stage.text = "NEARBY DEVICES PERMISSION DENIED"
        }
    }
    private fun approveChallenge() {
        val request = BciBleLinkState.claim() ?: return
        credentialRequest = request
        val guard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!guard.isDeviceSecure) {
            BciBleLinkState.cancelClaim(request.epoch)
            credentialRequest = null; stage.text = "SECURE PHONE LOCK REQUIRED"; return
        }
        val intent = guard.createConfirmDeviceCredentialIntent("BCILock", "Authorize Enterprise challenge")
        if (intent == null) {
            BciBleLinkState.cancelClaim(request.epoch)
            credentialRequest = null; stage.text = "CREDENTIAL PROMPT UNAVAILABLE"; return
        }
        startActivityForResult(intent, AUTH_REQUEST)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST) return
        val request = credentialRequest ?: return
        credentialRequest = null
        if (resultCode != RESULT_OK) { BciBleLinkState.cancelClaim(request.epoch); updateState(); return }
        try {
            val signer = Signature.getInstance("SHA256withECDSA")
            val key = keystore().getKey(KEY_ALIAS, null) as java.security.PrivateKey
            signer.initSign(key); signer.update(prefix); signer.update(request.nonce)
            val signature = signer.sign()
            if (BciBleLinkState.signed(request, signature)) {
                getSharedPreferences("bci_auth_status", MODE_PRIVATE).edit()
                    .putLong("last_signed_at", System.currentTimeMillis()).apply()
            } else stage.text = "CHALLENGE CHANGED OR EXPIRED — REQUEST AGAIN"
        } catch (e: Exception) {
            BciBleLinkState.cancelClaim(request.epoch)
            stage.text = "SIGNING FAILED: ${e.javaClass.simpleName}"
        }
        updateState()
    }
    private fun updateState() {
        if (!::stage.isInitialized) return
        val s = BciBleLinkState.snapshot()
        stage.text = when {
            s.challengePending && s.approvalInProgress -> "VERIFYING PHONE CREDENTIAL"
            s.challengePending -> "APPROVAL REQUIRED"
            s.signedResponseReady -> "SIGNED RESPONSE READY"
            s.running -> "AUTHENTICATOR ACTIVE"
            else -> "AUTHENTICATOR OFFLINE"
        }
        link.text = when {
            s.connected -> "GATT PEER CONNECTED · UNVERIFIED"
            s.advertising -> "ADVERTISING FOR ENDPOINT"
            s.running -> "STARTING BLE SERVICE"
            else -> "NO ACTIVE GATT LINK"
        }
        approve.isEnabled = s.challengePending && !s.approvalInProgress
        deny.isEnabled = s.challengePending && !s.approvalInProgress
        start.isEnabled = !s.running
        stop.isEnabled = s.running
    }
    override fun onResume() {
        super.onResume(); handler.removeCallbacks(refresh)
        updateState(); handler.postDelayed(refresh, 600)
    }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(refresh); super.onDestroy() }
}
