// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
// Component: BCILock Stage 2 V2 BLE Preview State (NO FIREWALL ENFORCEMENT)
// SHA-256: docs/BCILock-v163-STAGE2-SHA256SUMS
package org.beaconcomputing.bcilock

import android.os.SystemClock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

/**
 * Separate V2 transport: V1 challenge state is never overwritten.
 * All GATT writes are limited to 20 bytes, avoiding assumptions about MTU.
 * Each signed preview is single-peer, ephemeral, scope-bound and user-approved.
 * WARNING: BLE peer presence is NOT authentication of the computer.
 */
object BciBleV2State {
    const val FRAME_START: Int = 0x01
    const val FRAME_NEXT: Int = 0x02
    private const val MAX_JSON = 1024
    private const val MAX_FRAME = 20
    private const val ASSEMBLY_TTL_MS = 12_000L
    private const val RESPONSE_TTL_MS = 35_000L
    private const val REPLAY_CACHE_TTL_MS = 300_000L

    data class Pending(
        val epoch: Long,
        val peer: String,
        val json: String,
        val nonce: String,
        val scope: String,
        val target: String,
        val expiresAt: Long
    )

    data class Snapshot(
        val pending: Pending?,
        val approving: Boolean,
        val signedResponseReady: Boolean,
        val message: String
    )

    private var epoch = 0L
    private var request: Pending? = null
    private var approvalInProgress = false
    private var response = byteArrayOf()
    private var responseExpiresAt = 0L
    private var assembly: ByteArrayOutputStream? = null
    private var assemblyPeer: String? = null
    private var assemblyExpected = 0
    private var assemblyNextSequence = 1
    private var assemblyStartedAt = 0L
    private var message = "V2 preview idle"
    private val recentNonces = LinkedHashMap<String, Long>()

    private fun resetAssembly() {
        assembly = null
        assemblyPeer = null
        assemblyExpected = 0
        assemblyNextSequence = 1
        assemblyStartedAt = 0L
    }

    private fun resetCurrent() {
        epoch++
        request = null
        approvalInProgress = false
        response = byteArrayOf()
        responseExpiresAt = 0L
        resetAssembly()
    }

    private fun expire() {
        val now = SystemClock.elapsedRealtime()
        if (assembly != null && now - assemblyStartedAt >= ASSEMBLY_TTL_MS) resetAssembly()
        if (request != null && System.currentTimeMillis() / 1000 >= request!!.expiresAt) {
            resetCurrent()
            message = "V2 request expired"
        }
        if (response.isNotEmpty() && now >= responseExpiresAt) {
            resetCurrent()
            message = "V2 response expired"
        }
        val iter = recentNonces.entries.iterator()
        while (iter.hasNext()) {
            if (now - iter.next().value > REPLAY_CACHE_TTL_MS) iter.remove()
        }
    }

    @Synchronized fun start() {
        resetCurrent()
        message = "V2 preview idle"
    }

    @Synchronized fun stop() {
        resetCurrent()
        message = "V2 preview stopped"
    }

    @Synchronized fun disconnected(address: String) {
        if (request?.peer == address || assemblyPeer == address || response.isNotEmpty()) {
            resetCurrent()
            message = "V2 BLE link disconnected"
        }
    }

    /**
     * Framing:
     * START = 0x01, uint16 big-endian total length, <=17 bytes of JSON
     * NEXT  = 0x02, uint16 big-endian contiguous sequence (1,2...), <=17 bytes.
     * Each write must be acknowledged by Android before the next write.
     */
    @Synchronized fun receive(address: String, frame: ByteArray): Boolean {
        expire()
        if (!BciBleLinkState.isPeer(address) || frame.size !in 4..MAX_FRAME ||
            request != null || approvalInProgress || response.isNotEmpty()) return false

        val marker = frame[0].toInt() and 0xff
        val value = ((frame[1].toInt() and 0xff) shl 8) or (frame[2].toInt() and 0xff)

        if (marker == FRAME_START) {
            if (value !in 64..MAX_JSON) return false
            resetAssembly()
            assembly = ByteArrayOutputStream(value)
            assemblyPeer = address
            assemblyExpected = value
            assemblyStartedAt = SystemClock.elapsedRealtime()
        } else if (marker == FRAME_NEXT) {
            if (assembly == null || assemblyPeer != address ||
                value != assemblyNextSequence || value == 0xffff) {
                resetAssembly()
                return false
            }
            assemblyNextSequence++
        } else {
            resetAssembly()
            return false
        }

        val buffer = assembly ?: return false
        val dataSize = frame.size - 3
        if (buffer.size() + dataSize > assemblyExpected) {
            resetAssembly()
            return false
        }
        buffer.write(frame, 3, dataSize)
        if (buffer.size() < assemblyExpected) return true

        val raw = buffer.toByteArray()
        resetAssembly()
        if (raw.any { it.toInt() !in 0x20..0x7e }) return false
        return try {
            val json = String(raw, StandardCharsets.US_ASCII)
            // Reject duplicate keys, overlong or noncanonical representations.
            if (!raw.contentEquals(BciAuthorizationV2.canonical(json))) return false
            val preview = BciAuthorizationV2.inspect(json)
            val nonce = JSONObject(json).getString("nonce")
            if (recentNonces.containsKey(nonce)) return false
            recentNonces[nonce] = SystemClock.elapsedRealtime()
            if (recentNonces.size > 128) recentNonces.remove(recentNonces.keys.first())
            epoch++
            request = Pending(epoch, address, json, nonce,
                preview.scope, preview.target, preview.expiresAt)
            message = "V2 preview received: approval REQUIRED"
            true
        } catch (_: Exception) {
            message = "V2 request rejected"
            false
        }
    }

    @Synchronized fun snapshot(): Snapshot {
        expire()
        return Snapshot(request, approvalInProgress, response.isNotEmpty(), message)
    }

    @Synchronized fun claim(): Pending? {
        expire()
        val p = request ?: return null
        if (approvalInProgress || !BciBleLinkState.isPeer(p.peer)) return null
        approvalInProgress = true
        return p.copy()
    }

    @Synchronized fun deny(requestEpoch: Long) {
        if (request?.epoch == requestEpoch) {
            resetCurrent()
            message = "V2 preview denied"
        }
    }

    @Synchronized fun cancelClaim(requestEpoch: Long) {
        if (request?.epoch == requestEpoch) {
            resetCurrent()
            message = "V2 approval cancelled"
        }
    }

    @Synchronized fun signed(p: Pending, signatureDer: ByteArray): Boolean {
        expire()
        val current = request ?: return false
        if (!approvalInProgress || p.epoch != current.epoch ||
            p.peer != current.peer || p.nonce != current.nonce ||
            !BciBleLinkState.isPeer(current.peer) ||
            signatureDer.size !in 64..80) return false
        response = signatureDer.copyOf()
        responseExpiresAt = SystemClock.elapsedRealtime() + RESPONSE_TTL_MS
        request = null
        approvalInProgress = false
        message = "V2 preview signed — external verification required"
        return true
    }

    @Synchronized fun responseFor(address: String): ByteArray {
        expire()
        return if (BciBleLinkState.isPeer(address)) response.copyOf() else byteArrayOf()
    }
}
