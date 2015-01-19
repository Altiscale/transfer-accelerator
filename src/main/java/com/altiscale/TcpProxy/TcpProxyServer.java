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
import org.apache.log4j.LogManager;
import org.apache.log4j.Level;

import java.io.InputStream;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;

import com.altiscale.Util.ExecLoop;
import com.altiscale.Util.SecondMinuteHourCounter;
import com.altiscale.Util.ServerStatus;
import com.altiscale.Util.ServerWithStats;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

class HostPort {
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

class JumpHost {
  HostPort sshd;
  HostPort server;
  String user;
  String credentials;
  boolean compression;
  String ciphers;
  String sshBinary;

  /*  @param sshd         host:port of machine to use for establishing ssh tunnel to
   *                      jumphostServer. sshd.host is the name of the machine where sshd
   *                      is running, and sshd.port is the sshd port number. If port
   *                      number is -1, we let ssh use default port for that hostname.
   *  @param server       End server we want to connect to via ssh tunnel. For example,
   *                      httpfs-node:14000. server.host must be visible from host where
   *                      sshd is running.
   *  @param user         Optional username we want to use for ssh (to override the default).
   *  @param credentials  Optional path to a file with ssh credentials to use with ssh -i
   *  @param compression  If true, add optional -C flag to ssh tunnel command to turn on
   *                      compression.
   *  @param ciphers      Optional string for ssh -c command option for cipher specs.
   *  @param sshBinary    Optional binary path and name to use instead of default 'ssh'.
   */
  public JumpHost(HostPort sshd,
                  HostPort server,
                  String user,
                  String credentials,
                  boolean compression,
                  String ciphers,
                  String sshBinary) {
    this.sshd = sshd;
    this.server = server;
    this.user = user;
    this.credentials = credentials;
    this.compression = compression;
    this.ciphers = ciphers;
    this.sshBinary = sshBinary;
  }
}

class ProxyConfiguration {
  // Port where our clients will connect to.
  static final int defaultListeningPort = 14000;
  int listeningPort;

  // Port where we export web-based status.
  static final int defaultStatusPort = 48138;
  int statusPort;

  String loadBalancerString;

  // List of all our servers.
  ArrayList<HostPort> serverHostPortList;

  // JumpHost to use for establishing ssh tunnels to the server. Null if we don't want it.
  JumpHost jumphost;

  public ProxyConfiguration() {
    listeningPort = defaultListeningPort;
    statusPort = defaultStatusPort;
    loadBalancerString = "RoundRobin";  // default value
    serverHostPortList = new ArrayList<HostPort>();
    jumphost = null;
  }

  public HostPort parseServerString(String server) throws URISyntaxException {
    URI uri = new URI("my://" + server);
    String host = uri.getHost();
    int port = uri.getPort();
    if (uri.getHost() == null) {
      throw new URISyntaxException(uri.toString(), "URI must have at least a hostname.");
    }
    // Library sets port to -1 if it was missing in String server.
    return new HostPort(host, port);
  }

  public void parseServerStringAndAdd(String server) throws URISyntaxException {
    HostPort hostPort = parseServerString(server);
    if (hostPort.port == -1) {
      throw new URISyntaxException(server, "No port specified for server in server list.");
    }
    serverHostPortList.add(hostPort);
  }
}

/** TcpProxyServer is a server that manages one listening port and for each incoming TCP
 * connection to that port it creates another TCP connection to one of pre-set destinations
 * specified by an array of host:port (string:int) pairs.
 *
 * Goal of TcpProxy is to simply tunnel all bytes from incoming socket to its destination socket,
 * trying to load-balance so that each destination connection transfers similar amount of data.
 *
 * Destinations are usually ssh-tunnels connected to a single destination host:port.
 *
 **/
public class TcpProxyServer implements ServerWithStats {

  protected interface LoadBalancer {
    public Server getServer();
  }

  protected class RoundRobin implements LoadBalancer {
    private ArrayList<Server> servers;

    private int nextServerId = 0;

    public RoundRobin(ArrayList<Server> servers) {
      this.servers = servers;
    }

    public Server getServer() {
      nextServerId = (nextServerId + 1) % serverList.size();
      return serverList.get(nextServerId);
    }
  }

  protected class UniformRandom implements LoadBalancer {
     private ArrayList<Server> servers;

     public UniformRandom(ArrayList<Server> servers) {
       this.servers = servers;
     }

     public Server getServer() {
       return servers.get(
           new Random(System.currentTimeMillis()).nextInt(servers.size()));
     }
  }

  protected class LeastUsed implements LoadBalancer {
    private ArrayList<Server> servers;

    public LeastUsed(ArrayList<Server> servers) {
      this.servers = servers;
    }

    public Server getServer() {
      Server leastUsedServer = null;
      long leastUsedByteRate = Long.MAX_VALUE;
      for (Server server : servers) {
        if (server.failedCnt.getLastSecondCnt() == 0 &&
            server.byteRateCnt.getLastMinuteCnt() < leastUsedByteRate) {
          leastUsedByteRate = server.byteRateCnt.getLastMinuteCnt();
          leastUsedServer = server;
        }
      }

      // All servers have failures in the last second so we return one at random.
      if (leastUsedServer == null) {
         leastUsedServer = new UniformRandom(servers).getServer();
      }

      return leastUsedServer;
    }
  }

  // log4j logger.
  private static Logger LOG = Logger.getLogger("TransferAccelerator");

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

  private LoadBalancer loadBalancer;

  private String name;

  private String version;

  // transfer-accelerator uses by default ports in the range 48139 - 48160
  private static final int START_PORT_RANGE = 48139;
  private static final int MAX_NUM_SERVERS = 22;

  @Override
  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public String getServerStatsHtml() {
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

    String htmlServerStats = "";
    htmlServerStats += "HTTP/1.0 200 OK\r\n";
    htmlServerStats += "\r\n";
    htmlServerStats += "<head><meta http-equiv=\"refresh\" content=\"5\" /></head>\r\n";
    htmlServerStats += "<style> table, th, td { padding: 3px; border: 1px solid black;" +
                       " border-collapse: collapse; text-align: right;} </style>\r\n";
    htmlServerStats += "<TITLE>" + getServerName() + " Status</TITLE>\r\n";

    htmlServerStats += "<b>" + getServerName() + "</b> - " + tcpProxyPort + "<br/><br/><br/>\r\n";

    htmlServerStats += "<table>\r\n";
    htmlServerStats += "<tr><td><b>counters</b></td><td><b>values</b></td></tr>\r\n";

    htmlServerStats += "<tr><td>Open connections</td><td>" +
                       (openedConnections - closedConnections) +
                       "</td></tr>\r\n";

    htmlServerStats += "<tr><td><b>server</b> byte rate</td><td>" +
                       "<table><tr>" +
                       "<td>" + lastSecondByteRate + " B/s</td>" +
                       "<td>" + lastMinuteByteRate + " B/min</td>" +
                       "<td>" + lastHourByteRate + " B/h</td>" +
                       "</tr></table>";

    for (Server server : serverList) {
      htmlServerStats += "<tr><td><b>" + server.hostPort.toString() + "</b> byte rate </td><td>" +
                         "<table><tr>" +
                         "<td>" + server.byteRateCnt.getLastSecondCnt() + " B/s</td>" +
                         "<td>" + server.byteRateCnt.getLastMinuteCnt() + " B/min</td>" +
                         "<td>" + server.byteRateCnt.getLastHourCnt() + " B/h</td>" +
                         "</tr></table>" +
                         "</td></tr>\r\n";
    }

    for (Server server : serverList) {
      htmlServerStats += "<tr><td><b>" + server.hostPort.toString() + "</b>" +
                         " failed connections </td>" +
                         "<td><table><tr>" +
                         "<td>" + server.failedCnt.getLastSecondCnt() + " /s</td>" +
                         "<td>" + server.failedCnt.getLastMinuteCnt() + " /min</td>" +
                         "<td>" + server.failedCnt.getLastHourCnt() + " /h</td>" +
                         "</tr></table>" +
                         "</td></tr>\r\n";
    }

    htmlServerStats += "<tr><td>opened connections</td><td>" + openedConnections +
                       "</td></tr>\r\n";
    htmlServerStats += "<tr><td>closed connections</td><td>" + closedConnections +
                       "</td></tr>\r\n";
    htmlServerStats += "</table>\r\n";

    htmlServerStats += "Healthy servers " + getHealthyServerCnt() + " out of " + serverList.size();

    return htmlServerStats;
  }

  @Override
  public boolean isHealthy() {
    return 0 != getHealthyServerCnt();
  }

  private int getHealthyServerCnt() {
    int healthyCnt = 0;
    for (Server server : serverList) {
      if (server.isHealthy()) {
        healthyCnt++;
      }
    }
    return healthyCnt;
  }

  public TcpProxyServer(String name) {
    this.name = name;
    serverList = new ArrayList<Server>();
  }

  public void init(ProxyConfiguration conf) {
    config = conf;

    // Launch ServerStats thread.
    new Thread(new ServerStatus(this, config.statusPort)).start();

    // Initialize servers and optional ssh tunnels via jumphost.
    for (HostPort serverHostPort : config.serverHostPortList) {
      Server server = null;
      if (null == config.jumphost) {
        server = new Server(serverHostPort);
      } else {
        server = new Server(serverHostPort, config.jumphost);
        server.startJumphostThread();
      }
      assert null != server;
      serverList.add(server);
    }

    // Open our listening port.
    tcpProxyPort = config.listeningPort;
    try {
      tcpProxyService = new ServerSocket(tcpProxyPort);
      LOG.info("Listening for incoming clients on port " + tcpProxyPort);
    } catch (IOException ioe) {
      LOG.error("IO exception while establishing proxy service on port " + tcpProxyPort);
      System.exit(1);
    }

    // Set load balancer.
    if (config.loadBalancerString.equals("LeastUsed")) {
      loadBalancer = new LeastUsed(getServerList());
    } else if (config.loadBalancerString.equals("UniformRandom")) {
      loadBalancer = new UniformRandom(getServerList());
    } else {
      loadBalancer = new RoundRobin(getServerList());
    }
    setLoadBalancer(loadBalancer);
  }

  public ArrayList<Server> getServerList() {
    return serverList;
  }

  public void setupTunnel(Socket clientSocket) {
    final int RETRY_MAX = 3;
    for (int i = 0; i < RETRY_MAX; i++) {
      Server server = loadBalancer.getServer();
      try {
        server.establishTunnel(clientSocket);
        break;
      } catch (IOException ioe) {
        LOG.error("Error while connecting to server " +
                  server.hostPort.host + ":" + server.hostPort.port);
        server.incrementFailedConn();
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

  public String getServerName() {
    return name;
  }

  public static Options getCommandLineOptions() {
    Options options = new Options();

    options.addOption("v", "verbose", false, "Verbose logging.");

    options.addOption("V", "version", false, "Print version number.");

    options.addOption(OptionBuilder.withLongOpt("port")
                                   .withDescription("Listening port for proxy clients. " +
                                       "Default listening port is " +
                                       ProxyConfiguration.defaultListeningPort + ".")
                                   .withArgName("PORT")
                                   .withType(Number.class)
                                   .hasArg()
                                   .create('p'));

    options.addOption(OptionBuilder.withLongOpt("webstatus_port")
                                   .withDescription("Port for proxy status in html format. " +
                                       "Default status port is " +
                                       ProxyConfiguration.defaultStatusPort + ".")
                                   .withArgName("STATUS_PORT")
                                   .withType(Number.class)
                                   .hasArg()
                                   .create('w'));

    options.addOption(OptionBuilder.withLongOpt("servers")
                                   .withArgName("HOST1:PORT1> <HOST2:PORT2")
                                   .withDescription("Server/servers for the proxy to connect to" +
                                                    " in host:port format.")
                                   .hasArgs()
                                   .withValueSeparator(' ')
                                   .create('s'));

    options.addOption(OptionBuilder.withLongOpt("num_servers")
                                   .withArgName("NUM_SERVERS")
                                   .withDescription("Number of servers to instatntiate.")
                                   .hasArgs()
                                   .create('n'));

    options.addOption(OptionBuilder.withLongOpt("load_balancer")
                                   .withArgName("LOAD_BALANCER")
                                   .withDescription("Load balancing algorithm. Options: " +
                                                    "RoundRobin, LeastUsed, UniformRandom.")
                                   .hasArg()
                                   .create('b'));

    options.addOption(OptionBuilder.withLongOpt("ssh_binary")
        .withArgName("SSH_BINARY")
        .withDescription("Optional path to use as ssh command. Default is ssh.")
        .hasArg()
        .create());

    options.addOption(OptionBuilder.withLongOpt("jumphost_user")
        .withArgName("USER")
        .withDescription("Username for ssh to jumphost.")
        .hasArg()
        .create('u'));

    options.addOption(OptionBuilder.withLongOpt("jumphost_credentials")
        .withArgName("FILENAME")
        .withDescription("Filename for optional ssh credentials (ssh -i option).")
        .hasArg()
        .create('i'));

    options.addOption("C", "jumphost_compression", false, "Enable compression in ssh tunnels.");

    options.addOption(OptionBuilder.withLongOpt("jumphost_ciphers")
        .withArgName("CIPHER_SPEC")
        .withDescription("Select ciphers for ssh tunnel encryption (ssh -c option).")
        .hasArg()
        .create('c'));

    options.addOption(OptionBuilder.withLongOpt("jumphost")
        .withArgName("JUMPHOST:JH_PORT")
        .withDescription("Connect to servers via ssh tunnel to jumphost in jumphost:port format. " +
            "You still need to specify servers and their ports using --servers.")
        .hasArg()
        .create('j'));

    options.addOption(OptionBuilder.withLongOpt("jumphost_server")
        .withArgName("JHSERVER:JHS_PORT")
        .withDescription("Jumphost server behind the firewall to connect all servers using: " +
            "SSH_BINARY -i ~/.ssh/id_rsa -n -N -L PORT:JHSERVER:JHS_PORT -l USER " +
            "-p JH_PORT JUMPHOST")
        .hasArg()
        .create('y'));

    options.addOption(OptionBuilder.withLongOpt("help").create('h'));

    return options;
  }

  public void setLoadBalancer(LoadBalancer loadBalancer) {
    this.loadBalancer = loadBalancer;
  }

  public static void printHelp(Options options) {
    String header = "Connects clients to multiple replicas of the same server." +
                    "It can also setup multiple ssh tunnels via jumphost to a single server" +
                    "Listens on <PORT> and forwards " +
                    "incomming connections to " +
                    "<HOST1:PORT1> <HOST2:PORT2> ...\n\n";
    String footer = "";
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("TransferAccelerator", header, options, footer, true);
  }

  private static ProxyConfiguration assembleConfigFromCommandLine(Options options, String[] args) {
    CommandLine commandLine = null;
    try {
      commandLine = new GnuParser().parse(options, args);
    } catch (org.apache.commons.cli.ParseException e) {
      LOG.info("Parsing exception" + e.getMessage());
      printHelp(options);
      System.exit(1);
    }

    if (commandLine.hasOption("h")) {
      printHelp(options);
      System.exit(1);
    }

    if (commandLine.hasOption("version")) {
      LOG.info("Transfer Accelerator Version " +  getProxyVersion());
      System.exit(1);
    }

    if (commandLine.hasOption("verbose") ) {
      LogManager.getRootLogger().setLevel(Level.DEBUG);
    }

    ProxyConfiguration conf = new ProxyConfiguration();

    if (commandLine.hasOption("port")) {
      conf.listeningPort = Integer.parseInt(commandLine.getOptionValue("port"));
    }

    if (commandLine.hasOption("webstatus_port")) {
      conf.statusPort =  Integer.parseInt(commandLine.getOptionValue("webstatus_port"));
    }

    // Maybe add jumphost.
    HostPort jumphostSshd = null;
    if (commandLine.hasOption("jumphost")) {
      String jumphostString = commandLine.getOptionValue("jumphost");
      try {
        jumphostSshd = conf.parseServerString(jumphostString);
      } catch (URISyntaxException e) {
        LOG.error("Server path parsing exception for jumphost: " + e.getMessage());
        printHelp(options);
        System.exit(1);
      }
    }

    // Add jumphostServer if we have a jumphost.
    HostPort jumphostServer = null;
    if (commandLine.hasOption("jumphost_server")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify jumphost_server.");
        printHelp(options);
        System.exit(1);
      }
      String jumphostServerString = commandLine.getOptionValue("jumphost_server");
      try {
        jumphostServer = conf.parseServerString(jumphostServerString);
        if (jumphostServer.port == -1) {
          throw new URISyntaxException(jumphostServerString,
                                       "Jumphost server parameter missing port.");
        }
      } catch (URISyntaxException e) {
        LOG.error("Server path parsing exception for jumphost_server:" + e.getMessage());
        printHelp(options);
        System.exit(1);
      }
    }

    // Maybe add jumphostUser if we have a jumphost.
    String jumphostUser = null;
    if (commandLine.hasOption("jumphost_user")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify jumphost_user.");
        printHelp(options);
        System.exit(1);
      }
      jumphostUser = commandLine.getOptionValue("jumphost_user");
    }

    // Maybe add jumphostCredentials if we have a jumphost.
    String jumphostCredentials = null;
    if (commandLine.hasOption("jumphost_credentials")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify jumphost_credentials.");
        printHelp(options);
        System.exit(1);
      }
      jumphostCredentials = commandLine.getOptionValue("jumphost_credentials");
    }

    // Maybe set jumphostCompression if we have a jumphost.
    boolean jumphostCompression = false;
    if (commandLine.hasOption("jumphost_compression")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify jumphost_compression.");
        printHelp(options);
        System.exit(1);
      }
      jumphostCompression = true;
    }

    // Maybe add jumphostCiphers if we have a jumphost.
    String jumphostCiphers = null;
    if (commandLine.hasOption("jumphost_ciphers")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify jumphost_ciphers.");
        printHelp(options);
        System.exit(1);
      }
      jumphostCiphers = commandLine.getOptionValue("jumphost_ciphers");
    }

    // Maybe add sshBinary if we have a jumphost.
    String sshBinary = null;
    if (commandLine.hasOption("ssh_binary")) {
      if (!commandLine.hasOption("jumphost")) {
        LOG.error("You need to specify jumphost if you specify ssh_binary.");
        printHelp(options);
        System.exit(1);
      }
      sshBinary = commandLine.getOptionValue("ssh_binary");
    }

    // Add jumphost to the config.
    if (null != jumphostSshd && null != jumphostServer) {
      conf.jumphost = new JumpHost(jumphostSshd, jumphostServer,
                                   jumphostUser, jumphostCredentials,
                                   jumphostCompression, jumphostCiphers,
                                   sshBinary);
    }

    if (!commandLine.hasOption("num_servers") && !commandLine.hasOption("servers")) {
      LOG.error("You need to specify one of the num_servers or servers flags.");
      printHelp(options);
      System.exit(1);
    }

    if (commandLine.hasOption("num_servers") && commandLine.hasOption("servers")) {
      LOG.error("You need to specify one of the num_servers or servers flags, not both.");
      printHelp(options);
      System.exit(1);
    }

    // Add servers.
    if (commandLine.hasOption("num_servers")) {
      try {
        int num_servers = Integer.parseInt(commandLine.getOptionValue("num_servers"));
        if (num_servers > TcpProxyServer.MAX_NUM_SERVERS ) {
          throw new Exception("Please specify -servers.");
        }
        for (int i = 0; i < num_servers; i++) {
          conf.parseServerStringAndAdd("localhost:" + (TcpProxyServer.START_PORT_RANGE + i));
        }
      } catch (Exception e) {
        LOG.error("num_servers parsing exception " + e.getMessage());
        printHelp(options);
        System.exit(1);
      }
    }

    if (commandLine.hasOption("servers")) {
      String[] servers = commandLine.getOptionValues("servers");
      try {
        for (String server : servers) {
          conf.parseServerStringAndAdd(server);
        }
      } catch (URISyntaxException e) {
        LOG.error("Server path parsing exception " + e.getMessage());
        printHelp(options);
        System.exit(1);
      }
    }

    // Maybe set load balancer.
    if (commandLine.hasOption("load_balancer")) {
      HashSet<String> loadBalancers = new HashSet<String>(
          Arrays.asList("RoundRobin", "LeastUsed", "UniformRandom"));
      conf.loadBalancerString = commandLine.getOptionValue("load_balancer");
      if (!loadBalancers.contains(conf.loadBalancerString)) {
        LOG.error("Bad load_balancer value.");
        printHelp(options);
        System.exit(1);
      }
    }
    return conf;
  }

  public static String getProxyVersion() {
    String mvnPropsPath = "/META-INF/maven/com.altiscale/TransferAccelerator/pom.properties";
    Properties props = new Properties();

    InputStream in = TcpProxyServer.class.getResourceAsStream(mvnPropsPath);
    String version = "";
    try {
      props.load(in);
      version = props.getProperty("version", "unknown");
    } catch (Exception e) {
      LOG.info(e.getMessage());
    } finally  {
      try {
        in.close();
      } catch (Exception e){
        LOG.info(e.getMessage());
        /*ignore*/
      }
    }
    return version;
  }

  public static void main (String[] args) {
    TcpProxyServer proxy = new TcpProxyServer("TransferAccelerator");

    proxy.setVersion(getProxyVersion());
    LOG.info("Version " + proxy.getVersion());

    // Create the options.
    Options options = getCommandLineOptions();

    LogManager.getRootLogger().setLevel(Level.WARN);

    ProxyConfiguration config = assembleConfigFromCommandLine(options, args);

    proxy.init(config);

    if (proxy.getServerList().size() < 1) {
      LOG.error("No server specified.");
      printHelp(options);
      System.exit(1);
    }

    // Loop on the listen port forever.
    proxy.runListeningLoop();
  }
}
