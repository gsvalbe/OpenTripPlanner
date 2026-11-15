package org.opentripplanner.transit.model.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Names a set of stop sequences, for example the patterns of one route, so that no two get the
 * same name. A name consists of stop names only, joined by {@link #SEPARATOR}. It never adds words
 * such as "via" or "express", so it reads the same in every language.
 * <ol>
 *   <li>The first and the last stop, if no other sequence has the same ends.</li>
 *   <li>Otherwise the first stop, one intermediate stop that no other sequence with the same ends
 *   serves, and the last stop. Of the candidate stops, the one closest to the middle of the
 *   sequence is used.</li>
 *   <li>Intermediate termini, where some trips start or end, are named too, at most
 *   {@link #MAX_TERMINI}, if the caller supplies them.</li>
 *   <li>Sequences that still cannot be told apart by their stop names, for example because they
 *   differ only by platform, are numbered. The first one keeps the plain name.</li>
 * </ol>
 */
public final class StopSequenceNamer {

  public static final String SEPARATOR = " - ";

  /** The most intermediate termini that are named in one name. */
  public static final int MAX_TERMINI = 3;

  private StopSequenceNamer() {}

  /**
   * The name of one sequence.
   *
   * @param stopIndexes positions, in the sequence, of the stops that make up the name, in order
   * @param number 0, or a number that tells the sequence apart from others with the same stops
   */
  public record Name(List<Integer> stopIndexes, int number) {
    public Name {
      stopIndexes = List.copyOf(stopIndexes);
    }

    /** Format the name, given the name of the stop at each position of the sequence. */
    public String format(IntFunction<String> stopName) {
      return StopSequenceNamer.format(stopIndexes.stream().map(stopName::apply).toList(), number);
    }
  }

  /** Join the stop names, and append the number if it is not 0. */
  public static String format(List<String> stopNames, int number) {
    String name = String.join(SEPARATOR, stopNames);
    return number == 0 ? name : name + " (" + number + ")";
  }

  /**
   * Name each sequence of stop names. The result has the same order as the input; the numbering
   * of sequences that cannot be told apart follows the input order.
   */
  public static List<Name> name(List<List<String>> sequences) {
    return name(
      sequences,
      sequences
        .stream()
        .map(s -> List.<Integer>of())
        .toList()
    );
  }

  /**
   * Like {@link #name(List)}, but a sequence's name also contains its intermediate termini, that is
   * stops where some trips of the sequence start or end.
   *
   * @param termini for each sequence, positions of intermediate stops to name, most important
   *                first. At most {@link #MAX_TERMINI} are used, and a stop whose name is already in
   *                the name is skipped.
   */
  public static List<Name> name(List<List<String>> sequences, List<List<Integer>> termini) {
    Name[] result = new Name[sequences.size()];
    List<List<Integer>> base = new ArrayList<>();
    Map<List<String>, List<Integer>> byBase = new LinkedHashMap<>();
    for (int i = 0; i < sequences.size(); i++) {
      List<Integer> indexes = baseIndexes(sequences.get(i), termini.get(i));
      base.add(indexes);
      List<String> names = indexes.stream().map(sequences.get(i)::get).toList();
      byBase.computeIfAbsent(names, k -> new ArrayList<>()).add(i);
    }

    for (List<Integer> group : byBase.values()) {
      List<Integer> remaining = new ArrayList<>(group);
      // A sequence named by a stop of its own leaves the others more room: a sequence whose stops
      // are all served by a longer one becomes unique once that one is named.
      while (remaining.size() > 1) {
        List<Integer> named = new ArrayList<>();
        for (int i : remaining) {
          int via = distinguishingStop(sequences, i, remaining, base.get(i));
          if (via > 0) {
            List<Integer> indexes = new ArrayList<>(base.get(i));
            indexes.add(via);
            indexes.sort(null);
            result[i] = new Name(indexes, 0);
            named.add(i);
          }
        }
        if (named.isEmpty()) {
          break;
        }
        remaining.removeAll(named);
      }
      for (int k = 0; k < remaining.size(); k++) {
        int i = remaining.get(k);
        result[i] = new Name(base.get(i), k == 0 ? 0 : k + 1);
      }
    }
    return List.of(result);
  }

  /** The ends and up to {@link #MAX_TERMINI} distinctly named intermediate termini. */
  private static List<Integer> baseIndexes(List<String> sequence, List<Integer> termini) {
    List<Integer> ends = endIndexes(sequence);
    if (ends.size() == 1) {
      return ends;
    }
    int last = sequence.size() - 1;
    Set<String> used = new HashSet<>(List.of(sequence.getFirst(), sequence.getLast()));
    List<Integer> result = new ArrayList<>(ends);
    int added = 0;
    for (int k : termini) {
      if (added == MAX_TERMINI) {
        break;
      }
      if (k > 0 && k < last && used.add(sequence.get(k))) {
        result.add(k);
        added++;
      }
    }
    result.sort(null);
    return result;
  }

  /**
   * The intermediate stop of sequence {@code i}, closest to its middle, whose name none of the
   * other sequences contains. Returns -1 if there is none.
   */
  private static int distinguishingStop(
    List<List<String>> sequences,
    int i,
    List<Integer> candidates,
    List<Integer> named
  ) {
    List<String> sequence = sequences.get(i);
    Set<String> others = new HashSet<>();
    for (int j : candidates) {
      if (j != i) {
        others.addAll(sequences.get(j));
      }
    }
    named.forEach(k -> others.add(sequence.get(k)));

    int last = sequence.size() - 1;
    int best = -1;
    for (int k = 1; k < last; k++) {
      if (others.contains(sequence.get(k))) {
        continue;
      }
      if (best < 0 || Math.abs(2 * k - last) < Math.abs(2 * best - last)) {
        best = k;
      }
    }
    return best;
  }

  private static List<Integer> endIndexes(List<String> sequence) {
    return sequence.size() == 1 ? List.of(0) : List.of(0, sequence.size() - 1);
  }
}
