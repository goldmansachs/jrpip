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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VirtualOutputStreamTest
{
    private static final String TEST_STRING = "Hello, world! My name is Alex. I work for PARA. Life is good. This_is_a_very_long_unbreakable_word._Do_not_even_think_"
            + "of_breaking_since_it_is_very_important_for_this_test.";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final Set<Integer> closedSequenceIds = new HashSet<Integer>();

    private File testFolder;

    @Before
    public void setUp() throws IOException
    {
        this.testFolder = this.folder.newFolder("test");
    }

    @Test
    public void testMultipleThreads() throws InterruptedException, ClassNotFoundException, IOException
    {
        ExecutorService executor = Executors.newFixedThreadPool(200);
        List<Callable<Object>> callables = new ArrayList();
        final VirtualOutputStream virtualOutputStream = new VirtualOutputStream("testFile", this.testFolder.getPath(), 10, 2);
        for (int i = 0; i < 200; i++)
        {
            callables.add(Executors.callable(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        for (int i = 0; i < 100; i++)
                        {
                            OutputStream outputStream = virtualOutputStream.newOutputStream();
                            final ObjectOutputStream objectOutputStreamOne = new ObjectOutputStream(outputStream);
                            for (StringTokenizer stringTokenizer = new StringTokenizer(TEST_STRING, " "); stringTokenizer.hasMoreTokens(); )
                            {
                                String token = stringTokenizer.nextToken();
                                try
                                {
                                    objectOutputStreamOne.writeObject(token + " ");
                                }
                                catch (IOException e)
                                {
                                    throw new RuntimeException(e);
                                }
                            }
                            objectOutputStreamOne.close();
                            outputStream.close();
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }));
        }
        executor.invokeAll(callables);
        virtualOutputStream.close();
        File[] files = this.testFolder.listFiles();
        Assert.assertEquals(4, files.length);
        for (int i = 0; i < files.length; i++)
        {
            this.validateFile(files[i]);
        }
        Assert.assertEquals(20000, this.closedSequenceIds.size());
    }

    private byte[] createByteArray(List<byte[]> bytes)
    {
        int size = 0;
        for (int i = 0; i < bytes.size(); i++)
        {
            size += bytes.get(i).length;
        }
        byte[] finalResult = new byte[size];
        int currentSize = 0;
        for (int i = 0; i < bytes.size(); i++)
        {
            byte[] nextBytes = bytes.get(i);
            System.arraycopy(nextBytes, 0, finalResult, currentSize, nextBytes.length);
            currentSize += nextBytes.length;
        }
        return finalResult;
    }

    private void validateFile(File file) throws IOException
    {
        DataInputStream inputStream = null;
        try
        {
            Map<Integer, List<byte[]>> data = new HashMap<Integer, List<byte[]>>();
            inputStream = new DataInputStream(new FileInputStream(file));
            Assert.assertEquals(SerialMultiplexedWriter.VERSION, inputStream.readInt());
            int nextSequenceId = inputStream.readInt();
            while (nextSequenceId >= 0)
            {
                int size = inputStream.readInt();
                if (size > 0)
                {
                    Assert.assertFalse(this.closedSequenceIds.contains(nextSequenceId));
                    byte[] newData = new byte[size];
                    inputStream.read(newData);
                    List<byte[]> bytes = data.get(nextSequenceId);
                    if (bytes == null)
                    {
                        bytes = new ArrayList<byte[]>();
                        data.put(nextSequenceId, bytes);
                    }
                    bytes.add(newData);
                }
                else
                {
                    byte[] finalResult = this.createByteArray(data.get(nextSequenceId));
                    data.remove(nextSequenceId);
                    this.closedSequenceIds.add(nextSequenceId);
                    final ObjectInputStream testInputStream = new ObjectInputStream(new ByteArrayInputStream(finalResult));
                    for (StringTokenizer stringTokenizer = new StringTokenizer(TEST_STRING, " "); stringTokenizer.hasMoreTokens(); )
                    {
                        String token = stringTokenizer.nextToken();
                        try
                        {
                            Assert.assertEquals(token, ((String) testInputStream.readObject()).trim());
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                    testInputStream.close();
                }
                nextSequenceId = inputStream.readInt();
            }
            Assert.assertTrue(data.isEmpty());
        }
        finally
        {
            if (inputStream != null)
            {
                inputStream.close();
            }
        }
    }
}
