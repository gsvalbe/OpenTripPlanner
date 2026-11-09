package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * The identity of a stop at station level: its parent station, or a group of parentless stops
 * with the same name close to each other. Equality is by {@link #id()} only.
 */
final class StationKey {

  private final FeedScopedId id;
  private final StopLocation representative;

  @Nullable
  private final Station station;

  private final WgsCoordinate coordinate;

  private StationKey(
    FeedScopedId id,
    StopLocation representative,
    @Nullable Station station,
    WgsCoordinate coordinate
  ) {
    this.id = id;
    this.representative = representative;
    this.station = station;
    this.coordinate = coordinate;
  }

  static StationKey ofStation(Station station, StopLocation representative) {
    return new StationKey(station.getId(), representative, station, station.getCoordinate());
  }

  static StationKey ofStop(StopLocation stop) {
    return new StationKey(stop.getId(), stop, null, stop.getCoordinate());
  }

  FeedScopedId id() {
    return id;
  }

  StopLocation representative() {
    return representative;
  }

  @Nullable
  Station station() {
    return station;
  }

  WgsCoordinate coordinate() {
    return coordinate;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StationKey other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return id.toString();
  }
}
