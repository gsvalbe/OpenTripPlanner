package org.opentripplanner.ext.stopbearing.configure;

import dagger.Binds;
import dagger.Module;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingRepository;

/**
 * The repository is created in the application loading phase, so that an empty one exists when
 * the graph is built and so that it can be serialized with the graph.
 */
@Module
public interface StopBearingRepositoryModule {
  @Binds
  StopBearingRepository bindRepository(DefaultStopBearingRepository repository);
}
