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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gs.jrpip.util.JrpipThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VirtualOutputStreamExceptionHandlingTest
{
    private static final String TEST_STRING = "Hello, world! My name is Alex. I work for PARA. Life is good. This_is_a_very_long_unbreakable_word._Do_not_even_think_"
            + "of_breaking_since_it_is_very_important_for_this_test.";

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
        this.delete(new File("test"));
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

    private static int randomValue()
    {
        return (int) (Math.random() * 1000.0);
    }

    private static final class RandomExceptionThrowingOutputStream
            extends OutputStream
    {
        private final OutputStream delegate;

        private RandomExceptionThrowingOutputStream(OutputStream delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException
        {
            if (randomValue() == 600)
            {
                throw new IOException("test exception for write");
            }
            this.delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            if (randomValue() == 200)
            {
                throw new IOException("test exception for array write");
            }
            this.delegate.write(b, off, len);
        }

        @Override
        public void close() throws IOException
        {
            if (randomValue() > 800)
            {
                throw new IOException("test exception on close");
            }
            this.delegate.close();
        }
    }

    private static final class ExceptionThrowingUniqueFileOutputStreamBuilder
            extends UniqueFileOutputStreamBuilder
    {
        private ExceptionThrowingUniqueFileOutputStreamBuilder(
                String baseFileName,
                String fileDir,
                ExceptionHandler exceptionHandler)
        {
            super(baseFileName, fileDir, exceptionHandler);
        }

        @Override
        protected OutputStream newFileOutputStream() throws FileNotFoundException
        {
            if (randomValue() < 200)
            {
                throw new FileNotFoundException("test exception on new file");
            }
            return new RandomExceptionThrowingOutputStream(super.newFileOutputStream());
        }
    }

    @Test
    public void testException() throws IOException, InterruptedException
    {
        for (int i = 0; i < 15; i++)
        {
            this.executeTest();
        }
    }

    private void executeTest() throws InterruptedException
    {
        VirtualOutputStream virtualOutputStream = new VirtualOutputStream(new MultiOutputStreamBuilder()
        {
            public MultiOutputStream buildForExceptionHandler(ExceptionHandler exceptionHandler)
            {
                return new MultiOutputStream(new ExceptionThrowingUniqueFileOutputStreamBuilder("testFile", "test", exceptionHandler), 50, 1, exceptionHandler);
            }
        });
        List<Callable<Object>> callables = new ArrayList<Callable<Object>>();

        for (int i = 0; i < 100; i++)
        {
            callables.add(Executors.callable(this.newRunnable(virtualOutputStream)));
        }
        ExecutorService executorService = Executors.newFixedThreadPool(100, new JrpipThreadFactory("test pool"));
        try
        {
            executorService.invokeAll(callables);
            virtualOutputStream.close();
        }
        finally
        {
            executorService.shutdown();
        }
    }

    private Runnable newRunnable(final OutputStreamBuilder outputStreamBuilder)
    {
        return new Runnable()
        {
            public void run()
            {
                try
                {
                    OutputStream outputStream = outputStreamBuilder.newOutputStream();
                    final ObjectOutputStream objectOutputStreamOne = new ObjectOutputStream(outputStream);
                    for (int i = 0; i < 1000; i++)
                    {
                        for (StringTokenizer stringTokenizer = new StringTokenizer(TEST_STRING, " "); stringTokenizer.hasMoreTokens(); )
                        {
                            String token = stringTokenizer.nextToken();
                            try
                            {
                                objectOutputStreamOne.writeObject(token + " ");
                            }
                            catch (IOException e)
                            {
                                throw new RuntimeException();
                            }
                        }
                    }
                    objectOutputStreamOne.close();
                    outputStream.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
