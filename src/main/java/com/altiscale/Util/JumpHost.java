/**
 * Copyright 2015 Altiscale <cosmin@altiscale.com>
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
package com.altiscale.Util;

/**
 *  Utility class that holds parameters for a jumphost connection.
 */
public class JumpHost {
  public HostPort sshd;
  public HostPort server;
  public String user;
  public String credentials;
  public boolean compression;
  public String ciphers;
  public String sshBinary;
  public boolean openInterfaces;

  /*  @param sshd            host:port of machine to use for establishing ssh tunnel to
   *                         jumphostServer. sshd.host is the name of the machine where sshd
   *                         is running, and sshd.port is the sshd port number. If port
   *                         number is -1, we let ssh use default port for that hostname.
   *  @param server          End server we want to connect to via ssh tunnel. For example,
   *                         httpfs-node:14000. server.host must be visible from host where
   *                         sshd is running.
   *  @param user            Optional username we want to use for ssh (to override the default).
   *  @param credentials     Optional path to a file with ssh credentials to use with ssh -i
   *  @param compression     If true, add optional -C flag to ssh tunnel command to turn on
   *                         compression.
   *  @param ciphers         Optional string for ssh -c command option for cipher specs.
   *  @param sshBinary       Optional binary path and name to use instead of default 'ssh'.
   *  @param openInterfaces  Tunnels will be open on all interfaces not just the default one.
   */
  public JumpHost(HostPort sshd,
                  HostPort server,
                  String user,
                  String credentials,
                  boolean compression,
                  String ciphers,
                  String sshBinary,
                  boolean openInterfaces) {
    this.sshd = sshd;
    this.server = server;
    this.user = user;
    this.credentials = credentials;
    this.compression = compression;
    this.ciphers = ciphers;
    this.sshBinary = sshBinary;
    this.openInterfaces = openInterfaces;
  }
}

