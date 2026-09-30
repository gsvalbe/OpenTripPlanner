package org.opentripplanner.routing.fares;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.site.FareZone;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Describes a stop in terms of those properties that a {@link FareService} looks at when it
 * computes the fare of a leg visiting it, in practice the fare zones (Fares V1) or the fare areas
 * (Fares V2) that the stop belongs to.
 * <p>
 * Two stops with equal identities are interchangeable for fare computation: the fare of a leg of a
 * given trip is then fully determined by the identities of its boarding stop, its alighting stop
 * and the stops in between. Since most trips visit only a handful of distinct zones, callers that
 * need the fares between many stops of the same trip can use this to compute each distinct fare
 * only once instead of once per stop pair.
 */
public record StopFareIdentity(
  String feedId,
  Set<FeedScopedId> fareZones,
  Set<FeedScopedId> fareAreas
) {
  public StopFareIdentity {
    fareZones = Set.copyOf(fareZones);
    fareAreas = Set.copyOf(fareAreas);
  }

  /**
   * The identity of a stop whose fares are determined by the GTFS Fares V1 zones it belongs to.
   */
  public static StopFareIdentity ofFareZones(StopLocation stop) {
    return new StopFareIdentity(
      stop.getId().getFeedId(),
      stop.getFareZones().stream().map(FareZone::getId).collect(Collectors.toUnmodifiableSet()),
      Set.of()
    );
  }

  /**
   * The identity of a stop whose fares are determined by the GTFS Fares V2 areas it belongs to.
   */
  public static StopFareIdentity ofFareAreas(StopLocation stop, Collection<FeedScopedId> areas) {
    return new StopFareIdentity(stop.getId().getFeedId(), Set.of(), Set.copyOf(areas));
  }
}
