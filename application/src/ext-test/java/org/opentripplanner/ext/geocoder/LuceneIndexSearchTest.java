package org.opentripplanner.ext.geocoder;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationService;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;

/**
 * Tests the unified search over stops, routes and patterns. The model is deliberately Riga-shaped:
 * two routes numbered "5" of different modes in different cities, plus a stop whose name contains
 * the number 5, which is exactly the situation the ranking has to get right.
 */
class LuceneIndexSearchTest {

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();

  private static final WgsCoordinate RIGA = new WgsCoordinate(56.95, 24.1);
  private static final WgsCoordinate LIEPAJA = new WgsCoordinate(56.505, 21.01);

  private static final RegularStop DEPO_IELA = TEST_MODEL.stop("Depo iela 5")
    .withCoordinate(56.9501, 24.1001)
    .build();
  private static final RegularStop CENTRALTIRGUS = TEST_MODEL.stop("Centrāltirgus")
    .withCoordinate(56.945, 24.115)
    .build();
  private static final RegularStop TRAMVAJU_DEPO = TEST_MODEL.stop("Tramvaju depo")
    .withCoordinate(56.96, 24.12)
    .build();
  private static final RegularStop LIEPAJAS_OSTA = TEST_MODEL.stop("Liepājas osta")
    .withCoordinate(56.5051, 21.0101)
    .build();

  private static final Route TRAM_5 = TimetableRepositoryForTest.route("tram-5")
    .withShortName("5")
    .withLongName(I18NString.of("Mīlgrāvis - Ķengarags"))
    .withMode(TransitMode.TRAM)
    .build();
  private static final Route TRAM_11 = TimetableRepositoryForTest.route("tram-11")
    .withShortName("11")
    .withLongName(I18NString.of("Mežaparks - Ausekļa iela"))
    .withMode(TransitMode.TRAM)
    .build();
  private static final Route BUS_5 = TimetableRepositoryForTest.route("bus-5")
    .withShortName("5")
    .withLongName(I18NString.of("Liepājas osta - Karosta"))
    .withMode(TransitMode.BUS)
    .build();

  // two routes with the same number in different regions: only the region name tells them apart
  private static final Route BUS_31_RIGA = TimetableRepositoryForTest.route("bus-31-riga")
    .withShortName("31")
    .withLongName(I18NString.of("Abrenes iela - Ulbroka"))
    .withRegionName("Rīga")
    .withMode(TransitMode.BUS)
    .build();
  private static final Route BUS_31_LIEPAJA = TimetableRepositoryForTest.route("bus-31-liepaja")
    .withShortName("31")
    .withLongName(I18NString.of("Ezerkrasts - Karosta"))
    .withRegionName("Liepāja")
    .withMode(TransitMode.BUS)
    .build();

  private static final TripPattern TRAM_5_PATTERN = TimetableRepositoryForTest.tripPattern(
    "tram-5-pattern",
    TRAM_5
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(DEPO_IELA, CENTRALTIRGUS))
    .withName("5 to Ķengarags")
    .withTripPatternName("Caur centru")
    .build();
  private static final TripPattern TRAM_11_PATTERN = TimetableRepositoryForTest.tripPattern(
    "tram-11-pattern",
    TRAM_11
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(TRAMVAJU_DEPO, CENTRALTIRGUS))
    .withName("11 to Ausekļa iela")
    .build();
  private static final TripPattern BUS_5_PATTERN = TimetableRepositoryForTest.tripPattern(
    "bus-5-pattern",
    BUS_5
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(LIEPAJAS_OSTA))
    .withName("5 to Karosta")
    .build();
  private static final TripPattern BUS_31_RIGA_PATTERN = TimetableRepositoryForTest.tripPattern(
    "bus-31-riga-pattern",
    BUS_31_RIGA
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(CENTRALTIRGUS))
    .withName("31 to Ulbroka")
    .build();
  private static final TripPattern BUS_31_LIEPAJA_PATTERN = TimetableRepositoryForTest.tripPattern(
    "bus-31-liepaja-pattern",
    BUS_31_LIEPAJA
  )
    .withStopPattern(TimetableRepositoryForTest.stopPattern(LIEPAJAS_OSTA))
    .withName("31 to Karosta")
    .build();

  private static LuceneIndex index;

  @BeforeAll
  static void setup() {
    var siteRepository = TEST_MODEL.siteRepositoryBuilder();
    List.of(DEPO_IELA, CENTRALTIRGUS, TRAMVAJU_DEPO, LIEPAJAS_OSTA).forEach(
      siteRepository::withRegularStop
    );
    var timetableRepository = new TimetableRepository(siteRepository.build());
    timetableRepository.index();

    var patterns = List.of(
      TRAM_5_PATTERN,
      TRAM_11_PATTERN,
      BUS_5_PATTERN,
      BUS_31_RIGA_PATTERN,
      BUS_31_LIEPAJA_PATTERN
    );
    var transitService = new DefaultTransitService(timetableRepository) {
      @Override
      public Collection<Route> listRoutes() {
        return List.of(TRAM_5, TRAM_11, BUS_5, BUS_31_RIGA, BUS_31_LIEPAJA);
      }

      @Override
      public Collection<TripPattern> findPatterns(Route route) {
        return patterns
          .stream()
          .filter(p -> p.getRoute().equals(route))
          .toList();
      }
    };
    var stopConsolidationService = new DefaultStopConsolidationService(
      new DefaultStopConsolidationRepository(),
      timetableRepository
    );
    index = new LuceneIndex(transitService, stopConsolidationService);
  }

  @Test
  void routeNumberOutranksStopNameContainingTheNumber() {
    var hits = index.search("5", null, null, 10);

    assertThat(hits).isNotEmpty();
    assertThat(hits.getFirst().type()).isEqualTo(SearchType.ROUTE);
    assertThat(ids(hits)).contains(DEPO_IELA.getId());
  }

  @Test
  void modeWordRestrictsRoutesToThatMode() {
    var hits = index.search("tramvajs 5", null, null, 10);
    var routeIds = idsOfType(hits, SearchType.ROUTE);

    assertThat(routeIds).contains(TRAM_5.getId());
    assertThat(routeIds).doesNotContain(BUS_5.getId());
  }

  @Test
  void modeWordAloneListsRoutesOfThatMode() {
    var hits = index.search("tramvajs", null, null, 10);

    assertThat(ids(hits)).containsExactly(TRAM_5.getId(), TRAM_11.getId());
  }

  @Test
  void modeWordStillMatchesStopWhoseNameContainsIt() {
    var hits = index.search("tramvaju depo", null, null, 10);

    assertThat(ids(hits)).contains(TRAMVAJU_DEPO.getId());
  }

  @Test
  void focusPointDecidesBetweenEquallyNamedRoutes() {
    var fromRiga = idsOfType(index.search("5", RIGA, null, 10), SearchType.ROUTE);
    var fromLiepaja = idsOfType(index.search("5", LIEPAJA, null, 10), SearchType.ROUTE);

    assertThat(fromRiga.getFirst()).isEqualTo(TRAM_5.getId());
    assertThat(fromLiepaja.getFirst()).isEqualTo(BUS_5.getId());
  }

  @Test
  void distanceIsReportedOnlyWithAFocusPoint() {
    var withFocus = index.search("Depo iela", RIGA, null, 10);
    assertThat(withFocus.getFirst().distanceMeters()).isNotNull();
    assertThat(withFocus.getFirst().distanceMeters()).isLessThan(1000.0);

    var withoutFocus = index.search("Depo iela", null, null, 10);
    assertThat(withoutFocus.getFirst().distanceMeters()).isNull();
  }

  @Test
  void patternIsFoundByItsOwnName() {
    var hits = index.search("Caur centru", null, null, 10);

    assertThat(idsOfType(hits, SearchType.PATTERN)).contains(TRAM_5_PATTERN.getId());
  }

  @Test
  void typesFilterRestrictsResults() {
    var hits = index.search("5", null, Set.of(SearchType.STOP), 10);

    assertThat(hits).isNotEmpty();
    assertThat(hits.stream().map(SearchHit::type).distinct().toList()).containsExactly(
      SearchType.STOP
    );
  }

  @Test
  void searchIsCaseInsensitive() {
    var lower = ids(index.search("centraltirgus", null, null, 10));
    var upper = ids(index.search("CENTRALTIRGUS", null, null, 10));

    assertThat(lower).contains(CENTRALTIRGUS.getId());
    assertThat(upper).contains(CENTRALTIRGUS.getId());
  }

  @Test
  void routeIsFoundByLongName() {
    var hits = index.search("Ķengarags", null, null, 10);

    assertThat(ids(hits)).contains(TRAM_5.getId());
  }

  @Test
  void regionNameDisambiguatesRoutesWithTheSameNumber() {
    var riga = idsOfType(index.search("rīga 31", null, null, 10), SearchType.ROUTE);
    var liepaja = idsOfType(index.search("liepāja 31", null, null, 10), SearchType.ROUTE);

    assertThat(riga.getFirst()).isEqualTo(BUS_31_RIGA.getId());
    assertThat(liepaja.getFirst()).isEqualTo(BUS_31_LIEPAJA.getId());
  }

  @Test
  void regionNameMatchesWithoutDiacritics() {
    var hits = idsOfType(index.search("riga 31", null, null, 10), SearchType.ROUTE);

    assertThat(hits.getFirst()).isEqualTo(BUS_31_RIGA.getId());
  }

  @Test
  void routeIsFoundByRegionNameAlone() {
    var hits = ids(index.search("Rīga", null, null, 10));

    assertThat(hits).contains(BUS_31_RIGA.getId());
  }

  @Test
  void englishModeWordMatchesToo() {
    var hits = idsOfType(index.search("tram 5", null, null, 10), SearchType.ROUTE);

    assertThat(hits).contains(TRAM_5.getId());
    assertThat(hits).doesNotContain(BUS_5.getId());
  }

  @Test
  void limitIsRespected() {
    assertThat(index.search("5", null, null, 1)).hasSize(1);
  }

  @Test
  void blankQueryReturnsNothing() {
    assertThat(index.search("   ", null, null, 10)).isEmpty();
  }

  private static List<FeedScopedId> ids(List<SearchHit> hits) {
    return hits.stream().map(SearchHit::id).toList();
  }

  private static List<FeedScopedId> idsOfType(List<SearchHit> hits, SearchType type) {
    return hits
      .stream()
      .filter(h -> h.type() == type)
      .map(SearchHit::id)
      .toList();
  }
}
