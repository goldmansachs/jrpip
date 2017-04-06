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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import com.gs.jrpip.JrpipTestCase;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JrpipLogTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testIsPrintable()
    {
        String[] arguments = {"-f", "testInputFile.txt", "-id", "1,2,3", "-opt", "myOption"};
        Set<String> availableOptions = JrpipTestCase.newSetWith("-f", "-id", "-opt");
        JrpipLog jrpipLog = new JrpipLog(arguments, availableOptions);

        Assert.assertEquals(jrpipLog.parseInputFileName(false), jrpipLog.getValueForOption("-f", false));
        Assert.assertArrayEquals(jrpipLog.parseStreamIdSet(true).toArray(), JrpipTestCase.newSetWith(1, 2, 3).toArray());
        Assert.assertEquals("myOption", jrpipLog.getValueForOption("-opt", false));
    }

    @Test
    public void testOpenFile() throws Exception
    {
        File logfile = this.folder.newFile();
        String logfileStr = logfile.getCanonicalPath();

        JrpipLog jrpipLog = this.createJrpipLog(new String[]{"-f", logfileStr}, new String[]{"-f"});
        FileInputStream finStream = null;
        try
        {
            finStream = jrpipLog.openFile(logfileStr);
            Assert.assertNotNull(finStream);
        }
        finally
        {
            jrpipLog.closeCloseable(finStream);
        }
    }

    @Test
    public void failedCloseShouldNotThrow()
    {
        this.createJrpipLog(new String[]{"-f", "testInputFile.txt"}, new String[]{"-f"}).closeCloseable(new Closeable()
        {
            public void close() throws IOException
            {
                throw new RuntimeException("Should not fail!");
            }
        });
        Assert.assertTrue(true);
    }

    @Test(expected = RuntimeException.class)
    public void testMandatoryOptionCheck()
    {
        this.createJrpipLog(new String[]{"-f", "testInputFile.txt"}, new String[]{"-f", "-id"}).parseStreamIdSet(false);
    }

    @Test(expected = NumberFormatException.class)
    public void testIdNumberErrorCheck()
    {
        this.createJrpipLog(new String[]{"-id", "a,b,c"}, new String[]{"-id"}).parseStreamIdSet(true);
    }

    @Test(expected = RuntimeException.class)
    public void testMalformedOptionCheck()
    {
        String[] arguments = {"-bad"};
        Set<String> availableOptions = JrpipTestCase.newSetWith("-bad");
        new JrpipLog(arguments, availableOptions);
    }

    @Test(expected = RuntimeException.class)
    public void testUnsupportedOptionCheck()
    {
        String[] arguments = {"-unsupported", "not_supported"};
        Set<String> availableOptions = JrpipTestCase.newSetWith("");
        JrpipLog jrpipLog = new JrpipLog(arguments, availableOptions);
        jrpipLog.getValueForOption("-unsupported", false);
    }

    @Test(expected = FileNotFoundException.class)
    public void testFileNotExistsCheck() throws FileNotFoundException
    {
        String[] arguments = {"-f", "doesNotExists.txt"};
        Set<String> availableOptions = JrpipTestCase.newSetWith("-f");
        JrpipLog jrpipLog = new JrpipLog(arguments, availableOptions);
        jrpipLog.openFile("doesNotExists.txt");
    }

    @Test
    public void testJrpipLogWithCustomLogger()
    {
        String[] arguments = {"-id", "1,2,3", "-writer", "com.gs.jrpip.logger.MockCustomizedResponseHandler"};
        String[] options = {"-id", "-writer"};
        JrpipLog jrpipLog = this.createJrpipLog(arguments, options);
        CustomizedResponseHandler responseHandler = jrpipLog.getResponseDataWriter();
        Assert.assertSame(responseHandler.getClass(), MockCustomizedResponseHandler.class);

        String[] arguments2 = {"-id", "1,2,3", "-writer", DummyLoggerWithParams.class.getName() + ",DummyLogger"};
        JrpipLog jrpipLog2 = this.createJrpipLog(arguments2, options);
        CustomizedResponseHandler responseHandler2 = jrpipLog2.getResponseDataWriter();
        Assert.assertSame(responseHandler2.getClass(), DummyLoggerWithParams.class);
    }

    private JrpipLog createJrpipLog(String[] arguments, String[] availableOptions)
    {
        return new JrpipLog(arguments, JrpipTestCase.newSetWith(availableOptions));
    }

    public static class DummyLoggerWithParams implements CustomizedResponseHandler
    {
        private final String identifier;

        public DummyLoggerWithParams(String identifier)
        {
            this.identifier = identifier;
        }

        public String getIdentifier()
        {
            return this.identifier;
        }

        @Override
        public boolean handlesObject(Object data)
        {
            return false;
        }

        @Override
        public void writeObject(Object data, XmlFileWriter out)
        {
        }
    }
}
