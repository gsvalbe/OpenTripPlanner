package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes.GraphQLGeneralPatternStopStatus;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternStop;

public class GeneralPatternStopImpl implements GraphQLDataFetchers.GraphQLGeneralPatternStop {

  @Override
  public DataFetcher<Boolean> lineTerminus() {
    return environment -> getSource(environment).lineTerminus();
  }

  @Override
  public DataFetcher<Iterable<Object>> mergedStops() {
    return environment -> List.copyOf(getSource(environment).mergedStops());
  }

  @Override
  public DataFetcher<Object> station() {
    return environment -> getSource(environment).station();
  }

  @Override
  public DataFetcher<GraphQLGeneralPatternStopStatus> status() {
    return environment ->
      switch (getSource(environment).status()) {
        case ALWAYS -> GraphQLGeneralPatternStopStatus.ALWAYS;
        case SOMETIMES_SKIPPED -> GraphQLGeneralPatternStopStatus.SOMETIMES_SKIPPED;
      };
  }

  @Override
  public DataFetcher<Object> stop() {
    return environment -> getSource(environment).stop();
  }

  @Override
  public DataFetcher<Boolean> tripsEndHere() {
    return environment -> getSource(environment).tripsEndHere();
  }

  @Override
  public DataFetcher<Boolean> tripsStartHere() {
    return environment -> getSource(environment).tripsStartHere();
  }

  private GeneralPatternStop getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
