package com.gs.jrpip.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthGenerator
{
    private static final Base32String INSTANCE =
            new Base32String("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"); // RFC 4648

    private static final AtomicInteger counter = new AtomicInteger(new SecureRandom().nextInt());

    public static byte[] decode(String encoded) throws ParseException
    {
        return getInstance().decode(encoded);
    }

    public static long createChallenge()
    {
        return (System.currentTimeMillis() << 20) | (counter.incrementAndGet() & ((1 << 20) - 1));
    }

    private CodeGenerator generator;

    public AuthGenerator(byte[] token) throws GeneralSecurityException
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token, ""));
        this.generator = new CodeGenerator(mac);
    }

    public boolean verifyChallenge(long challenge, int encoded)
    {
        long timePart = challenge >>> 20;
        long now = System.currentTimeMillis();
        if (timePart < now - 2000 || timePart > now + 2000)
        {
            return false;
        }
        return encoded == this.generator.generateResponseCode(challenge);
    }

    public byte[] generateKeyIv(long challenge)
    {
        challenge ^= challenge >>> 23;
        challenge *= -6261870919139520145L;
        challenge ^= challenge >>> 39;
        challenge *= 2747051607443084853L;
        challenge ^= challenge >>> 37;

        return this.generator.hash(challenge);
    }

    public int authCode(long challenge)
    {
        return this.generator.generateResponseCode(challenge);
    }

    static Base32String getInstance()
    {
        return INSTANCE;
    }

    public static class CodeGenerator
    {
        private final Mac mac;

        public CodeGenerator(final Mac mac)
        {
            this.mac = mac;
        }

        public int generateResponseCode(long challenge)
        {
            byte[] value = ByteBuffer.allocate(8).putLong(challenge).array();
            return generateResponseCode(value);
        }

        public byte[] hash(long challenge)
        {
            byte[] value = ByteBuffer.allocate(8).putLong(challenge).array();
            return mac.doFinal(value);
        }

        public int generateResponseCode(byte[] challenge)
        {
            byte[] hash = mac.doFinal(challenge);

            int offset = hash[hash.length - 1] & 0xF;
            return hashToInt(hash, offset);
        }

        private int hashToInt(byte[] bytes, int start)
        {
            int ch1 = ((int)bytes[start]) & 0xFF;
            int ch2 = ((int)bytes[start + 1]) & 0xFF;
            int ch3 = ((int)bytes[start + 2]) & 0xFF;
            int ch4 = ((int)bytes[start + 3]) & 0xFF;
            return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
        }
    }

    public static class Base32String
    {
        private static final String SEPARATOR = "-";

        //32 alpha-numeric characters. RFC 4648
        private final String alphabet;
        private final char[] digits;
        private final int mask;
        private final int shift;
        private final HashMap<Character, Integer> charMap;


        protected Base32String(String alphabet)
        {
            this.alphabet = alphabet;
            digits = this.alphabet.toCharArray();
            mask = digits.length - 1;
            shift = Integer.numberOfTrailingZeros(digits.length);
            charMap = new HashMap<>();
            for (int i = 0; i < digits.length; i++)
            {
                charMap.put(digits[i], i);
            }
        }

        protected byte[] decode(String encoded) throws ParseException
        {
            // Remove whitespace and separators
            encoded = encoded.trim().replaceAll(SEPARATOR, "").replaceAll(" ", "");
            encoded = encoded.toUpperCase();
            if (encoded.length() == 0)
            {
                return new byte[0];
            }
            int encodedLength = encoded.length();
            int outLength = encodedLength * shift / 8;
            byte[] result = new byte[outLength];
            int buffer = 0;
            int next = 0;
            int bitsLeft = 0;
            char[] chars = encoded.toCharArray();
            for (int i=0;i<chars.length;i++)
            {
                char c = chars[i];
                if (!charMap.containsKey(c))
                {
                    throw new ParseException("Illegal character: " + c+" at pos "+i, i);
                }
                buffer <<= shift;
                buffer |= charMap.get(c) & mask;
                bitsLeft += shift;
                if (bitsLeft >= 8)
                {
                    result[next++] = (byte) (buffer >> (bitsLeft - 8));
                    bitsLeft -= 8;
                }
            }
            return result;
        }
    }
}
