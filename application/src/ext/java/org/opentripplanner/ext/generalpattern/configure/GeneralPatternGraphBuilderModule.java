package org.opentripplanner.ext.generalpattern.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.build.GeneralPatternGraphBuilder;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.transit.service.TransitRepository;

@Module
public class GeneralPatternGraphBuilderModule {

  @Provides
  @Singleton
  static GeneralPatternGraphBuilder provideGeneralPatternGraphBuilder(
    BuildConfig config,
    TransitRepository transitRepository,
    GeneralPatternRepository repository
  ) {
    return new GeneralPatternGraphBuilder(config.generalPatterns, transitRepository, repository);
  }
}
