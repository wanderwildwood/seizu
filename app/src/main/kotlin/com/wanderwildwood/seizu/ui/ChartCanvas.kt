package com.wanderwildwood.seizu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
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
import com.wanderwildwood.seizu.sky.ALTITUDE_CIRCLES
import com.wanderwildwood.seizu.sky.AZIMUTH_SPOKES
import com.wanderwildwood.seizu.sky.BodyKind
import com.wanderwildwood.seizu.sky.CARDINALS
import com.wanderwildwood.seizu.sky.LineKind
import com.wanderwildwood.seizu.sky.Layers
import com.wanderwildwood.seizu.sky.MarkWeight
import com.wanderwildwood.seizu.sky.MoonPhase
import com.wanderwildwood.seizu.sky.Projection
import com.wanderwildwood.seizu.sky.Scene
import com.wanderwildwood.seizu.sky.SkyObject
import com.wanderwildwood.seizu.sky.bodyRadius
import com.wanderwildwood.seizu.sky.starRadius
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

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
 *
 * Everything here is drawn at a hairline: a chart that has to hold nine thousand stars,
 * eighty-eight figures and their names on a 4.3" panel runs out of paper long before it
 * runs out of sky, and the width of the marks is what decides whether it reads as a chart
 * or as a smudge.
 */
@Composable
fun ChartCanvas(
    scene: Scene,
    layers: Layers,
    facing: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
    onSelect: (SkyObject?) -> Unit,
    onZoom: (Float) -> Unit,
    onPan: (Float, Float) -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val insetPx = with(density) { CHART_INSET.toPx() }
    val tolerancePx = with(density) { 24.dp.toPx() }
    // The gesture loop below outlives the composition that started it, so it reads the
    // callbacks through a handle that is kept current rather than closing over whichever
    // pair of lambdas happened to exist when the first finger went down.
    val zoomBy by rememberUpdatedState(onZoom)
    val panBy by rememberUpdatedState(onPan)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // A zoomed disc is wider than the box that holds it, and Canvas does not clip
            // to its bounds on its own -- without this the chart paints straight over the
            // date, the place and the row of buttons underneath it.
            .clipToBounds()
            // Pinch to zoom, drag to move. Both are reported as deltas and handed
            // straight out: where the chart is sits in the view model with everything
            // else that survives a rotation, not in a pile of remembered floats here.
            .pointerInput(insetPx) {
                detectTransformGestures { _, pan, pinch, _ ->
                    val radius = chartRadius(size.width.toFloat(), size.height.toFloat(), insetPx)
                    if (pinch != 1f) zoomBy(pinch)
                    // A finger dragging left is a view moving right over the chart, which
                    // is why the sign turns over here.
                    if (pan != Offset.Zero) panBy(-pan.x / radius, -pan.y / radius)
                }
            }
            .pointerInput(scene, facing, zoom, panX, panY) {
                detectTapGestures(
                    // The whole sky back on the screen, for when a zoomed chart has been
                    // dragged somewhere unrecognisable. Every other way out would be a
                    // button, and there is no room for a third one.
                    onDoubleTap = { onResetView() },
                    onTap = { tap ->
                        val projection = chartProjection(
                            size.width.toFloat(), size.height.toFloat(),
                            insetPx, zoom, panX, panY, facing,
                        )
                        onSelect(nearest(scene, projection, tap, tolerancePx))
                    },
                )
            },
    ) {
        val projection = chartProjection(size.width, size.height, insetPx, zoom, panX, panY, facing)
        val weight = layers.markWeight
        val base = MARK_BASE.toPx() * weight.scale

        // Labels are collected as the chart is drawn and put down at the end, in the order
        // they were asked for: a word that would land on a word already written is dropped
        // rather than printed over it. It is the difference between a chart and a heap of
        // type — and the reason the order below runs from the things you are looking for
        // by name down to the things you can read off the chart anyway.
        val labels = mutableListOf<Label>()

        if (layers.altitudeGrid) {
            drawAltitudeGrid(projection, weight)
        }

        scene.lines.forEach { line ->
            drawSkyLine(line.points, line.kind, projection, weight)
        }

        drawHorizon(projection, weight, labels)

        // The stars go down before the rings, so that where a planet sits on a faint star
        // the ring is the mark on top — the same order the tap goes through them in.
        // Their names are held back and added last, below the names of everything else.
        val starLabels = mutableListOf<Label>()
        val stars = if (layers.starNames) scene.stars.sortedBy { it.magnitude } else scene.stars
        stars.forEach { star ->
            val (x, y) = projection.project(star.azimuth, star.elevation)
            // Never smaller than a pixel across, or the faintest stars are a grey haze
            // rather than the fifth-magnitude stars they are.
            val radius = starRadius(star.magnitude, base).coerceAtLeast(1f)
            drawCircle(Color.Black, radius = radius, center = Offset(x, y))
            val name = star.label
            if (layers.starNames && name != null) {
                starLabels += Label(name, x + radius + LABEL_GAP.toPx(), y, STAR_TEXT)
            }
        }

        scene.bodies.forEach { body ->
            val (x, y) = projection.project(body.azimuth, body.elevation)
            val radius = bodyRadius(body.kind, body.magnitude, base)
            // A ring, not a disc, so a planet is never mistaken for a bright star and so
            // the Sun is not a coin of solid black in the middle of the chart.
            drawCircle(
                Color.Black,
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = (1.dp.toPx() * weight.scale).coerceAtLeast(1f)),
            )
            val phase = body.phase
            if (body.kind == BodyKind.MOON && phase != null) {
                drawMoonPhase(Offset(x, y), radius, phase)
            } else if (body.kind == BodyKind.SUN || body.kind == BodyKind.MOON) {
                drawCircle(Color.Black, radius = base * 0.7f, center = Offset(x, y))
            }
            if (layers.solarNames) {
                labels += Label(body.label, x + radius + LABEL_GAP.toPx(), y, BODY_TEXT)
            }
        }

        if (layers.constellationNames) {
            scene.names.forEach { name ->
                val (x, y) = projection.project(name.azimuth, name.elevation)
                labels += Label(name.label, x, y, CONSTELLATION_TEXT, centred = true)
            }
        }

        // Brightest star first, so that where two names would collide the one anybody
        // would be asking about is the one that keeps its label.
        labels += starLabels

        drawLabels(measurer, labels)
    }
}

/** A word waiting to be put on the chart, once it is known whether there is room. */
private class Label(
    val text: String,
    val x: Float,
    val y: Float,
    val sizeSp: Float,
    val centred: Boolean = false,
)

/** The base width every mark on the chart is a multiple of, before the mark weight. */
private val MARK_BASE = 1.25.dp

/** White space left round the disc for the four letters of the compass. */
private val CHART_INSET = 16.dp

private val LABEL_GAP = 2.5.dp

private const val CARDINAL_TEXT = 12f
private const val BODY_TEXT = 10f
private const val CONSTELLATION_TEXT = 10f
private const val STAR_TEXT = 8.5f

/** How far outside the rim the compass letters sit, in degrees below the horizon. */
private const val CARDINAL_DROP = -3.0

private fun chartRadius(width: Float, height: Float, insetPx: Float): Float =
    ((min(width, height) / 2f) - insetPx).coerceAtLeast(1f)

/**
 * The one projection, used both to draw the chart and to work out what was tapped.
 *
 * Both of those have to agree to the pixel or the chart lies about what is under your
 * finger, which on a chart where every mark looks like every other mark is a mistake
 * nothing on the screen would reveal.
 */
private fun chartProjection(
    width: Float,
    height: Float,
    insetPx: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    facing: Int,
): Projection {
    val radius = chartRadius(width, height, insetPx)
    return Projection(
        radiusPx = radius,
        centreX = width / 2f,
        centreY = height / 2f,
        zoom = zoom,
        // Pan is carried in radii so it means the same thing on any screen; it becomes
        // pixels here and nowhere else.
        panX = panX * radius,
        panY = panY * radius,
        facing = facing,
    )
}

private fun DrawScope.strokeFor(kind: LineKind, weight: MarkWeight): Float {
    val width = when (kind) {
        LineKind.CONSTELLATION -> 0.9.dp
        LineKind.ECLIPTIC, LineKind.EQUATOR -> 0.8.dp
        LineKind.GRID -> 0.6.dp
        LineKind.BOUNDARY -> 0.5.dp
    }
    // Never thinner than a pixel. A half-pixel line on a sixteen-grey panel is not a
    // finer line, it is a grey one, and grey is the one thing E Ink cannot hold still.
    return (width.toPx() * weight.scale).coerceAtLeast(1f)
}

/** The rim, and the four points of the compass around it. */
private fun DrawScope.drawHorizon(
    projection: Projection,
    weight: MarkWeight,
    labels: MutableList<Label>,
) {
    drawCircle(
        Color.Black,
        radius = projection.horizonRadiusPx,
        center = Offset(projection.zenithX, projection.zenithY),
        style = Stroke(width = (1.1.dp.toPx() * weight.scale).coerceAtLeast(1f)),
    )

    CARDINALS.forEach { (name, azimuth) ->
        val (x, y) = projection.project(azimuth, CARDINAL_DROP)
        val anchor = keptOnScreen(Offset(x, y))
        labels += Label(name, anchor.x, anchor.y, CARDINAL_TEXT, centred = true)
    }
}

/**
 * A point dragged back to the edge of the screen, along the line it lies on.
 *
 * Zoom past the rim and the compass letters are somewhere off the side of the panel,
 * which is the moment a chart stops being a chart: you cannot match it against the sky if
 * you cannot tell which way it is pointing. Pinning them to the edge in the direction
 * they actually lie keeps the answer on the screen at every zoom.
 */
private fun DrawScope.keptOnScreen(point: Offset): Offset {
    val margin = 14.dp.toPx()
    if (point.x in margin..(size.width - margin) && point.y in margin..(size.height - margin)) {
        return point
    }
    val centre = Offset(size.width / 2f, size.height / 2f)
    val dx = point.x - centre.x
    val dy = point.y - centre.y
    if (dx == 0f && dy == 0f) return centre
    val toSide = if (dx != 0f) (centre.x - margin) / abs(dx) else Float.MAX_VALUE
    val toEnd = if (dy != 0f) (centre.y - margin) / abs(dy) else Float.MAX_VALUE
    val reach = min(toSide, toEnd)
    return Offset(centre.x + dx * reach, centre.y + dy * reach)
}

/**
 * Upstream's azimuthal grid: circles of altitude and spokes of bearing.
 *
 * Drawn from the geometry rather than from a run of projected points, because on this
 * projection a circle of constant altitude is a true circle about the zenith and a line
 * of constant bearing is a straight line out of it. Tracing either as a polygon would be
 * a hundred chances to show a corner.
 */
private fun DrawScope.drawAltitudeGrid(projection: Projection, weight: MarkWeight) {
    val stroke = strokeFor(LineKind.GRID, weight)
    val zenith = Offset(projection.zenithX, projection.zenithY)

    ALTITUDE_CIRCLES.forEach { altitude ->
        drawCircle(
            Color.Black,
            radius = projection.distanceFor(altitude).toFloat(),
            center = zenith,
            style = Stroke(width = stroke),
        )
    }
    AZIMUTH_SPOKES.forEach { azimuth ->
        val (x, y) = projection.project(azimuth, 0.0)
        drawLine(Color.Black, start = zenith, end = Offset(x, y), strokeWidth = stroke)
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
    weight: MarkWeight,
) {
    val stroke = strokeFor(kind, weight)

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

    // The grid, the ecliptic and the equator are left solid deliberately: a dash pattern
    // at these stroke widths dithers into a grey smudge on a sixteen-grey panel. Weight
    // alone separates them from the constellation figures.
}

/**
 * The words, put down last and only where there is room for them.
 *
 * Two things a printed atlas does, and a screen chart usually does not. A label that
 * would sit on a label already placed is dropped, so the chart never doubles a word over
 * a word; and every label is knocked out of the drawing underneath in white, the way an
 * engraver would lift a line out from under a name, so a star's name is readable where it
 * crosses a constellation figure.
 *
 * A label falling off the canvas is dropped rather than clipped. It is not only tidiness:
 * a word half off the edge of a 4.3" panel is unreadable anyway, and with the chart
 * zoomed in most of them are off the edge.
 */
private fun DrawScope.drawLabels(measurer: TextMeasurer, labels: List<Label>) {
    val padding = 1.5.dp.toPx()
    val taken = mutableListOf<Rect>()

    labels.forEach { label ->
        val layout = measurer.measure(
            text = AnnotatedString(label.text),
            style = TextStyle(color = Color.Black, fontSize = label.sizeSp.sp),
        )
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        val left = if (label.centred) label.x - (width / 2f) else label.x
        val top = label.y - (height / 2f)
        if (left < 0f || top < 0f || left + width > size.width || top + height > size.height) {
            return@forEach
        }

        val box = Rect(left - padding, top - padding, left + width + padding, top + height + padding)
        if (taken.any { it.overlaps(box) }) return@forEach
        taken += box

        drawRect(Color.White, topLeft = Offset(box.left, box.top), size = Size(box.width, box.height))
        drawText(layout, topLeft = Offset(left, top))
    }
}

/**
 * The lit part of the Moon, inside the ring already drawn for it.
 *
 * The terminator is upright rather than turned to face the real Sun. A chart mark
 * seventeen pixels across cannot carry a position angle -- at that size the tilt reads as
 * a drawing error, not as information -- and what is actually wanted from a glance is how
 * much moon there will be. So this is an almanac's symbol: lit on the right waxing, on
 * the left waning, the way the phase is printed in every diary.
 *
 * The shape is the standard one. Half the disc is lit; the terminator is the edge of an
 * ellipse whose width is how far the phase is from half. Past half that ellipse is added
 * to the lit half, before half it is taken out of it, and at half there is nothing to do.
 */
private fun DrawScope.drawMoonPhase(centre: Offset, radius: Float, phase: MoonPhase) {
    val lit = phase.illuminated.toFloat()
    // Within a pixel of either end there is no crescent to draw, and the ellipse below
    // degenerates. New stays the empty ring the other bodies get; full is simply filled.
    if (lit <= 0.03f) return
    if (lit >= 0.97f) {
        drawCircle(Color.Black, radius = radius, center = centre)
        return
    }

    val box = Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius)
    // Sweeping clockwise from the top gives the right-hand half, which is the waxing side.
    val halfStart = if (phase.waxing) -90f else 90f
    val half = Path().apply { arcTo(box, halfStart, 180f, true); close() }

    val waist = radius * (2f * lit - 1f)
    val terminator = Path().apply {
        addOval(Rect(centre.x - abs(waist), centre.y - radius, centre.x + abs(waist), centre.y + radius))
    }

    val shape = Path()
    shape.op(half, terminator, if (waist > 0f) PathOperation.Union else PathOperation.Difference)
    drawPath(shape, Color.Black)
}

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
    scene.stars.forEach(::consider)
    return best
}
