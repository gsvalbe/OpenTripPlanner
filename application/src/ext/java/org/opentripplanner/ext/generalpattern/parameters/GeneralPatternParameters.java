package org.opentripplanner.ext.generalpattern.parameters;

import java.util.Set;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Build parameters for general patterns.
 *
 * @param excludeRegions Routes whose {@link Route#getRegionName()} is in this set are skipped.
 *                       Used to leave large city networks, where OTP patterns are already readable,
 *                       untouched.
 * @param excludeFeeds Feed ids whose routes are skipped.
 * @param excludeAgencies Agency ids (feed-scoped, as {@code feedId:agencyId}) whose routes are
 *                        skipped.
 * @param excludeModes Transit modes that are skipped.
 * @param stationFallbackRadius Stops without a parent station are treated as one station when they
 *                              have the same normalized name and lie within this distance (meters).
 * @param minSharedStations A pattern only joins a general pattern when they share at least this
 *                          many stations.
 * @param corridorBuffer Distance (meters) used when testing whether two variants run along the same
 *                       corridor.
 * @param corridorMinOverlap Share of a variant's shape that must lie within {@code corridorBuffer}
 *                           of the other variant for an ambiguous merge to be accepted.
 * @param skipRadius Maximum distance (meters) between an unserved stop and a variant's shape for the
 *                   stop to count as passed without stopping, rather than as a detour not taken.
 * @param railSkipRadius Same as {@code skipRadius}, for rail.
 */
public record GeneralPatternParameters(
  Set<String> excludeRegions,
  Set<String> excludeFeeds,
  Set<String> excludeAgencies,
  Set<TransitMode> excludeModes,
  double stationFallbackRadius,
  int minSharedStations,
  double corridorBuffer,
  double corridorMinOverlap,
  double skipRadius,
  double railSkipRadius
) {
  public static final GeneralPatternParameters DEFAULT = new GeneralPatternParameters(
    Set.of(),
    Set.of(),
    Set.of(),
    Set.of(),
    400,
    2,
    1000,
    0.85,
    400,
    250
  );

  public GeneralPatternParameters {
    excludeRegions = Set.copyOf(excludeRegions);
    excludeFeeds = Set.copyOf(excludeFeeds);
    excludeAgencies = Set.copyOf(excludeAgencies);
    excludeModes = Set.copyOf(excludeModes);
    requirePositive(stationFallbackRadius, "stationFallbackRadius");
    requirePositive(corridorBuffer, "corridorBuffer");
    requirePositive(skipRadius, "skipRadius");
    requirePositive(railSkipRadius, "railSkipRadius");
    if (minSharedStations < 1) {
      throw new IllegalArgumentException("minSharedStations must be at least 1");
    }
    if (corridorMinOverlap < 0 || corridorMinOverlap > 1) {
      throw new IllegalArgumentException("corridorMinOverlap must be in [0, 1]");
    }
  }

  /**
   * Whether general patterns should be built for the given route.
   */
  public boolean includes(Route route) {
    if (excludeModes.contains(route.getMode())) {
      return false;
    }
    if (excludeFeeds.contains(route.getId().getFeedId())) {
      return false;
    }
    if (
      route.getAgency() != null && excludeAgencies.contains(route.getAgency().getId().toString())
    ) {
      return false;
    }
    return route.getRegionName() == null || !excludeRegions.contains(route.getRegionName());
  }

  /**
   * The skip radius to use for the given mode.
   */
  public double skipRadius(TransitMode mode) {
    return mode == TransitMode.RAIL ? railSkipRadius : skipRadius;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(GeneralPatternParameters.class)
      .addCol("excludeRegions", excludeRegions)
      .addCol("excludeFeeds", excludeFeeds)
      .addCol("excludeAgencies", excludeAgencies)
      .addCol("excludeModes", excludeModes)
      .addNum("stationFallbackRadius", stationFallbackRadius)
      .addNum("minSharedStations", minSharedStations)
      .addNum("corridorBuffer", corridorBuffer)
      .addNum("corridorMinOverlap", corridorMinOverlap)
      .addNum("skipRadius", skipRadius)
      .addNum("railSkipRadius", railSkipRadius)
      .toString();
  }

  private static void requirePositive(double value, String name) {
    if (!(value > 0)) {
      throw new IllegalArgumentException(name + " must be positive: " + value);
    }
  }
}
