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

/*  TcpTunnel unittests. */
package com.altiscale.TcpProxy;

import com.altiscale.TcpProxy.HostPort;
import com.altiscale.TcpProxy.Server;
import com.altiscale.TcpProxy.TcpTunnel;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.altiscale.Util.SecondMinuteHourCounter;

/**
 * Unittests for TcpTunnel.
 */
public class TcpTunnelTest extends TestCase {
  class EchoClient implements Runnable {
    int serverPort;
    Socket mySocket;
    String expectedInputText;
    String outputText;
    Thread thread;

    public EchoClient(int port, String expectedInput, String output) {
      serverPort = port;
      mySocket = null;
      expectedInputText = expectedInput;
      outputText = output;
      thread = null;
    }

    public void start() {
      assert null == thread;
      thread = new Thread(this, "testClient");
      thread.start();
    }

    public void run() {
      System.out.println("In testClient run()...");
      DataInputStream input = null;
      DataOutputStream output = null;
      try {
        mySocket = new Socket("localhost", serverPort);
        input = new DataInputStream(mySocket.getInputStream());
        output = new DataOutputStream(mySocket.getOutputStream());

        // First write our outputText.
        System.out.println("Writing " + outputText);
        output.write(outputText.getBytes(), 0, outputText.getBytes().length);

        // Read our input.
        int cnt = 0;
        int offset = 0;
        byte[] buffer = new byte[1024];
        do {
          cnt = input.read(buffer, offset, buffer.length - offset);
          offset += cnt;
        } while (cnt >= 0 && offset < expectedInputText.getBytes().length);
        // Check that we got what we expected.
        String word = new String(buffer, 0, offset);
        System.out.println("Read completed: " + word);
        assert expectedInputText.equals(word);
        mySocket.close();
      } catch (IOException ioe) {
        System.out.println("Exception in testClient: " + ioe.getMessage());
        assert false;
      }
    }
  }

  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public TcpTunnelTest(String testName) {
      super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(TcpTunnelTest.class);
  }

  public void testOneTunnel() {
    int port = 8787;

    try {
      ServerSocket serverSocket = new ServerSocket(port);

      EchoClient echoClient = new EchoClient(port,
          "42", "What is the answer to life the universe and everything?");

      EchoClient echoServer = new EchoClient(port,
          "What is the answer to life the universe and everything?", "42");

      echoClient.start();
      echoServer.start();

      Socket client = serverSocket.accept();
      assert client != null;
      Socket server = serverSocket.accept();
      assert server != null;

      // Start test client and test server.
      TcpProxyServer proxy = new TcpProxyServer("TransferAccelerator");
      TcpTunnel tunnel = new TcpTunnel(
                             client,
                             server,
                             new Server(new HostPort("host", 1111)));
      tunnel.spawnTunnelThreads();
      while (!client.isClosed() || !server.isClosed()) {
        Thread.yield();
      }
    } catch (IOException ioe) {
      System.out.println("Exception in test: " + ioe.getMessage());
      assert false;
    }
  }
}
