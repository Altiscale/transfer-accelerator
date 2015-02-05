/**
 * Copyright 2014 Altiscale <zoran@altiscale.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.altiscale.Util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * ExecLoop starts a java thread and then executes a command in new process on its host machine.
 * It then waits for the process to finish and either tries to restart it again or finishes.
 *
 */
public class ExecLoop implements Runnable {

  // log4j logger.
  private static Logger LOG;

  // Command to run on host OS in a new process.
  private String command;

  // Process this ExecLoop is monitoring.
  private Process execProcess;

  // Should we restart this process?
  private boolean shouldRestart;

  // Counter for restarts.
  final SecondMinuteHourCounter restartCnt = new SecondMinuteHourCounter("ExecLoop");

  // How much should we wait between restarts?
  private long waitMilliseconds;

  // True if the process is running command.
  private boolean isRunning;

  private Thread thread;

  public ExecLoop(String command, boolean shouldRestart, Logger LOG) {
    this.LOG = LOG;
    this.command = command;
    this.shouldRestart = shouldRestart;
    this.waitMilliseconds = 500;
    this.isRunning = false;
    this.execProcess = null;
    this.thread = null;
  }

  public void setWaitMilliseconds(long waitMilliseconds) {
    this.waitMilliseconds = waitMilliseconds;
  }

  private synchronized void setIsRunning(boolean value) {
    isRunning = value;
  }

  public synchronized boolean isRunning() {
    return isRunning;
  }

  public synchronized void setShouldRestart(boolean value) {
    shouldRestart = value;
  }

  public synchronized boolean shouldRestart() {
    return shouldRestart;
  }

  /*
  *  Method to create new thread which will exec(command).
  *
  *  @return  Thread in which we're running.
  */
  public Thread start() {
    assert null == thread;  // we should never call this method twice.
    LOG.debug("Starting thread to run [" + command + "]");
    thread = new Thread(this);
    thread.start();
    return thread;
  }

  @Override
  public void run() {
    assert null == execProcess;

    while (shouldRestart()) {
      try {
        execProcess = Runtime.getRuntime().exec(command);
        setIsRunning(true);
        LOG.info("Executed command: [" + command + "]");
        execProcess.waitFor();
        setIsRunning(false);
        TimeUnit.MILLISECONDS.sleep(waitMilliseconds);
      } catch (IOException ioe) {
        LOG.error("Failed to execute command [" + command + "]: " + ioe.getMessage());
      } catch (InterruptedException ie) {
        LOG.error("Interrupted process with exception: " + ie.getMessage());
      }
    }
  }
}
