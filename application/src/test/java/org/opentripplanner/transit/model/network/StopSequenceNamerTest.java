package org.opentripplanner.transit.model.network;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopSequenceNamerTest {

  @Test
  void uniqueEnds() {
    assertThat(names(List.of("A", "B", "C"), List.of("C", "B", "A"), List.of("A", "B")))
      .containsExactly("A - C", "C - A", "A - B")
      .inOrder();
  }

  @Test
  void sameEndsUseAStopOfTheirOwnClosestToTheMiddle() {
    assertThat(names(List.of("A", "B", "C", "D", "E", "F", "G"), List.of("A", "B", "X", "F", "G")))
      .containsExactly("A - D - G", "A - X - G")
      .inOrder();
  }

  @Test
  void sequenceWithoutAStopOfItsOwnKeepsThePlainName() {
    // The express serves a subset of the stopping train's stops
    assertThat(
      names(List.of("A", "C", "E"), List.of("A", "B", "C", "D", "E"), List.of("A", "B", "C", "E"))
    )
      .containsExactly("A - E", "A - D - E", "A - B - E")
      .inOrder();
  }

  @Test
  void sequencesWithTheSameStopNamesAreNumbered() {
    assertThat(names(List.of("A", "B", "C"), List.of("A", "B", "C"), List.of("A", "B", "C")))
      .containsExactly("A - C", "A - C (2)", "A - C (3)")
      .inOrder();
  }

  @Test
  void endStopNamesAreNotUsedAsTheMiddleStop() {
    // A loop, where the only stop of its own has the same name as the ends
    assertThat(names(List.of("A", "B", "A"), List.of("A", "A", "C", "A")))
      .containsExactly("A - B - A", "A - C - A")
      .inOrder();
  }

  @Test
  void intermediateTerminiAreNamedAtMostThree() {
    var seq = List.of("A", "B", "C", "D", "E", "F", "G");
    var names = StopSequenceNamer.name(List.of(seq), List.of(List.of(1, 2, 3, 4, 5)));
    assertThat(names.getFirst().format(seq::get)).isEqualTo("A - B - C - D - G");
  }

  @Test
  void terminiAndDistinguishingStopsMakeNamesUnique() {
    var s1 = List.of("A", "B", "C", "D", "E");
    var s2 = List.of("A", "B", "C", "X", "E");
    var names = StopSequenceNamer.name(List.of(s1, s2), List.of(List.of(1), List.of(1)));
    assertThat(names.get(0).format(s1::get)).isEqualTo("A - B - D - E");
    assertThat(names.get(1).format(s2::get)).isEqualTo("A - B - X - E");
  }

  @Test
  void terminusWithTheNameOfAnEndIsSkipped() {
    var seq = List.of("A", "B", "A", "C");
    var names = StopSequenceNamer.name(List.of(seq), List.of(List.of(2, 1)));
    assertThat(names.getFirst().format(seq::get)).isEqualTo("A - B - C");
  }

  @SafeVarargs
  private static List<String> names(List<String>... sequences) {
    var input = List.of(sequences);
    var names = StopSequenceNamer.name(input);
    List<String> result = new ArrayList<>();
    for (int i = 0; i < input.size(); i++) {
      result.add(names.get(i).format(input.get(i)::get));
    }
    return result;
  }
}
