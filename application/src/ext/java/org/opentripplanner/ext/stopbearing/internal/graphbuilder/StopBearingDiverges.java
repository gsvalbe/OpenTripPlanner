package org.opentripplanner.ext.stopbearing.internal.graphbuilder;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.graph_builder.issue.api.DataImportIssue;

/**
 * Vehicles leave a stop or station in clearly different directions, so it gets no bearing.
 */
record StopBearingDiverges(
  FeedScopedId id,
  String name,
  double maxSpread
) implements DataImportIssue {
  private static final String FMT =
    "Departures from %s (%s) do not fit in a %.0f° sector, so it has no bearing";

  @Override
  public String getMessage() {
    return String.format(FMT, name, id, maxSpread);
  }
}
