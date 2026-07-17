package org.opentripplanner.ext.geocoder;

import static com.google.common.truth.Truth.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentripplanner.transit.model.basic.TransitMode;

class ModeWordParserTest {

  @ParameterizedTest
  @ValueSource(strings = { "tram", "Tram", "TRAM", "tramvajs", "tramvaja", "tramvaju", "tramvaji" })
  void recognizesTramInItsInflectedForms(String word) {
    var parsed = ModeWordParser.parse(word);

    assertThat(parsed.modes()).containsExactly(TransitMode.TRAM);
    assertThat(parsed.remainingText()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = { "vilciens", "vilciena", "vilcienu", "train", "rail" })
  void recognizesRail(String word) {
    assertThat(ModeWordParser.parse(word).modes()).containsExactly(TransitMode.RAIL);
  }

  @Test
  void separatesModeWordFromTheRestOfTheQuery() {
    var parsed = ModeWordParser.parse("tramvajs 5");

    assertThat(parsed.modes()).containsExactly(TransitMode.TRAM);
    assertThat(parsed.remainingText()).isEqualTo("5");
  }

  @Test
  void keepsWordOrderOfTheRemainingText() {
    var parsed = ModeWordParser.parse("autobuss uz Centraltirgus");

    assertThat(parsed.modes()).containsExactly(TransitMode.BUS);
    assertThat(parsed.remainingText()).isEqualTo("uz Centraltirgus");
  }

  @Test
  void collectsSeveralModes() {
    var parsed = ModeWordParser.parse("tramvajs vilciens 5");

    assertThat(parsed.modes()).containsExactly(TransitMode.TRAM, TransitMode.RAIL);
    assertThat(parsed.remainingText()).isEqualTo("5");
  }

  @ParameterizedTest
  @ValueSource(strings = { "Alexanderplatz", "5", "Depo iela", "Busselton", "trampoline" })
  void leavesNonModeWordsAlone(String query) {
    var parsed = ModeWordParser.parse(query);

    assertThat(parsed.modes()).isEmpty();
    assertThat(parsed.remainingText()).isEqualTo(query);
  }

  @Test
  void foldsDiacritics() {
    var parsed = ModeWordParser.parse("Tramvajs");

    assertThat(parsed.modes()).containsExactly(TransitMode.TRAM);
  }

  @Test
  void prefersTheMostSpecificStem() {
    assertThat(ModeWordParser.parse("trolejbuss").modes()).containsExactly(TransitMode.TROLLEYBUS);
    assertThat(ModeWordParser.parse("troleibuss").modes()).containsExactly(TransitMode.TROLLEYBUS);
    assertThat(ModeWordParser.parse("autobuss").modes()).containsExactly(TransitMode.BUS);
  }

  @Test
  void blankQueryHasNeitherModesNorText() {
    var parsed = ModeWordParser.parse("   ");

    assertThat(parsed.modes()).isEmpty();
    assertThat(parsed.hasText()).isFalse();
    assertThat(parsed.hasModes()).isFalse();
  }

  @Test
  void reportsWhatItFound() {
    var parsed = ModeWordParser.parse("tramvajs 5");

    assertThat(parsed.hasModes()).isTrue();
    assertThat(parsed.hasText()).isTrue();
    assertThat(parsed.modes()).isEqualTo(Set.of(TransitMode.TRAM));
  }
}
