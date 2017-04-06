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

package com.gs.jrpip.util.lz4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import com.gs.jrpip.FixedDeflaterOutputStream;
import com.gs.jrpip.FixedInflaterInputStream;
import org.junit.Assert;
import org.junit.Test;

public class Lz4Test
{
    @Test
    public void testRandomlyPatternStream() throws IOException
    {
        long seed = System.currentTimeMillis();

        for(int i=0;i<100;i++)
        {
            RandomStream out = new RandomStream(seed);
            RandomStream in = new RandomStream(seed);
            while(out.hasMore())
            {
                Assert.assertEquals("setup ERROR failed for seed "+seed, out.read(), in.read());
            }
            Assert.assertEquals(-1, out.read());
            Assert.assertEquals(-1, in.read());

            RandomStream randOut = new RandomStream(seed);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
            FixedDeflaterOutputStream fixedDeflaterOutputStream = new FixedDeflaterOutputStream(bos);
            while(randOut.hasMore())
            {
                fixedDeflaterOutputStream.write(randOut.read());
            }
            fixedDeflaterOutputStream.finish();

            byte[] buf = bos.toByteArray();
            ByteArrayInputStream bis = new ByteArrayInputStream(buf);
//            System.out.println("In: "+randOut.length+" out: "+buf.length);
            FixedInflaterInputStream fixedInflaterInputStream = new FixedInflaterInputStream(bis);
            RandomStream randIn = new RandomStream(seed);
            while(randIn.hasMore())
            {
                Assert.assertEquals("failed for seed "+seed, randIn.read(), (byte) (fixedInflaterInputStream.read() & 0xFF));
            }
            Assert.assertEquals("failed for seed "+seed, -1, fixedInflaterInputStream.read());
            fixedInflaterInputStream.finish();

            seed = seed * 123456789L;
        }


    }

    @Test
    public void testRandomStream() throws IOException
    {
        // this test is almost useless. It always triggers the RAW output of LZ4.
        //failed for seed 1487195791283 expected:<82> but was:<171>
//        long seed = System.currentTimeMillis();
        long seed = 1487195791283L;
        Random randOut = new Random(seed);
        Random randIn = new Random(seed);
        for(int i=0;i<100;i++)
        {
            int length = randOut.nextInt(4*64*1024) + 1;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
            FixedDeflaterOutputStream fixedDeflaterOutputStream = new FixedDeflaterOutputStream(bos);
            for(int j=0;j<length;j++)
            {
                fixedDeflaterOutputStream.write(randOut.nextInt(256));
            }
            fixedDeflaterOutputStream.finish();

            byte[] buf = bos.toByteArray();
            ByteArrayInputStream bis = new ByteArrayInputStream(buf);
//            System.out.println("In: "+length+" out: "+buf.length);
            FixedInflaterInputStream fixedInflaterInputStream = new FixedInflaterInputStream(bis);
            length = randIn.nextInt(4*64*1024) + 1;
            for(int j=0;j<length;j++)
            {
                Assert.assertEquals("failed for seed "+seed, (byte) (randIn.nextInt(256) & 0xFF), (byte) (fixedInflaterInputStream.read() & 0xFF));
            }
            Assert.assertEquals("failed for seed "+seed, -1, fixedInflaterInputStream.read());
            fixedInflaterInputStream.finish();
        }
    }

    private static class RandomStream
    {
        private final long seed;
        private Random rand;
        private int length;
        private int readSoFar;
        private int byteOffset;
        private int byteWidth;
        private int patterns;
        private byte[][] patternBytes;
        private int currentPattern = -1;
        private int currentPatternPos;

        public RandomStream(long seed)
        {
            this.seed = seed;
            this.rand = new Random(seed);
            length = this.rand.nextInt(4*64*1024) + 1;
            this.byteWidth = this.rand.nextInt(255) + 1;
            this.byteOffset = this.rand.nextInt((256 - this.byteWidth));
            this.patterns = this.rand.nextInt(10) + 1;
            this.patternBytes = new byte[this.patterns][];
            for(int i=0;i<this.patterns;i++)
            {
                int patternLength = this.rand.nextInt(20)+2;
                this.patternBytes[i] = new byte[patternLength];
                for(int j=0;j<patternLength;j++)
                {
                    this.patternBytes[i][j] = (byte) (this.rand.nextInt(this.byteWidth) + this.byteOffset);
                }
            }
        }

        public boolean hasMore()
        {
            return this.readSoFar < this.length;
        }

        public int read()
        {
            if (this.readSoFar == this.length)
            {
                return -1;
            }
            if (currentPattern == -1 || currentPatternPos == patternBytes[currentPattern].length)
            {
                currentPattern = this.rand.nextInt(this.patterns);
                currentPatternPos = 0;
            }
            byte result = patternBytes[currentPattern][currentPatternPos];
            currentPatternPos++;
            readSoFar++;
            return (byte) result;
        }

    }
}
