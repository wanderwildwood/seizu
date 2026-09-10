package com.wanderwildwood.seizu.sky

import android.content.Context
import com.wanderwildwood.seizu.R
import org.tengel.planisphere.Catalog
import org.tengel.planisphere.ConstellationDb
import org.tengel.planisphere.Skies

/**
 * Loading the catalogues, once.
 *
 * The Yale Bright Star Catalogue is 1.7 MB of fixed-width text and parsing it is by far
 * the slowest thing this app does, so it happens on a background thread at startup and
 * never again. The upstream classes are singletons with static `init`, which is why this
 * is a guarded object rather than something injected: the shape is inherited, and
 * changing it would mean changing code whose correctness is the reason for keeping it.
 */
object SkyData {

    @Volatile
    private var loaded = false

    /** True once the sky can be built. The chart shows a line about waiting until then. */
    val isReady: Boolean get() = loaded

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val resources = context.resources

        Catalog.init(
            resources.openRawResource(R.raw.bs_catalog),
            resources.openRawResource(R.raw.star_names),
        )
        ConstellationDb.init(
            resources.openRawResource(R.raw.constellation_lines),
            resources.openRawResource(R.raw.constellation_names),
            resources.openRawResource(R.raw.constellation_boundaries),
            Catalog.instance(),
        )
        Skies.initPlanets(
            resources.openRawResource(R.raw.horizons_jupiter),
            resources.openRawResource(R.raw.horizons_saturn),
            resources.openRawResource(R.raw.horizons_uranus),
            resources.openRawResource(R.raw.horizons_neptune),
        )

        loaded = true
    }

    fun builder(): SkyBuilder = SkyBuilder(Catalog.instance(), ConstellationDb.instance())
}
