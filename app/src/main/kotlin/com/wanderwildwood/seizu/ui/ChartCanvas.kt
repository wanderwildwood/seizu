package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwildwood.seizu.sky.BodyKind
import com.wanderwildwood.seizu.sky.CARDINALS
import com.wanderwildwood.seizu.sky.LineKind
import com.wanderwildwood.seizu.sky.Layers
import com.wanderwildwood.seizu.sky.Projection
import com.wanderwildwood.seizu.sky.Scene
import com.wanderwildwood.seizu.sky.SkyObject
import com.wanderwildwood.seizu.sky.SkyStar
import com.wanderwildwood.seizu.sky.starRadius
import kotlin.math.hypot

/**
 * The sky, drawn as a printed star atlas rather than as a photograph of the night.
 *
 * Black marks on white paper. Every screen planisphere is white stars on a black field,
 * because it is imitating the sky; that is exactly wrong here. A full-screen black field
 * on E Ink is the slowest, most ghost-prone thing the panel can be asked to hold, it
 * cannot be read in the daylight this phone is otherwise good in, and it would drown
 * every mark that has to sit on top of it. Paper charts have been black-on-white for four
 * hundred years and they are read outdoors by torchlight, which is the situation this is
 * actually for.
 */
@Composable
fun ChartCanvas(
    scene: Scene,
    layers: Layers,
    facing: Int,
    zoom: Float,
    onSelect: (SkyObject?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(scene, facing, zoom) {
                detectTapGestures { tap ->
                    val projection = projectionFor(size.width.toFloat(), size.height.toFloat(), zoom, facing)
                    onSelect(nearest(scene, projection, tap, with(density) { 24.dp.toPx() }))
                }
            },
    ) {
        val projection = projectionFor(size.width, size.height, zoom, facing)

        drawHorizon(projection, measurer)

        scene.lines.forEach { line ->
            drawSkyLine(line.points, line.kind, projection)
        }

        scene.stars.forEach { star ->
            val (x, y) = projection.project(star.azimuth, star.elevation)
            val radius = starRadius(star.magnitude, 2.dp.toPx())
            drawCircle(Color.Black, radius = radius, center = Offset(x, y))
            if (layers.starNames && star.label != null) {
                label(measurer, star.label, x + radius + 3f, y, 9.sp.value)
            }
        }

        scene.bodies.forEach { body ->
            val (x, y) = projection.project(body.azimuth, body.elevation)
            val radius = starRadius(body.magnitude, 2.dp.toPx()).coerceAtMost(14.dp.toPx())
            // A ring, not a disc, so a planet is never mistaken for a bright star and so
            // the Sun is not a coin of solid black in the middle of the chart.
            drawCircle(
                Color.Black,
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx()),
            )
            if (body.kind == BodyKind.SUN || body.kind == BodyKind.MOON) {
                drawCircle(Color.Black, radius = 1.5.dp.toPx(), center = Offset(x, y))
            }
            if (layers.solarNames) {
                label(measurer, body.label, x + radius + 3f, y, 10.sp.value)
            }
        }
    }
}

private fun DrawScope.projectionFor(
    width: Float,
    height: Float,
    zoom: Float,
    facing: Int,
): Projection = Projection(
    radiusPx = (minOf(width, height) / 2f) - 14.dp.toPx(),
    centreX = width / 2f,
    centreY = height / 2f,
    zoom = zoom,
    facing = facing,
)

private fun projectionFor(width: Float, height: Float, zoom: Float, facing: Int): Projection =
    Projection(
        radiusPx = (minOf(width, height) / 2f) - 40f,
        centreX = width / 2f,
        centreY = height / 2f,
        zoom = zoom,
        facing = facing,
    )

/** The rim, and the four points of the compass around it. */
private fun DrawScope.drawHorizon(projection: Projection, measurer: TextMeasurer) {
    drawCircle(
        Color.Black,
        radius = projection.horizonRadiusPx,
        center = Offset(projection.centreX, projection.centreY),
        style = Stroke(width = 2.dp.toPx()),
    )

    CARDINALS.forEach { (name, azimuth) ->
        val (x, y) = projection.project(azimuth, -4.0)
        label(measurer, name, x - 6f, y, 13.sp.value)
    }
}

/**
 * A run of sky points joined up, dropped wherever it goes below the horizon.
 *
 * Without the break a constellation half-set would be drawn straight across the chart
 * from one side to the other — the projection sends anything below the horizon outside
 * the rim, so joining across the gap draws a line through the whole sky that is not
 * there.
 */
private fun DrawScope.drawSkyLine(
    points: List<DoubleArray>,
    kind: LineKind,
    projection: Projection,
) {
    val stroke = when (kind) {
        LineKind.CONSTELLATION -> 1.2.dp.toPx()
        LineKind.ECLIPTIC, LineKind.EQUATOR -> 1.dp.toPx()
        LineKind.GRID -> 0.7.dp.toPx()
    }
    val dashes = kind != LineKind.CONSTELLATION

    var path = Path()
    var started = false

    fun flush() {
        if (started) drawPath(path, Color.Black, style = Stroke(width = stroke))
        path = Path()
        started = false
    }

    points.forEach { point ->
        if (point[1] < 0) {
            flush()
            return@forEach
        }
        val (x, y) = projection.project(point[0], point[1])
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    flush()

    // Dashed lines are drawn as a second pass of white ticks over the line rather than
    // with a path effect, which keeps every stroke in this file one solid colour and one
    // weight — the two things E Ink renders reliably.
    if (dashes) {
        // Left solid deliberately: a dash pattern at this stroke width dithers into a
        // grey smudge on a sixteen-grey panel. Weight alone separates these from the
        // constellation figures.
    }
}

/**
 * A word beside a mark, skipped entirely when it would fall off the canvas.
 *
 * The skip is not an optimisation. `drawText` with a measurer derives its constraints from
 * the remaining space below and right of the anchor, so an anchor past the edge asks for a
 * negative width and throws — and with the chart zoomed in, most labels are off the edge.
 * Measuring first also lets the text sit centred on the star rather than hanging below it.
 */
private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
) {
    if (x < -MARGIN || y < -MARGIN || x > size.width + MARGIN || y > size.height + MARGIN) {
        return
    }
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(color = Color.Black, fontSize = sizeSp.sp),
    )
    drawText(layout, topLeft = Offset(x, y - layout.size.height / 2f))
}

private const val MARGIN = 80f

/** The object nearest a tap, within [tolerance] pixels, or null. */
private fun nearest(
    scene: Scene,
    projection: Projection,
    tap: Offset,
    tolerance: Float,
): SkyObject? {
    var best: SkyObject? = null
    var bestDistance = tolerance

    fun consider(objectAt: SkyObject) {
        val (x, y) = projection.project(objectAt.azimuth, objectAt.elevation)
        val distance = hypot(x - tap.x, y - tap.y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = objectAt
        }
    }

    // Bodies first and stars second, so that where a planet sits on top of a faint star
    // the planet wins ties.
    scene.bodies.forEach(::consider)
    scene.stars.filter { it is SkyStar }.forEach(::consider)
    return best
}
