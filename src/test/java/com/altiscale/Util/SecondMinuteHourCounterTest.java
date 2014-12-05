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

  public void testSimple() {
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
}
