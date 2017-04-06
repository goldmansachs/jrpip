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

public class StreamFlusher
        implements Runnable
{
    private final ArrayList<FlushableCloseable> activeOutputStreams = new ArrayList<FlushableCloseable>();
    private final ArrayList<FlushableCloseable> newOutputStreams = new ArrayList<FlushableCloseable>();

    public void addStream(FlushableCloseable flushableCloseable)
    {
        synchronized (this.newOutputStreams)
        {
            this.newOutputStreams.add(flushableCloseable);
        }
    }

    @Override
    public void run()
    {
        this.flushActiveStreamsAndPurgeClosedStreams();
    }

    public void flushActiveStreamsAndPurgeClosedStreams()
    {
        synchronized (this.newOutputStreams)
        {
            if (!this.newOutputStreams.isEmpty())
            {
                this.activeOutputStreams.addAll(this.newOutputStreams);
                this.newOutputStreams.clear();
            }
        }
        for (int i = 0; i < this.activeOutputStreams.size(); )
        {
            FlushableCloseable outputStream = this.activeOutputStreams.get(i);
            if (outputStream.isClosed())
            {
                int last = this.activeOutputStreams.size() - 1;
                if (i != last)
                {
                    this.activeOutputStreams.set(i, this.activeOutputStreams.get(last));
                }
                this.activeOutputStreams.remove(last);
            }
            else
            {
                outputStream.flush();
                i++;
            }
        }
    }
}
