package org.opentripplanner.ext.stopbearing;

import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * Read access to stop bearings, for the APIs. The bearing of a stop is the compass direction, in
 * whole degrees clockwise from north (0-359), in which vehicles leave it. It is used to rotate stop
 * icons on a map.
 * <p>
 * A stop has no bearing when no scheduled trip departs from it (a terminal), when the departing
 * trips go in clearly different directions, or when the {@code StopBearing} feature is off.
 */
public interface StopBearingService {
  /** The bearing of a stop or station. */
  Optional<Integer> findBearing(FeedScopedId stopOrStationId);
}
