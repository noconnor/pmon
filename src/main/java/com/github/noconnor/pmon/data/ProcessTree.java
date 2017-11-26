package com.github.noconnor.pmon.data;

import lombok.Data;

import java.util.List;

@Data
public class ProcessTree {
  private final String name;
  private final long id;
  private List<ProcessData> children;
}
