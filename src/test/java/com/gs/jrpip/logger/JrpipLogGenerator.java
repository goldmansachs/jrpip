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

package com.gs.jrpip.logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gs.jrpip.Echo;
import com.gs.jrpip.MockVirtualOutputStreamCreator;
import org.junit.Assert;

import static org.junit.Assert.assertNotNull;

public final class JrpipLogGenerator
{
    private JrpipLogGenerator()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    public static void createJrpipDumpFiles(
            int threadNumber,
            final Object echoContent,
            MockVirtualOutputStreamCreator outputStreamCreator,
            final Echo echo,
            final int numberOfEcho) throws InterruptedException
    {
        ExecutorService executorService = null;
        try
        {
            executorService = Executors.newFixedThreadPool(threadNumber);
            List<Callable<Object>> callables = new ArrayList<Callable<Object>>(threadNumber);

            for (int i = 0; i < threadNumber; i++)
            {
                callables.add(Executors.callable(new Runnable()
                {
                    public void run()
                    {
                        for (int j = 0; j < numberOfEcho; j++)
                        {
                            if (echoContent instanceof String)
                            {
                                echo.echo((String) echoContent);
                            }
                            else
                            {
                                echo.echoObject(echoContent);
                            }
                        }
                    }
                }));
            }
            executorService.invokeAll(callables);
        }
        finally
        {
            if (executorService != null)
            {
                executorService.shutdown();
            }
        }
        closeVirtualOutputStream(outputStreamCreator);
    }

    public static void closeVirtualOutputStream(MockVirtualOutputStreamCreator virtualOutputStreamCreator)
    {
        assertNotNull(virtualOutputStreamCreator.getVirtualOutputStream());
        virtualOutputStreamCreator.getVirtualOutputStream().close();
    }

    public static void deleteDirectory(String pathname)
    {
        File binaryLogsDir = new File(pathname);
        File[] files = binaryLogsDir.listFiles();
        if (files != null)
        {
            for (int i = 0; i < files.length; i++)
            {
                files[i].delete();
            }
        }
        binaryLogsDir.delete();
    }

    public static File findLogFileByExtension(String pathName, final String extension)
    {
        File binaryLogsDir = new File(pathName);
        File[] files = binaryLogsDir.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.endsWith(extension);
            }
        });
        Assert.assertEquals(1, files.length);
        return files[0];
    }
}
