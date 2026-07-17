package org.opentripplanner.apis.gtfs.datafetchers;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.apis.support.graphql.DataFetchingSupport.dataFetchingEnvironment;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentripplanner.apis.gtfs.model.SearchResultModel;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.i18n.TranslatedString;
import org.opentripplanner.transit.model._data.TimetableRepositoryForTest;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;

/**
 * The unified {@code name} exists so that a client can render a result list without switching on the
 * union member, so each member has to produce a sensible label.
 */
class SearchResultImplTest {

  private static final TimetableRepositoryForTest TEST_MODEL = TimetableRepositoryForTest.of();
  private static final SearchResultImpl SUBJECT = new SearchResultImpl();

  private static String nameOf(Object item, Map<String, Object> args) throws Exception {
    var model = new SearchResultModel(item, 1.0, null);
    return SUBJECT.name().get(dataFetchingEnvironment(model, args));
  }

  private static String nameOf(Object item) throws Exception {
    return nameOf(item, Map.of());
  }

  @Test
  void stopUsesItsName() throws Exception {
    RegularStop stop = TEST_MODEL.stop("Centrāltirgus").build();

    assertThat(nameOf(stop)).isEqualTo("Centrāltirgus");
  }

  @Test
  void stationUsesItsName() throws Exception {
    var station = TEST_MODEL.station("Centrālā stacija").build();

    assertThat(nameOf(station)).isEqualTo("Centrālā stacija");
  }

  @Test
  void routeUsesItsShortName() throws Exception {
    var route = route("5", I18NString.of("Centrs - Ķengarags"));

    assertThat(nameOf(route)).isEqualTo("5");
  }

  @Test
  void routeWithoutShortNameFallsBackToLongName() throws Exception {
    var route = TimetableRepositoryForTest.route("tram-x")
      .withShortName(null)
      .withMode(TransitMode.TRAM)
      .withLongName(I18NString.of("Centrs - Ķengarags"))
      .build();

    assertThat(nameOf(route)).isEqualTo("Centrs - Ķengarags");
  }

  @Test
  void patternUsesItsOwnName() throws Exception {
    var pattern = TimetableRepositoryForTest.tripPattern("tram-5-pattern", route("5", null))
      .withStopPattern(
        TimetableRepositoryForTest.stopPattern(
          TEST_MODEL.stop("A").build(),
          TEST_MODEL.stop("B").build()
        )
      )
      .withName("Caur centru")
      .build();

    assertThat(nameOf(pattern)).isEqualTo("Caur centru");
  }

  /**
   * Only the graph build names patterns, so a pattern introduced by a real-time update has no name
   * of its own. The field is non-null, so it has to fall back rather than fail the whole response.
   */
  @Test
  void unnamedPatternFallsBackToItsRoute() throws Exception {
    var pattern = TimetableRepositoryForTest.tripPattern("tram-5-pattern", route("5", null))
      .withStopPattern(
        TimetableRepositoryForTest.stopPattern(
          TEST_MODEL.stop("A").build(),
          TEST_MODEL.stop("B").build()
        )
      )
      .build();

    assertThat(pattern.getName()).isNull();
    assertThat(nameOf(pattern)).isEqualTo("5");
  }

  @Test
  void languageArgumentSelectsTheTranslation() throws Exception {
    var stop = TEST_MODEL.stop("Pasilan asema")
      .withName(
        TranslatedString.getI18NString(Map.of("", "Pasilan asema", "sv", "Böle station"), false)
      )
      .build();

    assertThat(nameOf(stop, Map.of("language", "sv"))).isEqualTo("Böle station");
    assertThat(nameOf(stop)).isEqualTo("Pasilan asema");
  }

  private static Route route(String shortName, I18NString longName) {
    return TimetableRepositoryForTest.route("tram-5")
      .withShortName(shortName)
      .withMode(TransitMode.TRAM)
      .withLongName(longName == null ? I18NString.of("Centrs - Ķengarags") : longName)
      .build();
  }
}
