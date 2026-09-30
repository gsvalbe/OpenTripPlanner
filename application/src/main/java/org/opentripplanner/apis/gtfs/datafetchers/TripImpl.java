package org.opentripplanner.apis.gtfs.datafetchers;

import graphql.relay.Relay;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.apis.gtfs.GraphQLRequestContext;
import org.opentripplanner.apis.gtfs.GraphQLUtils;
import org.opentripplanner.apis.gtfs.generated.GraphQLDataFetchers;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes.GraphQLBikesAllowed;
import org.opentripplanner.apis.gtfs.generated.GraphQLTypes.GraphQLCarsAllowed;
import org.opentripplanner.apis.gtfs.mapping.BikesAllowedMapper;
import org.opentripplanner.apis.gtfs.mapping.CarsAllowedMapper;
import org.opentripplanner.apis.gtfs.model.TripFare;
import org.opentripplanner.apis.gtfs.model.TripOccupancy;
import org.opentripplanner.apis.support.SemanticHash;
import org.opentripplanner.core.model.basic.Cost;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.fares.ItineraryFaresDecorator;
import org.opentripplanner.model.TripTimeOnDate;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.model.fare.ItineraryFare;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.leg.ScheduledTransitLegBuilder;
import org.opentripplanner.routing.alertpatch.EntitySelector;
import org.opentripplanner.routing.alertpatch.TransitAlert;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.routing.fares.StopFareIdentity;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.transfer.constrained.model.ConstrainedTransfer;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.organization.Agency;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.transit.model.timetable.Timetable;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

public class TripImpl implements GraphQLDataFetchers.GraphQLTrip {

  @Override
  public DataFetcher<Iterable<String>> activeDates() {
    return environment ->
      getTransitService(environment)
        .getCalendarService()
        .getServiceDatesForServiceId(getSource(environment).getServiceId())
        .stream()
        .map(ServiceDateUtils::asCompactString)
        .collect(Collectors.toList());
  }

  @Override
  public DataFetcher<Iterable<TransitAlert>> alerts() {
    return environment -> {
      TransitAlertService alertService = getTransitService(environment).getTransitAlertService();
      var args = new GraphQLTypes.GraphQLTripAlertsArgs(environment.getArguments());
      List<GraphQLTypes.GraphQLTripAlertType> types = args.getGraphQLTypes();
      if (types != null) {
        Collection<TransitAlert> alerts = new ArrayList<>();
        types.forEach(type -> {
          switch (type) {
            case TRIP:
              alerts.addAll(alertService.getTripAlerts(getSource(environment).getId()));
              break;
            case AGENCY:
              alerts.addAll(alertService.getAgencyAlerts(getAgency(environment).getId()));
              break;
            case ROUTE_TYPE:
              int routeType = getRoute(environment).getGtfsType();
              alerts.addAll(
                alertService.getRouteTypeAlerts(
                  routeType,
                  getSource(environment).getId().getFeedId()
                )
              );
              alerts.addAll(
                alertService.getRouteTypeAndAgencyAlerts(routeType, getAgency(environment).getId())
              );
              break;
            case ROUTE:
              alerts.addAll(alertService.getRouteAlerts(getRoute(environment).getId()));
              break;
            case PATTERN:
              alerts.addAll(
                alertService.getDirectionAndRouteAlerts(
                  getSource(environment).getDirection(),
                  getRoute(environment).getId()
                )
              );
              break;
            case STOPS_ON_TRIP:
              alerts.addAll(
                alertService
                  .getAllAlerts()
                  .stream()
                  .filter(alert ->
                    alert
                      .entities()
                      .stream()
                      .anyMatch(
                        entity ->
                          (entity instanceof EntitySelector.StopAndRoute stopAndRoute &&
                            stopAndRoute.routeId().equals(getRoute(environment).getId())) ||
                          (entity instanceof EntitySelector.StopAndTrip stopAndTrip &&
                            stopAndTrip.tripId().equals(getSource(environment).getId()))
                      )
                  )
                  .toList()
              );
              getStops(environment).forEach(stop -> {
                FeedScopedId stopId = ((StopLocation) stop).getId();
                alerts.addAll(alertService.getStopAlerts(stopId));
              });
              break;
          }
        });
        return alerts.stream().distinct().collect(Collectors.toList());
      } else {
        return alertService.getTripAlerts(getSource(environment).getId());
      }
    };
  }

  @Override
  public DataFetcher<TripTimeOnDate> arrivalStoptime() {
    return environment -> {
      try {
        TransitService transitService = getTransitService(environment);
        Trip trip = getSource(environment);
        var args = new GraphQLTypes.GraphQLTripArrivalStoptimeArgs(environment.getArguments());

        ZoneId timeZone = transitService.getTimeZone();
        LocalDate serviceDate = args.getGraphQLServiceDate() != null
          ? ServiceDateUtils.parseString(args.getGraphQLServiceDate())
          : LocalDate.now();

        TripPattern tripPattern = transitService.findPattern(trip, serviceDate);
        if (tripPattern == null) {
          return null;
        }

        Instant midnight = ServiceDateUtils.asStartOfService(serviceDate, timeZone).toInstant();
        Timetable timetable = transitService.findTimetable(tripPattern, serviceDate);

        TripTimes tripTimes = timetable.getTripTimes(trip);
        if (tripTimes == null) {
          return null;
        }

        return new TripTimeOnDate(
          tripTimes,
          tripTimes.getNumStops() - 1,
          tripPattern,
          serviceDate,
          midnight
        );
      } catch (ParseException e) {
        // invalid date format
        return null;
      }
    };
  }

  @Override
  public DataFetcher<GraphQLBikesAllowed> bikesAllowed() {
    return environment -> BikesAllowedMapper.map(getSource(environment).getBikesAllowed());
  }

  @Override
  public DataFetcher<GraphQLCarsAllowed> carsAllowed() {
    return environment -> CarsAllowedMapper.map(getSource(environment).getCarsAllowed());
  }

  @Override
  public DataFetcher<String> blockId() {
    return environment -> getSource(environment).getGtfsBlockId();
  }

  @Override
  public DataFetcher<TripTimeOnDate> departureStoptime() {
    return environment -> {
      try {
        TransitService transitService = getTransitService(environment);
        Trip trip = getSource(environment);
        var args = new GraphQLTypes.GraphQLTripDepartureStoptimeArgs(environment.getArguments());

        ZoneId timeZone = transitService.getTimeZone();
        LocalDate serviceDate = args.getGraphQLServiceDate() != null
          ? ServiceDateUtils.parseString(args.getGraphQLServiceDate())
          : LocalDate.now();

        TripPattern tripPattern = transitService.findPattern(trip, serviceDate);
        if (tripPattern == null) {
          return null;
        }

        Instant midnight = ServiceDateUtils.asStartOfService(serviceDate, timeZone).toInstant();
        Timetable timetable = transitService.findTimetable(tripPattern, serviceDate);

        TripTimes tripTimes = timetable.getTripTimes(trip);
        if (tripTimes == null) {
          return null;
        }

        return new TripTimeOnDate(tripTimes, 0, tripPattern, serviceDate, midnight);
      } catch (ParseException e) {
        // invalid date format
        return null;
      }
    };
  }

  @Override
  public DataFetcher<String> directionId() {
    return environment -> {
      Direction direction = getSource(environment).getDirection();
      if (direction == Direction.UNKNOWN) {
        return null;
      }
      return Integer.toString(direction.gtfsCode);
    };
  }

  @Override
  public DataFetcher<TripFare> fareBetween() {
    return environment -> {
      try {
        var args = new GraphQLTypes.GraphQLTripFareBetweenArgs(environment.getArguments());
        TripFareContext context = resolveFareContext(environment, args.getGraphQLServiceDate());
        if (context == null) {
          return null;
        }
        int fromPos = args.getGraphQLFromStopPosition();
        int toPos = args.getGraphQLToStopPosition();
        int numStops = context.tripTimes().getNumStops();
        if (fromPos < 0 || toPos <= fromPos || toPos >= numStops) {
          return null;
        }
        return computeTripFare(environment, context, fromPos, toPos);
      } catch (ParseException e) {
        // invalid date format
        return null;
      }
    };
  }

  @Override
  public DataFetcher<Iterable<TripFare>> faresFrom() {
    return environment -> {
      try {
        var args = new GraphQLTypes.GraphQLTripFaresFromArgs(environment.getArguments());
        TripFareContext context = resolveFareContext(environment, args.getGraphQLServiceDate());
        if (context == null) {
          return List.of();
        }
        int fromPos = args.getGraphQLFromStopPosition();
        int numStops = context.tripTimes().getNumStops();
        if (fromPos < 0 || fromPos >= numStops - 1) {
          return List.of();
        }
        return computeTripFares(environment, context, fromPos);
      } catch (ParseException e) {
        // invalid date format
        return List.of();
      }
    };
  }

  @Override
  public DataFetcher<Iterable<Iterable<Double>>> geometry() {
    return environment -> {
      TripPattern tripPattern = getTripPattern(environment);
      if (tripPattern == null) {
        return null;
      }

      LineString geometry = tripPattern.getGeometry();
      if (geometry == null) {
        return null;
      }
      return Arrays.stream(geometry.getCoordinateSequence().toCoordinateArray())
        .map(coordinate -> Arrays.asList(coordinate.x, coordinate.y))
        .collect(Collectors.toList());
    };
  }

  @Override
  public DataFetcher<String> gtfsId() {
    return environment -> getSource(environment).getId().toString();
  }

  @Override
  public DataFetcher<Relay.ResolvedGlobalId> id() {
    return environment ->
      new Relay.ResolvedGlobalId("Trip", getSource(environment).getId().toString());
  }

  @Override
  public DataFetcher<Boolean> isReplacement() {
    return environment ->
      getTransitService(environment)
        .getReplacementHelper()
        .isReplacementTrip(getSource(environment));
  }

  @Override
  public DataFetcher<TripPattern> pattern() {
    return this::getTripPattern;
  }

  @Override
  public DataFetcher<Boolean> replacementsExist() {
    return environment ->
      getTransitService(environment)
        .getReplacementHelper()
        .replacementsExist(getSource(environment));
  }

  @Override
  public DataFetcher<Route> route() {
    return environment -> getSource(environment).getRoute();
  }

  @Override
  public DataFetcher<String> routeShortName() {
    return environment -> {
      Trip trip = getSource(environment);
      return trip.getRoute().getShortName();
    };
  }

  @Override
  public DataFetcher<String> semanticHash() {
    return environment -> {
      TripPattern tripPattern = getTripPattern(environment);
      if (tripPattern == null) {
        return null;
      }
      return SemanticHash.forTripPattern(tripPattern, getSource(environment));
    };
  }

  @Override
  public DataFetcher<String> serviceId() {
    return environment -> getSource(environment).getServiceId().toString();
  }

  @Override
  public DataFetcher<String> shapeId() {
    return environment ->
      Optional.ofNullable(getSource(environment).getShapeId())
        .map(FeedScopedId::toString)
        .orElse(null);
  }

  @Override
  public DataFetcher<Iterable<Object>> stops() {
    return this::getStops;
  }

  @Override
  public DataFetcher<Iterable<TripTimeOnDate>> stoptimes() {
    return environment ->
      getTransitService(environment).getScheduledTripTimes(getSource(environment)).orElse(null);
  }

  @Override
  public DataFetcher<Iterable<TripTimeOnDate>> stoptimesForDate() {
    return environment -> {
      try {
        TransitService transitService = getTransitService(environment);
        Trip trip = getSource(environment);
        var args = new GraphQLTypes.GraphQLTripStoptimesForDateArgs(environment.getArguments());

        ZoneId timeZone = transitService.getTimeZone();
        LocalDate serviceDate = args.getGraphQLServiceDate() != null
          ? ServiceDateUtils.parseString(args.getGraphQLServiceDate())
          : LocalDate.now(timeZone);

        TripPattern tripPattern = transitService.findPattern(trip, serviceDate);
        if (tripPattern == null) {
          return List.of();
        }

        Instant midnight = ServiceDateUtils.asStartOfService(serviceDate, timeZone).toInstant();
        Timetable timetable = transitService.findTimetable(tripPattern, serviceDate);
        return TripTimeOnDate.fromTripTimesWithScheduleFallback(
          timetable,
          trip,
          serviceDate,
          midnight
        );
      } catch (ParseException e) {
        // invalid date format
        return null;
      }
    };
  }

  @Override
  public DataFetcher<Geometry> tripGeometry() {
    return environment -> {
      TripPattern tripPattern = getTripPattern(environment);
      if (tripPattern == null) {
        return null;
      }
      return tripPattern.getGeometry();
    };
  }

  @Override
  public DataFetcher<String> tripHeadsign() {
    return environment ->
      org.opentripplanner.framework.graphql.GraphQLUtils.getTranslation(
        getSource(environment).getHeadsign(),
        environment
      );
  }

  @Override
  public DataFetcher<String> tripShortName() {
    return environment -> getSource(environment).getShortName();
  }

  @Override
  public DataFetcher<GraphQLTypes.GraphQLWheelchairBoarding> wheelchairAccessible() {
    return environment -> GraphQLUtils.toGraphQL(getSource(environment).getWheelchairBoarding());
  }

  @Override
  public DataFetcher<Iterable<ConstrainedTransfer>> outgoingTransfers() {
    return environment ->
      getTransitService(environment).findTransfersByFromTrip(getSource(environment));
  }

  @Override
  public DataFetcher<Iterable<ConstrainedTransfer>> incomingTransfers() {
    return environment ->
      getTransitService(environment).findTransfersByToTrip(getSource(environment));
  }

  @Override
  public DataFetcher<TripOccupancy> occupancy() {
    return environment -> {
      Trip trip = getSource(environment);
      return new TripOccupancy(
        getRealtimeVehiclesService(environment).getVehicleOccupancyStatus(trip)
      );
    };
  }

  /**
   * Resolves the pattern, times and service date needed to compute fares for this trip, or
   * {@code null} if the trip does not run on the requested date.
   */
  @Nullable
  private TripFareContext resolveFareContext(
    DataFetchingEnvironment environment,
    @Nullable String serviceDateArg
  ) throws ParseException {
    TransitService transitService = getTransitService(environment);
    Trip trip = getSource(environment);
    ZoneId timeZone = transitService.getTimeZone();
    LocalDate serviceDate = serviceDateArg != null
      ? ServiceDateUtils.parseString(serviceDateArg)
      : LocalDate.now(timeZone);

    TripPattern tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern == null) {
      return null;
    }
    Timetable timetable = transitService.findTimetable(tripPattern, serviceDate);
    TripTimes tripTimes = timetable.getTripTimes(trip);
    if (tripTimes == null) {
      return null;
    }
    return new TripFareContext(tripPattern, tripTimes, serviceDate, timeZone);
  }

  /**
   * Computes the fares from a stop position to every later stop of this trip.
   * <p>
   * Fares are usually determined by the fare zones of the stops rather than by the stops
   * themselves, and a trip commonly stays within a single zone. Therefore the fare is computed only
   * once per distinct combination of alighting stop identity and set of identities visited on the
   * way, and shared by all stop pairs that have the same combination. If the fare service cannot
   * express its fares in terms of stops alone the fares are computed for each stop pair
   * individually.
   */
  private List<TripFare> computeTripFares(
    DataFetchingEnvironment environment,
    TripFareContext context,
    int fromPos
  ) {
    var stops = context.tripPattern().getStops();
    int numStops = context.tripTimes().getNumStops();
    FareService fareService = getFareService(environment);
    StopFareIdentity[] identities = stopFareIdentities(fareService, stops, fromPos, numStops);

    Map<FareCacheKey, List<FareOffer>> cache = identities == null ? null : new HashMap<>();
    Set<StopFareIdentity> visited = identities == null ? null : new HashSet<>();
    int visitedVersion = 0;
    if (identities != null) {
      visited.add(identities[fromPos]);
    }

    List<TripFare> fares = new ArrayList<>(numStops - fromPos - 1);
    for (int toPos = fromPos + 1; toPos < numStops; toPos++) {
      final int alightPos = toPos;
      List<FareOffer> offers;
      if (identities == null) {
        offers = computeFareOffers(fareService, context, fromPos, alightPos);
      } else {
        if (visited.add(identities[alightPos])) {
          visitedVersion++;
        }
        offers = cache.computeIfAbsent(new FareCacheKey(identities[alightPos], visitedVersion), _ ->
          computeFareOffers(fareService, context, fromPos, alightPos)
        );
      }
      fares.add(new TripFare(stops.get(fromPos), stops.get(alightPos), offers));
    }
    return fares;
  }

  /**
   * Computes the fare for travelling on this trip between two stop positions.
   */
  private TripFare computeTripFare(
    DataFetchingEnvironment environment,
    TripFareContext context,
    int fromPos,
    int toPos
  ) {
    var stops = context.tripPattern().getStops();
    var offers = computeFareOffers(getFareService(environment), context, fromPos, toPos);
    return new TripFare(stops.get(fromPos), stops.get(toPos), offers);
  }

  /**
   * Computes the fare offers for travelling on this trip between two stop positions by building a
   * synthetic single-leg itinerary and running it through the configured {@link FareService}.
   */
  private List<FareOffer> computeFareOffers(
    @Nullable FareService fareService,
    TripFareContext context,
    int fromPos,
    int toPos
  ) {
    if (fareService == null) {
      return List.of();
    }
    var startOfService = ServiceDateUtils.asStartOfService(context.serviceDate(), context.zoneId());
    ZonedDateTime startTime = startOfService.plusSeconds(
      context.tripTimes().getDepartureTime(fromPos)
    );
    ZonedDateTime endTime = startOfService.plusSeconds(context.tripTimes().getArrivalTime(toPos));

    var leg = new ScheduledTransitLegBuilder<>()
      .withTripTimes(context.tripTimes())
      .withTripPattern(context.tripPattern())
      .withBoardStopIndexInPattern(fromPos)
      .withAlightStopIndexInPattern(toPos)
      .withStartTime(startTime)
      .withEndTime(endTime)
      .withServiceDate(context.serviceDate())
      .withZoneId(context.zoneId())
      .build();

    var itinerary = Itinerary.ofScheduledTransit(List.of(leg))
      .withGeneralizedCost(Cost.ZERO)
      .build();

    ItineraryFare fare = fareService.calculateFares(itinerary);
    if (fare == null) {
      return List.of();
    }
    var decorated = ItineraryFaresDecorator.decorateItineraryWithFare(itinerary, fare);
    return decorated.listTransitLegs().getFirst().fareOffers();
  }

  /**
   * The fare identities of the stops from {@code fromPos} onwards, or {@code null} if the fare
   * service does not compute its fares from the stops of a leg alone.
   */
  @Nullable
  private static StopFareIdentity[] stopFareIdentities(
    @Nullable FareService fareService,
    List<StopLocation> stops,
    int fromPos,
    int numStops
  ) {
    if (fareService == null) {
      return null;
    }
    var identities = new StopFareIdentity[numStops];
    for (int pos = fromPos; pos < numStops; pos++) {
      var identity = fareService.fareIdentity(stops.get(pos));
      if (identity.isEmpty()) {
        return null;
      }
      identities[pos] = identity.get();
    }
    return identities;
  }

  private record TripFareContext(
    TripPattern tripPattern,
    TripTimes tripTimes,
    LocalDate serviceDate,
    ZoneId zoneId
  ) {}

  /**
   * Identifies the fare of a leg which starts at a fixed stop: the identity of the alighting stop
   * plus the number of times the set of identities visited so far has grown. Since that set only
   * ever grows, the counter identifies its contents.
   */
  private record FareCacheKey(StopFareIdentity alightStop, int visitedVersion) {}

  private List<Object> getStops(DataFetchingEnvironment environment) {
    TripPattern tripPattern = getTripPattern(environment);
    if (tripPattern == null) {
      return List.of();
    }
    return List.copyOf(tripPattern.getStops());
  }

  private Agency getAgency(DataFetchingEnvironment environment) {
    return getRoute(environment).getAgency();
  }

  private Route getRoute(DataFetchingEnvironment environment) {
    return getSource(environment).getRoute();
  }

  private TripPattern getTripPattern(DataFetchingEnvironment environment) {
    return getTransitService(environment).findPattern(environment.getSource());
  }

  private TripPattern getTripPattern(
    DataFetchingEnvironment environment,
    @Nullable LocalDate date
  ) {
    return date == null
      ? getTripPattern(environment)
      : getTransitService(environment).findPattern(environment.getSource(), date);
  }

  private TransitService getTransitService(DataFetchingEnvironment environment) {
    return environment.<GraphQLRequestContext>getContext().transitService();
  }

  private FareService getFareService(DataFetchingEnvironment environment) {
    return environment.<GraphQLRequestContext>getContext().fareService();
  }

  private RealtimeVehicleService getRealtimeVehiclesService(DataFetchingEnvironment environment) {
    return environment.<GraphQLRequestContext>getContext().realTimeVehicleService();
  }

  private static Optional<LocalDate> getOptionalServiceDateArgument(
    DataFetchingEnvironment environment
  ) throws ParseException {
    var args = new GraphQLTypes.GraphQLTripArrivalStoptimeArgs(environment.getArguments());
    if (args.getGraphQLServiceDate() != null) {
      return Optional.of(ServiceDateUtils.parseString(args.getGraphQLServiceDate()));
    }
    return Optional.empty();
  }

  private Trip getSource(DataFetchingEnvironment environment) {
    return environment.getSource();
  }
}
