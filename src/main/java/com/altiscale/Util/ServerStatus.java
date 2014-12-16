/**
* Copyright Altiscale 2014
* Author: Cosmin Negruseri <cosmin@altiscale.com>
*
* ServerStatus is a Runnable that listens on a port and returns a html page with values
* from getServerStats.
*/

package com.altiscale.Util;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.altiscale.Util.ServerWithStats;

import org.apache.log4j.Logger;

public class ServerStatus implements Runnable {

  // log4j logger.
  private static Logger LOG = Logger.getLogger("TransferAccelerator");

  private ServerWithStats serverWithStats;
  private int port;

  public ServerStatus(ServerWithStats server, int port) {
    this.port = port;
    this.serverWithStats = server;
  }

  @Override
  public void run() {
    try {
      InetSocketAddress addr = new InetSocketAddress(port);
      HttpServer httpServer = HttpServer.create(addr, 0);
      httpServer.createContext("/stats", new StatsHandler(serverWithStats));
      httpServer.createContext("/health", new HealthHandler(serverWithStats));
      httpServer.start();
      LOG.info("Started HttpServer accessible at localhost:" + port + "/stats");
    } catch (IOException e) {
      LOG.error("Could not start HttpServer. " + e.getMessage());
      System.exit(1);
    }
  }

  class HealthHandler implements HttpHandler {
    ServerWithStats serverWithStats;

    public HealthHandler(ServerWithStats server) {
      this.serverWithStats = server;
    }

    public void handle(HttpExchange exchange) throws IOException {
      String requestMethod = exchange.getRequestMethod();
      if (requestMethod.equalsIgnoreCase("GET")) {
        String response = serverWithStats.getServerHealthHtml();
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream responseBody = exchange.getResponseBody();
        Headers requestHeaders = exchange.getRequestHeaders();
        responseBody.write(response.getBytes());
        responseBody.close();
      }
    }
  }

  class StatsHandler implements HttpHandler {
    ServerWithStats serverWithStats;

    public StatsHandler(ServerWithStats server) {
      this.serverWithStats = server;
    }

    public void handle(HttpExchange exchange) throws IOException {
      String requestMethod = exchange.getRequestMethod();
      if (requestMethod.equalsIgnoreCase("GET")) {
        String response = serverWithStats.getServerStatsHtml();
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream responseBody = exchange.getResponseBody();
        Headers requestHeaders = exchange.getRequestHeaders();
        responseBody.write(response.getBytes());
        responseBody.close();
      }
    }
  }
}
