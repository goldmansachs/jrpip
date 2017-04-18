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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UniqueFileOutputStreamBuilderTest
{
    public static final String FILE_DIR = "junnitTestFileDir";
    private static final String BASE = "junitTestBase";

    @Before
    public void setUp()
    {
        this.deleteTestFiles();
    }

    @After
    public void tearDown()
    {
        this.deleteTestFiles();
    }

    private void deleteTestFiles()
    {
        this.delete(new File(FILE_DIR));
    }

    private void delete(File dir)
    {
        if (dir.exists())
        {
            for(File f: dir.listFiles())
            {
                f.delete();
            }
            dir.delete();
        }
    }

    @Test
    public void testStream() throws IOException
    {
        OutputStream outputStream = new UniqueFileOutputStreamBuilder(BASE, FILE_DIR, null).newOutputStream();
        outputStream.write(1);
        outputStream.close();
        File logDir = new File(FILE_DIR);
        Assert.assertTrue(logDir.exists());
        Assert.assertEquals(1, logDir.listFiles().length);
    }
}
