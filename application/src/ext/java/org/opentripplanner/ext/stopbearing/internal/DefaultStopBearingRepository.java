package org.opentripplanner.ext.stopbearing.internal;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;

@Singleton
public class DefaultStopBearingRepository implements StopBearingRepository {

  private final Map<FeedScopedId, Integer> bearings = new HashMap<>();

  @Inject
  public DefaultStopBearingRepository() {}

  @Override
  public void putAll(Map<FeedScopedId, Integer> bearings) {
    bearings.forEach((id, bearing) -> {
      if (bearing < 0 || bearing >= 360) {
        throw new IllegalArgumentException("Bearing out of range for " + id + ": " + bearing);
      }
    });
    this.bearings.putAll(bearings);
  }

  @Override
  public Optional<Integer> findBearing(FeedScopedId stopOrStationId) {
    return Optional.ofNullable(bearings.get(stopOrStationId));
  }

  @Override
  public int size() {
    return bearings.size();
  }
}
