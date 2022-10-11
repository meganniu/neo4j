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
package org.neo4j.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.util.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;
import org.neo4j.io.pagecache.monitoring.PageCacheCounters;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.io.pagecache.tracing.cursor.PageCursorCounters;
import org.neo4j.io.pagecache.tracing.cursor.PageCursorTracer;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.storageengine.api.StorageEngineFactory;

/**
 * Convenience for making tracing assertions for {@link PageCacheTracer}/{@link PageCursorTracer}.
 * Supports specifying expected tracing numbers for different storage engines, useful when running
 * integration tests for different storage engines.
 */
public class PageCacheTracerAssertions {
    public static TracingBuilder assertThatTracing(GraphDatabaseAPI db) {
        return new TracingBuilder(db.getDependencyResolver()
                .resolveDependency(StorageEngineFactory.class)
                .name());
    }

    public static PerStorageEngine pins(long pins) {
        return new PerStorageEngine(pins);
    }

    public static class TracingBuilder {
        private final String storageEngineName;
        private final Map<String, PerStorageEngine> perStorage = new HashMap<>();

        private TracingBuilder(String storageEngineName) {
            this.storageEngineName = storageEngineName;
        }

        public TracingBuilder record(PerStorageEngine storageEngineNumbers) {
            return storageEngine("record", storageEngineNumbers);
        }

        public TracingBuilder freki(PerStorageEngine storageEngineNumbers) {
            return storageEngine("freki", storageEngineNumbers);
        }

        public TracingBuilder storageEngine(String name, PerStorageEngine storageEngineNumbers) {
            perStorage.put(name, storageEngineNumbers);
            return this;
        }

        public void matches(PageCacheTracer pageCacheTracer) {
            forCorrectStorageEngine().assertMatches(pageCacheTracer);
        }

        public void matches(PageCursorTracer pageCursorTracer) {
            forCorrectStorageEngine().assertMatches(pageCursorTracer);
        }

        private PerStorageEngine forCorrectStorageEngine() {
            var numbers = perStorage.get(storageEngineName);
            checkState(
                    numbers != null,
                    "No page cache tracer numbers specified for storage engine '%s'",
                    storageEngineName);
            return numbers;
        }
    }

    public static class PerStorageEngine {
        private final long pins;
        private boolean overlookUnpins;
        private Long faults;

        private PerStorageEngine(long pins) {
            this.pins = pins;
        }

        public PerStorageEngine skipUnpins() {
            this.overlookUnpins = true;
            return this;
        }

        public PerStorageEngine faults(long faults) {
            this.faults = faults;
            return this;
        }

        public PerStorageEngine noFaults() {
            this.faults = 0L;
            return this;
        }

        private void assertMatches(PageCacheCounters tracer) {
            assertMatches(tracer.pins(), tracer.unpins(), tracer.hits(), tracer.faults());
        }

        private void assertMatches(PageCursorCounters tracer) {
            assertMatches(tracer.pins(), tracer.unpins(), tracer.hits(), tracer.faults());
        }

        private void assertMatches(long tracedPins, long tracedUnpins, long tracedHits, long tracedFaults) {
            assertThat(pins).as("pins").isEqualTo(tracedPins);
            if (!overlookUnpins) {
                assertThat(pins).as("unpins").isEqualTo(tracedUnpins);
            }
            if (faults != null) {
                assertThat(faults).as("faults").isEqualTo(tracedFaults);
                assertThat(pins - faults).as("hits").isEqualTo(tracedHits);
            }
        }
    }
}
