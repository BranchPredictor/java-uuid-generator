package com.fasterxml.uuid.impl;

import java.util.UUID;

import com.fasterxml.uuid.*;

/**
 * Implementation of UUID generator that uses time/location based generation
 * method field compatible with UUIDv1, reorderd for improved DB locality.
 * This is usually referred to as "Version 6".
 * <p>
 * As all JUG provided implementations, this generator is fully thread-safe.
 * Additionally it can also be made externally synchronized with other instances
 * (even ones running on other JVMs); to do this, use
 * {@link com.fasterxml.uuid.ext.FileBasedTimestampSynchronizer} (or
 * equivalent).
 *
 * @since 4.1
 */
public class TimeBasedReorderedGenerator extends NoArgGenerator
{
    public static int BYTE_OFFSET_TIME_HIGH = 0;
    public static int BYTE_OFFSET_TIME_MID = 4;
    public static int BYTE_OFFSET_TIME_LOW = 7;

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected final EthernetAddress _ethernetAddress;

    /**
     * Object used for synchronizing access to timestamps, to guarantee
     * that timestamps produced by this generator are unique and monotonically increasings.
     * Some implementations offer even stronger guarantees, for example that
     * same guarantee holds between instances running on different JVMs (or
     * with native code).
     */
    protected final UUIDTimer _timer;

    /**
     * Base values for the second long (last 8 bytes) of UUID to construct
     */
    protected final long _uuidL2;
    
    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /**
     * @param ethAddr Hardware address (802.1) to use for generating
     *   spatially unique part of UUID. If system has more than one NIC,
     */
    
    public TimeBasedReorderedGenerator(EthernetAddress ethAddr, UUIDTimer timer)
    {
        byte[] uuidBytes = new byte[16];
        if (ethAddr == null) {
            ethAddr = EthernetAddress.constructMulticastAddress();
        }
        // initialize baseline with MAC address info
        _ethernetAddress = ethAddr;
        _ethernetAddress.toByteArray(uuidBytes, 10);
        // and add clock sequence
        int clockSeq = timer.getClockSequence();
        uuidBytes[UUIDUtil.BYTE_OFFSET_CLOCK_SEQUENCE] = (byte) (clockSeq >> 8);
        uuidBytes[UUIDUtil.BYTE_OFFSET_CLOCK_SEQUENCE+1] = (byte) clockSeq;
        long l2 = UUIDUtil.gatherLong(uuidBytes, 8);
        _uuidL2 = UUIDUtil.initUUIDSecondLong(l2);
        _timer = timer;
    }
    
    /*
    /**********************************************************************
    /* Access to config
    /**********************************************************************
     */

    @Override
    public UUIDType getType() { return UUIDType.TIME_BASED_REORDERED; }

    public EthernetAddress getEthernetAddress() { return _ethernetAddress; }
    
    /*
    /**********************************************************************
    /* UUID generation
    /**********************************************************************
     */

    @Override
    public UUID generate()
    {
        // Ok, get 60-bit timestamp (4 MSB are ignored)
        final long rawTimestamp = _timer.getTimestamp();
        return construct(rawTimestamp);
    }

    /**
     * @since 4.3
     * @param rawTimestamp unix epoch millis
     * @return unix epoch time based UUID
     */
    public UUID construct(long rawTimestamp)
    {
        // First: discard 4 MSB, next 32 bits (top of 60-bit timestamp) form the
        // highest 32-bit segments
        final long timestampHigh = (rawTimestamp >>> 28) << 32;
        // and then bottom 28 bits split into mid (16 bits), low (12 bits)
        final int timestampLow = (int) rawTimestamp;
        // and then low part gets mixed with variant identifier
        final int timeBottom = (((timestampLow >> 12) & 0xFFFF) << 16)
                // and final 12 bits mixed with variant identifier
                | 0x6000 | (timestampLow & 0xFFF);
        long timeBottomL = (long) timeBottom;
        timeBottomL = ((timeBottomL << 32) >>> 32); // to get rid of sign extension

        // and reconstruct
        long l1 = timestampHigh | timeBottomL;
        return new UUID(l1, _uuidL2);
    }
}
