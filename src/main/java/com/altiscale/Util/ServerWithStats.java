/**
* Copyright Altiscale 2014
* Author: Cosmin Negruseri <cosmin@altiscale.com>
*
* ServerWithStats is an interface that helps with exporting server statistics.
*/

package com.altiscale.Util;

public interface ServerWithStats {
  public String getServerStatsHtml();
  public boolean isHealthy();
  public String getServerName();
  public String getVersion();
  public void setVersion(String version);
}
