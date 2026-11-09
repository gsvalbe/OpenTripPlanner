package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * One stop of a general pattern, at station level, with the trips that reach, serve, pass or skip
 * it. The counts are derived from the variants by {@link GeneralPattern#of}; status, termini and
 * shares are derived from the counts.
 */
public final class GeneralPatternStop implements Serializable {

  private final GeneralPatternPlace place;
  private final boolean lineTerminus;
  private final int reachingTrips;
  private final int servedTrips;
  private final int passingTrips;
  private final int detourTrips;
  private final int startingTrips;
  private final int endingTrips;

  GeneralPatternStop(
    GeneralPatternPlace place,
    boolean lineTerminus,
    int reachingTrips,
    int servedTrips,
    int passingTrips,
    int detourTrips,
    int startingTrips,
    int endingTrips
  ) {
    this.place = Objects.requireNonNull(place);
    this.lineTerminus = lineTerminus;
    this.reachingTrips = reachingTrips;
    this.servedTrips = servedTrips;
    this.passingTrips = passingTrips;
    this.detourTrips = detourTrips;
    this.startingTrips = startingTrips;
    this.endingTrips = endingTrips;
    if (servedTrips + passingTrips + detourTrips != reachingTrips) {
      throw new IllegalArgumentException(
        "Served, passing and detour trips must add up to reaching trips: " + this
      );
    }
  }

  public GeneralPatternPlace place() {
    return place;
  }

  /** A stop the route uses at this place. */
  public StopLocation stop() {
    return place.stop();
  }

  /** The parent station, if the stops have one. */
  @Nullable
  public Station station() {
    return place.station();
  }

  /** All stops (platforms, same-name stops) merged into this general pattern stop. */
  public List<StopLocation> mergedStops() {
    return place.mergedStops();
  }

  /** Trips whose span (first to last stop) includes this stop. */
  public int reachingTrips() {
    return reachingTrips;
  }

  /** Trips that stop here. */
  public int servedTrips() {
    return servedTrips;
  }

  /** Trips that pass this stop without stopping. */
  public int passingTrips() {
    return passingTrips;
  }

  /** Trips that do not take the detour to this stop. */
  public int detourTrips() {
    return detourTrips;
  }

  /** Trips that start here. */
  public int startingTrips() {
    return startingTrips;
  }

  /** Trips that end here. */
  public int endingTrips() {
    return endingTrips;
  }

  /** Whether this is the first or the last stop of the general pattern. */
  public boolean lineTerminus() {
    return lineTerminus;
  }

  /** Whether at least one trip starts here. */
  public boolean tripsStartHere() {
    return startingTrips > 0;
  }

  /** Whether at least one trip ends here. */
  public boolean tripsEndHere() {
    return endingTrips > 0;
  }

  public StopStatus status() {
    if (passingTrips > 0) {
      return StopStatus.SOMETIMES_SKIPPED;
    }
    if (detourTrips > 0) {
      return StopStatus.DETOUR;
    }
    return StopStatus.ALWAYS;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(GeneralPatternStop.class)
      .addObj("stop", place.stop().getId())
      .addObj("station", place.station() == null ? null : place.station().getId())
      .addNum("mergedStops", place.mergedStops().size())
      .addNum("reaching", reachingTrips)
      .addNum("served", servedTrips)
      .addNum("passing", passingTrips, 0)
      .addNum("detour", detourTrips, 0)
      .addNum("starting", startingTrips, 0)
      .addNum("ending", endingTrips, 0)
      .toString();
  }
}
