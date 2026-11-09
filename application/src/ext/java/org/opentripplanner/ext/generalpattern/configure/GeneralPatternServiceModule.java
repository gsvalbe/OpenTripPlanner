package org.opentripplanner.ext.generalpattern.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.ext.generalpattern.GeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.GeneralPatternService;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternService;
import org.opentripplanner.framework.application.OTPFeature;

@Module
public class GeneralPatternServiceModule {

  /**
   * A graph built with the feature on still holds general patterns when the server runs with it
   * off. Serve an empty repository then, so the API reflects the server's configuration.
   */
  @Provides
  @Singleton
  static GeneralPatternService provideGeneralPatternService(GeneralPatternRepository repository) {
    return new DefaultGeneralPatternService(
      OTPFeature.GeneralPatterns.isOn() ? repository : new DefaultGeneralPatternRepository()
    );
  }
}
