/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.causalclustering.messaging.marshalling.v2.encoding;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.IOException;

import org.neo4j.causalclustering.messaging.NetworkFlushableChannelNetty4;
import org.neo4j.causalclustering.messaging.marshalling.v2.ContentType;
import org.neo4j.causalclustering.messaging.marshalling.v2.SerializableContent;
import org.neo4j.storageengine.api.WritableChannel;

public class SerializableContentEncoder extends MessageToByteEncoder<SerializableContent>
{
    @Override
    protected void encode( ChannelHandlerContext ctx, SerializableContent msg, ByteBuf out ) throws Exception
    {
        sendToNetwork( ctx, msg, out );
    }

    public void marshal( SerializableContent serializableContent, WritableChannel channel ) throws IOException
    {
        serializableContent.serialize( channel );
    }

    private void sendToNetwork( ChannelHandlerContext ctx, SerializableContent msg, ByteBuf out ) throws IOException
    {
        if ( msg instanceof SerializableContent.SimpleSerializableContent )
        {
            msg.serialize( new NetworkFlushableChannelNetty4( out ) );
        }
        else
        {
            ctx.fireChannelRead( msg );
        }
    }
}
