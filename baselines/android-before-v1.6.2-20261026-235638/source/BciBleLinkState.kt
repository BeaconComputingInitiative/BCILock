// BEACON COMPUTING INITIATIVE
// Component: BCILock BLE Link State v1.6.2
// Developer Seal: CJB-DEV-2026
// SHA-256: see release SHA256SUMS
package org.beaconcomputing.bcilock

import android.os.SystemClock

/** Process-local, synchronized observation state. Never equate a GATT link with Kali identity. */
object BciBleLinkState {
    data class Snapshot(
        val running: Boolean,
        val advertising: Boolean,
        val connected: Boolean,
        val challengePending: Boolean,
        val approvalInProgress: Boolean,
        val signedResponseReady: Boolean,
        val label: String
    )
    data class Challenge(val epoch: Long, val nonce: ByteArray)
    private const val CHALLENGE_TTL_MS = 40_000L
    private const val RESPONSE_TTL_MS = 40_000L
    private var active = false
    private var advertising = false
    private var peer: String? = null
    private var nonce: ByteArray? = null
    private var response: ByteArray = byteArrayOf()
    private var expiresAt = 0L
    private var responseExpiresAt = 0L
    private var epoch = 0L
    private var busy = false
    private var message = "Authenticator stopped"

    @Synchronized fun start() { active = true; advertising = false; message = "Starting GATT service" }
    @Synchronized fun advertising(value: Boolean) {
        if (!active) return
        advertising = value
        message = if (value) "Advertising; awaiting GATT connection" else "Advertising unavailable"
    }
    @Synchronized fun connected(address: String): Boolean {
        if (!active) return false
        if (peer != null && peer != address) return false
        peer = address
        message = "BLE peer connected; endpoint identity unverified"
        return true
    }
    @Synchronized fun isPeer(address: String): Boolean = active && peer == address
    @Synchronized fun disconnected(address: String) {
        if (peer == address) {
            peer = null; clearChallenge(); message = "BLE peer disconnected"
        }
    }
    private fun clearChallenge() {
        epoch += 1; nonce = null; response = byteArrayOf(); expiresAt = 0L
        responseExpiresAt = 0L; busy = false
    }
    @Synchronized fun stop() {
        active = false; advertising = false; peer = null; clearChallenge()
        message = "Authenticator stopped"
    }
    @Synchronized fun error(reason: String) {
        message = reason
        advertising = false
    }
    @Synchronized fun receive(address: String, incoming: ByteArray): Boolean {
        if (!isPeer(address) || incoming.size != 32) return false
        clearChallenge()
        nonce = incoming.copyOf()
        expiresAt = SystemClock.elapsedRealtime() + CHALLENGE_TTL_MS
        message = "Challenge received; credential approval required"
        return true
    }
    private fun expireIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (nonce != null && now >= expiresAt) {
            clearChallenge(); message = "Challenge expired; request a new one"
        }
        if (response.isNotEmpty() && now >= responseExpiresAt) response = byteArrayOf()
    }
    @Synchronized fun snapshot(): Snapshot {
        expireIfNeeded()
        return Snapshot(active, advertising, peer != null, nonce != null, busy,
            response.isNotEmpty(), message)
    }
    @Synchronized fun claim(): Challenge? {
        expireIfNeeded()
        val n = nonce ?: return null
        if (peer == null || busy) return null
        busy = true
        return Challenge(epoch, n.copyOf())
    }
    @Synchronized fun deny(requestEpoch: Long) {
        if (requestEpoch == epoch) { clearChallenge(); message = "Challenge denied" }
    }
    @Synchronized fun cancelClaim(requestEpoch: Long) {
        if (requestEpoch == epoch) { clearChallenge(); message = "Approval cancelled" }
    }
    @Synchronized fun signed(request: Challenge, signature: ByteArray): Boolean {
        expireIfNeeded()
        val n = nonce ?: return false
        if (peer == null || !busy || request.epoch != epoch ||
            !n.contentEquals(request.nonce) || signature.isEmpty()) return false
        response = signature.copyOf()
        responseExpiresAt = SystemClock.elapsedRealtime() + RESPONSE_TTL_MS
        nonce = null; busy = false
        message = "Signature ready; Kali verification not yet acknowledged"
        return true
    }
    @Synchronized fun responseFor(address: String): ByteArray {
        expireIfNeeded()
        return if (isPeer(address)) response.copyOf() else byteArrayOf()
    }
}
