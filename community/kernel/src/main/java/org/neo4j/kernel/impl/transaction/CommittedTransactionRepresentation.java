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
package org.neo4j.kernel.impl.transaction;

import java.io.IOException;
import org.neo4j.internal.helpers.collection.Visitor;
import org.neo4j.kernel.impl.transaction.log.LogicalTransactionStore;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryCommit;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryStart;
import org.neo4j.storageengine.api.CommandBatch;
import org.neo4j.storageengine.api.StorageCommand;

/**
 * This class represents the concept of a TransactionRepresentation that has been
 * committed to the TransactionStore. It contains, in addition to the TransactionRepresentation
 * itself, a Start and Commit entry. This is the thing that {@link LogicalTransactionStore} returns when
 * asked for a transaction via a cursor.
 */
public record CommittedTransactionRepresentation(
        LogEntryStart startEntry, CommandBatch commandBatch, LogEntryCommit commitEntry) {

    public void accept(Visitor<StorageCommand, IOException> visitor) throws IOException {
        commandBatch.accept(visitor);
    }

    public int getChecksum() {
        return commitEntry().getChecksum();
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean includeCommands) {
        return "CommittedTransactionRepresentation{" + "startEntry="
                + startEntry + ", transactionRepresentation="
                + commandBatch.toString(includeCommands) + ", commitEntry="
                + commitEntry + '}';
    }
}
