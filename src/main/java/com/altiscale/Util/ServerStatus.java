package com.altiscale.Util;

import org.apache.log4j.Logger;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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
        out.write("<TITLE>Server Statuse</TITLE>\r\n");
        out.write("<P>" + server.getServerStats() +  "</P>\r\n");
        out.flush();
        out.close();
        in.close();
        clientSocket.close();
      }  
    } catch (java.io.IOException e) {
      // TODO(cosmin) fix import problem
      LOG.info("ServerStats thread died.");
    }
  }
}
