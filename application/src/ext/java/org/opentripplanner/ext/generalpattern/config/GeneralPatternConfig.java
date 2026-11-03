package org.opentripplanner.ext.generalpattern.config;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_9;

import org.opentripplanner.ext.generalpattern.parameters.GeneralPatternParameters;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Maps the {@code generalPatterns} build-config block into {@link GeneralPatternParameters}.
 */
public class GeneralPatternConfig {

  public static GeneralPatternParameters mapGeneralPatternConfig(
    String parameterName,
    NodeAdapter root
  ) {
    var dft = GeneralPatternParameters.DEFAULT;
    var c = root
      .of(parameterName)
      .since(V2_9)
      .summary("Configure how general patterns are built.")
      .description(
        """
        A general pattern merges the trip patterns of a route that differ only by platform,
        skipped stops, short turns or small detours into one readable stop sequence. They are
        built only when the `GeneralPatterns` feature is enabled.
        """
      )
      .asObject();

    return new GeneralPatternParameters(
      c
        .of("excludeRegions")
        .since(V2_9)
        .summary("Skip routes whose `region_name` is in this list.")
        .description(
          "Use this for large city networks, where the regular trip patterns are already readable."
        )
        .asStringSet(dft.excludeRegions()),
      c
        .of("excludeFeeds")
        .since(V2_9)
        .summary("Skip routes from these feed ids.")
        .asStringSet(dft.excludeFeeds()),
      c
        .of("excludeAgencies")
        .since(V2_9)
        .summary("Skip routes of these agencies, given as `feedId:agencyId`.")
        .asStringSet(dft.excludeAgencies()),
      c
        .of("excludeModes")
        .since(V2_9)
        .summary("Skip routes with these transit modes.")
        .asEnumSet(TransitMode.class, dft.excludeModes()),
      c
        .of("stationFallbackRadius")
        .since(V2_9)
        .summary(
          "Stops without a parent station are merged when they have the same name and lie within this distance, in meters."
        )
        .asDouble(dft.stationFallbackRadius()),
      c
        .of("minSharedStations")
        .since(V2_9)
        .summary("Minimum number of shared stations for a pattern to join a general pattern.")
        .asInt(dft.minSharedStations()),
      c
        .of("corridorBuffer")
        .since(V2_9)
        .summary(
          "Distance, in meters, used to test whether two variants run along the same corridor."
        )
        .asDouble(dft.corridorBuffer()),
      c
        .of("corridorMinOverlap")
        .since(V2_9)
        .summary(
          "Share of a variant's shape that must lie within `corridorBuffer` of the other variant."
        )
        .description(
          """
          Only used when the stop order of the merged variants is ambiguous. A lower value merges
          more variants; a higher value keeps branches and different roads apart.
          """
        )
        .asDouble(dft.corridorMinOverlap()),
      c
        .of("skipRadius")
        .since(V2_9)
        .summary(
          "Maximum distance, in meters, from a variant's shape for an unserved stop to count as passed."
        )
        .description(
          """
          A stop the variant does not serve, but passes within this distance of, is marked as
          skipped. A stop further away is a detour that the variant does not take.
          """
        )
        .asDouble(dft.skipRadius()),
      c
        .of("railSkipRadius")
        .since(V2_9)
        .summary("Same as `skipRadius`, used for rail.")
        .asDouble(dft.railSkipRadius())
    );
  }
}
