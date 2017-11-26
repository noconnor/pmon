package com.github.noconnor.pmon.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

@Data
@EqualsAndHashCode(exclude = {"lastUpdateTime", "disabled"})
public class ProcessData {
  private final String name;
  private long id;
  private List<ConnectionData> children = newArrayList();
  private long lastUpdateTime;
  private boolean disabled;
}
