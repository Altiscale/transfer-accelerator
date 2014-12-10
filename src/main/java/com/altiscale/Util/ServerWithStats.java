/**
* Copyright Altiscale 2014
* Author: Cosmin Negruseri <cosmin@altiscale.com>
*
* ServerWithStats is an interface that helps with exporting server statistics.
*/

package com.altiscale.Util;

import java.util.Map;

public interface ServerWithStats {
  public Map<String, String> getServerStats();
  public String getServerName();
}
