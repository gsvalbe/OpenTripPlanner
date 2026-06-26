package org.opentripplanner.apis.gtfs.datafetchers;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.apis.support.graphql.DataFetchingSupport.dataFetchingEnvironment;
import static org.opentripplanner.ext.fares.model.FareModelForTest.AIRPORT_TO_CITY_CENTER_SET;
import static org.opentripplanner.ext.fares.model.FareModelForTest.INSIDE_CITY_CENTER_SET;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.gtfs.model.TripFare;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.ext.fares.service.gtfs.v1.DefaultFareService;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.model.fare.FareProduct;
import org.opentripplanner.routing.core.FareType;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.Money;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.FareZone;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.SiteRepository;
import org.opentripplanner.transit.service.SiteRepositoryBuilder;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * Verifies the {@code Trip.faresFrom} and {@code Trip.fareBetween} data fetchers against a GTFS
 * Fares V1 ({@link DefaultFareService}) configuration.
 */
class TripImplFareTest {

  private static final TimetableRepositoryForTest MODEL = TimetableRepositoryForTest.of();
  private static final Money TEN_DOLLARS = Money.usDollars(10);

  // Zone ids match those referenced by the fare rule sets in FareModelForTest.
  private static final SiteRepositoryBuilder SITE_BUILDER = SiteRepository.of();
  private static final FareZone AIRPORT_ZONE = FareZone.of(id("airport-zone")).build();
  private static final FareZone CITY_CENTER_ZONE = FareZone.of(id("city-center")).build();

  private static final RegularStop AIRPORT = SITE_BUILDER.regularStop(id("airport"))
    .withCoordinate(new WgsCoordinate(1, 1))
    .addFareZones(AIRPORT_ZONE)
    .withName(I18NString.of("Airport"))
    .build();
  private static final RegularStop CITY_CENTER_A = SITE_BUILDER.regularStop(id("city-center-a"))
    .withCoordinate(new WgsCoordinate(1, 2))
    .addFareZones(CITY_CENTER_ZONE)
    .withName(I18NString.of("City center: stop A"))
    .build();
  private static final RegularStop CITY_CENTER_B = SITE_BUILDER.regularStop(id("city-center-b"))
    .withCoordinate(new WgsCoordinate(1, 3))
    .addFareZones(CITY_CENTER_ZONE)
    .withName(I18NString.of("City center: stop B"))
    .build();

  // The trip runs airport -> city center A -> city center B.
  private static final List<RegularStop> STOPS = List.of(AIRPORT, CITY_CENTER_A, CITY_CENTER_B);

  private static final Route ROUTE = TimetableRepositoryForTest.route("route-1").build();
  private static final Trip TRIP = TimetableRepositoryForTest.trip("trip-1")
    .withRoute(ROUTE)
    .build();

  private static final TransitService TRANSIT_SERVICE = buildTransitService();
  private static final FareService FARE_SERVICE = buildFareService();

  private static final TripImpl IMPL = new TripImpl();

  @Test
  void faresFromReturnsFareToEveryLaterStop() throws Exception {
    var fares = faresFrom(0);

    // Boarding at the airport (position 0) yields fares to city center A and city center B.
    assertThat(fares).hasSize(2);
    assertThat(stopName(fares.get(0).toStop())).isEqualTo("City center: stop A");
    assertThat(stopName(fares.get(1).toStop())).isEqualTo("City center: stop B");
    fares.forEach(fare -> {
      assertThat(stopName(fare.fromStop())).isEqualTo("Airport");
      assertThat(prices(fare)).containsExactly(TEN_DOLLARS);
    });
  }

  @Test
  void fareBetweenReturnsSingleFare() throws Exception {
    var fare = fareBetween(1, 2);

    assertThat(fare).isNotNull();
    assertThat(stopName(fare.fromStop())).isEqualTo("City center: stop A");
    assertThat(stopName(fare.toStop())).isEqualTo("City center: stop B");
    assertThat(prices(fare)).containsExactly(TEN_DOLLARS);
  }

  @Test
  void invalidPositionsReturnNothing() throws Exception {
    // toStopPosition not after fromStopPosition.
    assertThat(fareBetween(2, 1)).isNull();
    // fromStopPosition is the last stop, so there are no later stops.
    assertThat(faresFrom(2)).isEmpty();
  }

  private List<TripFare> faresFrom(int fromStopPosition) throws Exception {
    var env = dataFetchingEnvironment(
      TRIP,
      Map.of("fromStopPosition", fromStopPosition),
      TRANSIT_SERVICE,
      FARE_SERVICE
    );
    var result = IMPL.faresFrom().get(env);
    return StreamSupport.stream(result.spliterator(), false).toList();
  }

  private TripFare fareBetween(int fromStopPosition, int toStopPosition) throws Exception {
    var env = dataFetchingEnvironment(
      TRIP,
      Map.of("fromStopPosition", fromStopPosition, "toStopPosition", toStopPosition),
      TRANSIT_SERVICE,
      FARE_SERVICE
    );
    return IMPL.fareBetween().get(env);
  }

  private static List<Money> prices(TripFare fare) {
    return fare
      .fareProducts()
      .stream()
      .map(FareOffer::fareProduct)
      .map(FareProduct::price)
      .toList();
  }

  private static String stopName(Object stop) {
    return ((StopLocation) stop).getName().toString();
  }

  private static TransitService buildTransitService() {
    var stopTimes = List.of(
      stopTime(10, AIRPORT, "11:00"),
      stopTime(20, CITY_CENTER_A, "11:05"),
      stopTime(30, CITY_CENTER_B, "11:10")
    );
    var tripTimes = TripTimesFactory.tripTimes(TRIP, stopTimes, new Deduplicator());
    TripPattern pattern = TimetableRepositoryForTest.tripPattern("pattern-1", ROUTE)
      .withStopPattern(TimetableRepositoryForTest.stopPattern(STOPS.toArray(StopLocation[]::new)))
      .withScheduledTimeTableBuilder(builder -> builder.addTripTimes(tripTimes))
      .build();

    STOPS.forEach(SITE_BUILDER::withRegularStop);
    var timetableRepository = new TimetableRepository(SITE_BUILDER.build());
    timetableRepository.addTripPattern(id("pattern-1"), pattern);
    timetableRepository.addAgency(ROUTE.getAgency());
    timetableRepository.index();
    return new DefaultTransitService(timetableRepository);
  }

  private static FareService buildFareService() {
    var service = new DefaultFareService();
    service.addFareRules(
      FareType.regular,
      List.of(AIRPORT_TO_CITY_CENTER_SET, INSIDE_CITY_CENTER_SET)
    );
    return service;
  }

  private static StopTime stopTime(int seq, StopLocation stop, String time) {
    var stopTime = MODEL.stopTime(TRIP, seq, stop);
    int seconds = TimeUtils.time(time);
    stopTime.setArrivalTime(seconds);
    stopTime.setDepartureTime(seconds);
    return stopTime;
  }
}
