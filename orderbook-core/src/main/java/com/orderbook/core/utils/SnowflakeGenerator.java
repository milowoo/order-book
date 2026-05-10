package com.orderbook.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowflakeGenerator {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeGenerator.class);
    private static final long START_STAMP = 1514736000000L;
    private static final long SEQUENCE_BIT = 8L;
    private static final long MACHINE_BIT = 8L;
    private static final long DATACENTER_BIT = 7L;
    private static final long MAX_DATACENTER_NUM = 127L;
    private static final long MAX_MACHINE_NUM = 255L;
    private static final long MAX_SEQUENCE = 255L;
    private static final long MACHINE_LEFT = 8L;
    private static final long DATACENTER_LEFT = 16L;
    private static final long TIMESTAMP_LEFT = 23L;
    private long datacenterId;
    private long machineId;
    private long sequence = 0L;
    private long lastStamp = -1L;
    private static volatile SnowflakeGenerator idGenerator = null;

    public SnowflakeGenerator() {
        byte[] bytes = IpUtils.LOCAL_IP.getAddress();
        if (bytes.length == 4) {
            byte thirdNum = bytes[2];
            byte fourthNum = bytes[3];
            this.datacenterId = (long) (127 & thirdNum % 127);
            this.machineId = 255L & (long) fourthNum;
            log.info("thirdNum:{} fourthNum:{} ", thirdNum, fourthNum);
        } else {
            log.error("can not init generator.");
            System.exit(-1);
        }
        log.warn("Use local ip-{} as datacenter:{} machine id:{} ", new Object[]{IpUtils.LOCAL_IP, this.datacenterId, this.machineId});
    }

    public static SnowflakeGenerator getInstance() {
        if (idGenerator == null) {
            synchronized (SnowflakeGenerator.class) {
                if (idGenerator == null) {
                    idGenerator = new SnowflakeGenerator();
                }
            }
        }
        return idGenerator;
    }

    public static synchronized void setInstance(SnowflakeGenerator instance) {
        idGenerator = instance;
    }

    public SnowflakeGenerator(long datacenterId, long machineId) {
        if (datacenterId <= 127L && datacenterId >= 0L) {
            if (machineId <= 255L && machineId >= 0L) {
                this.datacenterId = datacenterId;
                this.machineId = machineId;
            } else {
                throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
            }
        } else {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
    }

    public synchronized long nextId() {
        long currStamp = this.getNewStamp();
        if (currStamp < this.lastStamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        } else {
            if (currStamp == this.lastStamp) {
                this.sequence = this.sequence + 1L & 255L;
                if (this.sequence == 0L) {
                    currStamp = this.getNextMill();
                }
            } else {
                this.sequence = 0L;
            }
            this.lastStamp = currStamp;
            return currStamp - 1514736000000L << 23 | this.datacenterId << 16 | this.machineId << 8 | this.sequence;
        }
    }

    private long getNextMill() {
        long mill;
        for (mill = this.getNewStamp(); mill <= this.lastStamp; mill = this.getNewStamp()) {
        }
        return mill;
    }

    private long getNewStamp() {
        return System.currentTimeMillis();
    }

    public long getMachineId() {
        return this.machineId;
    }
}
