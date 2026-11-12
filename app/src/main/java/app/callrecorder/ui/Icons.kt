package app.callrecorder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few icons not in material-icons-core, drawn here to avoid the 30 MB extended icon library. */
object AppIcons {
    private fun icon(name: String, evenOdd: Boolean = false, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) androidx.compose.ui.graphics.PathFillType.EvenOdd else androidx.compose.ui.graphics.PathFillType.NonZero,
            pathBuilder = block,
        ).build()

    val Pause = icon("Pause") {
        moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
    }

    val Back10 = icon("Back10") {
        // circular arrow + "10" suggestion kept simple: a left chevron pair
        moveTo(11f, 18f); lineTo(5f, 12f); lineTo(11f, 6f); lineTo(12.4f, 7.4f); lineTo(7.8f, 12f); lineTo(12.4f, 16.6f); close()
        moveTo(18f, 18f); lineTo(12f, 12f); lineTo(18f, 6f); lineTo(19.4f, 7.4f); lineTo(14.8f, 12f); lineTo(19.4f, 16.6f); close()
    }

    val Fwd10 = icon("Fwd10") {
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f); lineTo(11.6f, 16.6f); lineTo(16.2f, 12f); lineTo(11.6f, 7.4f); close()
        moveTo(6f, 6f); lineTo(12f, 12f); lineTo(6f, 18f); lineTo(4.6f, 16.6f); lineTo(9.2f, 12f); lineTo(4.6f, 7.4f); close()
    }

    val Folder = icon("Folder") {
        moveTo(10f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); lineTo(2f, 18f)
        curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
        lineTo(22f, 8f); curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f); lineTo(12f, 6f); close()
    }

    val Mic = icon("Mic") {
        moveTo(12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f); lineTo(15f, 5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f); curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f); lineTo(9f, 11f)
        curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
        moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f); curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
        lineTo(5f, 11f); curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f); lineTo(11f, 21f); lineTo(13f, 21f)
        lineTo(13f, 17.72f); curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f); close()
    }

    val StarOutline = icon("StarOutline", evenOdd = true) {
        moveTo(22f, 9.24f); lineTo(14.81f, 8.62f); lineTo(12f, 2f); lineTo(9.19f, 8.63f); lineTo(2f, 9.24f)
        lineTo(7.46f, 13.97f); lineTo(5.82f, 21f); lineTo(12f, 17.27f); lineTo(18.18f, 21f); lineTo(16.55f, 13.97f); close()
        moveTo(12f, 15.4f); lineTo(8.24f, 17.67f); lineTo(9.24f, 13.39f); lineTo(5.92f, 10.51f); lineTo(10.3f, 10.13f)
        lineTo(12f, 6.1f); lineTo(13.71f, 10.14f); lineTo(18.09f, 10.52f); lineTo(14.77f, 13.4f); lineTo(15.77f, 17.68f); close()
    }
}
