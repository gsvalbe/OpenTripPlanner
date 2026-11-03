package org.opentripplanner.ext.generalpattern.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.build.GeneralPatternGraphBuilder;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.transit.service.TimetableRepository;

@Module
public class GeneralPatternGraphBuilderModule {

  @Provides
  @Singleton
  static GeneralPatternGraphBuilder provideGeneralPatternGraphBuilder(
    BuildConfig config,
    TimetableRepository timetableRepository,
    GeneralPatternRepository repository
  ) {
    return new GeneralPatternGraphBuilder(config.generalPatterns, timetableRepository, repository);
  }
}
