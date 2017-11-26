package com.github.noconnor.pmon.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(exclude = {"lastUpdateTime", "disabled"})
public class ConnectionData {
  private final String name;
  private long id;
  private String srcAddress;
  private int srcPort;
  private String targetAddress;
  private int targetPort;
  private long lastUpdateTime;
  private boolean disabled;
}
