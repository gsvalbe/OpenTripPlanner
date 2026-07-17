package org.opentripplanner.ext.geocoder;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.opentripplanner.transit.model.basic.TransitMode;

/**
 * Recognizes words describing a transit mode ("tram", "tramvajs") in a free-text search query and
 * separates them from the rest of the query.
 * <p>
 * This allows a query like "tramvajs 5" to be interpreted as "route number 5 which is a tram"
 * rather than as a text match against the literal string "tramvajs 5".
 * <p>
 * Matching is done on stems rather than exact words so that inflected forms are recognized without
 * enumerating them: Latvian "tramvajs", "tramvaja" and "tramvaju" all start with the stem
 * "tramvaj". Input is lower-cased and diacritics are folded, so "vilciens" and "vilciens" with or
 * without macrons behave identically.
 */
class ModeWordParser {

  /**
   * Stems shorter than this are only matched exactly, never as a prefix. Without this a short stem
   * like "bus" would swallow unrelated words such as "Busselton".
   */
  private static final int MIN_PREFIX_STEM_LENGTH = 4;

  /**
   * How much longer than the stem an inflected form may be. Latvian endings are at most three
   * characters ("tramvaj|iem"), so anything longer is a different word that merely starts the same
   * way — "trampoline" is not a tram.
   */
  private static final int MAX_INFLECTION_LENGTH = 3;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  /**
   * Maps a diacritic-free, lower-case stem to the mode it describes. English and Latvian are
   * included; more languages can be added here.
   */
  private static final Map<String, TransitMode> STEMS = Map.ofEntries(
    Map.entry("tram", TransitMode.TRAM),
    Map.entry("tramvaj", TransitMode.TRAM),
    Map.entry("streetcar", TransitMode.TRAM),
    Map.entry("bus", TransitMode.BUS),
    Map.entry("autobus", TransitMode.BUS),
    Map.entry("trolleybus", TransitMode.TROLLEYBUS),
    Map.entry("trolley", TransitMode.TROLLEYBUS),
    Map.entry("troleibus", TransitMode.TROLLEYBUS),
    Map.entry("trolejbus", TransitMode.TROLLEYBUS),
    Map.entry("train", TransitMode.RAIL),
    Map.entry("rail", TransitMode.RAIL),
    Map.entry("vilcien", TransitMode.RAIL),
    Map.entry("dzelzcel", TransitMode.RAIL),
    Map.entry("metro", TransitMode.SUBWAY),
    Map.entry("subway", TransitMode.SUBWAY),
    Map.entry("underground", TransitMode.SUBWAY),
    Map.entry("ferry", TransitMode.FERRY),
    Map.entry("pramis", TransitMode.FERRY),
    Map.entry("prame", TransitMode.FERRY),
    Map.entry("coach", TransitMode.COACH),
    Map.entry("funicular", TransitMode.FUNICULAR),
    Map.entry("gondola", TransitMode.GONDOLA),
    Map.entry("monorail", TransitMode.MONORAIL),
    Map.entry("taxi", TransitMode.TAXI),
    Map.entry("airplane", TransitMode.AIRPLANE),
    Map.entry("lidmasin", TransitMode.AIRPLANE)
  );

  /**
   * Splits the query into the modes its words describe and the text that remains.
   * <p>
   * A query of just "tramvajs" yields the TRAM mode and an empty remaining text, which callers
   * interpret as "list tram routes" rather than as a text search. Because a mode word can also be
   * part of a genuine name ("Tramvaju depo"), callers are expected to keep matching the original,
   * unsplit query text as well.
   */
  static ParsedQuery parse(String query) {
    var tokens = WHITESPACE.split(query.strip());
    var modes = new LinkedHashSet<TransitMode>();
    var remaining = new StringBuilder();

    for (var token : tokens) {
      var mode = modeFor(token);
      if (mode == null) {
        if (!remaining.isEmpty()) {
          remaining.append(' ');
        }
        remaining.append(token);
      } else {
        modes.add(mode);
      }
    }

    return new ParsedQuery(remaining.toString(), Set.copyOf(modes));
  }

  private static TransitMode modeFor(String token) {
    var normalized = normalize(token);
    if (normalized.isEmpty()) {
      return null;
    }
    var exact = STEMS.get(normalized);
    if (exact != null) {
      return exact;
    }
    return STEMS.entrySet()
      .stream()
      .filter(e -> isInflectionOf(normalized, e.getKey()))
      // the longest matching stem is the most specific one: "trolejbus" must win over "trolley"
      .max(Comparator.comparingInt(e -> e.getKey().length()))
      .map(Map.Entry::getValue)
      .orElse(null);
  }

  /**
   * Whether the word is the stem plus at most a grammatical ending, rather than a longer word that
   * merely begins with it.
   */
  private static boolean isInflectionOf(String word, String stem) {
    return (
      stem.length() >= MIN_PREFIX_STEM_LENGTH &&
      word.startsWith(stem) &&
      word.length() - stem.length() <= MAX_INFLECTION_LENGTH
    );
  }

  /**
   * Lower-cases and strips diacritics so that "Vilciens" and "vilciens" match the same stem.
   */
  static String normalize(String text) {
    var decomposed = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD);
    return DIACRITICS.matcher(decomposed).replaceAll("");
  }

  /**
   * The result of splitting a query: the words that were not recognized as mode words, and the
   * modes that the recognized words describe.
   */
  record ParsedQuery(String remainingText, Set<TransitMode> modes) {
    boolean hasText() {
      return !remainingText.isBlank();
    }

    boolean hasModes() {
      return !modes.isEmpty();
    }
  }
}
