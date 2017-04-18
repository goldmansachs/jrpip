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

public final class VirtualOutputStreamFactory
{
    public static final VirtualOutputStreamCreator DEFAULT_CREATOR = new VirtualOutputStreamCreator()
    {
        public VirtualOutputStream create(String baseFileName, String fileDir, int flushSizeBytes, int fileMaxSizeMegs)
        {
            return new VirtualOutputStream(baseFileName, fileDir, flushSizeBytes, fileMaxSizeMegs);
        }
    };

    private static VirtualOutputStreamCreator outputStreamCreator = DEFAULT_CREATOR;

    private VirtualOutputStreamFactory()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    public interface VirtualOutputStreamCreator
    {
        VirtualOutputStream create(String baseFileName, String fileDir, int flushSizeBytes, int fileMaxSizeMegs);
    }

    public static void setOutputStreamCreator(VirtualOutputStreamCreator creator)
    {
        outputStreamCreator = creator;
    }

    public static VirtualOutputStream create(String baseFileName, String fileDir)
    {
        return create(baseFileName, fileDir, MultiOutputStream.DEFAULT_FLUSH_BUFFER_SIZE_BYTES, MultiOutputStream.DEFAULT_MAX_FILE_SIZE_MEGABYTES);
    }

    public static VirtualOutputStream create(
            String baseFileName,
            String fileDir,
            int flushSizeBytes,
            int fileMaxSizeMegs)
    {
        return outputStreamCreator.create(baseFileName, fileDir, flushSizeBytes, fileMaxSizeMegs);
    }
}
