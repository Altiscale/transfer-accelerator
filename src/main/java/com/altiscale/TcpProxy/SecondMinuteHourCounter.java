/**
 * Copyright Altiscale 2014
 * Author: Cosmin Negruseri <cosmin@altiscale.com>
 *
 * SecondMinuteHourCounter implements three counters over a second, a minute and an hour sliding
 * time windows.
 * 
**/
 
package com.altiscale.TcpProxy;

import java.util.ArrayDeque;

public class SecondMinuteHourCounter {

  // Name of the counter
  private String name;

  // Deques keeping the counts for the last 60 seconds and 60  minutes respectively.
  private ArrayDeque<Pair> seconds, minutes;

  // Counter for the current hour.
  private Pair hour;

  class Pair {
    long timestamp;
    int count;

    public Pair(long timestamp, int count) {
      this.timestamp = timestamp;
      this.count = count;
    }

    public long getTimestamp() {
      return this.timestamp;
    }

    public int getCount() {
      return this.count;
    }

    public void incCount(int amount) {
      this.count += amount;
    }

  }

  public SecondMinuteHourCounter(String name) {
    this.name = name;
    this.seconds = new ArrayDeque<Pair>();
    this.minutes = new ArrayDeque<Pair>();
    this.hour = new Pair(-1, 0);
  }

  public void incBy(int amount) {
    long currentSecond = System.currentTimeMillis() / 1000;
    if (seconds.size() > 0 && seconds.getLast().getTimestamp() == currentSecond) {
      seconds.getLast().incCount(amount);
    } else {
      seconds.add(new Pair(currentSecond, amount));
    }
    
    while (seconds.size() > 0 && seconds.getFirst().getTimestamp() < currentSecond - 60) {
      seconds.removeFirst();
    }

    long currentMinute = currentSecond / 60;
    if (minutes.size() > 0 && minutes.getLast().getTimestamp() == currentMinute) {
      minutes.getLast().incCount(amount);
    } else {
      minutes.add(new Pair(currentMinute, amount));
    }

    while (minutes.size() > 0 && minutes.getFirst().getTimestamp() < currentMinute - 60) {
      minutes.removeFirst();
    }

    long currentHour = currentMinute / 60;
    if (currentHour == hour.getTimestamp()) {
      hour.incCount(amount);
    } else {
      hour = new Pair(currentHour, amount);
    }
  }

  public int getLastSecond() {
    long currentSecond = System.currentTimeMillis() / 1000;
    if (seconds.size() > 0 && seconds.getLast().getTimestamp() == currentSecond) {
      return seconds.getLast().getCount();
    }
    return 0;
  }

  public int getLastMinute() {
    long currentMinute = System.currentTimeMillis() / 1000 / 60;
    if (minutes.size() > 0 && currentMinute == minutes.getLast().getTimestamp()) {
      return minutes.getLast().getCount();
    }
    return 0;
  }

  public int getLastHour() {
    long currentHour = System.currentTimeMillis() / 1000 / 60 / 60;
    if (currentHour == hour.getTimestamp()) {
      return hour.getCount();
    }
    return 0;
  }
}
