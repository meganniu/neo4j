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
package org.neo4j.io.pagecache.tracing;

/**
 * Begin a page fault as part of a pin event.
 */
public interface PageFaultEvent extends EvictionEventOpportunity, AutoCloseablePageCacheTracerEvent {
    /**
     * A PageFaultEvent that does nothing.
     */
    PageFaultEvent NULL = new PageFaultEvent() {
        @Override
        public void addBytesRead(long bytes) {}

        @Override
        public void setException(Throwable throwable) {}

        @Override
        public void freeListSize(int freeListSize) {}

        @Override
        public EvictionEvent beginEviction(long cachePageId) {
            return EvictionEvent.NULL;
        }

        @Override
        public void setCachePageId(long cachePageId) {}

        @Override
        public void close() {}
    };

    /**
     * Add up a number of bytes that has been read from the backing file into the free page being bound.
     */
    void addBytesRead(long bytes);

    /**
     * The id of the cache page that is being faulted into.
     */
    void setCachePageId(long cachePageId);

    /**
     * Update free list size as result of fault
     * @param freeListSize new free list size
     */
    void freeListSize(int freeListSize);

    /**
     * The page fault did not complete successfully, but instead caused the given Throwable to be thrown.
     */
    void setException(Throwable throwable);

    /**
     * The page fault completed.
     */
    @Override
    void close();
}
