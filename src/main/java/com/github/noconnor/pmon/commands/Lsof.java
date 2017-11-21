package com.github.noconnor.pmon.commands;

import lombok.Builder;
import lombok.Value;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringEscapeUtils;
import com.google.common.base.Joiner;
import com.google.common.net.HostAndPort;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

public class Lsof {

  // Netstat on linux/unix , lsof on Mac
  private static final String COMMAND = "/usr/sbin/lsof -i -P";

  //
  // Proof of concept!
  //
  public void execute() throws IOException, InterruptedException {
    Runtime rt = Runtime.getRuntime();
    Process proc = rt.exec(COMMAND);
    proc.waitFor();

    BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

    Map<String, List<Connection>> output = newHashMap();
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
          .srcAddress(src.getHostText())
          .srcPort(src.getPort())
          .targetAddress(tgt.getHostText())
          .targetPort(tgt.getPort())
          .build();

        List<Connection> connections = output.getOrDefault(process, newArrayList());
        connections.add(conn);
        output.put(process, connections);
      }
    }

    // Bleugh
    StringBuilder tree = new StringBuilder();
    tree.append("{\"name\":\"processes\",");
    tree.append("\"children\":[");
    List<String> nodes = newArrayList();
    output.forEach( (k,v) -> {
      StringBuilder node = new StringBuilder();
      node.append("{");
      node.append("\"name\":\"");
      node.append(StringEscapeUtils.escapeJson(k));
      node.append("\"");
      if(isNotEmpty(v)) {
        node.append(", \"children\" : [");
        List<String> children = newArrayList();
        v.sort(Comparator.comparing(o -> o.targetAddress));
        v.forEach( c -> {
            StringBuilder child = new StringBuilder();
            child.append("{\"name\":\"");
            child.append(c.targetAddress);
            child.append(":");
            child.append(c.targetPort);
            child.append("\"}");
            children.add(child.toString());
          }
        );
        node.append(Joiner.on(",").join(children));
        node.append("]");
      }
      node.append("}");
      nodes.add(node.toString());
    });
    tree.append(Joiner.on(",").join(nodes));
    tree.append("]}");

    URL url = ClassLoader.getSystemClassLoader().getResource("flare.json");
    try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(url.getFile())))){
      writer.write(tree.toString());
    }

    System.out.println(tree);

  }


  @Value
  @Builder
  static class Connection {
    String srcAddress;
    int srcPort;
    String targetAddress;
    int targetPort;
  }


  public static void main(String[] args) throws IOException, InterruptedException {
    new Lsof().execute();
  }

}
