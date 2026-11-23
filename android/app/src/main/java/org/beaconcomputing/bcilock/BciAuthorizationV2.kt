// BEACON COMPUTING INITIATIVE
// Developer Seal: CJB-DEV-2026
// Component: BCILock v1.6.3 Android Authorization V2 Signer
// SHA-256: see SHA256SUMS
// Mode: DRY_RUN_ONLY / egress.preview / NO FIREWALL CONTROL
package org.beaconcomputing.bcilock

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

/**
 * V2 is a distinct authorization protocol. Never sign legacy UNLOCK bytes as V2.
 * Integrator MUST display scope, target, and expiry; obtain explicit confirmation;
 * and invoke Android's credential prompt before calling signApprovedPreview().
 * The existing AndroidKeyStore alias is reused so the enrolled public key remains valid.
 * This helper does NOT add a GATT characteristic, receive requests, or change egress.
 */
object BciAuthorizationV2 {
    const val PROTOCOL = "BCILOCK-AUTH-V2"
    const val SCOPE = "egress.preview"
    const val TARGET = "Enterprise"
    const val KEY_ALIAS = "BCILock-v040-Enterprise"
    private val DOMAIN = byteArrayOf(66, 67, 73, 76, 79, 67, 75, 45, 65, 85, 84, 72, 45, 86, 50, 0)
    private val REQUIRED = setOf("protocol", "scope", "target", "key_sha256", "nonce", "issued_at", "expires_at")
    private val HEX64 = Regex("[0-9a-f]{64}")

    data class Preview(val target: String, val scope: String, val expiresAt: Long)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun digest(data: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Parse for UI display; reject malformed, inappropriate or stale requests. */
    fun inspect(requestJson: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Preview {
        require(requestJson.length <= 4096) { "Request too large" }
        val req = JSONObject(requestJson)
        require(req.keys().asSequence().toSet() == REQUIRED) { "Unexpected request schema" }
        require(req.getString("protocol") == PROTOCOL && req.getString("scope") == SCOPE) { "Unsupported operation" }
        require(req.getString("target") == TARGET) { "Wrong endpoint" }
        val nonce = req.getString("nonce")
        val keyHash = req.getString("key_sha256")
        require(HEX64.matches(nonce) && HEX64.matches(keyHash)) { "Invalid hex value" }
        val ks = keyStore()
        val cert = ks.getCertificate(KEY_ALIAS) ?: error("No enrolled signing identity")
        require(digest(cert.publicKey.encoded) == keyHash) { "Key binding mismatch" }
        // Reject floating, quoted, or scientific-notation timestamps.
        val issuedValue = req.get("issued_at")
        val expiryValue = req.get("expires_at")
        require(issuedValue is Int || issuedValue is Long) { "Invalid issue time" }
        require(expiryValue is Int || expiryValue is Long) { "Invalid expiry time" }
        val issued = (issuedValue as Number).toLong()
        val expiry = (expiryValue as Number).toLong()
        require(issued <= nowSeconds && expiry > nowSeconds && expiry > issued && expiry - issued <= 60) {
            "Expired, future-dated, or excessive-duration request"
        }
        return Preview(TARGET, SCOPE, expiry)
    }

    /** Byte-for-byte match with Python: json.dumps(sort_keys=True,separators=(',',':'),ensure_ascii=True). */
    fun canonical(requestJson: String): ByteArray {
        val req = JSONObject(requestJson)
        // Inspect first to validate inputs before canonicalization. These fields are restricted to
        // the ASCII constants or lowercase hex; no arbitrary Unicode or escaped user text exists.
        inspect(requestJson)
        val fields = listOf(
            "\"expires_at\":" + req.getLong("expires_at"),
            "\"issued_at\":" + req.getLong("issued_at"),
            "\"key_sha256\":\"" + req.getString("key_sha256") + "\"",
            "\"nonce\":\"" + req.getString("nonce") + "\"",
            "\"protocol\":\"" + PROTOCOL + "\"",
            "\"scope\":\"" + SCOPE + "\"",
            "\"target\":\"" + TARGET + "\""
        )
        return ("{" + fields.joinToString(",") + "}").toByteArray(StandardCharsets.US_ASCII)
    }

    /**
     * Call only after explicit UI consent AND successful device credential confirmation.
     * Keystore enforces an independent recent-authentication requirement on this key.
     * Returns base64 of ASN.1 DER ECDSA(P-256, SHA-256) signature, not an access token.
     */
    fun signApprovedPreview(requestJson: String, userConfirmed: Boolean): String {
        require(userConfirmed) { "User did not approve preview" }
        val body = canonical(requestJson)
        val key = keyStore().getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            ?: error("AndroidKeyStore signing key missing")
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(key)
        signature.update(DOMAIN)
        signature.update(body)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
}
