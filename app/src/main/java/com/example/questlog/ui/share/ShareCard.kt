package com.example.questlog.ui.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.questlog.R
import com.example.questlog.theme.QuestColors
import com.example.questlog.ui.format.levelTitle
import com.example.questlog.ui.progress.reclaimedLine
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.ProgressStats
import java.io.File

fun shareCaption(reclaimedAllTimeMs: Long): String =
    "I've reclaimed ${reclaimedLine(reclaimedAllTimeMs)} from my phone with QuestLog 🏰"

fun builtLine(tiles: List<CityTile>): String = "${tiles.count { it.isOwned }} of ${tiles.size} built"

/** A fixed, uneven skyline profile (fraction of max tower height), repeating for bigger realms. */
private val SKYLINE = floatArrayOf(0.62f, 1f, 0.48f, 0.78f, 0.92f, 0.4f)

fun towerHeight(index: Int): Float = SKYLINE[index % SKYLINE.size]

private const val W = 1080
private const val H = 1920

/** The 1080×1920 story card ("skyline"), drawn with the app's colour tokens and its serif face. */
fun renderShareCard(context: Context, stats: ProgressStats, tiles: List<CityTile>, c: QuestColors): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(c.ground.toArgb())
    val serif = ResourcesCompat.getFont(context, R.font.instrument_serif_regular) ?: Typeface.SERIF
    val italic = ResourcesCompat.getFont(context, R.font.instrument_serif_italic) ?: Typeface.SERIF
    fun paint(color: Int, size: Float, face: Typeface = Typeface.DEFAULT, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = face; letterSpacing = spacing; textAlign = align
        }
    val margin = 100f
    val mid = W / 2f
    val muted = c.inkMuted.toArgb()
    val center = Paint.Align.CENTER

    canvas.drawText("questlog", margin, 160f, paint(c.inkPrimary.toArgb(), 64f, serif))

    canvas.drawText("SO FAR I'VE TAKEN BACK", mid, 520f, paint(muted, 30f, spacing = 0.2f, align = center))
    canvas.drawText(reclaimedLine(stats.reclaimedAllTimeMs), mid, 720f, paint(c.earned.toArgb(), 210f, italic, align = center))
    canvas.drawText("from my phone", mid, 800f, paint(muted, 40f, align = center))

    // The realm as a skyline: built = red tower, Pro-locked = red outline, not yet built = pale grey.
    canvas.drawText("YOUR REALM · ${builtLine(tiles).uppercase()}", mid, 960f, paint(muted, 28f, spacing = 0.2f, align = center))
    val ground = 1400f
    val maxTower = 360f
    if (tiles.isNotEmpty()) {
        val gap = 28f
        val w = (W - 2 * (margin + 40f) - gap * (tiles.size - 1)) / tiles.size
        tiles.forEachIndexed { i, t ->
            val x = margin + 40f + i * (w + gap)
            val top = ground - maxTower * towerHeight(i)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            when {
                t.isOwned -> p.color = c.earned.toArgb()
                t.isPremium -> { p.color = c.locked.toArgb(); p.style = Paint.Style.STROKE; p.strokeWidth = 5f }
                else -> p.color = c.rule.toArgb()
            }
            canvas.drawRoundRect(RectF(x, top, x + w, ground), 20f, 20f, p)
        }
    }
    canvas.drawLine(margin, ground, W - margin, ground, Paint().apply { color = c.inkMuted.toArgb(); strokeWidth = 3f })
    val names = paint(muted, 28f, align = center)
    val nameLine = tiles.joinToString(" · ") { it.displayName }
    while (names.measureText(nameLine) > W - 2 * margin && names.textSize > 16f) names.textSize -= 1f
    canvas.drawText(nameLine, mid, ground + 60f, names)

    canvas.drawLine(margin, 1660f, W - margin, 1660f, Paint().apply { color = c.rule.toArgb(); strokeWidth = 2f })
    canvas.drawText("${stats.streakDays}-day streak", margin, 1730f, paint(c.inkPrimary.toArgb(), 38f))
    canvas.drawText(
        "Level ${stats.level} · ${levelTitle(stats.level)}",
        W - margin, 1730f, paint(c.inkPrimary.toArgb(), 38f, align = Paint.Align.RIGHT),
    )
    canvas.drawText("Less scrolling, more building.", margin, 1800f, paint(muted, 42f, italic))
    return bmp
}

/** Writes the card to cache/share/ and opens the share sheet. False if nothing can receive it. */
fun shareCard(context: Context, card: Bitmap, caption: String): Boolean {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "questlog-progress.png")
    file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        clipData = ClipData.newRawUri(null, uri) // lets the chooser preview the image
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
