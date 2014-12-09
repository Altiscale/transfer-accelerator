/**
* Copyright Altiscale 2014
* Author: Cosmin Negruseri <cosmin@altiscale.com>
*
* ServerStatus is a Runnable that listens on port 1982 and returns a html page with values
* from getServerStats.
*/

package com.altiscale.Util;

import org.apache.log4j.Logger;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

import com.altiscale.Util.ServerWithStats;

public class ServerStatus implements Runnable {

  // log4j logger.
  private static Logger LOG = Logger.getLogger("TcpProxy");
 
  ServerWithStats server; 
  public ServerStatus(ServerWithStats server) {
    this.server = server;
  }

  @Override
  public void run() {
    try {
      int port = 1982;
      ServerSocket serverSocket = new ServerSocket(port);
      LOG.info("Started StatsServer thread on port 1982");

      // repeatedly wait for connections, and process
      while (true) {
        Socket clientSocket = serverSocket.accept();

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

        // TODO(cosmin) handle /statusz
        String s;
        while ((s = in.readLine()) != null) {
          if (s.isEmpty()) {
            break;
          }
        }

        out.write("HTTP/1.0 200 OK\r\n");
        out.write("\r\n");
        out.write("<TITLE>Server Status</TITLE>\r\n");

        Map<String, String> map = server.getServerStats();
        out.write("<table border=\"1\">\r\n");
        out.write("<tr><td>counters</td><td>values</td></tr>\r\n");
        for (Map.Entry<String, String> entry : map.entrySet()) {
          out.write("<tr><td>" + entry.getKey() + "</td><td>" + entry.getValue() + "</td></tr>\r\n");
        }
        out.write("</table>\r\n");
        out.flush();
        out.close();
        in.close();
        clientSocket.close();
      }  
    } catch (java.io.IOException e) {
      LOG.info("ServerStats thread died.");
    }
  }
}
