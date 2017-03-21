//
// --------------------------------------------------------------------------
//  Gurux Ltd
// 
//
//
// Filename:        $HeadURL$
//
// Version:         $Revision$,
//                  $Date$
//                  $Author$
//
// Copyright (c) Gurux Ltd
//
//---------------------------------------------------------------------------
//
//  DESCRIPTION
//
// This file is a part of Gurux Device Framework.
//
// Gurux Device Framework is Open Source software; you can redistribute it
// and/or modify it under the terms of the GNU General Public License 
// as published by the Free Software Foundation; version 2 of the License.
// Gurux Device Framework is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
// See the GNU General Public License for more details.
//
// More information of Gurux products: http://www.gurux.org
//
// This code is licensed under the GNU General Public License v2. 
// Full text may be retrieved at http://www.gnu.org/licenses/gpl-2.0.txt
//---------------------------------------------------------------------------

package gurux.common;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

import gurux.common.enums.TraceLevel;

/**
 * This class is used to handle synchronous data sending and receiving.
 *
 * @author Gurux Ltd.
 */
public class GXSynchronousMediaBase {

    /**
     * Trace level.
     */
    private gurux.common.enums.TraceLevel mTrace;

    /**
     * Occurred exception.
     */
    private RuntimeException mException;
    /**
     * Received bytes.
     */
    private byte[] mReceivedBuffer = null;
    /**
     * Received event.
     */
    private AutoResetEvent mReceivedEvent = new AutoResetEvent(false);
    /**
     * Synchronous object.
     */
    private final Object mSync = new Object();
    /**
     * Amount of received bytes.
     */
    private int mReceivedSize;
    /**
     * Position where end of packet was last time search. This is used to
     * improve searching.
     */
    private int mLastPosition;

    /**
     * Reset last position.
     */
    public final void resetLastPosition() {
        synchronized (mSync) {
            mLastPosition = 0;
        }
    }

    /**
     * Reset received size.
     */
    public final void resetReceivedSize() {
        mReceivedSize = 0;
    }

    /**
     * Set received event.
     */
    public final void setReceived() {
        mReceivedEvent.set();
    }

    /**
     * Get occurred exception.
     *
     * @return Occurred exception.
     */
    public final RuntimeException getException() {
        return mException;
    }

    /**
     * Set occurred exception.
     *
     * @param value Occurred exception.
     */
    public final void setException(final RuntimeException value) {
        mException = value;
    }

    /**
     * Get synchronous object.
     *
     * @return Synchronous object.
     */
    public final Object getSync() {
        return mSync;
    }

    /**
     * Get trace level.
     *
     * @return Current trace level.
     */
    public final TraceLevel getTrace() {
        return mTrace;
    }

    /**
     * Set trace level.
     *
     * @param value New trace level.
     */
    public final void setTrace(final TraceLevel value) {
        mTrace = value;
    }

    /**
     * Get data as byte array.
     *
     * @param value Data object
     * @return Values as byte array.
     */
    public static byte[] getAsByteArray(final Object value) {
        if (value == null) {
            return new byte[0];
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof Byte) {
            return new byte[]{((Byte) value)};
        }
        if (value instanceof Short) {
            return ByteBuffer.allocate(GXCommon.SHORT_BYTES)
                    .putInt((Short) value).array();
        }

        if (value instanceof Integer) {
            return ByteBuffer.allocate(GXCommon.INTEGER_BYTES)
                    .putInt((Integer) value).array();
        }
        if (value instanceof String) {
            try {
                return java.nio.ByteBuffer
                        .wrap(((String) value).getBytes("ASCII")).array();
            } catch (UnsupportedEncodingException ex) {
                throw new RuntimeException(ex.getMessage());
            }
        }
        throw new RuntimeException(
                "Unknown data type " + value.getClass().getName());
    }

    /**
     * Convert byte array to object.
     *
     * @param value     Byte array.
     * @param type      Type to convert.
     * @param readBytes Amount of read bytes.
     * @return Created object.
     */
    public static Object byteArrayToObject(final byte[] value, final Type type,
                                           final int[] readBytes) {
        if (type == byte[].class) {
            readBytes[0] = value.length;
            return value;
        }
        if (type == Byte.class) {
            readBytes[0] = 1;
            return value[0];
        }
        java.nio.ByteBuffer buff = java.nio.ByteBuffer.wrap(value);
        if (type == Short.class) {
            readBytes[0] = GXCommon.SHORT_BYTES;
            buff.getShort();
        }
        if (type == Integer.class) {
            readBytes[0] = GXCommon.INTEGER_BYTES;
            buff.getInt();
        }
        if (type == Long.class) {
            readBytes[0] = GXCommon.LONG_BYTES;
            buff.getLong();
        }
        if (type == String.class) {
            readBytes[0] = value.length;
            return new String(value);
        }
        throw new RuntimeException("Invalid object type.");
    }

    /**
     * Construcctor.
     *
     * @param bufferSize Buffer size.
     */
    public GXSynchronousMediaBase(final int bufferSize) {
        mReceivedBuffer = new byte[bufferSize];
        mLastPosition = 0;
    }

    /**
     * Append new data.
     *
     * @param data  data to append.
     * @param index Index where start.
     * @param count Count of bytes to add.
     */
    public final void appendData(final byte[] data, final int index,
                                 final int count) {
        synchronized (mSync) {
            // Allocate new buffer.
            if (mReceivedSize + count > mReceivedBuffer.length) {
                byte[] tmp = new byte[2 * mReceivedBuffer.length];
                System.arraycopy(mReceivedBuffer, 0, tmp, 0, mReceivedSize);
                mReceivedBuffer = tmp;
            }
            System.arraycopy(data, index, mReceivedBuffer, mReceivedSize, count);
            mReceivedSize += count - index;
        }
    }

    /**
     * Finds the first occurrence of the pattern in the text.
     *
     * @param data    Data where to find.
     * @param pattern Search pattern.
     * @param index   Byte index to start.
     * @param count   Count of bytes to search.
     * @return Is pattern found.
     */
    public static int indexOf(final byte[] data, final byte[] pattern,
                              final int index, final int count) {
        int[] failure = computeFailure(pattern);

        int j = 0;
        if (data.length == 0 || data.length < index) {
            return -1;
        }

        for (int i = index; i < count; i++) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == data[i]) {
                j++;
            }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process, where the
     * pattern is matched against itself.
     *
     * @param pattern Pattern to search.
     * @return Failure pattern.
     */
    private static int[] computeFailure(final byte[] pattern) {
        int[] failure = new int[pattern.length];
        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j > 0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }
        return failure;
    }

    /**
     * Find data from buffer.
     *
     * @param <T>     Data type to search.
     * @param args    Receive parameters.
     * @param isFound Is data found in given time.
     * @return Position where end of packet was found. -1 Is returned if data
     * was not found in given time.
     */
    private <T> int findData(final ReceiveParameters<T> args,
                             final boolean[] isFound) {
        boolean isReceived;
        int nSize = 0, foundPosition = -1;
        int lastBuffSize = 0;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        long startTime = calendar.getTime().getTime();
        byte[] terminator = null;
        int nMinSize = (int) Math.max(args.getCount(), nSize);
        int waitTime = args.getWaitTime();
        this.mException = null;
        if (waitTime <= 0) {
            waitTime = -1;
        }
        isFound[0] = true;
        if (args.getEop() != null) {
            if (args.getEop() instanceof Array) {
                terminator = getAsByteArray(Array.get(args.getEop(), 0));
            } else {
                terminator = getAsByteArray(args.getEop());
            }
            nSize = terminator.length;
        }

        // Wait until reply occurred.
        do {
            if (waitTime == 0) {
                isFound[0] = false;
                // If we want to read all data.
                if (args.getAllData()) {
                    foundPosition = mReceivedSize;
                } else {
                    foundPosition = -1;
                }
                break;
            }
            if (waitTime != -1) {
                waitTime = (int) (args.getWaitTime()
                        - (startTime - calendar.getTime().getTime()));
                if (waitTime < 0) {
                    waitTime = 0;
                }
            }
            synchronized (mSync) {
                isReceived = !(lastBuffSize == mReceivedSize
                        || mReceivedSize < nMinSize);
            }
            // Do not wait if there is data on the buffer...
            if (!isReceived) {
                if (waitTime == -1) {
                    isReceived = mReceivedEvent.waitOne();
                } else if (waitTime != 0) {
                    isReceived = mReceivedEvent.waitOne(waitTime);
                }
            }
            if (this.mException != null) {
                throw this.mException;
            }
            // If timeout occurred.
            if (!isReceived) {
                isFound[0] = false;
                // If we want to read all data.
                if (args.getAllData()) {
                    foundPosition = mReceivedSize;
                } else {
                    foundPosition = -1;
                }
                break;
            }
            synchronized (mSync) {
                lastBuffSize = mReceivedSize;
                // Read more data, if not enough.
                if (mReceivedSize < nMinSize) {
                    continue;
                }
                // If only byte count matters.
                if (nSize == 0) {
                    foundPosition = args.getCount();
                } else {
                    int index;
                    if (mLastPosition != 0 && mLastPosition < mReceivedSize) {
                        index = mLastPosition;
                    } else {
                        index = args.getCount();
                    }
                    // If terminator found.
                    if (args.getEop() instanceof Array) {
                        for (Object it : (Object[]) args.getEop()) {
                            byte[] term = getAsByteArray(it);
                            if (term.length != 1
                                    && mReceivedSize - index < term.length) {
                                index = mReceivedSize - term.length;
                            }
                            foundPosition = indexOf(mReceivedBuffer, term, index,
                                    mReceivedSize);
                            if (foundPosition != -1) {
                                break;
                            }
                        }
                    } else {
                        if (terminator.length != 1
                                && mReceivedSize - index < terminator.length) {
                            index = mReceivedSize - terminator.length;
                        }
                        foundPosition = indexOf(mReceivedBuffer, terminator,
                                index, mReceivedSize);
                    }
                    mLastPosition = mReceivedSize;
                    if (foundPosition != -1) {
                        foundPosition += terminator.length;
                    }
                }
            }
        } while (foundPosition == -1);
        // If terminator is not given read only bytes that are needed.
        if (nSize == 0) {
            foundPosition = args.getCount();
        }
        return foundPosition;
    }

    /**
     * Receive new data synchronously from the media.
     *
     * @param <T>  Used media component.
     * @param args Receive parameters.
     * @return Is new data received.
     */
    public final <T> boolean receive(final ReceiveParameters<T> args) {
        if (args.getEop() == null && args.getCount() == 0) {
            throw new IllegalArgumentException(
                    "Either Count or Eop must be set.");
        }
        boolean[] retValue = new boolean[1];
        int foundPosition = findData(args, retValue);
        Object data = null;
        if (foundPosition != -1) {

            synchronized (mSync) {
                if (args.getAllData()) {
                    // If all data is copied.
                    foundPosition = mReceivedSize;
                }
                if (foundPosition != 0) {
                    // Convert bytes to object.
                    byte[] tmp = new byte[foundPosition];
                    System.arraycopy(mReceivedBuffer, 0, tmp, 0, foundPosition);
                    int[] readBytes = new int[1];
                    data = byteArrayToObject(tmp, args.getReplyType(),
                            readBytes);
                    // Remove read data.
                    mReceivedSize -= foundPosition;
                    // Received size can go less than zero if we have received
                    // data
                    // and we try to read more.
                    if (mReceivedSize < 0) {
                        mReceivedSize = 0;
                    }
                    if (mReceivedSize != 0) {
                        System.arraycopy(mReceivedBuffer, foundPosition,
                                mReceivedBuffer, 0, mReceivedSize);
                    }
                }
            }
            // Reset count after read.
            args.setCount(0);
            // Append data.
            int oldReplySize;
            if (args.getReply() == null) {
                args.setReply(data);
            } else {
                if (args.getReply() instanceof String) {
                    String str = (String) args.getReply();
                    str += (String) data;
                    data = str;
                    args.setReply(data);
                } else if (args.getReply() instanceof byte[]) {
                    byte[] oldArray = (byte[]) args.getReply();
                    byte[] newArray = (byte[]) data;
                    if (newArray != null) {
                        oldReplySize = Array.getLength(oldArray);
                        int len = oldReplySize + Array.getLength(newArray);
                        byte[] arr = new byte[len];
                        // Copy old values.
                        System.arraycopy((byte[]) args.getReply(), 0, arr, 0,
                                Array.getLength(oldArray));
                        // Copy new values.
                        System.arraycopy(newArray, 0, arr,
                                Array.getLength(oldArray),
                                Array.getLength(newArray));
                        args.setReply(arr);
                    }
                } else {
                    throw new RuntimeException("Invalid reply type");
                }
            }
        }
        return retValue[0];
    }
}