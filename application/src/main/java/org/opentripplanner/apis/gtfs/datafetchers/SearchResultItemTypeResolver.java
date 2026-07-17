package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.site.StopLocationsGroup;

public class SearchResultItemTypeResolver implements TypeResolver {

  @Override
  public GraphQLObjectType getType(TypeResolutionEnvironment environment) {
    Object o = environment.getObject();
    var schema = environment.getSchema();

    if (o instanceof StopLocation || o instanceof StopLocationsGroup) {
      return schema.getObjectType("Stop");
    }
    if (o instanceof Route) {
      return schema.getObjectType("Route");
    }
    if (o instanceof TripPattern) {
      return schema.getObjectType("Pattern");
    }

    return null;
  }
}
