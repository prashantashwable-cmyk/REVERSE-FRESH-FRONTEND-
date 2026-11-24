package com.aiec.elevatoros.device

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.aiec.elevatoros.domain.Signing
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * This phone's signing key (ECDSA P-256). Normally created inside the Android Keystore, where the private key can
 * never be read out of the phone. Where no Keystore exists (unit tests on a computer), a software key is kept in
 * app-private storage instead.
 */
class DeviceKey(private val context: Context) {
    private val alias = "aiec-device-key"
    val private: PrivateKey
    val public: PublicKey
    val hardwareBacked: Boolean

    init {
        // Android Keystore first. Some phones fail to make or use an EC key there (seen on older Android versions and
        // some makers): then a software key in app-private storage is used instead of stopping the app.
        val fromKeystore = runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(alias)) {
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                    initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).build())
                }.generateKeyPair()
            }
            val priv = ks.getKey(alias, null) as PrivateKey
            val pub = ks.getCertificate(alias)!!.publicKey
            // prove the key really signs and verifies on this phone before trusting it
            val probe = "aiec".toByteArray()
            val sig = java.security.Signature.getInstance("SHA256withECDSA").apply { initSign(priv); update(probe) }.sign()
            check(java.security.Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(probe) }.verify(sig))
            priv to pub
        }.getOrNull()
        if (fromKeystore != null && !File(context.filesDir, "devkey.bin").exists()) {
            private = fromKeystore.first
            public = fromKeystore.second
            hardwareBacked = true
        } else {
            val f = File(context.filesDir, "devkey.bin")
            val pubF = File(context.filesDir, "devkey.pub")
            if (!f.exists() || !pubF.exists()) {
                val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
                f.writeBytes(kp.private.encoded); pubF.writeBytes(kp.public.encoded)
            }
            val kf = KeyFactory.getInstance("EC")
            private = kf.generatePrivate(PKCS8EncodedKeySpec(f.readBytes()))
            public = kf.generatePublic(X509EncodedKeySpec(pubF.readBytes()))
            hardwareBacked = false
        }
    }

    val publicB64: String get() = Base64.getEncoder().encodeToString(public.encoded)
    val kid: String get() = Signing.kidOf(publicB64)
    val own: Signing.Own get() = Signing.Own(kid, public)
    val deviceName: String get() = (Build.MANUFACTURER + " " + Build.MODEL).trim()
}
