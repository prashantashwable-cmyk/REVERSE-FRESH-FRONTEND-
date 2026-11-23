package com.aiec.elevatoros.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.aiec.elevatoros.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import kotlin.coroutines.resume

/** Result of asking for the phone's position. Unavailable or stale positions are never reported as verified. */
sealed class GpsResult {
    data class Fix(val point: GeoPoint, val approximate: Boolean) : GpsResult()
    data object Denied : GpsResult()
    data object Disabled : GpsResult()
    data object Timeout : GpsResult()
}

/**
 * GPS without Google Play services (works on every Android phone): one fresh fix with accuracy and time,
 * or the last known fix only if it is under 2 minutes old.
 */
class Gps(private val context: Context) {
    fun hasPermission(): Boolean = fine() || coarse()
    private fun fine() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun coarse() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 20_000): GpsResult {
        if (!hasPermission()) return GpsResult.Denied
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return GpsResult.Disabled
        val approximate = !fine()
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 }.minByOrNull { it.accuracy }
        val fresh = withTimeoutOrNull(timeoutMs) { single(lm, providers.first()) }
        val loc = fresh ?: recent ?: return GpsResult.Timeout
        return GpsResult.Fix(GeoPoint(loc.latitude, loc.longitude, loc.accuracy.toDouble(), Instant.ofEpochMilli(loc.time).toString()), approximate)
    }

    @SuppressLint("MissingPermission")
    private suspend fun single(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = android.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
        } else {
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) { lm.removeUpdates(this); if (cont.isActive) cont.resume(location) }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            cont.invokeOnCancellation { lm.removeUpdates(l) }
            @Suppress("DEPRECATION") lm.requestSingleUpdate(provider, l, Looper.getMainLooper())
        }
    }
}

/** Reads screens aloud in the chosen language (🔊), offline when the phone has the voice installed. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS }
    fun say(text: String, lang: String) {
        if (!ready) return
        tts.language = when (lang) { "hi" -> Locale("hi", "IN"); "mr" -> Locale("mr", "IN"); else -> Locale("en", "IN") }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aiec")
    }
    fun shutdown() = tts.shutdown()
}
