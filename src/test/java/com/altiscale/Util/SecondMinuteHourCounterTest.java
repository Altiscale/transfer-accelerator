/**
 * Copyright Altiscale 2014
 * Author: Cosmin Negruseri <cosmin@altiscale.com>
 *
 * SecondMinuteHourCounter unittest.
 */

package com.altiscale.Util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

class TestTimer extends AltiTimer {
   /** Fake timer class useful in testing */
   long time;
   
   public TestTimer(long time) {
     this.time = time;
   }
   public void setTime(long time) {
     this.time = time;
   }
   
   @Override
   public long currentTimeMillis() {
     return time;
   }
}

public class SecondMinuteHourCounterTest extends TestCase {

  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public SecondMinuteHourCounterTest(String testName) {
    super(testName);
  }
 
  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(SecondMinuteHourCounterTest.class);
  }

  public void testExpireAfterOneSecond() {
    TestTimer timer = new TestTimer(0);
    SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "Test Counter");

    counter.increment();

    timer.setTime(999);
    assert counter.getLastSecondCnt() == 1;

    timer.setTime(1000);
    assert counter.getLastSecondCnt() == 1;

    timer.setTime(1001);
    assert counter.getLastSecondCnt() == 0;
  }

  public void testExpireAfterOneMinute() {
    TestTimer timer = new TestTimer(0);
    SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "Test Counter");

    counter.increment();

    timer.setTime(60000);
    assert counter.getLastMinuteCnt() == 1;

    timer.setTime(60059);
    assert counter.getLastMinuteCnt() == 1;

    timer.setTime(60060);
    assert counter.getLastMinuteCnt() == 0;
  }

  public void testExpireAfterOneHour() {
    TestTimer timer = new TestTimer(0);
    SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "TestCounter");
  
    counter.increment();

    timer.setTime(3600000);
    assert counter.getLastHourCnt() == 1;

    timer.setTime(3603590);
    assert counter.getLastHourCnt() == 1;

    timer.setTime(3603600);
    assert counter.getLastHourCnt() == 0;
  }

  public void testThreeIncrements() {
    /**
       Simple test where increment is used 3 time.
       time     0.001s - add 1
       time     0.500s - add 100
       time    60.000s - add 10000
     */
    TestTimer timer = new TestTimer(0);
    SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "Test Counter");

    // first increment
    timer.setTime(1);
    counter.increment();

    // second increment
    timer.setTime(500);
    counter.incrementBy(100);

    // the two increments are in our 1s window
    timer.setTime(1000);
    assert counter.getLastSecondCnt() == 101;    
    
    // first increment is out of the 1s window
    timer.setTime(1500);
    assert counter.getLastSecondCnt() == 100;

    // second increment is out of the 1s window
    timer.setTime(1501);
    assert counter.getLastSecondCnt() == 0;
    
    // third increment
    timer.setTime(60000);
    counter.incrementBy(10000);

    // all three increments are in the 1min window.
    assert counter.getLastMinuteCnt() == 10101;

    // first increment out of the 1min window
    timer.setTime(60500);
    assert counter.getLastMinuteCnt() == 10100;
    
    // all increments in the 1h window.
    timer.setTime(3600000);
    assert counter.getLastHourCnt() == 10101;
    
    // first two increments out of the 1h window.
    timer.setTime(3604000);
    assert counter.getLastHourCnt() == 10000;

    // all increments out of the 1h window.
    timer.setTime(7200000);
    assert counter.getLastHourCnt() == 0;
  }

  public void testOneBucket() {
     TestTimer timer = new TestTimer(0);
     SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "One bucket", 1L);
     
     counter.increment();

     // The counter discards data when it's more than one bucket away from our window. 
     // In this test case we have one bucket, so our window size is equal to our bucket size.
     // Because of this we have to be at least 2 seconds away to discard old data.
     timer.setTime(1999);
     assert counter.getLastSecondCnt() == 1;
     timer.setTime(2000);
     assert counter.getLastSecondCnt() == 0;

     timer.setTime(119000);
     assert counter.getLastMinuteCnt() == 1;
     timer.setTime(120000);
     assert counter.getLastMinuteCnt() == 0;
  }

  public void testFourBuckets() {
     TestTimer timer = new TestTimer(0);
     SecondMinuteHourCounter counter = new SecondMinuteHourCounter(timer, "Four buckets", 4L);

     counter.increment();
     timer.setTime(1249);
     assert counter.getLastSecondCnt() == 1;
     timer.setTime(1250);
     assert counter.getLastSecondCnt() == 0;
  }
}
