/*
  Copyright 2017 Goldman Sachs.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
 */

package com.gs.jrpip.util.stream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketConsumer
        implements Runnable
{
    private static final Packet SHUT_DOWN_POCKET = new Packet(-100);
    private static final Logger LOGGER = LoggerFactory.getLogger(PacketConsumer.class);
    private final BlockingQueue<Packet> packetQueue;
    private final PacketWriter fileWriter;
    private boolean shutdown;

    /**
     * Kept for api compatibility sake.
     *
     * @deprecated Use PacketConsumer(BlockingQueue<Packet>, PacketWriter) instead
     */
    @Deprecated
    public PacketConsumer(BlockingQueue<Packet> blockingQueue, CompositeOutputWriter fileWriter)
    {
        this(blockingQueue, (PacketWriter) fileWriter);
    }

    public PacketConsumer(BlockingQueue<Packet> blockingQueue, PacketWriter fileWriter)
    {
        this.fileWriter = fileWriter;
        this.packetQueue = blockingQueue;
    }

    public void startPolling(Executor executor)
    {
        executor.execute(this);
    }

    public void waitForShutdown()
    {
        while (!this.shutdown)
        {
            try
            {
                Thread.sleep(500L);
            }
            catch (InterruptedException e)
            {
                //ignored
            }
        }
    }

    public void signalShutdownNow()
    {
        this.shutdownNow();
        this.signalShutdown();
    }

    public void signalShutdown()
    {
        this.addToQueue(SHUT_DOWN_POCKET);
    }

    public void addToQueue(Packet packet)
    {
        if (this.shutdown)
        {
            return;
        }

        if (!this.packetQueue.offer(packet))
        {
            this.shutdownNow();
            LOGGER.error("Shutting consumer down since last sequence of bytes could not be added to the queue.");
        }
    }

    public void endStream(int streamId)
    {
        this.packetQueue.add(new Packet(streamId));
    }

    @Override
    public void run()
    {
        while (!this.shutdown)
        {
            this.processPacket();
        }
    }

    public void processPacket()
    {
        Packet packet = this.takePacket();

        if (packet.equals(SHUT_DOWN_POCKET))
        {
            this.shutdownNow();
        }
        else
        {
            this.fileWriter.writePacket(packet);
        }
    }

    private Packet takePacket()
    {
        Packet packet = null;
        do
        {
            try
            {
                packet = this.packetQueue.take();
            }
            catch (InterruptedException e)
            {
                //ignored
            }
        }
        while (packet == null);
        return packet;
    }

    private void shutdownNow()
    {
        LOGGER.warn("Will clear packet queue up since in shutdown mode.");
        this.packetQueue.clear();
        this.fileWriter.close();
        this.shutdown = true;
    }
}
