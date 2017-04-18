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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UniqueFileOutputStreamBuilder
        implements OutputStreamBuilder
{
    private final String baseFileName;
    private final String fileDir;
    private final String pid;
    private final String hostname;
    private int counter;
    private final ExceptionHandler exceptionHandler;

    public UniqueFileOutputStreamBuilder(String baseFileName, String fileDir, ExceptionHandler exceptionHandler)
    {
        this.baseFileName = baseFileName;
        this.fileDir = fileDir;
        this.pid = this.getPid();
        this.hostname = this.getHostname();
        this.exceptionHandler = exceptionHandler;
        this.createMissingDirectories(this.fileDir);
    }

    @Override
    public DataOutputStream newOutputStream()
    {
        try
        {
            return new DataOutputStream(this.newFileOutputStream());
        }
        catch (FileNotFoundException e)
        {
            this.exceptionHandler.handle(e);
            return VirtualOutputStream.NULL_DATA_OUTPUT_STREAM;
        }
    }

    protected OutputStream newFileOutputStream() throws FileNotFoundException
    {
        return new FileOutputStream(this.newFile());
    }

    @Override
    public void close()
    {
    }

    @Override
    public void shutdownNow()
    {
    }

    private String getPid()
    {
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        if (pid == null)
        {
            pid = "0";
        }
        else
        {
            int atIndex = pid.indexOf('@');
            if (atIndex > 0)
            {
                pid = pid.substring(0, atIndex);
            }
        }
        return pid;
    }

    private String getHostname()
    {
        try
        {
            String name = InetAddress.getLocalHost().getHostName();
            int firstDot = name.indexOf('.');
            if (firstDot > 0)
            {
                name = name.substring(0, firstDot);
            }
            return name;
        }
        catch (UnknownHostException e)
        {
        }
        return "unknown";
    }

    private File newFile()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
        String fileName = this.buildFileName(dateFormat);
        return new File(this.fileDir, fileName);
    }

    private String buildFileName(SimpleDateFormat dateFormat)
    {
        return this.baseFileName + "-" + dateFormat.format(new Date()) + "-" + this.hostname + "-" + this.pid + "-" + ++this.counter + ".log";
    }

    private void createMissingDirectories(String fileDir)
    {
        File dir = new File(fileDir);
        if (!dir.exists())
        {
            dir.mkdirs();
        }
    }
}
