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


/* TcpProxy Server unittests. */
package com.altiscale.TcpProxy;

import com.altiscale.TcpProxy.Server;
import com.altiscale.Util.HostPort;
import com.altiscale.Util.JumpHost;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unittests for TcpTunnel.
 */
public class ServerTest extends TestCase {
  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public ServerTest(String testName) {
      super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(ServerTest.class);
  }

  public void testSshTunnelCommandAll() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", 22),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     "acme-keys",
                                     true,
                                     "blowfish-cbc,aes128-cbc,3des-cbc",
                                     "/usr/bin/ssh",
                                     true);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "/usr/bin/ssh -i acme-keys -C " +
        "-c blowfish-cbc,aes128-cbc,3des-cbc -o StrictHostKeyChecking=no -n -N -L " +
        "*:12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }

  public void testSshTunnelCommandBadAll() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", 22),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     "/tmp/road-runner-keys",
                                     true,
                                     "des",
                                     "/tmp/roadrunner/rm -rf /;",
                                     false);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "/tmp/roadrunner/rm -rf /; -i /tmp/road-runner-keys -C -c des -o StrictHostKeyChecking=no -n -N -L " +
        "12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }

  public void testSshTunnelCommandDefaultSshPort() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", -1),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     null,
                                     false,
                                     null,
                                     null,
                                     false);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "ssh -o StrictHostKeyChecking=no -n -N -L 12345:acme-supersecret-server:14000" +
        " -l wileEcoyote acme-secret-lab");
  }

  public void testSshTunnelCommandOpenInterfaces() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", 22),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     null,
                                     false,
                                     null,
                                     null,
                                     true);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "ssh -o StrictHostKeyChecking=no -n -N -L" +
        " *:12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }


  public void testSshTunnelCommand() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", 22),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     null,
                                     false,
                                     null,
                                     null,
                                     false);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "ssh -o StrictHostKeyChecking=no -n -N -L 12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }
}
