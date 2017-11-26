package com.github.noconnor.pmon.commands;

import com.github.noconnor.pmon.data.ConnectionData;
import com.github.noconnor.pmon.data.ProcessData;
import com.google.common.net.HostAndPort;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.System.currentTimeMillis;


@Slf4j
public class Lsof implements Command {

  private static final String COMMAND = "/usr/sbin/lsof -i -P";

  @Override
  public Map<String, ProcessData> execute() throws IOException, InterruptedException {
    Runtime rt = Runtime.getRuntime();
    Process proc = rt.exec(COMMAND);
    proc.waitFor();

    Map<String, ProcessData> processes = newHashMap();

    try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
      String line;
      while ((line = stdInput.readLine()) != null) {
        String[] parts = line.split("\\s+");
        String process = parts[0];
        String connection = parts[8];

        String[] connectionParts = connection.split("->");
        if (connectionParts.length > 1) {

          HostAndPort src = HostAndPort.fromString(connectionParts[0]);
          HostAndPort tgt = HostAndPort.fromString(connectionParts[1]);

          ConnectionData conn = ConnectionData.builder()
            .name(tgt.getHostText())
            .lastUpdateTime(currentTimeMillis())
            .srcAddress(src.getHostText())
            .srcPort(src.getPort())
            .targetAddress(tgt.getHostText())
            .targetPort(tgt.getPort())
            .build();

          ProcessData entry = processes.getOrDefault(process, new ProcessData(process));

          entry.getChildren().add(conn);
          processes.put(process, entry);
        }
      }
    }
    return processes;

  }


}
