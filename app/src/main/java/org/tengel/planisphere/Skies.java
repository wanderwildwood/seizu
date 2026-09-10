/*
 * Added by this fork, not part of AndroidPlanisphere.
 *
 * Java allows one public top-level class per file, so PlanetCsv - which is declared
 * alongside Planet - cannot itself be made public without splitting a file this fork
 * would rather keep diffable against upstream. This is the one door through that wall:
 * a public entry point in the same package that forwards to it and does nothing else.
 */
package org.tengel.planisphere;

import java.io.IOException;
import java.io.InputStream;

public final class Skies
{
    private Skies() {}

    public static void initPlanets(InputStream jupiter, InputStream saturn,
                                   InputStream uranus, InputStream neptune)
            throws IOException
    {
        PlanetCsv.init(jupiter, saturn, uranus, neptune);
    }
}
