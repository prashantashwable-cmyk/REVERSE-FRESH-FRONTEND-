package com.aiec.elevatoros.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.aiec.elevatoros.domain.EvidenceRef
import com.aiec.elevatoros.domain.GeoPoint
import com.aiec.elevatoros.domain.Hash
import com.aiec.elevatoros.domain.Ids
import com.aiec.elevatoros.domain.Time
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Evidence photos and signatures live in app-private storage (files/evidence), never in the public gallery.
 * Each photo gets a stamp band (date/time, GPS with accuracy or "—", person, job, company), is reduced to at most
 * 1600 px, and is identified by the SHA-256 of the stored bytes. A stamp helps; it does not prove presence.
 */
class EvidenceStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "evidence").apply { mkdirs() }
    val captureDir get() = File(context.filesDir, "capture").apply { mkdirs() }

    fun newCaptureFile(): File = File(captureDir, "cap_${System.currentTimeMillis()}.jpg")
    fun file(id: String): File? = listOf("$id.jpg", "$id.png").map { File(dir, it) }.firstOrNull { it.exists() }

    fun savePhoto(raw: File, tag: String, gps: GeoPoint?, stampLines: List<String>): EvidenceRef? {
        val bmp = decodeUpright(raw) ?: return null
        val stamped = stamp(bmp, stampLines)
        val out = ByteArrayOutputStream()
        stamped.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val bytes = out.toByteArray()
        val id = "EV" + Ids.rid(12)
        File(dir, "$id.jpg").writeBytes(bytes)
        raw.delete()
        return EvidenceRef(id, "photo", Hash.sha256(bytes), Time.now(), gps, tag)
    }

    fun saveSignature(bmp: Bitmap): EvidenceRef {
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray(); val id = "SG" + Ids.rid(12)
        File(dir, "$id.png").writeBytes(bytes)
        return EvidenceRef(id, "sign", Hash.sha256(bytes), Time.now())
    }

    /** Unused captures (camera cancelled, app killed mid-capture) are removed after a day. */
    fun cleanup(now: Long = System.currentTimeMillis()) {
        captureDir.listFiles()?.filter { now - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
    }

    private fun decodeUpright(f: File): Bitmap? {
        if (!f.exists() || f.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = when (runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
        val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(rot); postScale(scale, scale) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun stamp(src: Bitmap, lines: List<String>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val size = maxOf(16f, out.width / 42f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = size; typeface = Typeface.DEFAULT_BOLD }
        val band = (lines.size + 0.6f) * size * 1.25f
        c.drawRect(0f, out.height - band, out.width.toFloat(), out.height.toFloat(), Paint().apply { color = Color.argb(170, 0, 0, 0) })
        lines.forEachIndexed { i, s -> c.drawText(s, size * 0.6f, out.height - band + size * 1.25f * (i + 1), p) }
        return out
    }

    companion object {
        private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(Time.IST)
        fun stampLines(person: String, role: String, job: String, company: String, gps: GeoPoint?, dev: String): List<String> = listOf(
            FMT.format(Instant.now()) + " IST · " + (gps?.let { "%.5f, %.5f ±%dm".format(it.lat, it.lon, (it.acc ?: 0.0).toInt()) } ?: "GPS —"),
            "$person ($role) · $job",
            "$company · $dev"
        )
    }
}
