/**
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.local.store;

import io.informant.util.ByteStream;
import io.informant.util.NotThreadSafe;
import io.informant.util.ThreadSafe;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import checkers.lock.quals.GuardedBy;

import com.google.common.base.Charsets;
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
public class RollingFile {

    private static final Logger logger = LoggerFactory.getLogger(RollingFile.class);

    private final File file;
    @GuardedBy("lock")
    private final RollingOutputStream out;
    @GuardedBy("lock")
    private final OutputStream compressedOut;
    @GuardedBy("lock")
    private RandomAccessFile inFile;
    private final Object lock = new Object();
    private volatile boolean closing = false;

    RollingFile(File file, int requestedRollingSizeKb) throws IOException {
        this.file = file;
        out = new RollingOutputStream(file, requestedRollingSizeKb);
        compressedOut = new LZFOutputStream(out);
        inFile = new RandomAccessFile(file, "r");
    }

    FileBlock write(ByteStream byteStream) {
        synchronized (lock) {
            if (closing) {
                return FileBlock.expired();
            }
            out.startBlock();
            try {
                byteStream.writeTo(compressedOut);
                compressedOut.flush();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                return FileBlock.expired();
            }
            return out.endBlock();
        }
    }

    ByteStream read(FileBlock block, String rolledOverResponse) {
        return new FileBlockByteStream(block, rolledOverResponse);
    }

    public void resize(int newRollingSizeKb) throws IOException {
        synchronized (lock) {
            if (closing) {
                return;
            }
            inFile.close();
            out.resize(newRollingSizeKb);
            inFile = new RandomAccessFile(file, "r");
        }
    }

    void close() throws IOException {
        logger.debug("close()");
        synchronized (lock) {
            closing = true;
            out.close();
            inFile.close();
        }
    }

    @NotThreadSafe
    private class FileBlockByteStream extends ByteStream {

        private final FileBlock block;
        private final String rolledOverResponse;
        private boolean end;

        private FileBlockByteStream(FileBlock block, String rolledOverResponse) {
            this.block = block;
            this.rolledOverResponse = rolledOverResponse;
        }

        @Override
        public boolean hasNext() {
            if (block.getLength() > Integer.MAX_VALUE) {
                // TODO read and lzf decode bytes in chunks to avoid having to allocate a single
                // large byte array
                logger.error("cannot currently read more than Integer.MAX_VALUE bytes");
                return false;
            }
            return !end;
        }

        @Override
        public byte[] next() throws IOException {
            synchronized (lock) {
                if (!out.stillExists(block)) {
                    end = true;
                    return rolledOverResponse.getBytes(Charsets.UTF_8.name());
                }
                long filePosition = out.convertToFilePosition(block.getStartIndex());
                inFile.seek(RollingOutputStream.HEADER_SKIP_BYTES + filePosition);
                byte[] bytes = new byte[(int) block.getLength()];
                long remaining = out.getRollingSizeKb() * 1024 - filePosition;
                if (block.getLength() > remaining) {
                    RandomAccessFiles.readFully(inFile, bytes, 0, (int) remaining);
                    inFile.seek(RollingOutputStream.HEADER_SKIP_BYTES);
                    RandomAccessFiles.readFully(inFile, bytes, (int) remaining,
                            (int) (block.getLength() - remaining));
                } else {
                    RandomAccessFiles.readFully(inFile, bytes);
                }
                end = true;
                return LZFDecoder.decode(bytes);
            }
        }
    }
}