package org.opentripplanner.routing.fares;

import java.io.Serializable;
import java.util.Optional;
import org.opentripplanner.model.fare.ItineraryFare;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Computes a fare for a given sequence of legs. The FareService is serialized as part of the
 * Graph; Hence it should be {@link Serializable}.
 */
public interface FareService extends Serializable {
  /**
   * @param itinerary the OTP2 Itinerary for which we want to compute a fare
   */
  ItineraryFare calculateFares(Itinerary itinerary);

  /**
   * Returns the fare-relevant identity of a stop, or empty if the fares computed by this service
   * depend on more than the stops that a leg visits, for example on the time of day or the distance
   * travelled. Callers must then compute the fare of every leg individually.
   *
   * @see StopFareIdentity
   */
  default Optional<StopFareIdentity> fareIdentity(StopLocation stop) {
    return Optional.empty();
  }
}
