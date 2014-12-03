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
**/

//TODO(cosmin) to add unittests ASAP

import java.util.ArrayDeque;

class Pair {
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

// This class implements a sliding window with a set of buckets that are kept in a Queue.
// It's not perfectly accurate. The more buckets the better the accuracy.
class SlidingWindowCounter {
  private long counter;
  private ArrayDeque<Pair> buckets;
  private long numBuckets;
  private long bucketSize;
  private long windowSize;

  // we assume windowSize is a multiple of numBuckets
  public SlidingWindowCounter(int numBuckets, long windowSize) {
    this.numBuckets = numBuckets;
    this.windowSize = windowSize;
    this.bucketSize = windowSize / numBuckets;
    this.buckets = new ArrayDeque<Pair>();
  }

  public void incrementBy(long amount) {
    removeOutdatedBuckets();
    counter += amount;
    long bucketTimestamp = System.currentTimeMillis() / bucketSize;
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

  private void removeOutdatedBuckets() {
    long bucketTimestamp = System.currentTimeMillis() / bucketSize;
    while (buckets.size() > 0 && buckets.getFirst().getTimestamp() * bucketSize < bucketTimestamp * bucketSize - windowSize) {
      counter -= buckets.getFirst().getCount();
      buckets.removeFirst();
    }
  }
}

public class SecondMinuteHourCounter {

  private SlidingWindowCounter secondCounter, minuteCounter, hourCounter;
  private long totalCounter;
  private String name;

  public SecondMinuteHourCounter(String name) {
    this.name = name;
    this.totalCounter = 0;
    this.secondCounter = new SlidingWindowCounter(1000, 1000);
    this.minuteCounter = new SlidingWindowCounter(1000, 60 * 1000);
    this.hourCounter = new SlidingWindowCounter(1000, 60 * 60 * 1000);
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
}
