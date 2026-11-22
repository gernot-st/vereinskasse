package de.vereinskasse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.vereinskasse.logik.LogoSpeicher
import java.io.ByteArrayOutputStream
import java.io.File

/** Logos als Dateien im privaten App-Speicher. */
class AndroidLogoSpeicher(context: Context) : LogoSpeicher {
    private val ordner = File(context.filesDir, "logos")
    private fun datei(id: String) = File(ordner, id.filter { it.isLetterOrDigit() } + ".img")

    override fun laden(id: String) = datei(id).takeIf { it.exists() }?.readBytes()
    override fun speichern(id: String, bytes: ByteArray) {
        ordner.mkdirs()
        val tmp = File(ordner, "$id.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(datei(id))
    }
    override fun loeschen(id: String) {
        datei(id).delete()
    }
    override fun alle() = ordner.listFiles()?.filter { it.name.endsWith(".img") }?.map { it.name.removeSuffix(".img") } ?: emptyList()
    override fun dekodieren(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

private const val MAX_KANTE = 512

/**
 * Verkleinert ein gewähltes Bild auf max. 512 px, dreht es nach EXIF (Handyfotos)
 * und speichert es als PNG (bei Transparenz) oder JPEG.
 */
fun bildVerkleinern(bytes: ByteArray): ByteArray? {
    val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, masse)
    if (masse.outWidth <= 0 || masse.outHeight <= 0) return null
    var stichprobe = 1
    while (maxOf(masse.outWidth, masse.outHeight) / (stichprobe * 2) >= MAX_KANTE) stichprobe *= 2
    var bild = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = stichprobe })
        ?: return null

    val drehung = try {
        when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }
    val faktor = minOf(1f, MAX_KANTE.toFloat() / maxOf(bild.width, bild.height))
    if (faktor < 1f || drehung != 0f) {
        val matrix = Matrix().apply {
            postScale(faktor, faktor)
            postRotate(drehung)
        }
        bild = Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, matrix, true)
    }

    val transparent = bild.hasAlpha() && run {
        val pixel = IntArray(bild.width * bild.height)
        bild.getPixels(pixel, 0, bild.width, 0, 0, bild.width, bild.height)
        pixel.any { (it ushr 24) < 255 }
    }
    return ByteArrayOutputStream().also {
        bild.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, it)
    }.toByteArray()
}
