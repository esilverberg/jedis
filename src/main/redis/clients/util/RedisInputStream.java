/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package redis.clients.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

// SCRUFF added 8/2018 per exceptions in Crashlytics and based on modern rewrites of this class
// see https://github.com/xetorthio/jedis/blob/master/src/main/java/redis/clients/jedis/util/RedisInputStream.java
public class RedisInputStream extends FilterInputStream {

    protected final byte buf[];

    protected int count, limit;

    public RedisInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    public RedisInputStream(InputStream in) {
        this(in, 8192);
    }

    public byte readByte() throws JedisConnectionException {
        ensureFill();
        return buf[count++];
    }

    public String readLine() {
        final StringBuilder sb = new StringBuilder();
        while (true) {
            ensureFill();

            byte b = buf[count++];
            if (b == '\r') {
                ensureFill(); // Must be one more byte

                byte c = buf[count++];
                if (c == '\n') {
                    break;
                }
                sb.append((char) b);
                sb.append((char) c);
            } else {
                sb.append((char) b);
            }
        }

        final String reply = sb.toString();
        if (reply.length() == 0) {
            throw new JedisConnectionException("It seems like server has closed the connection.");
        }

        return reply;
    }

    @Override
    public int read(byte[] b, int off, int len) throws JedisConnectionException {
        ensureFill();

        final int length = Math.min(limit - count, len);
        System.arraycopy(buf, count, b, off, length);
        count += length;
        return length;
    }

    private void ensureFill() throws JedisConnectionException {
        if (count >= limit) {
            try {
                limit = in.read(buf);
                count = 0;
                if (limit == -1) {
                    throw new JedisConnectionException("Unexpected end of stream.");
                }
            } catch (IOException e) {
                throw new JedisConnectionException(e);
            }
        }
    }
}
