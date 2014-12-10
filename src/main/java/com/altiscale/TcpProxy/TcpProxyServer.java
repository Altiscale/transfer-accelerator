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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Map;

import com.altiscale.Util.SecondMinuteHourCounter;
import com.altiscale.Util.ServerStatus;
import com.altiscale.Util.ServerWithStats;

public class TcpProxyServer implements ServerWithStats {

  protected class HostPort {
    String host;
    int port;

    public HostPort(String host, int port) {
      this.host = host;
      this.port = port;
    }

    public String toString() {
      return "" + host + ":" + port;
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

    SecondMinuteHourCounter requestCnt;
    SecondMinuteHourCounter openedCnt;
    SecondMinuteHourCounter closedCnt;
    SecondMinuteHourCounter byteRateCnt;

    public Server(HostPort hostPort) {
      this.hostPort = hostPort;
      requestCnt = new SecondMinuteHourCounter("requestCnt " + hostPort.toString());
      openedCnt = new SecondMinuteHourCounter("openedCnt " + hostPort.toString());
      closedCnt = new SecondMinuteHourCounter("closedCnt " + hostPort.toString());
      byteRateCnt = new SecondMinuteHourCounter("byteRateCnt " + hostPort.toString());
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

    public void establishTunnel(Socket clientSocket) throws java.io.IOException {
      requestCnt.increment();
      Socket serverSocket = new Socket(hostPort.host, hostPort.port);
      LOG.info("Setting tunnel between [" +
          clientSocket.getInetAddress().getHostAddress() + ":" +
          clientSocket.getPort() + "] and server [" +
          hostPort.host + ":" + hostPort.port + "]");
      TcpTunnel tunnel = new TcpTunnel(clientSocket, serverSocket, this);

      // Create threads that will handle this tunnel.
      tunnel.spawnTunnelThreads();
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

  private String name;

  @Override
  public Map<String, String> getServerStats() {
    long lastSecondByteRate = 0;
    long lastMinuteByteRate = 0;
    long lastHourByteRate = 0;
    long openedConnections = 0;
    long closedConnections = 0;
    for (Server server : serverList) {
      openedConnections += server.openedCnt.getTotalCnt();
      closedConnections += server.closedCnt.getTotalCnt();
      lastSecondByteRate += server.byteRateCnt.getLastSecondCnt();
      lastMinuteByteRate += server.byteRateCnt.getLastMinuteCnt();
      lastHourByteRate += server.byteRateCnt.getLastHourCnt();
    }

    TreeMap<String, String> map = new TreeMap<String, String>();

    map.put("Opened Connections", "" + openedConnections);
    map.put("Closed Connections", "" + closedConnections);
    map.put("byte rate - last second", "" + lastSecondByteRate);
    map.put("byte rate - last minute", "" + lastMinuteByteRate);
    map.put("byte rate - last hour", "" + lastHourByteRate);

    return map;
  }

  public TcpProxyServer(String name) {
    this.name = name;
    serverList = new ArrayList<Server>();
  }

  public void init(ProxyConfiguration conf) {
    config = conf;

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

  public void setupTunnel(Socket clientSocket) {
    final int RETRY_MAX = 3;
    for (int i = 0; i < RETRY_MAX; i++) {
      Server server = getRoundRobinServer();
      try {
        server.establishTunnel(clientSocket);
        break;
      } catch (IOException ioe) {
        LOG.error("Error while connecting to server " +
                  server.hostPort.host + ":" + server.hostPort.port);
      }
    }
  }

  public void runListeningLoop() {
    while (!tcpProxyService.isClosed()) {
      try {
        Socket clientSocket = null;
        clientSocket = tcpProxyService.accept();
        if (null != clientSocket) {
          setupTunnel(clientSocket);
        }
      } catch (IOException ioe) {
        LOG.error("IOException while accepting connection: " + ioe.getMessage());
      }
    }
  }

  public String usage() {
    return "Usage: tcpProxy <proxyPort> [<server_name> <server_port>]+";
  }

  public String getServerName() {
    return name;
  }

  public static void main (String[] args) {
    TcpProxyServer proxy = new TcpProxyServer("TcpProxy");

    // TODO(zoran): add command line arguments using Apache Commons CLI.
    // TODO(zoran): enable users to specify one hostname and multiple ports.
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

    // Lauch ServerStats thread.
    new Thread(new ServerStatus(proxy, 1982)).start();

    // Loop on the listen port forever.
    proxy.runListeningLoop();
  }
}
