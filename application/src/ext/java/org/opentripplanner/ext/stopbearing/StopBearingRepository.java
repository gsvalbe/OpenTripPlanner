package org.opentripplanner.ext.stopbearing;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * Holds the stop bearings computed during graph build, keyed by stop or station id. It is
 * serialized with the graph and is read-only after the build.
 */
public interface StopBearingRepository extends Serializable {
  /** Add bearings, in whole degrees clockwise from north. Used only by the graph build. */
  void putAll(Map<FeedScopedId, Integer> bearings);

  /** The bearing of a stop or station, empty if it has none. */
  Optional<Integer> findBearing(FeedScopedId stopOrStationId);

  int size();
}
