package com.github.noconnor.pmon;

import com.github.noconnor.pmon.commands.Lsof;
import com.github.noconnor.pmon.data.ConnectionData;
import com.github.noconnor.pmon.data.ProcessData;
import com.github.noconnor.pmon.data.ProcessTree;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.isNull;

public class Application {

  public static void main(String[] args) throws UnknownHostException {

    AtomicLong idGenerator = new AtomicLong();

    ProcessTree tree = new ProcessTree(InetAddress.getLocalHost().getHostName(), idGenerator.incrementAndGet());
    Map<String, ProcessData> processHistory = newHashMap();


    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
      try {

        Map<String, ProcessData> runningProcesses = new Lsof().execute();

        runningProcesses.forEach((processName, runningProcessData) -> {
          ProcessData historicalProcess = processHistory.get(processName);
          if (isNull(historicalProcess)) {
            // Add new process to the map
            runningProcessData.setId(idGenerator.incrementAndGet());
            runningProcessData.getChildren().forEach(c -> c.setId(idGenerator.incrementAndGet()));
            processHistory.put(processName, runningProcessData);
          } else {

            // Mark old connections as disabled
            historicalProcess.getChildren().forEach(historicalConnection -> {
              Optional<ConnectionData> activeConnection = runningProcessData.getChildren().stream()
                .filter(c -> c.getName().equals(historicalConnection.getName()))
                .findFirst();

              if (activeConnection.isPresent()) {
                historicalConnection.setDisabled(false);
                historicalConnection.setLastUpdateTime(activeConnection.get().getLastUpdateTime());
              } else {
                historicalConnection.setDisabled(true);
              }
            });

            // add new connections
            runningProcessData.getChildren().forEach(activeConnection -> {

              Optional<ConnectionData> match = historicalProcess.getChildren().stream()
                .filter(c -> c.getName().equals(activeConnection.getName()))
                .findFirst();

              if (!match.isPresent()) {
                activeConnection.setId(idGenerator.incrementAndGet());
                historicalProcess.getChildren().add(activeConnection);
              }
            });
          }
        });

        // Mark old processes as disabled
        processHistory.forEach((processName, historicalProcessData) -> {
          if (!runningProcesses.containsKey(processName)) {
            historicalProcessData.setDisabled(true);
            historicalProcessData.getChildren().forEach(c -> c.setDisabled(true));
          } else {
            historicalProcessData.setDisabled(false);
          }
          historicalProcessData.getChildren().sort(reverseOrder(comparingLong(ConnectionData::getLastUpdateTime)));
        });

        tree.setChildren(newArrayList(processHistory.values()));

        Gson gson = new Gson();
        String json = gson.toJson(tree);
        URL url = ClassLoader.getSystemClassLoader().getResource("flare.json");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(url.getFile())))) {
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
