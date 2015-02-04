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

package com.altiscale.TcpProxy;

import org.apache.log4j.Logger;

import java.net.Socket;

import com.altiscale.TcpProxy.HostPort;
import com.altiscale.TcpProxy.JumpHost;
import com.altiscale.Util.ExecLoop;
import com.altiscale.Util.SecondMinuteHourCounter;

/**
 *  Server class holds host:port of where we expect TcpTunnel's servers to run and
 *  optional jumphost through which we setup ssh tunnels.
 **/
public class Server {
  // log4j logger.
  private static Logger LOG = Logger.getLogger("TransferAccelerator");

  // Host and port of the server to connect. If jumphost exists, then it's as seen from
  // jumphost.
  HostPort hostPort;

  // Jumphost to use for ssh tunnel to server. Null if not needed.
  JumpHost jumphost;

  // If we have a jumphost, we also start ssh process, monitor it, and restart it if needed.
  ExecLoop sshProcess;

  SecondMinuteHourCounter requestCnt;
  SecondMinuteHourCounter failedCnt;
  SecondMinuteHourCounter openedCnt;
  SecondMinuteHourCounter closedCnt;
  SecondMinuteHourCounter byteRateCnt;

  /*
   *  @param hostPort        host:port of the server-side for our tcp tunnels.
   */
  public Server(HostPort hostPort) {
    init(hostPort);
  }

  /*
   *  @param hostPort        host:port of the server-side for our tcp tunnels. If used with a
   *                         jumphost host must be the name of machine where our proxy is running
   *                         (for example, localhost or 127.0.0.1). Port number must be available
   *                         on the machine we are running.
   *  @param jumphost        jumphost for ssh tunnel to the server.
   *
   *  @return  Thread in which we're running.
   */
  public Server(HostPort hostPort, JumpHost jumphost) {
    // We first initialize as if we don't use jumphost, and then set jumphost params.
    init(hostPort);
    this.jumphost = jumphost;
  }

  private void init(HostPort hostPort) {
    this.hostPort = hostPort;
    this.jumphost = null;
    requestCnt = new SecondMinuteHourCounter("requestCnt " + hostPort.toString());
    failedCnt = new SecondMinuteHourCounter("incrementCnt " + hostPort.toString());
    openedCnt = new SecondMinuteHourCounter("openedCnt " + hostPort.toString());
    closedCnt = new SecondMinuteHourCounter("closedCnt " + hostPort.toString());
    byteRateCnt = new SecondMinuteHourCounter("byteRateCnt " + hostPort.toString());
  }

  public String sshJumphostCommand() {
    assert null != jumphost.sshd;
    assert null != jumphost.server;

    String sshTunnelCmd = "ssh";

    if (null != jumphost.sshBinary) {
      sshTunnelCmd = jumphost.sshBinary;
    }

    if (null != jumphost.credentials) {
      sshTunnelCmd += " -i " + jumphost.credentials;
    }
    if (jumphost.compression) {
      sshTunnelCmd += " -C";
    }
    if (null != jumphost.ciphers) {
      sshTunnelCmd += " -c " + jumphost.ciphers;
    }

    // Start in foreground, but not interactive.
    sshTunnelCmd += " -n -N -L ";

    // Open all network interfaces.
    if (jumphost.openInterfaces)
      sshTunnelCmd += "\\*:";

    sshTunnelCmd += hostPort.port + ":" + jumphost.server.host + ":" + jumphost.server.port;

    if (null != jumphost.user) {
      sshTunnelCmd += " -l " + jumphost.user;
    }
    if (-1 != jumphost.sshd.port) {
      sshTunnelCmd += " -p " + jumphost.sshd.port;
    }
    sshTunnelCmd += " " + jumphost.sshd.host;
    return sshTunnelCmd;
  }

  public void startJumphostThread() {
    assert null == sshProcess;

    sshProcess = new ExecLoop(sshJumphostCommand(), true, LOG);
    // Launch ssh tunnel in ExecLoop.
    sshProcess.start();
  }

  public void incrementFailedConn() {
    failedCnt.increment();
  }

  public void incrementOpenedConn() {
    openedCnt.increment();
  }

  public void incrementClosedConn() {
    closedCnt.increment();
  }

  public void incrementByteRateBy(long amount) {
    byteRateCnt.incrementBy(amount);
  }

  public boolean isHealthy() {
    if (null == sshProcess) return true;
    return sshProcess.isRunning();
  }

  public void establishTunnel(Socket clientSocket) throws java.io.IOException {
    requestCnt.increment();
    Socket serverSocket = new Socket(hostPort.host, hostPort.port);
    LOG.debug("Setting tunnel between [" +
        clientSocket.getInetAddress().getHostAddress() + ":" +
        clientSocket.getPort() + "] and server [" +
        hostPort.host + ":" + hostPort.port + "]");
    TcpTunnel tunnel = new TcpTunnel(clientSocket, serverSocket, this);

    // Create threads that will handle this tunnel.
    tunnel.spawnTunnelThreads();
  }
}
