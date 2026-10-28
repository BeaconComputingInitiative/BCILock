// BEACON COMPUTING INITIATIVE
// Component: BCILock Bluetooth Bridge v0.4.0
// Developer Seal: CJB-DEV-2026
// Source integrity: SHA-256 recorded in SHA256SUMS
package org.beaconcomputing.bcilock

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

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
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var response: ByteArray = byteArrayOf()
    private var pending: ByteArray? = null
    private lateinit var status: TextView
    private lateinit var details: TextView
    private val prefix = "BCILOCK-V1|UNLOCK|Enterprise|".toByteArray(Charsets.US_ASCII)

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
            gen.initialize(spec)
            gen.generateKeyPair()
            ks = keystore()
        }
        return ks.getCertificate(KEY_ALIAS).publicKey.encoded
    }
    private fun fingerprint(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it) }
    private fun state(message: String) = runOnUiThread { status.text = message }
    private fun colored(text: String, size: Float, color: Int, font: String): TextView {
        val view = TextView(this)
        view.text = text; view.textSize = size; view.setTextColor(color); view.gravity = Gravity.CENTER
        view.typeface = try { Typeface.createFromAsset(assets, "fonts/$font") } catch (_: Exception) { Typeface.DEFAULT }
        return view
    }
    private fun button(label: String, action: () -> Unit): Button {
        val btn = Button(this); btn.text = label; btn.setTextColor(Color.WHITE)
        btn.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xffa33250.toInt(), 0xff4d0923.toInt(), 0xff1c0c16.toInt())).apply {
            cornerRadius = 25f; setStroke(3, 0xff38dbe8.toInt())
        }
        btn.setOnClickListener { action() }
        return btn
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 65, 28, 30); setBackgroundColor(0xff0c1219.toInt())
        }
        layout.addView(colored("BEACON COMPUTING INITIATIVE", 14f, 0xff42d9ec.toInt(), "oxanium.ttf"))
        layout.addView(colored("BCI LOCK", 42f, Color.WHITE, "oxanium.ttf"))
        layout.addView(colored("SECURE BLUETOOTH AUTHENTICATOR", 17f, 0xff42d9ec.toInt(), "rajdhani.ttf"))
        status = colored("OFFLINE — AUTHENTICATION NOT STARTED", 19f, Color.WHITE, "rajdhani.ttf")
        status.setPadding(8, 55, 8, 30); layout.addView(status)
        details = colored("Initializing Android Keystore…", 13f, 0xffb3d0d5.toInt(), "jetbrainsmono.ttf")
        details.setPadding(8, 8, 8, 24); layout.addView(details)
        layout.addView(button("START BLUETOOTH AUTHENTICATOR") { startOrRequest() })
        layout.addView(colored("Approval requires your phone's screen credential.\nNo unlock occurs without a signed challenge.",
            15f, 0xffb3d0d5.toInt(), "rajdhani.ttf"))
        layout.addView(colored("CJB-DEV-2026  |  v0.4.0", 12f, 0xff42d9ec.toInt(), "jetbrainsmono.ttf"))
        setContentView(ScrollView(this).apply { addView(layout) })
        try {
            val key = publicKey()
            details.text = "Endpoint: Enterprise\nPublic-key SHA-256:\n${fingerprint(key)}\n\nTransport: Bluetooth LE GATT"
        } catch (e: Exception) { state("KEY ERROR: ${e.javaClass.simpleName}: ${e.message}") }
    }
    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
    private fun startOrRequest() {
        if (!permitted()) {
            if (Build.VERSION.SDK_INT >= 31) requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), PERMISSION_REQUEST)
            return
        }
        startBluetooth()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == PERMISSION_REQUEST && permitted()) startBluetooth()
        else if (code == PERMISSION_REQUEST) state("NEARBY DEVICES PERMISSION DENIED")
    }
    @Suppress("MissingPermission")
    private fun startBluetooth() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) { state("TURN BLUETOOTH ON FIRST"); return }
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) { state("THIS PHONE DOES NOT SUPPORT BLE ADVERTISING"); return }
        if (server != null) { state("AUTHENTICATOR ALREADY STARTED"); return }
        val gatt = manager.openGattServer(this, callback)
        if (gatt == null) { state("GATT SERVER UNAVAILABLE"); return }
        server = gatt
        val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(PUBLIC,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        service.addCharacteristic(BluetoothGattCharacteristic(CHALLENGE,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(BluetoothGattCharacteristic(RESPONSE,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        if (!gatt.addService(service)) { state("GATT SERVICE REGISTRATION FAILED") }
        else state("STARTING GATT SERVICE…")
    }
    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { state("ADVERTISING — AWAITING LAPTOP CHALLENGE") }
        override fun onStartFailure(errorCode: Int) { state("BLE ADVERTISE ERROR: $errorCode") }
    }
    private val callback = object : BluetoothGattServerCallback() {
        @Suppress("MissingPermission")
        override fun onServiceAdded(statusCode: Int, service: BluetoothGattService?) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) { state("GATT SERVICE ERROR: $statusCode"); return }
            val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
            val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).setIncludeDeviceName(false).build()
            advertiser?.startAdvertising(settings, data, advertisingCallback)
        }
        @Suppress("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic) {
            val bytes = when (characteristic.uuid) {
                PUBLIC -> try { publicKey() } catch (_: Exception) { byteArrayOf() }
                RESPONSE -> response
                else -> byteArrayOf()
            }
            val code = if (offset <= bytes.size) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
            server?.sendResponse(device, requestId, code, offset,
                if (code == BluetoothGatt.GATT_SUCCESS) bytes.copyOfRange(offset, bytes.size) else byteArrayOf())
        }
        @Suppress("MissingPermission")
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val valid = characteristic.uuid == CHALLENGE && !preparedWrite && offset == 0 && value.size == 32
            if (responseNeeded) server?.sendResponse(device, requestId,
                if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            if (valid) {
                synchronized(this@BciBridgeActivity) { pending = value.copyOf(); response = byteArrayOf() }
                runOnUiThread {
                    state("UNLOCK REQUEST — APPROVAL REQUIRED")
                    android.app.AlertDialog.Builder(this@BciBridgeActivity)
                        .setTitle("Authorize Enterprise unlock?")
                        .setMessage("Approve this one-time laptop challenge using your phone screen credential.")
                        .setPositiveButton("Verify") { _, _ -> authorizeCredential() }
                        .setNegativeButton("Deny") { _, _ -> pending = null; state("REQUEST DENIED") }
                        .show()
                }
            }
        }
    }
    private fun authorizeCredential() {
        val guard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!guard.isDeviceSecure) { state("SET A SECURE PHONE SCREEN LOCK FIRST"); return }
        val intent = guard.createConfirmDeviceCredentialIntent("BCILock", "Authorize Enterprise unlock")
        if (intent == null) { state("SECURE CREDENTIAL PROMPT UNAVAILABLE"); return }
        startActivityForResult(intent, AUTH_REQUEST)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != AUTH_REQUEST) return
        if (resultCode != RESULT_OK) { pending = null; state("AUTHORIZATION CANCELLED"); return }
        try {
            val nonce = pending ?: return
            val signing = Signature.getInstance("SHA256withECDSA")
            val key = keystore().getKey(KEY_ALIAS, null) as java.security.PrivateKey
            signing.initSign(key); signing.update(prefix); signing.update(nonce)
            response = signing.sign()
            pending = null
            state("SIGNED APPROVAL READY — LAPTOP MAY VERIFY")
        } catch (e: Exception) { pending = null; response = byteArrayOf(); state("SIGN ERROR: ${e.javaClass.simpleName}") }
    }
    @Suppress("MissingPermission")
    override fun onDestroy() {
        try { advertiser?.stopAdvertising(advertisingCallback) } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}
