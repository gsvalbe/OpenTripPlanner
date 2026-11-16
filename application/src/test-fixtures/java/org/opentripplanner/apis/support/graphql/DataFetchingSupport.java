package org.opentripplanner.apis.support.graphql;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionContextBuilder;
import graphql.execution.ExecutionId;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import java.util.Map;
import org.opentripplanner.apis.gtfs.GtfsGraphQLRequestContext;
import org.opentripplanner.ext.generalpattern.GeneralPatternService;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternService;
import org.opentripplanner.ext.stopbearing.StopBearingService;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.DefaultStopBearingService;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.transit.service.TransitService;

/**
 * Support class for building data fetching environments for testing data fetchers.
 */
public class DataFetchingSupport {

  public static DataFetchingEnvironment dataFetchingEnvironment(Object source) {
    return dataFetchingEnvironment(source, Map.of());
  }

  public static DataFetchingEnvironment dataFetchingEnvironment(
    Object source,
    Map<String, Object> arguments
  ) {
    return dataFetchingEnvironment(
      source,
      arguments,
      new DefaultTransitService(new TransitRepository())
    );
  }

  public static DataFetchingEnvironment dataFetchingEnvironment(
    Object source,
    Map<String, Object> arguments,
    TransitService service
  ) {
    return dataFetchingEnvironment(source, arguments, service, null);
  }

  public static DataFetchingEnvironment dataFetchingEnvironment(
    Object source,
    Map<String, Object> arguments,
    TransitService service,
    FareService fareService
  ) {
    return dataFetchingEnvironment(
      source,
      arguments,
      service,
      fareService,
      new DefaultGeneralPatternService(new DefaultGeneralPatternRepository())
    );
  }

  public static DataFetchingEnvironment dataFetchingEnvironment(
    Object source,
    Map<String, Object> arguments,
    TransitService service,
    FareService fareService,
    GeneralPatternService generalPatternService
  ) {
    return dataFetchingEnvironment(
      source,
      arguments,
      service,
      fareService,
      generalPatternService,
      new DefaultStopBearingService(new DefaultStopBearingRepository())
    );
  }

  public static DataFetchingEnvironment dataFetchingEnvironment(
    Object source,
    Map<String, Object> arguments,
    TransitService service,
    FareService fareService,
    GeneralPatternService generalPatternService,
    StopBearingService stopBearingService
  ) {
    final var executionContext = executionContext();
    return DataFetchingEnvironmentImpl.newDataFetchingEnvironment(executionContext)
      .source(source)
      .arguments(arguments)
      .context(
        new GtfsGraphQLRequestContext(
          null,
          service,
          null,
          null,
          fareService,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          generalPatternService,
          stopBearingService
        )
      )
      .build();
  }

  public static ExecutionContext executionContext() {
    return executionContextBuilder().build();
  }

  public static ExecutionContext executionContext(ExecutionInput executionInput) {
    return executionContextBuilder().executionInput(executionInput).build();
  }

  private static ExecutionContextBuilder executionContextBuilder() {
    return newExecutionContextBuilder()
      .executionId(ExecutionId.from("test"))
      .graphQLContext(GraphQLContext.getDefault());
  }
}
