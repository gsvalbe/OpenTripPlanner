package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Where a general pattern stops, at station level, without any trip counts.
 *
 * @param stop A stop the route uses at this place.
 * @param station The parent station, if the stops have one.
 * @param mergedStops All stops (platforms, same-name stops) merged into this place.
 */
public record GeneralPatternPlace(
  StopLocation stop,
  @Nullable Station station,
  List<StopLocation> mergedStops
) implements Serializable {
  public GeneralPatternPlace {
    Objects.requireNonNull(stop);
    mergedStops = List.copyOf(mergedStops);
    if (!mergedStops.contains(stop)) {
      throw new IllegalArgumentException("The stop must be one of the merged stops: " + stop);
    }
  }
}
