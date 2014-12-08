/**
 * Copyright Altiscale 2014
 * Author: Cosmin Negruseri <cosmin@altiscale.com>
 *
 * SecondMinuteHourCounter implements three counters over a second, a minute and an hour sliding
 * time windows and a total count.
 *
 * This class is usefull in server side debugging and performance analysis. For example we can use
 * it to keep counters for how many requests are handled by a web server or for measuring the
 * throughput of a network transfer.
 *
 * The methods used are increment and incrementBy, they aren't very light weight so using them
 * in a tight loop might slow performance. The use case for increment is somewhat infrequent requests
 * up to 1000 per second. When we need faster increments we use the incrementBy to do it in bulk.
**/

//TODO(cosmin) to add java style comments ASAP

package com.altiscale.Util;

import java.util.ArrayDeque;
import java.util.Iterator;

class AltiTimer {
  /** This class wraps System.currentTimeMillis. It's useful for testing. */
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }
}

class SlidingWindowCounter {
  /** This class implements a sparse sliding window using a set of buckets kept in a Deque.
   *  @param numBuckets Number of buckets per each interval (more buckets, higher precision) 
   */

  private long counter;
  private ArrayDeque<Pair> buckets;
  private long numBuckets;
  private long bucketSize;
  private long windowSize;
  private AltiTimer timer;

  class Pair {
    /** This class implements a pair needed in our sparse sliding window implementation.
     *
     * @param timestamp keeps a id for the bucket we're working on from the beggining of time.
     * @param count keeps the count for that particular bucket.
     */
    private long timestamp;
    private long count;

    public Pair(long timestamp, long count) {
      this.timestamp = timestamp;
      this.count = count;
    }

    public long getTimestamp() {
      return this.timestamp;
    }

    public long getCount() {
      return this.count;
    }

    public void incCount(long amount) {
      this.count += amount;
    }
  }

  public SlidingWindowCounter(AltiTimer timer, long numBuckets, long windowSize) {
    this.timer = timer;
    this.numBuckets = numBuckets;
    this.windowSize = windowSize;
    // we assume windowSize is a multiple of numBuckets
    assert windowSize % numBuckets == 0;
    this.bucketSize = windowSize / numBuckets;
    this.buckets = new ArrayDeque<Pair>();
  }

  public void incrementBy(long amount) {
    removeOutdatedBuckets();
    counter += amount;
    long bucketTimestamp = timer.currentTimeMillis() / bucketSize;
    if (buckets.size() > 0 && buckets.getLast().getTimestamp() == bucketTimestamp) {
      buckets.getLast().incCount(amount);
    } else {
      buckets.add(new Pair(bucketTimestamp, amount));
    }
  }
  
  public long getCount() {
    removeOutdatedBuckets();
    return counter;
  }

  /** This method lazily deletes buckets that are no longer within the sliding window */
  private void removeOutdatedBuckets() {
    long currentTime = timer.currentTimeMillis();
    long bucketTimestamp = currentTime / bucketSize;
    while (buckets.size() > 0 &&
           buckets.getFirst().getTimestamp() * bucketSize <
             currentTime - windowSize) {
      counter -= buckets.getFirst().getCount();
      buckets.removeFirst();
    }
  }
}

public class SecondMinuteHourCounter {

  private SlidingWindowCounter secondCounter, minuteCounter, hourCounter;
  private long totalCounter;
  private long numBuckets;
  private String name;

  public SecondMinuteHourCounter(String name) {
    this(new AltiTimer(), name, 1000L);
  }

  public SecondMinuteHourCounter(AltiTimer timer, String name) {
    this(timer, name, 1000L);
  }

  public SecondMinuteHourCounter(AltiTimer timer, String name, Long numBuckets) {
    this.name = name;
    this.totalCounter = 0;
    this.numBuckets = numBuckets;
    this.secondCounter = new SlidingWindowCounter(timer, numBuckets, 1000);
    this.minuteCounter = new SlidingWindowCounter(timer, numBuckets, 60 * 1000);
    this.hourCounter = new SlidingWindowCounter(timer, numBuckets, 60 * 60 * 1000);
  }

  public synchronized void increment() {
    this.incrementBy(1);
  }

  public synchronized void incrementBy(long amount) {
    totalCounter += amount;
    secondCounter.incrementBy(amount);
    minuteCounter.incrementBy(amount);
    hourCounter.incrementBy(amount);
  }

  public synchronized long getLastSecondCnt() {
    return secondCounter.getCount();
  }

  public synchronized long getLastMinuteCnt() {
    return minuteCounter.getCount();
  }

  public synchronized long getLastHourCnt() {
    return hourCounter.getCount();
  }

  public synchronized long getTotalCnt() {
    return totalCounter;
  }

  public String toString() {
    return name +
           " getLastSecondCnt: " + getLastSecondCnt() +
           " getLastMinuteCnt: " + getLastMinuteCnt() +
           " getLastHourCnt: " + getLastHourCnt() +
           " total: " + getTotalCnt();
  }
}
