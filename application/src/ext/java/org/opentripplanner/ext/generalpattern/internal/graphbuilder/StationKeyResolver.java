package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Maps stops to station keys. A stop with a parent station maps to that station. Stops without a
 * parent station are grouped when they belong to the same feed, have the same normalized name and
 * lie within a given radius of the group's first stop. Many bus feeds use several stop ids for one
 * stop place (one per side of the road, or per platform) without a parent station.
 */
final class StationKeyResolver {

  private static final Pattern PUNCTUATION = Pattern.compile("[()\\[\\].,]");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private StationKeyResolver() {}

  static Map<StopLocation, StationKey> resolve(Collection<StopLocation> stops, double radius) {
    var sorted = new ArrayList<>(stops);
    sorted.sort(Comparator.comparing(StopLocation::getId));

    Map<StopLocation, StationKey> result = new HashMap<>();
    Map<Station, StationKey> stationKeys = new HashMap<>();
    Map<String, List<StationKey>> nameGroups = new LinkedHashMap<>();

    for (StopLocation stop : sorted) {
      Station parent = stop.getParentStation();
      if (parent != null) {
        result.put(stop, stationKeys.computeIfAbsent(parent, s -> StationKey.ofStation(s, stop)));
        continue;
      }
      String name = stop.getName() == null ? stop.getId().getId() : stop.getName().toString();
      String groupKey = stop.getId().getFeedId() + "|" + normalize(name);
      List<StationKey> candidates = nameGroups.computeIfAbsent(groupKey, k -> new ArrayList<>());
      StationKey match = null;
      for (StationKey candidate : candidates) {
        if (distance(candidate.representative(), stop) <= radius) {
          match = candidate;
          break;
        }
      }
      if (match == null) {
        match = StationKey.ofStop(stop);
        candidates.add(match);
      }
      result.put(stop, match);
    }
    return result;
  }

  static String normalize(String name) {
    String s = PUNCTUATION.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ");
    return SPACES.matcher(s).replaceAll(" ").strip();
  }

  private static double distance(StopLocation a, StopLocation b) {
    return SphericalDistanceLibrary.fastDistance(
      a.getCoordinate().latitude(),
      a.getCoordinate().longitude(),
      b.getCoordinate().latitude(),
      b.getCoordinate().longitude()
    );
  }
}
