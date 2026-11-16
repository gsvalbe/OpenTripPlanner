package org.opentripplanner.ext.stopbearing.configure;

import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.graphbuilder.StopBearingGraphBuilder;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.standalone.config.BuildConfig;
import org.opentripplanner.transit.service.TransitRepository;

@Module
public class StopBearingGraphBuilderModule {

  @Provides
  @Singleton
  static StopBearingGraphBuilder provideStopBearingGraphBuilder(
    BuildConfig config,
    TransitRepository transitRepository,
    StopBearingRepository repository,
    DataImportIssueStore issueStore
  ) {
    return new StopBearingGraphBuilder(
      config.stopBearings,
      transitRepository,
      repository,
      issueStore
    );
  }
}
