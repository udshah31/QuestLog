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

fun builtLine(tiles: List<CityTile>): String = "${tiles.count { it.isOwned }}/${tiles.size} built"

private const val SIZE = 1080

/** The 1080×1080 share card, drawn with the app's colour tokens and its serif face. */
fun renderShareCard(context: Context, stats: ProgressStats, tiles: List<CityTile>, c: QuestColors): Bitmap {
    val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(c.ground.toArgb())
    val serif = ResourcesCompat.getFont(context, R.font.instrument_serif_regular) ?: Typeface.SERIF
    val italic = ResourcesCompat.getFont(context, R.font.instrument_serif_italic) ?: Typeface.SERIF
    fun paint(color: Int, size: Float, face: Typeface = Typeface.DEFAULT, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = face; letterSpacing = spacing
        }
    val left = 96f

    canvas.drawText("questlog", left, 170f, paint(c.inkPrimary.toArgb(), 64f, serif))
    canvas.drawText("RECLAIMED ALL-TIME", left, 380f, paint(c.inkMuted.toArgb(), 30f, spacing = 0.2f))
    canvas.drawText(reclaimedLine(stats.reclaimedAllTimeMs), left, 540f, paint(c.earned.toArgb(), 170f, italic))
    canvas.drawText(
        "${stats.streakDays}-day streak · Level ${stats.level} · ${levelTitle(stats.level)}",
        left, 640f, paint(c.inkPrimary.toArgb(), 40f),
    )

    // The realm, as Today's strip: built = red bar, Pro-locked = outline, the rest = rule grey.
    canvas.drawText("YOUR REALM · ${builtLine(tiles).uppercase()}", left, 790f, paint(c.inkMuted.toArgb(), 30f, spacing = 0.2f))
    if (tiles.isNotEmpty()) {
        val gap = 16f
        val w = (SIZE - 2 * left - gap * (tiles.size - 1)) / tiles.size
        tiles.forEachIndexed { i, t ->
            val x = left + i * (w + gap)
            val r = RectF(x, 830f, x + w, 854f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            when {
                t.isOwned -> p.color = c.earned.toArgb()
                t.isPremium -> { p.color = c.locked.toArgb(); p.style = Paint.Style.STROKE; p.strokeWidth = 4f }
                else -> p.color = c.rule.toArgb()
            }
            canvas.drawRoundRect(r, 12f, 12f, p)
        }
    }

    canvas.drawText("Less scrolling, more building.", left, SIZE - 110f, paint(c.inkMuted.toArgb(), 44f, italic))
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
