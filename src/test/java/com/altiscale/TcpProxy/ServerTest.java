/** Copyright Altiscale 2014
 *  Author: Zoran Dimitrijevic <zoran@altiscale.com>
 *
 *  TcpProxy Server unittests.
 **/

package com.altiscale.TcpProxy;

import com.altiscale.TcpProxy.HostPort;
import com.altiscale.TcpProxy.Server;

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
                                     "/tmp/road-runner-keys",
                                     "/tmp/roadrunner/rm -rf /;");
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "/tmp/roadrunner/rm -rf /; -n -N -L -i /tmp/road-runner-keys " +
        "12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }

  public void testSshTunnelCommandDefaultSshPort() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", -1),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     null,
                                     null);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "ssh -n -N -L 12345:acme-supersecret-server:14000 -l wileEcoyote acme-secret-lab");
  }

  public void testSshTunnelCommand() {
    HostPort hostPort = new HostPort("localhost", 12345);
    JumpHost jumphost = new JumpHost(new HostPort("acme-secret-lab", 22),
                                     new HostPort("acme-supersecret-server", 14000),
                                     "wileEcoyote",
                                     null,
                                     null);
    Server server = new Server(hostPort, jumphost);
    String sshCommand = server.sshJumphostCommand();
    System.out.println(sshCommand);
    assert sshCommand.equals(
        "ssh -n -N -L 12345:acme-supersecret-server:14000 -l wileEcoyote -p 22 acme-secret-lab");
  }
}
