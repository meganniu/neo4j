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
package org.neo4j.kernel.impl.transaction.log;

import org.neo4j.storageengine.api.Commitment;
import org.neo4j.storageengine.api.TransactionIdStore;

public class TransactionCommitment implements Commitment {

    private final TransactionMetadataCache transactionMetadataCache;
    private final TransactionIdStore transactionIdStore;
    private boolean committed;
    private long transactionId;
    private int checksum;
    private LogPosition logPosition;
    private long transactionCommitTimestamp;

    TransactionCommitment(TransactionMetadataCache transactionMetadataCache, TransactionIdStore transactionIdStore) {
        this.transactionMetadataCache = transactionMetadataCache;
        this.transactionIdStore = transactionIdStore;
    }

    @Override
    public void commit(long transactionId, LogPosition beforeCommit, LogPosition logPositionAfterCommit, int checksum) {
        this.transactionId = transactionId;
        this.logPosition = logPositionAfterCommit;
        this.checksum = checksum;
        this.transactionMetadataCache.cacheTransactionMetadata(transactionId, beforeCommit);
    }

    @Override
    public void publishAsCommitted(long transactionCommitTimestamp) {
        this.committed = true;
        this.transactionCommitTimestamp = transactionCommitTimestamp;
        transactionIdStore.transactionCommitted(transactionId, checksum, transactionCommitTimestamp);
    }

    @Override
    public void publishAsClosed() {
        if (committed) {
            transactionIdStore.transactionClosed(
                    transactionId,
                    logPosition.getLogVersion(),
                    logPosition.getByteOffset(),
                    checksum,
                    transactionCommitTimestamp);
        }
    }
}
