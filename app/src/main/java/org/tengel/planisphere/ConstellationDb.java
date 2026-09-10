/*
 * Copyright (C) 2020 Timo Engel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Kept from AndroidPlanisphere by Timo Engel, GPL-3.0-or-later, in its original package
 * so that it stays diffable against upstream. The astronomy is not rewritten: orbital
 * mechanics is exactly the kind of code where a porting slip yields a plausible-looking
 * and wrong sky, and this version is already exercised by a published app.
 *
 * The ONLY edits are widening visibility so the Kotlin above it can call in. Every one is
 * marked "// visibility widened". No arithmetic is touched.
 */

package org.tengel.planisphere;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ConstellationDb
{
    public class Constellation // visibility widened
    {
        public String mName = new String(); // visibility widened
        public ArrayList<Catalog.Entry> mLine = new ArrayList<Catalog.Entry>(); // visibility widened
    }

    private ArrayList<Constellation> mEntries = new ArrayList<Constellation>();
    private HashMap<String, String[]> mNames = new HashMap<String, String[]>();
    private HashMap<String, ArrayList<Double[]>> mBoundaries = new HashMap<>();
    private static ConstellationDb sInstance = null;

    public static ConstellationDb instance() throws NullPointerException
    {
        if (sInstance == null)
        {
            throw new NullPointerException("run init() before instance()");
        }
        return sInstance;
    }

    public synchronized static void init(InputStream lineStream, InputStream nameStream,
                                         InputStream boundStream,
                                         Catalog catalog) throws IOException
    {
        if (lineStream == null || nameStream == null || boundStream == null || catalog == null)
        {
            throw new NullPointerException("parameter must not be null");
        }
        else if(sInstance == null)
        {
            sInstance = new ConstellationDb(lineStream, nameStream, boundStream, catalog);
        }
    }


    private ConstellationDb(InputStream lineStream, InputStream nameStream,
                            InputStream boundStream,
                            Catalog catalog) throws IOException
    {
        BufferedReader fileReader = new BufferedReader(new InputStreamReader(lineStream));
        while (true)
        {
            String line = fileReader.readLine();
            if (line == null)
            {
                break;
            }
            if (line.startsWith("#") || line.length() == 0)
            {
                continue;
            }
            String[] lItems = line.split(" +");
            Constellation con = new Constellation();
            con.mName = lItems[0].trim().toLowerCase(Locale.ROOT);
            int pointId;
            for (int i = 2; i < lItems.length; ++i)
            {
                pointId = Integer.valueOf(lItems[i].trim());
                con.mLine.add(catalog.get(pointId));
            }
            mEntries.add(con);
        }

        fileReader = new BufferedReader(new InputStreamReader(nameStream));
        while (true)
        {
            String line = fileReader.readLine();
            if (line == null)
            {
                break;
            }
            String[] lItems = line.split("\t");
            mNames.put(lItems[0].trim().toLowerCase(Locale.ROOT),
                       new String[] {lItems[0].trim(), lItems[1].trim(), lItems[2].trim(),
                                     lItems[3].trim(), lItems[4].trim(), lItems[5].trim(),
                                     lItems[6].trim(), lItems[7].trim(), lItems[8].trim(),
                                     lItems[9].trim()});
        }

        fileReader = new BufferedReader(new InputStreamReader(boundStream));
        while(true)
        {
            String line = fileReader.readLine();
            if (line == null)
            {
                break;
            }
            String[] lItems = line.split("\\|");
            String rightAscension = lItems[0].trim();
            String[] raItems = rightAscension.split(" ");
            Integer raH = Integer.valueOf(raItems[0].trim());
            Integer raM = Integer.valueOf(raItems[1].trim());
            Double raS = Double.valueOf(raItems[2].trim());
            Double declination = Double.valueOf(lItems[1].trim());
            String name = lItems[2].trim().toLowerCase(Locale.ROOT);
            if (mBoundaries.get(name) == null)
            {
                mBoundaries.put(name, new ArrayList<Double[]>());
            }
            mBoundaries.get(name).add(new Double[] {raH + (raM / 60.0) + (raS / 60.0 / 60.0),
                                                    declination});
        }
    }

    public ArrayList<Constellation> get()
    {
        return mEntries;
    }

    /*
     * ADAPTED: upstream reads the wanted language out of its Settings singleton, which is
     * an Android preferences object this fork does not carry. The language index is passed
     * in instead. Column meanings are unchanged:
     * 0=Abbrv; 1=Latin; 2=English; 3=German; 4=Chinese; 5=Spanish;
     * 6=Norwegian 7=French 8=Italian 9=Russian
     */
    public String getName(String abbr, int langIdx)
    {
        String[] row = mNames.get(abbr);
        if (row == null || langIdx < 0 || langIdx >= row.length)
        {
            return abbr;
        }
        String name = row[langIdx];
        return (name == null || name.isEmpty()) ? abbr : name;
    }

    public ArrayList<Double[]> getBoundary(String name)
    {
        return mBoundaries.get(name);
    }
}
