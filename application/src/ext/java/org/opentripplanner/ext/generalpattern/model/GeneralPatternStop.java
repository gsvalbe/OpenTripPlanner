package org.opentripplanner.ext.generalpattern.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * One stop of a general pattern, at station level. The trip counts are scheduled trips over the
 * whole timetable; everything else (status, termini, track weights) is derived from them, so the
 * same logic can be applied to date-filtered counts later.
 */
public record GeneralPatternStop(
  StopLocation stop,
  @Nullable Station station,
  List<StopLocation> mergedStops,
  int reachingTrips,
  int servedTrips,
  int passingTrips,
  int detourTrips,
  int startingTrips,
  int endingTrips
) implements Serializable {
  public GeneralPatternStop(
    StopLocation stop,
    @Nullable Station station,
    List<StopLocation> mergedStops,
    int reachingTrips,
    int servedTrips,
    int passingTrips,
    int detourTrips,
    int startingTrips,
    int endingTrips
  ) {
    this.stop = Objects.requireNonNull(stop);
    this.station = station;
    this.mergedStops = List.copyOf(mergedStops);
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

  /** A representative stop: one of the {@link #mergedStops()} that the route uses. */
  @Override
  public StopLocation stop() {
    return stop;
  }

  /** The parent station, if the stops have one. */
  @Override
  @Nullable
  public Station station() {
    return station;
  }

  /** All stops (platforms, same-name stops) merged into this general pattern stop. */
  @Override
  public List<StopLocation> mergedStops() {
    return mergedStops;
  }

  /** Trips whose span (first to last stop) includes this stop. */
  @Override
  public int reachingTrips() {
    return reachingTrips;
  }

  /** Trips that stop here. */
  @Override
  public int servedTrips() {
    return servedTrips;
  }

  /** Trips that pass this stop without stopping. */
  @Override
  public int passingTrips() {
    return passingTrips;
  }

  /** Trips that do not take the detour to this stop. */
  @Override
  public int detourTrips() {
    return detourTrips;
  }

  /** Trips that start here. */
  @Override
  public int startingTrips() {
    return startingTrips;
  }

  /** Trips that end here. */
  @Override
  public int endingTrips() {
    return endingTrips;
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
      .addObj("stop", stop.getId())
      .addObj("station", station == null ? null : station.getId())
      .addNum("mergedStops", mergedStops.size())
      .addNum("reaching", reachingTrips)
      .addNum("served", servedTrips)
      .addNum("passing", passingTrips, 0)
      .addNum("detour", detourTrips, 0)
      .addNum("starting", startingTrips, 0)
      .addNum("ending", endingTrips, 0)
      .toString();
  }
}
