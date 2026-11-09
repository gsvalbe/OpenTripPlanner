package org.opentripplanner.apis.gtfs;

import static com.google.common.truth.Truth.assertThat;

import graphql.ExecutionResult;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternRepository;
import org.opentripplanner.ext.generalpattern.internal.DefaultGeneralPatternService;
import org.opentripplanner.ext.generalpattern.model.GeneralPattern;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternPlace;
import org.opentripplanner.ext.generalpattern.model.GeneralPatternVariant;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.transit.model._data.TransitRepositoryForTest;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.Station;

/**
 * Runs a GraphQL query against the GTFS schema for one general pattern: an all-stops variant
 * A - B - C and an express variant A - C that passes B without stopping.
 */
class GeneralPatternGraphQLTest {

  private static final TransitRepositoryForTest MODEL = TransitRepositoryForTest.of();
  private static final Route ROUTE = TransitRepositoryForTest.route("R1").build();
  private static final Station STATION_B = MODEL.station("B").build();
  private static final RegularStop A = MODEL.stop("A").build();
  private static final RegularStop B1 = MODEL.stop("B1").withParentStation(STATION_B).build();
  private static final RegularStop B2 = MODEL.stop("B2").withParentStation(STATION_B).build();
  private static final RegularStop C = MODEL.stop("C").build();
  private static final TripPattern ALL_STOPS = TransitRepositoryForTest.tripPattern("P1", ROUTE)
    .withStopPattern(TransitRepositoryForTest.stopPattern(A, B1, C))
    .build();
  private static final TripPattern EXPRESS = TransitRepositoryForTest.tripPattern("P2", ROUTE)
    .withStopPattern(TransitRepositoryForTest.stopPattern(A, C))
    .build();

  private static final GeneralPattern GENERAL_PATTERN = GeneralPattern.of(
    new FeedScopedId("F", "R1~A~C"),
    ROUTE,
    List.of(
      new GeneralPatternPlace(A, null, List.of(A)),
      new GeneralPatternPlace(B1, STATION_B, List.of(B1, B2)),
      new GeneralPatternPlace(C, null, List.of(C))
    ),
    List.of(
      new GeneralPatternVariant(List.of(ALL_STOPS), bits(0, 1, 2), 3),
      new GeneralPatternVariant(List.of(EXPRESS), bits(0, 2), 1)
    )
  ).withName(List.of(0, 1, 2), 0);

  @Test
  void queryGeneralPatternWithStopsAndBackReferences() {
    var result = execute(
      """
      {
        generalPattern(id: "F:R1~A~C") {
          gtfsId
          name
          route { gtfsId generalPatterns { gtfsId } }
          patterns { code generalPattern { gtfsId } }
          stops {
            stop { gtfsId }
            station { gtfsId }
            mergedStops { gtfsId }
            status
            lineTerminus
            tripsStartHere
            tripsEndHere
          }
        }
      }
      """
    );

    assertThat(result.getErrors()).isEmpty();
    Map<String, Object> gp = field(result.getData(), "generalPattern");
    assertThat(gp.get("gtfsId")).isEqualTo("F:R1~A~C");
    assertThat(gp.get("name")).isEqualTo("A - B - C");
    Map<String, Object> route = field(gp, "route");
    assertThat(route.get("generalPatterns")).isEqualTo(List.of(Map.of("gtfsId", "F:R1~A~C")));

    List<Map<String, Object>> patterns = field(gp, "patterns");
    assertThat(patterns).hasSize(2);
    assertThat(patterns.getFirst().get("generalPattern")).isEqualTo(Map.of("gtfsId", "F:R1~A~C"));

    List<Map<String, Object>> stops = field(gp, "stops");
    assertThat(stops).hasSize(3);
    var first = stops.get(0);
    assertThat(first.get("status")).isEqualTo("ALWAYS");
    assertThat(first.get("lineTerminus")).isEqualTo(true);
    assertThat(first.get("tripsStartHere")).isEqualTo(true);
    assertThat(first.get("station")).isNull();

    var middle = stops.get(1);
    assertThat(middle.get("status")).isEqualTo("SOMETIMES_SKIPPED");
    assertThat(middle.get("lineTerminus")).isEqualTo(false);
    assertThat(middle.get("tripsEndHere")).isEqualTo(false);
    assertThat(middle.get("station")).isEqualTo(Map.of("gtfsId", STATION_B.getId().toString()));
    assertThat(middle.get("mergedStops")).isEqualTo(
      List.of(Map.of("gtfsId", B1.getId().toString()), Map.of("gtfsId", B2.getId().toString()))
    );

    assertThat(stops.get(2).get("tripsEndHere")).isEqualTo(true);
  }

  @Test
  void unknownIdReturnsNull() {
    var result = execute("{ generalPattern(id: \"F:missing\") { gtfsId } }");

    assertThat(result.getErrors()).isEmpty();
    Map<String, Object> data = result.getData();
    assertThat(data.get("generalPattern")).isNull();
  }

  private static ExecutionResult execute(String query) {
    var repository = new DefaultGeneralPatternRepository();
    repository.addAll(List.of(GENERAL_PATTERN));
    var routeRequest = RouteRequest.defaultValue();
    var context = new GtfsGraphQLRequestContext(
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      SchemaFactory.createSchemaWithDefaultInjection(routeRequest),
      null,
      null,
      routeRequest,
      null,
      new DefaultGeneralPatternService(repository)
    );
    return GtfsGraphQLIndex.getGraphQLExecutionResult(
      query,
      null,
      null,
      2000,
      2000,
      Locale.ENGLISH,
      context,
      List.of()
    );
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object map, String name) {
    return (T) ((Map<String, Object>) map).get(name);
  }

  private static BitSet bits(int... indexes) {
    var bits = new BitSet();
    for (int i : indexes) {
      bits.set(i);
    }
    return bits;
  }
}
