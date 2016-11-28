/**
 * (c) 2016 SAP SE or an SAP affiliate company. All rights reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import com.altiscale.Util.HostPort;
import com.altiscale.Util.JumpHost;

/**
 *  Utility class which is dealing with the configuration parameters
 *  of the proxy server.
 *
 */
public class ProxyConfiguration {
  // Port where our clients will connect to.
  static final int defaultListeningPort = 14000;
  public int listeningPort;

  // Port where we export web-based status.
  static final int defaultStatusPort = 48138;
  int statusPort;

  String loadBalancerString;

  // List of all our servers.
  ArrayList<HostPort> serverHostPortList;

  // JumpHost to use for establishing ssh tunnels to the server. Null if we don't want it.
  public JumpHost jumphost;

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
