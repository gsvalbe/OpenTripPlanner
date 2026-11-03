package org.opentripplanner.ext.generalpattern.configure;

import dagger.Binds;
import dagger.Module;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternRepository;

/**
 * The repository is created in the application loading phase, so that an empty one exists when
 * the graph is built and so that it can be serialized with the graph.
 */
@Module
public interface GeneralPatternRepositoryModule {
  @Binds
  GeneralPatternRepository bindRepository(DefaultGeneralPatternRepository repository);
}
