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

import io.informant.util.DaemonExecutors;
import io.informant.util.OnlyUsedByTests;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.SyncFailedException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Needs to be externally synchronized around startBlock()/write()/endBlock().
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
class RollingOutputStream extends OutputStream {

    static final int HEADER_SKIP_BYTES = 20;

    private static final Logger logger = LoggerFactory.getLogger(RollingOutputStream.class);

    private static final int FSYNC_INTERVAL_MILLIS = 2000;
    private static final int HEADER_CURR_INDEX_POS = 0;

    private final ScheduledExecutorService scheduledExecutor = DaemonExecutors
            .newSingleThreadScheduledExecutor("Informant-RollingOutputFsync");

    private final File file;
    private RandomAccessFile out;

    // currIndex is ever-increasing even over rolling boundary
    // (btw it would take writing 2.9g per second for 100 years for currIndex to hit Long.MAX_VALUE)
    private long currIndex;
    // lastCompactionBaseIndex is the smallest currIndex saved during the last compaction
    private long lastCompactionBaseIndex;
    // currPosition is the current position in the file
    private long currPosition;
    // rollingSizeKb is volatile so it can be read outside of the external synchronization around
    // startBlock()/write()/endBlock()
    private volatile int rollingSizeKb;
    private long rollingSizeBytes;

    private long blockStartIndex;

    private final AtomicBoolean fsyncNeeded = new AtomicBoolean();

    RollingOutputStream(File file, int requestedRollingSizeKb) throws IOException {
        this.file = file;
        boolean newFile = !file.exists() || file.length() == 0;
        out = new RandomAccessFile(file, "rw");
        if (newFile) {
            currIndex = 0;
            rollingSizeKb = requestedRollingSizeKb;
            rollingSizeBytes = rollingSizeKb * 1024L;
            lastCompactionBaseIndex = 0;
            out.writeLong(currIndex);
            out.writeInt(rollingSizeKb);
            out.writeLong(lastCompactionBaseIndex);
        } else {
            currIndex = out.readLong();
            // have to ignore requested fixedLength for existing files, must explicitly call
            // resize() since this can be an expensive operation
            rollingSizeKb = out.readInt();
            rollingSizeBytes = rollingSizeKb * 1024L;
            lastCompactionBaseIndex = out.readLong();
            currPosition = (currIndex - lastCompactionBaseIndex) % rollingSizeBytes;
            out.seek(HEADER_SKIP_BYTES + currPosition);
        }
        scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                if (fsyncNeeded.getAndSet(false)) {
                    try {
                        out.getFD().sync();
                    } catch (SyncFailedException e) {
                        logger.error(e.getMessage(), e);
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }, FSYNC_INTERVAL_MILLIS, FSYNC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    void startBlock() {
        blockStartIndex = currIndex;
    }

    FileBlock endBlock() {
        fsyncNeeded.set(true);
        return FileBlock.from(blockStartIndex, currIndex - blockStartIndex);
    }

    boolean stillExists(FileBlock block) {
        // must be more recent than the last compaction base and also not have been rolled over
        return block.getStartIndex() >= lastCompactionBaseIndex
                && currIndex - block.getStartIndex() <= rollingSizeBytes;
    }

    // this is ok to read outside of external synchronization around startBlock()/write()/endBlock()
    int getRollingSizeKb() {
        return rollingSizeKb;
    }

    long convertToFilePosition(long index) {
        return (index - lastCompactionBaseIndex) % rollingSizeBytes;
    }

    // perform resize in-place to avoid using extra disk space
    void resize(int newRollingSizeKb) throws IOException {
        if (newRollingSizeKb == rollingSizeKb) {
            return;
        }
        long newRollingSizeBytes = newRollingSizeKb * 1024L;
        if (newRollingSizeKb < rollingSizeKb
                && currIndex - lastCompactionBaseIndex < newRollingSizeBytes) {
            // resizing smaller and on first "loop" after a compaction and haven't written up to the
            // new smaller size yet
            rollingSizeKb = newRollingSizeKb;
            rollingSizeBytes = newRollingSizeBytes;
            return;
        } else if (newRollingSizeKb > rollingSizeKb
                && currIndex - lastCompactionBaseIndex < rollingSizeBytes) {
            // resizing larger and on first "loop" after a compaction
            rollingSizeKb = newRollingSizeKb;
            rollingSizeBytes = newRollingSizeBytes;
            return;
        }
        // keep the min of the current and new rolling size
        int numKeepKb = Math.min(rollingSizeKb, newRollingSizeKb);
        long numKeepBytes = numKeepKb * 1024L;
        // at this point, because of the two shortcut conditionals above, currIndex must be >=
        // either the current or new rolling size (numKeepBytes)
        long startPosition = convertToFilePosition(currIndex - numKeepBytes);
        lastCompactionBaseIndex = currIndex - numKeepBytes;
        File tmpRollingFile = new File(file.getPath() + ".resizing.tmp");
        RandomAccessFile tmpOut = new RandomAccessFile(tmpRollingFile, "rw");
        tmpOut.writeLong(currIndex);
        tmpOut.writeInt(newRollingSizeKb);
        tmpOut.writeLong(lastCompactionBaseIndex);
        long remaining = rollingSizeBytes - startPosition;
        if (numKeepBytes > remaining) {
            out.seek(HEADER_SKIP_BYTES + startPosition);
            RandomAccessFiles.copyFully(out, tmpOut, remaining);
            out.seek(HEADER_SKIP_BYTES);
            RandomAccessFiles.copyFully(out, tmpOut, numKeepBytes - remaining);
        } else {
            out.seek(HEADER_SKIP_BYTES + startPosition);
            RandomAccessFiles.copyFully(out, tmpOut, numKeepBytes);
        }
        out.close();
        tmpOut.close();
        if (!file.delete()) {
            throw new IOException("Unable to delete existing rolling file during resize");
        }
        if (!tmpRollingFile.renameTo(file)) {
            throw new IOException("Unable to rename new rolling file during resize");
        }
        rollingSizeKb = newRollingSizeKb;
        rollingSizeBytes = newRollingSizeBytes;
        if (numKeepBytes == newRollingSizeBytes) {
            // shrunk and filled up file
            currPosition = 0;
        } else {
            currPosition = numKeepBytes;
        }
        out = new RandomAccessFile(file, "rw");
        out.seek(HEADER_SKIP_BYTES + currPosition);
    }

    @OnlyUsedByTests
    void sync() throws IOException {
        out.getFD().sync();
    }

    @Override
    public void close() throws IOException {
        super.close();
        scheduledExecutor.shutdownNow();
        out.close();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (currIndex + len - blockStartIndex > rollingSizeBytes) {
            throw new IOException("A single block cannot have more bytes than size of the rolling"
                    + " file");
        }
        // update header before writing data in case of abnormal shutdown during writing data
        currIndex += len;
        out.seek(HEADER_CURR_INDEX_POS);
        out.writeLong(currIndex);
        out.seek(HEADER_SKIP_BYTES + currPosition);

        long remaining = rollingSizeBytes - currPosition;
        if (len >= remaining) {
            // intentionally handling == case here
            out.write(b, off, (int) remaining);
            out.seek(HEADER_SKIP_BYTES);
            out.write(b, (int) remaining, (int) (len - remaining));
            currPosition = len - remaining;
        } else {
            out.write(b, off, len);
            currPosition += len;
        }
    }
}