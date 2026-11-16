package org.opentripplanner.ext.stopbearing.config;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import org.opentripplanner.ext.stopbearing.parameters.StopBearingParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * Maps the {@code stopBearings} build-config block into {@link StopBearingParameters}.
 */
public class StopBearingConfig {

  public static StopBearingParameters mapStopBearingConfig(String parameterName, NodeAdapter root) {
    var dft = StopBearingParameters.DEFAULT;
    var c = root
      .of(parameterName)
      .since(V2_9)
      .summary("Configure how stop bearings are computed.")
      .description(
        """
        The bearing of a stop is the direction in which vehicles leave it, taken from the shapes
        of the scheduled trip patterns. A station gets the combined bearing of its child stops.
        Terminals, and stops where vehicles leave in clearly different directions, get none.
        Bearings are computed only when the `StopBearing` feature is enabled.
        """
      )
      .asObject();

    return new StopBearingParameters(
      c
        .of("lookAheadDistance")
        .since(V2_9)
        .summary("How far along the shape, in meters, the departure direction is measured.")
        .asDouble(dft.lookAheadDistance()),
      c
        .of("maxStraightLineHopLength")
        .since(V2_9)
        .summary(
          "Ignore two-point hops longer than this, in meters, as straight lines drawn for trips without a shape."
        )
        .asDouble(dft.maxStraightLineHopLength()),
      c
        .of("maxSpread")
        .since(V2_9)
        .summary(
          "The departure directions of a stop must fit in a sector this wide, in degrees, for it to get a bearing."
        )
        .asDouble(dft.maxSpread()),
      c
        .of("maxOutlierShare")
        .since(V2_9)
        .summary("Share of a stop's trips that may leave outside the sector.")
        .description(
          "This keeps a rare variant, like a depot run, from removing the bearing of a busy stop."
        )
        .asDouble(dft.maxOutlierShare())
    );
  }
}
