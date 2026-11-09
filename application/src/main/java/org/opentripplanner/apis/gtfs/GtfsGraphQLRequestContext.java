package org.opentripplanner.apis.gtfs;

import graphql.schema.GraphQLSchema;
import javax.annotation.Nullable;
import org.opentripplanner.ext.generalpattern.GeneralPatternService;
import org.opentripplanner.ext.geocoder.LuceneIndex;
import org.opentripplanner.place.NearbyPlaceFinder;
import org.opentripplanner.place.NearbyStopFinder;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transit.service.TransitService;

public record GtfsGraphQLRequestContext(
  RoutingService routingService,
  TransitService transitService,
  TransitAlertService transitAlertService,
  RegularTransferService transferService,
  FareService fareService,
  VehicleRentalService vehicleRentalService,
  VehicleParkingService vehicleParkingService,
  RealtimeVehicleService realTimeVehicleService,
  GraphQLSchema schema,
  NearbyPlaceFinder nearbyPlaceFinder,
  NearbyStopFinder nearbyStopFinder,
  RouteRequest defaultRouteRequest,
  /**
   * The search index, or null when the geocoder feature is switched off and it was never built.
   */
  @Nullable LuceneIndex luceneIndex,
  /**
   * General patterns. Lookups return nothing when the general patterns feature is off.
   */
  GeneralPatternService generalPatternService
) {
  /**
   * Returns a clone of the default route request. The clone is necessary because one HTTP
   * request can lead to several GraphQL queries, for example through batch or alias queries.
   */
  @Override
  public RouteRequest defaultRouteRequest() {
    return defaultRouteRequest;
  }
}
