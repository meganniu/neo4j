/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.io.pagecache.logging;

public class CacheLogger {
    // private final String fileName;
    // private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    // public CacheLogger(String fileName) {
    //     this.fileName = fileName;
    // }

    // public CacheLogger() {
    //     this.fileName = "/var/lib/neo4j/logs/neo4j-page-cache-logs.txt";
    // }

    public static void logEvent(String msg) {
        StringBuffer sb = new StringBuffer()
                // .append(DATE_FORMAT.format(new Date()))
                .append(System.currentTimeMillis())
                .append(", ")
                .append(msg);
        System.out.println(sb.toString());
        // System.out.println(String.format("Successfully wrote to file %s.", fileName));
        // pw.println(sb.toString());
        // catch (IOException e) {
        //     System.out.println("An error occurred.");
        //     e.printStackTrace();
        // }
    }
}
