package org.opentripplanner.ext.stopbearing.internal;

import java.util.Optional;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;
import org.opentripplanner.ext.stopbearing.StopBearingService;

public class DefaultStopBearingService implements StopBearingService {

  private final StopBearingRepository repository;

  public DefaultStopBearingService(StopBearingRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Integer> findBearing(FeedScopedId stopOrStationId) {
    return repository.findBearing(stopOrStationId);
  }
}
