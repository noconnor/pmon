package com.github.noconnor.pmon;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.github.noconnor.pmon.commands.Lsof;
import com.github.noconnor.pmon.data.ConnectionData;
import com.github.noconnor.pmon.data.ProcessData;
import com.github.noconnor.pmon.data.ProcessTree;
import com.google.common.base.Joiner;
import com.google.gson.Gson;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.isNull;
import static spark.Spark.get;

@Slf4j
public class Application {

  public static void main(String[] args) throws Exception {

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
              Optional<ConnectionData> activeConnection = runningProcessData.getChildren()
                .stream()
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

              Optional<ConnectionData> match = historicalProcess.getChildren()
                .stream()
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

      } catch (Exception e) {
        log.error("Unexpected error", e);
      }
    }, 0, 20, TimeUnit.SECONDS);


    URL processesHtml = ClassLoader.getSystemClassLoader().getResource("template.html");
    String html = Joiner.on("\n").join(Files.readAllLines(Paths.get(processesHtml.getFile())));

    get("/processes.html", (req, res) -> {
      return html;
    });
    get("/flare.json", (req, res) -> {
      res.header("Content-type", "application/json");
      Gson gson = new Gson();
      return gson.toJson(tree);
    });

  }

}
