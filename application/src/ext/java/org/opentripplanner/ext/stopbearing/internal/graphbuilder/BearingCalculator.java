package org.opentripplanner.ext.stopbearing.internal.graphbuilder;

import java.util.Collection;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.ext.stopbearing.parameters.StopBearingParameters;
import org.opentripplanner.street.geometry.DirectionUtils;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;

/**
 * The geometry and statistics behind stop bearings: the departure direction of a single hop, and
 * the combined direction of all departures from a stop.
 */
class BearingCalculator {

  /** A departure closer than this to the stop, in meters, has no meaningful direction. */
  private static final double MIN_DISTANCE = 1.0;

  private final StopBearingParameters parameters;

  BearingCalculator(StopBearingParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * The direction in which a vehicle leaves the first point of the hop, in degrees clockwise from
   * north in [0, 360). It is measured from the start of the hop to the first point at least
   * {@code lookAheadDistance} meters along it, or to the end of a shorter hop. Empty for hops that
   * look like a straight line drawn because the trip has no shape, and for degenerate hops.
   */
  OptionalDouble departureBearing(LineString hop) {
    int n = hop.getNumPoints();
    if (n < 2) {
      return OptionalDouble.empty();
    }
    Coordinate start = hop.getCoordinateN(0);
    if (
      n == 2 &&
      SphericalDistanceLibrary.fastDistance(start, hop.getCoordinateN(1)) >
        parameters.maxStraightLineHopLength()
    ) {
      return OptionalDouble.empty();
    }

    Coordinate target = hop.getCoordinateN(n - 1);
    double travelled = 0;
    for (int i = 1; i < n; i++) {
      travelled += SphericalDistanceLibrary.fastDistance(
        hop.getCoordinateN(i - 1),
        hop.getCoordinateN(i)
      );
      if (travelled >= parameters.lookAheadDistance()) {
        target = hop.getCoordinateN(i);
        break;
      }
    }
    if (SphericalDistanceLibrary.fastDistance(start, target) < MIN_DISTANCE) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(normalize(DirectionUtils.getAzimuth(start, target)));
  }

  /**
   * Combine the departures from a stop into one bearing, in whole degrees in [0, 360). The
   * departures are averaged as weighted unit vectors. If the departures pointing more than half of
   * {@code maxSpread} away from the average carry more than {@code maxOutlierShare} of the weight,
   * the stop has no single direction and the result is empty. Otherwise the outliers are dropped
   * and the rest averaged again.
   */
  OptionalInt combine(Collection<Departure> departures) {
    double totalWeight = departures.stream().mapToDouble(Departure::weight).sum();
    if (totalWeight <= 0) {
      return OptionalInt.empty();
    }
    var mean = weightedMean(departures);
    if (mean.isEmpty()) {
      return OptionalInt.empty();
    }

    double halfSpread = parameters.maxSpread() / 2;
    var inliers = departures
      .stream()
      .filter(d -> DirectionUtils.bearingDifference(d.bearing(), mean.getAsDouble()) <= halfSpread)
      .toList();
    double inlierWeight = inliers.stream().mapToDouble(Departure::weight).sum();
    if ((totalWeight - inlierWeight) / totalWeight > parameters.maxOutlierShare()) {
      return OptionalInt.empty();
    }

    var inlierMean = weightedMean(inliers);
    if (inlierMean.isEmpty()) {
      return OptionalInt.empty();
    }
    return OptionalInt.of((int) Math.round(inlierMean.getAsDouble()) % 360);
  }

  private static OptionalDouble weightedMean(Collection<Departure> departures) {
    double x = 0;
    double y = 0;
    double totalWeight = 0;
    for (Departure d : departures) {
      double rad = Math.toRadians(d.bearing());
      x += d.weight() * Math.sin(rad);
      y += d.weight() * Math.cos(rad);
      totalWeight += d.weight();
    }
    // Departures that cancel out, like exactly opposite directions, have no mean
    if (totalWeight <= 0 || Math.hypot(x, y) < 1e-6 * totalWeight) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(normalize(Math.toDegrees(Math.atan2(x, y))));
  }

  private static double normalize(double bearing) {
    double b = bearing % 360;
    return b < 0 ? b + 360 : b;
  }

  /**
   * One departure direction from a stop.
   *
   * @param bearing degrees clockwise from north
   * @param weight the number of trips leaving in this direction
   */
  record Departure(double bearing, double weight) {}
}
