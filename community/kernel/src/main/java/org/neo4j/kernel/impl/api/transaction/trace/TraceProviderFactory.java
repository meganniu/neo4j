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
package org.neo4j.kernel.impl.api.transaction.trace;

import static org.neo4j.configuration.GraphDatabaseSettings.transaction_sampling_percentage;
import static org.neo4j.configuration.GraphDatabaseSettings.transaction_tracing_level;

import java.util.concurrent.ThreadLocalRandom;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;

public class TraceProviderFactory {
    private TraceProviderFactory() {}

    public static TraceProvider getTraceProvider(Config config) {
        GraphDatabaseSettings.TransactionTracingLevel tracingLevel = config.get(transaction_tracing_level);
        switch (tracingLevel) {
            case DISABLED:
                return () -> TransactionInitializationTrace.NONE;
            case ALL:
                return TransactionInitializationTrace::new;
            case SAMPLE:
                return () -> {
                    if (ThreadLocalRandom.current().nextInt(1, 101) <= config.get(transaction_sampling_percentage)) {
                        return new TransactionInitializationTrace();
                    } else {
                        return TransactionInitializationTrace.NONE;
                    }
                };
            default:
                throw new IllegalStateException("Unsupported trace mode: " + tracingLevel);
        }
    }
}
