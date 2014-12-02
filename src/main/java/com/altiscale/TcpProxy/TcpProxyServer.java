/**
 * Copyright Altiscale 2014
 * Author: Zoran Dimitrijevic <zoran@altiscale.com>
 *
 * TcpProxyServer is a server that manages one listening port and for each incoming TCP
 * connection to that port it creates another TCP connection to one of pre-set destinations
 * specified by an array of host:port (string:int) pairs.
 *
 * Goal of TcpProxy is to simply tunnel all bytes from incoming socket to its destination socket,
 * trying to load-balance so that each destination connection transfers similar amount of data.
 *
 * Destinations are usually ssh-tunnels connected to a single destination host:port.
 *
 **/

package com.altiscale.TcpProxy;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class TcpProxyServer {

  // TODO(zoran): this is a very common util method. Either find appropriate Hadoop method
  // or make it a class so we can use it in all hadoop open-source code.
  // TODO(zoran): this is just a simple "stub" - I still need to write it. And it must be
  // in its own class.
  class SecondMinuteHourCount {
    private long cnt;

    public SecondMinuteHourCount() {
      cnt = 0;
    }

    public void inc() {
      cnt++;
    }
    public long getCnt() {
      return cnt;
    }
  }

  protected class HostPort {
    String host;
    int port;

    public HostPort(String host, int port) {
      this.host = host;
      this.port = port;
    }
  }

  protected class ProxyConfiguration {
    int listeningPort;
    ArrayList<HostPort> serverHostPortList;

    public ProxyConfiguration(int port) {
      listeningPort = port;
      serverHostPortList = new ArrayList<HostPort>();
    }

    public void addServer(String host, int port) {
      serverHostPortList.add(new HostPort(host, port));
    }
  }

  protected class Server {
    HostPort hostPort;

    SecondMinuteHourCount requestCnt;
    SecondMinuteHourCount openedCnt;
    SecondMinuteHourCount closedCnt;
    SecondMinuteHourCount byteRateCnt;

    public Server(HostPort hostPort) {
      this.hostPort = hostPort;
      requestCnt = new SecondMinuteHourCount();
      openedCnt = new SecondMinuteHourCount();
      closedCnt = new SecondMinuteHourCount();
      byteRateCnt = new SecondMinuteHourCount();
    }
  }

  // log4j logger.
  private static Logger LOG = Logger.getLogger("TcpProxy");

  // Config for this proxy.
  private ProxyConfiguration config;

  // Port number where we want to listen for our clients.
  private int tcpProxyPort;

  // This is our ServerSocket running on tcpProxyPort.
  private ServerSocket tcpProxyService;

  // List of all servers we can use to tunnel our client trafic. We choose from this list
  // based on our load-balancing algorithm, and if we cannot connect we retry using next
  // server until we establish the tunnel.
  private ArrayList<Server> serverList;

  // Used by round-robin load-balancing algorithm.
  private int nextServerId = 0;

  public TcpProxyServer() {
    serverList = new ArrayList<Server>();
  }

  public void init(ProxyConfiguration config) {
    this.config = config;

    for (HostPort serverHostPort : config.serverHostPortList) {
      serverList.add(new Server(serverHostPort));
    }

    tcpProxyPort = config.listeningPort;
    try {
      tcpProxyService = new ServerSocket(tcpProxyPort);
      LOG.info("Listening for incoming clients on port " + tcpProxyPort);
    } catch (IOException ioe) {
      LOG.error("IO exception while establishing proxy service on port " + tcpProxyPort);
      System.exit(1);
    }
  }

  public ArrayList<Server> getServerList() {
    return serverList;
  }

  public Server getRoundRobinServer() {
    nextServerId = (nextServerId + 1) % serverList.size();
    return serverList.get(nextServerId);
  }

  public TcpTunnel setupTunnel(Socket clientSocket) {
    final int RETRY_MAX = 3;
    TcpTunnel tunnel = null;
    for (int i = 0; i < RETRY_MAX; i++) {
      Server server = getRoundRobinServer();
      try {
        Socket serverSocket = new Socket(server.hostPort.host, server.hostPort.port);
        LOG.info("Setting tunnel between [" +
            clientSocket.getInetAddress().getHostAddress() + ":" +
            clientSocket.getPort() + "] and server [" +
            server.hostPort.host + ":" + server.hostPort.port + "]");
        tunnel = new TcpTunnel(this, clientSocket, serverSocket);
        // Create threads that will handle this tunnel.
        tunnel.spawnTunnelThreads();
        return tunnel;
      } catch (IOException ioe) {
        LOG.error("Error while connecting to server " +
                  server.hostPort.host + ":" + server.hostPort.port);
      }
    }
    return tunnel;
  }

  public void runListeningLoop() {
    while (!tcpProxyService.isClosed()) {
      try {
        Socket clientSocket = null;
        clientSocket = tcpProxyService.accept();
        if (null != clientSocket) {
          TcpTunnel tunnel = setupTunnel(clientSocket);
        }
      } catch (IOException ioe) {
        LOG.error("IOException while accepting connection: " + ioe.getMessage());
      }
    }
  }

  public String usage() {
    return "Usage: tcpProxy <proxyPort> [<server_name> <server_port>]+";
  }

  public static void main (String[] args) {
    TcpProxyServer proxy = new TcpProxyServer();

    if (args.length < 3) {
      LOG.info(proxy.usage());
      System.exit(1);
    }
    int proxyPort = 0;
    try {
      proxyPort = Integer.parseInt(args[0]);
    } catch (Exception e) {
      LOG.error("Parsing error: " + e.getMessage());
      System.exit(1);
    }

    ProxyConfiguration conf = proxy.new ProxyConfiguration(proxyPort);

    // Setup servers from command line.
    for (int i = 1; i < args.length; i++) {
      String server = args[i];
      if (i + 1 < args.length) {
        i++;
        try {
          int port = Integer.parseInt(args[i]);
          conf.addServer(server, port);
        } catch (Exception e) {
          LOG.error("Error while setting server [" + server +
                    "] from command line: " + e.getMessage());
        }
      }
    }
    proxy.init(conf);

    if (proxy.getServerList().size() < 1) {
      LOG.error("No server specified.");
      System.exit(1);
    }

    // Loop on the listen port forever.
    proxy.runListeningLoop();
  }
}
