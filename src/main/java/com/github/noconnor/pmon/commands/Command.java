package com.github.noconnor.pmon.commands;

import java.util.Map;
import com.github.noconnor.pmon.data.ProcessData;

public interface Command {

  Map<String, ProcessData> execute() throws Exception;

}
