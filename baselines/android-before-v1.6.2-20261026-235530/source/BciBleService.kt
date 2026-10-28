// BEACON COMPUTING INITIATIVE
// Component: BCILock BLE Foreground Service v1.6.2
// Developer Seal: CJB-DEV-2026
// SHA-256: see release SHA256SUMS
package org.beaconcomputing.bcilock

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import java.security.KeyStore

/** Owns BLE GATT across Activity navigation; does not grant Kali network access. */
class BciBleService : Service() {
    companion object {
        const val START = "org.beaconcomputing.bcilock.START_AUTHENTICATOR"
        const val STOP = "org.beaconcomputing.bcilock.STOP_AUTHENTICATOR"
        const val CHANNEL = "bci_ble_authenticator"
        const val NOTIFICATION_ID = 1612
    }
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var manager: BluetoothManager? = null
    private var started = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "BCILock authenticator", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun notification(message: String): Notification {
        val click = Intent(this, BciBridgeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val action = PendingIntent.getActivity(this, 0, click,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("BCILock secure authenticator")
            .setContentText(message)
            .setContentIntent(action)
            .setOngoing(true)
            .build()
    }
    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            try {
                // Android 14 connectedDevice FGS preconditions: Bluetooth permissions and declared type.
                startForeground(NOTIFICATION_ID, notification("Starting Bluetooth authenticator"))
                started = true
                openGatt()
            } catch (e: Exception) {
                BciBleLinkState.stop()
                BciBleLinkState.error("BLE service failed: ${e.javaClass.simpleName}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
    @Suppress("MissingPermission")
    private fun openGatt() {
        if (!permitted()) throw SecurityException("Bluetooth runtime permissions required")
        manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager?.adapter ?: throw IllegalStateException("Bluetooth adapter unavailable")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth adapter disabled")
        advertiser = adapter.bluetoothLeAdvertiser ?: throw IllegalStateException("BLE advertising unavailable")
        BciBleLinkState.start()
        val gatt = manager!!.openGattServer(this, callback)
            ?: throw IllegalStateException("GATT server unavailable")
        server = gatt
        val service = BluetoothGattService(BciBridgeActivity.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(BciBridgeActivity.PUBLIC,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        service.addCharacteristic(BluetoothGattCharacteristic(BciBridgeActivity.CHALLENGE,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        service.addCharacteristic(BluetoothGattCharacteristic(BciBridgeActivity.RESPONSE,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        if (!gatt.addService(service)) throw IllegalStateException("GATT service registration failed")
    }
    private fun publicKey(): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getCertificate(BciBridgeActivity.KEY_ALIAS)?.publicKey?.encoded
            ?: throw IllegalStateException("Authenticator key not initialized")
    }
    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            BciBleLinkState.advertising(true)
            updateNotification("Advertising; awaiting Kali BLE connection")
        }
        override fun onStartFailure(errorCode: Int) {
            BciBleLinkState.error("BLE advertising error: $errorCode")
            updateNotification("BLE advertising failed ($errorCode)")
        }
    }
    private val callback = object : BluetoothGattServerCallback() {
        @Suppress("MissingPermission")
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                BciBleLinkState.error("GATT registration failed ($status)")
                stopSelf()
                return
            }
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
            val payload = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(BciBridgeActivity.SERVICE))
                .setIncludeDeviceName(false).build()
            advertiser?.startAdvertising(settings, payload, advertisingCallback)
        }
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                if (!BciBleLinkState.connected(device.address)) {
                    server?.cancelConnection(device)
                } else updateNotification("BLE connected — identity not yet verified")
            } else {
                BciBleLinkState.disconnected(device.address)
                updateNotification("Waiting for Bluetooth endpoint")
            }
        }
        @Suppress("MissingPermission")
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                                                  characteristic: BluetoothGattCharacteristic) {
            val bytes = if (BciBleLinkState.isPeer(device.address)) {
                when (characteristic.uuid) {
                    BciBridgeActivity.PUBLIC -> try { publicKey() } catch (_: Exception) { null }
                    BciBridgeActivity.RESPONSE -> BciBleLinkState.responseFor(device.address)
                    else -> null
                }
            } else null
            val ok = bytes != null && offset >= 0 && offset <= bytes.size
            server?.sendResponse(device, requestId,
                if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                offset, if (ok) bytes!!.copyOfRange(offset, bytes.size) else byteArrayOf())
        }
        @Suppress("MissingPermission")
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val valid = characteristic.uuid == BciBridgeActivity.CHALLENGE &&
                !preparedWrite && offset == 0 && value.size == 32 &&
                BciBleLinkState.receive(device.address, value)
            if (responseNeeded) server?.sendResponse(device, requestId,
                if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            if (valid) updateNotification("Signed challenge awaiting phone approval — tap to open")
        }
    }
    @Suppress("MissingPermission")
    override fun onDestroy() {
        try { advertiser?.stopAdvertising(advertisingCallback) } catch (_: Exception) { }
        try { server?.close() } catch (_: Exception) { }
        server = null; advertiser = null
        BciBleLinkState.stop()
        super.onDestroy()
    }
}
