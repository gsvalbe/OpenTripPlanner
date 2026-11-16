package org.opentripplanner.ext.stopbearing.parameters;

import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Build parameters for stop bearings.
 *
 * @param lookAheadDistance How far along the shape, in meters, the departure direction is measured.
 *                          Short distances pick up noise in the shape, long ones reach the next
 *                          turn.
 * @param maxStraightLineHopLength A hop with only two points that is longer than this, in meters,
 *                                 is assumed to be the straight-line fallback used when a trip has
 *                                 no usable shape, and is ignored.
 * @param maxSpread The departure directions of a stop must fit in a sector this wide, in degrees,
 *                  for the stop to get a bearing.
 * @param maxOutlierShare Departures outside the sector are tolerated as long as they make up at
 *                        most this share of the stop's trips, so a rare variant does not remove the
 *                        bearing of a busy stop.
 */
public record StopBearingParameters(
  double lookAheadDistance,
  double maxStraightLineHopLength,
  double maxSpread,
  double maxOutlierShare
) {
  public static final StopBearingParameters DEFAULT = new StopBearingParameters(30, 200, 90, 0.05);

  public StopBearingParameters {
    if (!(lookAheadDistance > 0)) {
      throw new IllegalArgumentException(
        "lookAheadDistance must be positive: " + lookAheadDistance
      );
    }
    if (!(maxStraightLineHopLength > 0)) {
      throw new IllegalArgumentException(
        "maxStraightLineHopLength must be positive: " + maxStraightLineHopLength
      );
    }
    if (!(maxSpread > 0 && maxSpread <= 360)) {
      throw new IllegalArgumentException("maxSpread must be in (0, 360]: " + maxSpread);
    }
    if (!(maxOutlierShare >= 0 && maxOutlierShare < 0.5)) {
      throw new IllegalArgumentException("maxOutlierShare must be in [0, 0.5): " + maxOutlierShare);
    }
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(StopBearingParameters.class)
      .addNum("lookAheadDistance", lookAheadDistance)
      .addNum("maxStraightLineHopLength", maxStraightLineHopLength)
      .addNum("maxSpread", maxSpread)
      .addNum("maxOutlierShare", maxOutlierShare)
      .toString();
  }
}
