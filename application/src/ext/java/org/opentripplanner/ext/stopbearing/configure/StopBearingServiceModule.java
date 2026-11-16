package org.opentripplanner.ext.stopbearing.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;
import org.opentripplanner.ext.stopbearing.StopBearingService;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingService;
import org.opentripplanner.framework.application.OTPFeature;

@Module
public class StopBearingServiceModule {

  /**
   * A graph built with the feature on still holds bearings when the server runs with it off.
   * Serve an empty repository then, so the API reflects the server's configuration.
   */
  @Provides
  @Singleton
  static StopBearingService provideStopBearingService(StopBearingRepository repository) {
    return new DefaultStopBearingService(
      OTPFeature.StopBearing.isOn() ? repository : new DefaultStopBearingRepository()
    );
  }
}
