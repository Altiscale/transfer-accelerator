/**
 * Copyright Altiscale 2014
 * Author: Cosmin Negruseri <cosmin@altiscale.com>
 *
 * SecondMinuteHourCounter implements three counters over a second, a minute and an hour respective
 * time windows.
 * 
**/
 
package com.altiscale.TcpProxy;

import java.util.ArrayDeque;

public class SecondMinuteHourCounter {

  // Name of the counter
  private String name;

  // Deques keeping the counts for the last 60 seconds respectively 60  minutes. 
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
  }

  public SecondMinuteHourCounter(String name) {
    this.name = name;
    this.seconds = new ArrayDeque<Pair>();
    this.minutes = new ArrayDeque<Pair>();
    this.hour = new Pair(-1, 0);
  }

  public void incBy(int amount) {
    long currentSecond = System.currentTimeMillis() / 1000;
    if (seconds.size() > 0 && seconds.getLast().timestamp == currentSecond) {
      seconds.getLast().count += amount;
    } else {
      seconds.add(new Pair(currentSecond, amount));
    }
    
    while (seconds.size() > 0 && seconds.getFirst().timestamp < currentSecond - 60) {
      seconds.removeFirst();
    }

    long currentMinute = currentSecond / 60;
    if (minutes.size() > 0 && minutes.getLast().timestamp == currentMinute) {
      minutes.getLast().count += amount;
    } else {
      minutes.add(new Pair(currentMinute, amount));
    }

    while (minutes.size() > 0 && minutes.getFirst().timestamp < currentMinute - 60) {
      minutes.removeFirst();
    }

    long currentHour = currentMinute / 60;
    if (currentHour == hour.timestamp) {
      hour.count += amount;
    } else {
      hour = new Pair(currentHour, amount);
    }
  }

  public int getLastSecond() {
    long currentSecond = System.currentTimeMillis() / 1000;
    if (seconds.size() > 0 && seconds.getLast().timestamp == currentSecond) {
      return seconds.getLast().count;
    }
    return 0;
  }

  public int getLastMinute() {
    long currentMinute = System.currentTimeMillis() / 1000 / 60;
    if (minutes.size() > 0 && currentMinute == minutes.getLast().timestamp) {
      return minutes.getLast().count;
    }
    return 0;
  }

  public int getLastHour() {
    long currentHour = System.currentTimeMillis() / 1000 / 60 / 60;
    if (currentHour == hour.timestamp) {
      return hour.count;
    }
    return 0;
  }

  public static void main(String[] args) {
    SecondMinuteHourCounter rc = new SecondMinuteHourCounter("test");

    System.out.println("aaaa");

    long time = System.currentTimeMillis() / 1000;

    for (int i = 0; i <= 120; i++) {
        if (time + i == System.currentTimeMillis() / 1000) {
          if (i < 55) {
            rc.incBy(10);
          }
          rc.incBy(10);
          System.out.println(" " + System.currentTimeMillis() + " : " +
                             rc.getLastSecond() + " : " +
                             rc.getLastMinute() + " : " +
                             rc.getLastHour());
          while (time + i == System.currentTimeMillis() / 1000) {}
        }
    }
  }
}
