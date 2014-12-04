/** Copyright Altiscale 2014
 *  Author: Zoran Dimitrijevic <zoran@altiscale.com>
 *
 *  TcpTunnel unittests.
 **/

package com.altiscale.TcpProxy;

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
        System.out.println("Exception in testCLient: " + ioe.getMessage());
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
      EchoClient echoClient = new EchoClient(port,
          "42", "What is the answer to life the universe and everything?");

      EchoClient echoServer = new EchoClient(port,
          "What is the answer to life the universe and everything?", "42");

      echoClient.start();
      echoServer.start();

      ServerSocket serverSocket = new ServerSocket(port);
      Socket client = serverSocket.accept();
      assert client != null;
      Socket server = serverSocket.accept();
      assert server != null;

      // Start test client and test server.
      TcpProxyServer proxy = new TcpProxyServer();
      TcpTunnel tunnel = new TcpTunnel(proxy, client, server);
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
