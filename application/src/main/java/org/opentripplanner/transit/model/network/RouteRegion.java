package org.opentripplanner.transit.model.network;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.basic.WgsBoundingBox;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * A named geographic region that routes belong to, see {@link Route#getRegionName()}. The bounds
 * are derived from the stops served by the routes in the region, and are missing if none of those
 * routes have any stops.
 */
public final class RouteRegion {

  private final String name;

  @Nullable
  private final WgsBoundingBox bounds;

  public RouteRegion(String name, @Nullable WgsBoundingBox bounds) {
    this.name = Objects.requireNonNull(name);
    this.bounds = bounds;
  }

  public String name() {
    return name;
  }

  public Optional<WgsBoundingBox> bounds() {
    return Optional.ofNullable(bounds);
  }

  /**
   * Order regions by relevance for the given location: the closest region first. Regions
   * containing the location are all at distance zero, so among them the smallest (most specific)
   * one comes first. Regions without bounds come last. Ties are broken by name.
   */
  public static Comparator<RouteRegion> closestFirst(WgsCoordinate location) {
    Comparator<RouteRegion> byDistance = Comparator.comparingDouble(r ->
      r.bounds == null ? Double.POSITIVE_INFINITY : r.bounds.distanceTo(location)
    );
    Comparator<RouteRegion> byArea = Comparator.comparingDouble(r ->
      r.bounds == null ? Double.POSITIVE_INFINITY : r.bounds.area()
    );
    return byDistance.thenComparing(byArea).thenComparing(RouteRegion::name);
  }

  @Override
  public boolean equals(Object o) {
    return (
      o instanceof RouteRegion other &&
      name.equals(other.name) &&
      Objects.equals(bounds, other.bounds)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, bounds);
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(RouteRegion.class)
      .addStr("name", name)
      .addObj("bounds", bounds)
      .toString();
  }
}
