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

import java.util.ArrayList;

public class CompositeOutputWriter implements PacketWriter
{
    private final ArrayList<SerialMultiplexedWriter> fileWriters = new ArrayList<SerialMultiplexedWriter>();
    private final int maxFileSizeMegabytes;
    private final OutputStreamBuilder outputStreamBuilder;
    private final ByteArrayPool byteArrayPool;
    private final ExceptionHandler exceptionHandler;

    public CompositeOutputWriter(
            int maxFileSizeMegabytes,
            OutputStreamBuilder outputStreamBuilder,
            ByteArrayPool byteArrayPool,
            ExceptionHandler exceptionHandler)
    {
        this.maxFileSizeMegabytes = maxFileSizeMegabytes;
        this.outputStreamBuilder = outputStreamBuilder;
        this.byteArrayPool = byteArrayPool;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void writePacket(Packet packet)
    {
        SerialMultiplexedWriter fileWriter = this.getOrCreateWriter(packet);
        fileWriter.writePacket(packet);
        packet.returnByteArrayToThePool(this.byteArrayPool);
        this.delistIfNotActive(fileWriter);
    }

    private SerialMultiplexedWriter getOrCreateWriter(Packet packet)
    {
        SerialMultiplexedWriter openWriterForPacket = this.findOpenWriterForPacket(packet);
        return openWriterForPacket == null ? this.getExistingWriterOrCreateNewWriter() : openWriterForPacket;
    }

    private SerialMultiplexedWriter findOpenWriterForPacket(Packet packet)
    {
        for (int i = 0; i < this.fileWriters.size(); i++)
        {
            SerialMultiplexedWriter writer = this.fileWriters.get(i);
            if (writer.containsStream(packet.getStreamId()))
            {
                return writer;
            }
        }
        return null;
    }

    private SerialMultiplexedWriter getExistingWriterOrCreateNewWriter()
    {
        SerialMultiplexedWriter potentialWriterCandidate = this.fileWriters.isEmpty() ? null : this.fileWriters.get(this.fileWriters.size() - 1);
        return this.isUsableWriter(potentialWriterCandidate) ? potentialWriterCandidate : this.createAndRegisterNewWriter();
    }

    private boolean isUsableWriter(SerialMultiplexedWriter availableFileWriter)
    {
        return availableFileWriter != null && !availableFileWriter.hasReachedMaxSizeLimit();
    }

    private SerialMultiplexedWriter createAndRegisterNewWriter()
    {
        SerialMultiplexedWriter availableFileWriter = this.newSingleFileWriter();
        availableFileWriter.writeVersion();
        this.fileWriters.add(availableFileWriter);
        return availableFileWriter;
    }

    private SerialMultiplexedWriter newSingleFileWriter()
    {
        return new SerialMultiplexedWriter(this.outputStreamBuilder.newOutputStream(), this.maxFileSizeMegabytes, this.exceptionHandler);
    }

    private void delistIfNotActive(SerialMultiplexedWriter fileWriter)
    {
        if (fileWriter.isDone())
        {
            fileWriter.close();
            this.fileWriters.remove(fileWriter);
        }
    }

    @Override
    public void close()
    {
        for (int i = 0; i < this.fileWriters.size(); i++)
        {
            this.fileWriters.get(0).close();
        }
        this.fileWriters.clear();
    }
}
