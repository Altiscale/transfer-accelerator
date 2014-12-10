/**
* Copyright Altiscale 2014
* Author: Cosmin Negruseri <cosmin@altiscale.com>
*
* ServerStatus is a Runnable that listens on a port and returns a html page with values
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

// TODO(cosmin): add http interface.

public class ServerStatus implements Runnable {

  // log4j logger.
  private static Logger LOG = Logger.getLogger("TcpProxy");
 
  private ServerWithStats server; 
  private int port;

  public ServerStatus(ServerWithStats server, int port) {
    this.port = port;
    this.server = server;
  }

  @Override
  public void run() {
    try {
      ServerSocket serverSocket = new ServerSocket(port);
      LOG.info("Started StatsServer thread on port " + port + ".");

      // Connection listening loop.
      while (true) {
        Socket clientSocket = serverSocket.accept();

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

        String s;
        while ((s = in.readLine()) != null) {
          if (s.isEmpty()) {
            break;
          }
        }

        out.write(server.getServerStatsHtml());

        out.flush();
        out.close();
        in.close();
        clientSocket.close();
      }  
    } catch (java.io.IOException e) {
      LOG.error("ServerStats thread died.");
    }
  }
}
