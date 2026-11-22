package de.vereinskasse

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.vereinskasse.logik.LogoSpeicher
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

fun bildDekodieren(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** Logos als Dateien in einem Ordner. */
class DateiLogoSpeicher(private val ordner: File) : LogoSpeicher {
    private fun datei(id: String) = File(ordner, id.filter { it.isLetterOrDigit() } + ".img")
    override fun laden(id: String) = datei(id).takeIf { it.exists() }?.readBytes()
    override fun speichern(id: String, bytes: ByteArray) {
        ordner.mkdirs()
        datei(id).writeBytes(bytes)
    }
    override fun loeschen(id: String) {
        datei(id).delete()
    }
    override fun alle() = ordner.listFiles()?.filter { it.name.endsWith(".img") }?.map { it.name.removeSuffix(".img") } ?: emptyList()
    override fun dekodieren(bytes: ByteArray) = bildDekodieren(bytes)
}

/** Verkleinert auf max. 512 px; PNG bei Transparenz, sonst JPEG. */
fun bildVerkleinern(datei: File): ByteArray? {
    val bild = ImageIO.read(datei) ?: return null
    val faktor = minOf(1.0, 512.0 / maxOf(bild.width, bild.height))
    val b = maxOf(1, (bild.width * faktor).toInt())
    val h = maxOf(1, (bild.height * faktor).toInt())
    val transparent = bild.colorModel.hasAlpha()
    val ziel = BufferedImage(b, h, if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    ziel.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        drawImage(bild, 0, 0, b, h, null)
        dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(ziel, if (transparent) "png" else "jpg", it) }.toByteArray()
}
