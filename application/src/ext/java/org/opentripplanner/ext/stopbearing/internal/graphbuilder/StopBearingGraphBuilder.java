package org.opentripplanner.ext.stopbearing.internal.graphbuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopbearing.StopBearingRepository;
import org.opentripplanner.ext.stopbearing.internal.graphbuilder.BearingCalculator.Departure;
import org.opentripplanner.ext.stopbearing.parameters.StopBearingParameters;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.model.GraphBuilderModule;
import org.opentripplanner.transit.model.framework.AbstractTransitEntity;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.timetable.Timetable;
import org.opentripplanner.transit.service.TransitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes the bearing of every regular stop and station from the shapes of the scheduled trip
 * patterns, and stores them in the {@link StopBearingRepository}. Each pattern that leaves a stop
 * adds the direction of its next hop, weighted by its number of trips. The last stop of a pattern
 * adds nothing, so a stop that is only ever a terminal has no bearing. A station combines the
 * departures of all its child stops, since these are often the same physical stop as published
 * by several feeds. Must run after the trip patterns are final.
 */
public class StopBearingGraphBuilder implements GraphBuilderModule {

  private static final Logger LOG = LoggerFactory.getLogger(StopBearingGraphBuilder.class);

  private final StopBearingParameters parameters;
  private final TransitRepository transitRepository;
  private final StopBearingRepository repository;
  private final DataImportIssueStore issueStore;

  public StopBearingGraphBuilder(
    StopBearingParameters parameters,
    TransitRepository transitRepository,
    StopBearingRepository repository,
    DataImportIssueStore issueStore
  ) {
    this.parameters = parameters;
    this.transitRepository = transitRepository;
    this.repository = repository;
    this.issueStore = issueStore;
  }

  @Override
  public void buildGraph() {
    repository.putAll(compute(transitRepository.getAllTripPatterns()));
  }

  /** The bearings of the stops and stations served by the given patterns, keyed by id. */
  Map<FeedScopedId, Integer> compute(Collection<TripPattern> patterns) {
    var calculator = new BearingCalculator(parameters);
    Map<RegularStop, List<Departure>> byStop = new HashMap<>();
    Map<Station, List<Departure>> byStation = new HashMap<>();

    for (TripPattern pattern : patterns) {
      int trips = numberOfTrips(pattern.getScheduledTimetable());
      if (trips == 0) {
        continue;
      }
      for (int pos = 0; pos < pattern.numberOfStops() - 1; pos++) {
        if (!(pattern.getStop(pos) instanceof RegularStop stop)) {
          continue;
        }
        var bearing = calculator.departureBearing(pattern.getHopGeometry(pos));
        if (bearing.isEmpty()) {
          continue;
        }
        var departure = new Departure(bearing.getAsDouble(), trips);
        byStop.computeIfAbsent(stop, s -> new ArrayList<>()).add(departure);
        if (stop.getParentStation() != null) {
          byStation.computeIfAbsent(stop.getParentStation(), s -> new ArrayList<>()).add(departure);
        }
      }
    }

    Map<FeedScopedId, Integer> result = new LinkedHashMap<>();
    int stopsDiverging = combineAll(calculator, byStop, result);
    int stationsDiverging = combineAll(calculator, byStation, result);

    LOG.info(
      "Stop bearings: {} of {} departure stops and {} of {} departure stations have a bearing",
      byStop.size() - stopsDiverging,
      byStop.size(),
      byStation.size() - stationsDiverging,
      byStation.size()
    );
    return result;
  }

  /** Combine the departures of each place into the result. Returns the number that diverge. */
  private <T extends AbstractTransitEntity<?, ?>> int combineAll(
    BearingCalculator calculator,
    Map<T, List<Departure>> departures,
    Map<FeedScopedId, Integer> result
  ) {
    int diverging = 0;
    for (var entry : departures.entrySet()) {
      var place = entry.getKey();
      var bearing = calculator.combine(entry.getValue());
      if (bearing.isPresent()) {
        result.put(place.getId(), bearing.getAsInt());
      } else {
        diverging++;
        issueStore.add(new StopBearingDiverges(place.getId(), name(place), parameters.maxSpread()));
      }
    }
    return diverging;
  }

  private static String name(AbstractTransitEntity<?, ?> place) {
    return switch (place) {
      case RegularStop stop -> String.valueOf(stop.getName());
      case Station station -> String.valueOf(station.getName());
      default -> place.getId().toString();
    };
  }

  private static int numberOfTrips(Timetable timetable) {
    return timetable.getTripTimes().size() + timetable.getFrequencyEntries().size();
  }
}
