package com.hungii.prototype

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class ShufflePose(val x: Float, val y: Float, val tilt: Float, val blur: Float)

/** A finite mix: zero displacement and velocity at both ends, inside each slot's bounds. */
internal fun shufflePose(progress: Float, index: Int, travel: Float): ShufflePose {
    val t = progress.coerceIn(0f, 1f)
    if (t == 0f || t == 1f) return ShufflePose(0f, 0f, (index - 1) * 4f, 0f)
    val envelope = sin(PI * t).let { (it * it).toFloat() }
    val phase = 5 * PI * t + index * 2 * PI / 3
    val x = envelope * (-(index - 1) * travel * .55f + sin(phase).toFloat() * travel * .42f)
    return ShufflePose(x, cos(phase).toFloat() * 18f * envelope,
        (index - 1) * 4f + sin(phase).toFloat() * 10f * envelope, envelope * 1.4f)
}
