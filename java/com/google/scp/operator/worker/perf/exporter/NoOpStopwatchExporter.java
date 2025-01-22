package com.google.scp.operator.worker.perf.exporter;

import com.google.scp.operator.worker.perf.StopwatchExporter;
import com.google.scp.operator.worker.perf.StopwatchRegistry;

/** Stopwatch exporter that does nothing. */
public class NoOpStopwatchExporter implements StopwatchExporter {

  @Override
  public void export(StopwatchRegistry stopwatches) {}
}
