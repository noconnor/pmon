package com.github.noconnor.pmon.commands;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.net.HostAndPort;
import com.google.gson.Gson;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.isNull;


@Slf4j
public class Lsof {

  private static final String COMMAND = "/usr/sbin/lsof -i -P";


  public Map<String, ProcessEntry> execute() throws IOException, InterruptedException {
    Runtime rt = Runtime.getRuntime();
    Process proc = rt.exec(COMMAND);
    proc.waitFor();

    Map<String, ProcessEntry> processes = newHashMap();

    try(BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
      String line;
      while ((line = stdInput.readLine()) != null) {
        String[] parts = line.split("\\s+");
        String process = parts[0];
        String connection = parts[8];

        String[] connectionParts = connection.split("->");
        if (connectionParts.length > 1) {

          HostAndPort src = HostAndPort.fromString(connectionParts[0]);
          HostAndPort tgt = HostAndPort.fromString(connectionParts[1]);

          Connection conn = Connection.builder()
            .name(tgt.getHostText())
            .lastUpdateTime(currentTimeMillis())
            .srcAddress(src.getHostText())
            .srcPort(src.getPort())
            .targetAddress(tgt.getHostText())
            .targetPort(tgt.getPort())
            .build();

          ProcessEntry entry = processes.getOrDefault(process, new ProcessEntry(process));

          entry.children.add(conn);
          processes.put(process, entry);
        }
      }
    }
    return processes;

  }


  @Data
  static class ProcessTree {
    private final String name;
    private final long id;
    private List<ProcessEntry> children;
  }

  @Data
  @EqualsAndHashCode(exclude={"lastUpdateTime", "disabled"})
  static class ProcessEntry {
    private final String name;
    private long id;
    private List<Connection> children = newArrayList();
    private long lastUpdateTime;
    private boolean disabled;
  }


  @Data
  @Builder
  @EqualsAndHashCode(exclude={"lastUpdateTime", "disabled"})
  static class Connection {
    private final String name;
    private long id;
    private String srcAddress;
    private int srcPort;
    private String targetAddress;
    private int targetPort;
    private long lastUpdateTime;
    private boolean disabled;
  }


  public static void main(String[] args) throws UnknownHostException {

    AtomicLong idGenerator = new AtomicLong();

    ProcessTree tree = new ProcessTree(InetAddress.getLocalHost().getHostName(), idGenerator.incrementAndGet());
    Map<String, ProcessEntry> processHistory = newHashMap();


    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
      try {

        Map<String, ProcessEntry> runningProcesses = new Lsof().execute();

        runningProcesses.forEach( (processName, runningProcessData) -> {
          ProcessEntry historicalProcess = processHistory.get(processName);
          if(isNull(historicalProcess)) {
            // Add new process to the map
            runningProcessData.id = idGenerator.incrementAndGet();
            runningProcessData.children.forEach( c -> c.id = idGenerator.incrementAndGet());
            processHistory.put(processName, runningProcessData);
          } else {

            // Mark old connections as disabled
            historicalProcess.children.forEach( historicalConnection -> {
              Optional<Connection> activeConnection = runningProcessData.children.stream()
                .filter(c -> c.name.equals(historicalConnection.name))
                .findFirst();

              if (activeConnection.isPresent()) {
                historicalConnection.disabled = false;
                historicalConnection.lastUpdateTime = activeConnection.get().lastUpdateTime;
              } else {
                historicalConnection.disabled = true;
              }
            });

            // add new connections
            runningProcessData.children.forEach( activeConnection -> {

              Optional<Connection> match = historicalProcess.children.stream()
                .filter(c -> c.name.equals(activeConnection.name))
                .findFirst();

              if(!match.isPresent()){
                activeConnection.id = idGenerator.incrementAndGet();
                historicalProcess.children.add(activeConnection);
              }
            });
          }
        });

        // Mark old processes as disabled
        processHistory.forEach( (processName, historicalProcessData) -> {
          if (!runningProcesses.containsKey(processName)) {
            historicalProcessData.disabled = true;
            historicalProcessData.children.forEach( c -> c.disabled = true);
          } else {
            historicalProcessData.disabled = false;
          }
          historicalProcessData.children.sort(reverseOrder(comparingLong(c -> c.lastUpdateTime)));
        });

        tree.children = newArrayList(processHistory.values());

        Gson gson = new Gson();
        String json = gson.toJson(tree);
        URL url = ClassLoader.getSystemClassLoader().getResource("flare.json");
        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(url.getFile())))){
          writer.write(json);
        }

        System.out.println(json);

      } catch (IOException | InterruptedException e) {
        // ignore
      }
    }, 0, 20, TimeUnit.SECONDS);

    while (!Thread.currentThread().isInterrupted()) {
      try {
        Thread.sleep(1_000);
      } catch (InterruptedException e) {
        break;
      }
    }
  }

}
