package org.opentripplanner.ext.geocoder;

import static java.util.Map.entry;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene103.Lucene103Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.suggest.document.Completion101PostingsFormat;
import org.apache.lucene.search.suggest.document.CompletionAnalyzer;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.ext.stopconsolidation.StopConsolidationService;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.StopType;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TimetableRepository;
import org.opentripplanner.transit.service.TransitService;

public class LuceneIndex implements Serializable {

  private static final int MAX_RESULTS = 25;
  private static final int MAX_SEARCH_RESULTS = 100;

  private static final String TYPE = "type";
  private static final String ID = "id";
  private static final String SECONDARY_IDS = "secondary_ids";
  private static final String SUGGEST = "suggest";
  private static final String NAME = "name";
  private static final String NAME_NGRAM = "name_ngram";
  private static final String CODE = "code";
  private static final String MODE = "mode";
  private static final String LAT = "latitude";
  private static final String LON = "longitude";
  private static final String LOCATION = "location";

  /**
   * Boosts applied to an exact match on the code field, which holds stop codes and route numbers.
   * A route's number is the strongest signal there is: a query of "5" means route 5, not the stop
   * "Depo iela 5". Patterns rank below routes because they carry their parent route's number and
   * would otherwise crowd it out.
   */
  private static final float ROUTE_CODE_BOOST = 300.0f;
  private static final float STOP_CODE_BOOST = 100.0f;
  private static final float PATTERN_CODE_BOOST = 60.0f;

  /**
   * Applied to the unsplit query text when mode words were removed from it, so that a name which
   * genuinely contains a mode word ("Tramvaju depo") still matches. It is deliberately weak: it is
   * a fallback, not the primary interpretation.
   */
  private static final float ORIGINAL_TEXT_BOOST = 0.5f;

  /**
   * The proximity boost is deliberately small: it is added on top of the text-match score to break
   * near-ties in favour of what is closer, not to override a clearly better match somewhere else.
   * The secondary distance sort already orders equally scoring results nearest-first, so this only
   * needs to nudge results whose text relevance is comparable. A larger value lets a mediocre nearby
   * match outrank an exact match one city over — for a search from Riga, "Krustpils iela" would then
   * beat the "Krustpils" station in Jēkabpils.
   */
  private static final float DISTANCE_BOOST = 3.0f;

  /**
   * The distance at which the proximity boost is halved. Tuned for city-scale search, where
   * everything a user is likely to mean is within a few tens of kilometres; a larger pivot makes
   * the boost too flat to reorder results within a city.
   */
  private static final double DISTANCE_PIVOT_METERS = 25_000.0;

  private final Analyzer analyzer;
  private final SuggestIndexSearcher searcher;
  private final StopClusterMapper stopClusterMapper;

  /**
   * Since the {@link TransitService} is request scoped, we don't inject it into this class.
   * However, we do need some methods in the service and that's why we instantiate it manually in this
   * constructor.
   */
  public LuceneIndex(
    TimetableRepository timetableRepository,
    StopConsolidationService stopConsolidationService
  ) {
    this(new DefaultTransitService(timetableRepository), stopConsolidationService);
  }

  /**
   * This method is only visible for testing.
   */
  LuceneIndex(
    TransitService transitService,
    @Nullable StopConsolidationService stopConsolidationService
  ) {
    this.stopClusterMapper = new StopClusterMapper(transitService, stopConsolidationService);
    var transitDocumentMapper = new TransitDocumentMapper(transitService);

    this.analyzer = new PerFieldAnalyzerWrapper(
      new StandardAnalyzer(),
      Map.ofEntries(
        entry(NAME, new EnglishAnalyzer()),
        entry(NAME_NGRAM, new EnglishNGramAnalyzer()),
        entry(SUGGEST, new CompletionAnalyzer(new StandardAnalyzer()))
      )
    );

    var directory = new ByteBuffersDirectory();

    try {
      try (
        var directoryWriter = new IndexWriter(
          directory,
          iwcWithSuggestField(analyzer, Set.of(SUGGEST))
        )
      ) {
        var regularStops = transitService
          .listStopLocations()
          .stream()
          .filter(stopLocation -> stopLocation.getStopType() == StopType.REGULAR)
          .toList();
        stopClusterMapper
          .generateStopClusters(regularStops, transitService.listStopLocationGroups())
          .forEach(stopCluster ->
            addStopClusterToIndex(
              directoryWriter,
              stopCluster.primaryId(),
              stopCluster.secondaryIds(),
              stopCluster.names(),
              stopCluster.codes(),
              stopCluster.coordinate().lat(),
              stopCluster.coordinate().lon()
            )
          );

        transitDocumentMapper
          .generateRouteDocuments()
          .forEach(doc -> addTransitDocumentToIndex(directoryWriter, SearchType.ROUTE, doc));
        transitDocumentMapper
          .generatePatternDocuments()
          .forEach(doc -> addTransitDocumentToIndex(directoryWriter, SearchType.PATTERN, doc));
      }

      DirectoryReader indexReader = DirectoryReader.open(directory);
      searcher = new SuggestIndexSearcher(indexReader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Return all "stop clusters" for a given query.
   * <p>
   * Stop clusters are defined as follows.
   * <p>
   *  - If a stop has a parent station, only the parent is returned.
   *  - If two stops have the same name *and* are less than 10 meters from each other, only
   *    one of those is chosen at random and returned.
   */
  public Stream<StopCluster> queryStopClusters(String query, @Nullable WgsCoordinate focusPoint) {
    return findDocuments(query, focusPoint).map(this::toStopCluster);
  }

  /**
   * Searches stops, routes and patterns in one ranked list.
   * <p>
   * Matching is case-insensitive and tolerates typos and partial words. Results are ranked by how
   * well they match the text, boosted by how close they are to {@code focusPoint} — so a user
   * typing "5" in Riga gets Riga's route 5 rather than one in another city.
   * <p>
   * Words naming a transit mode ("tram", "tramvajs") are understood as such: "tramvajs 5" finds tram
   * route 5 and not bus 5. A query consisting only of mode words lists that mode's routes, nearest
   * first.
   * <p>
   * The index is a snapshot taken at startup, so entities added by real-time updates are not
   * searchable. Callers resolve the returned ids against the transit service, so the data they show
   * is current even though the match was made against the snapshot.
   *
   * @param types the kinds of entity to return, or null/empty for all of them
   * @param limit the maximum number of results, capped at {@value #MAX_SEARCH_RESULTS}
   */
  public List<SearchHit> search(
    String query,
    @Nullable WgsCoordinate focusPoint,
    @Nullable Set<SearchType> types,
    int limit
  ) {
    var searchTypes = (types == null || types.isEmpty())
      ? EnumSet.allOf(SearchType.class)
      : EnumSet.copyOf(types);
    var parsed = ModeWordParser.parse(query);

    var luceneQuery = buildSearchQuery(query, parsed, focusPoint, searchTypes);
    if (luceneQuery.isEmpty()) {
      return List.of();
    }

    var n = Math.clamp(limit, 1, MAX_SEARCH_RESULTS);
    try {
      TopDocs topDocs;
      if (focusPoint == null) {
        topDocs = searcher.search(luceneQuery.get(), n);
      } else {
        var sort = new Sort(
          SortField.FIELD_SCORE,
          LatLonDocValuesField.newDistanceSort(
            LOCATION,
            focusPoint.latitude(),
            focusPoint.longitude()
          )
        );
        // doDocScores, so that the score survives onto the hits and not just into the sort
        topDocs = searcher.search(luceneQuery.get(), n, sort, true);
      }

      var hits = new ArrayList<SearchHit>(topDocs.scoreDocs.length);
      for (var scoreDoc : topDocs.scoreDocs) {
        var hit = toSearchHit(scoreDoc);
        if (hit != null) {
          hits.add(hit);
        }
      }
      return hits;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<Query> buildSearchQuery(
    String originalQuery,
    ModeWordParser.ParsedQuery parsed,
    @Nullable WgsCoordinate focusPoint,
    Set<SearchType> searchTypes
  ) {
    var matchBuilder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);

    if (parsed.hasText()) {
      for (var type : searchTypes) {
        matchBuilder.add(textSubQuery(type, parsed, originalQuery), Occur.SHOULD);
      }
    } else if (parsed.hasModes()) {
      // "tramvajs" on its own is a request for that mode's routes, ranked by proximity
      if (!searchTypes.contains(SearchType.ROUTE)) {
        return Optional.empty();
      }
      matchBuilder.add(modeOnlySubQuery(parsed.modes()), Occur.SHOULD);
    } else {
      return Optional.empty();
    }

    // What the user asked for decides *which* entities match; the focus point only decides the order
    // among them. The distance boost is therefore optional on top of a required match — as a plain
    // SHOULD alongside the text it would admit anything near the focus point, however unrelated.
    var builder = new BooleanQuery.Builder().add(matchBuilder.build(), Occur.MUST);
    if (focusPoint != null) {
      builder.add(distanceBoostQuery(focusPoint), Occur.SHOULD);
    }
    return Optional.of(builder.build());
  }

  /**
   * The clauses matching one document type. Each type gets its own sub-query because the same text
   * means different things depending on what it is matched against, and so has to be boosted
   * differently.
   */
  private Query textSubQuery(
    SearchType type,
    ModeWordParser.ParsedQuery parsed,
    String originalQuery
  ) {
    var builder = new BooleanQuery.Builder()
      .setMinimumNumberShouldMatch(1)
      .add(typeFilter(type), Occur.FILTER);

    addTextClauses(builder, parsed.remainingText(), codeBoost(type));

    if (parsed.hasModes()) {
      // the words we stripped may have been part of a name, so keep matching the original text too
      addOriginalTextClause(builder, originalQuery);

      // a stop's mode is not indexed and is often ambiguous anyway, so stops are never excluded by
      // it; for routes and patterns the mode is exactly what the user asked for
      if (type != SearchType.STOP) {
        builder.add(modeFilter(parsed.modes()), Occur.FILTER);
      }
    }
    return builder.build();
  }

  private Query modeOnlySubQuery(Set<TransitMode> modes) {
    return new BooleanQuery.Builder()
      .add(typeFilter(SearchType.ROUTE), Occur.FILTER)
      .add(modeFilter(modes), Occur.FILTER)
      .add(new MatchAllDocsQuery(), Occur.MUST)
      .build();
  }

  private void addTextClauses(BooleanQuery.Builder builder, String text, float codeBoost) {
    Query nameQuery;
    try {
      // the text comes from a search box, so it is escaped rather than treated as query syntax
      nameQuery = new QueryParser(NAME_NGRAM, analyzer).parse(QueryParser.escape(text));
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }

    builder
      .add(nameQuery, Occur.SHOULD)
      .add(new TermQuery(new Term(NAME_NGRAM, analyzer.normalize(NAME_NGRAM, text))), Occur.SHOULD)
      .add(fuzzyNameQuery(analyzer.normalize(NAME, text)), Occur.SHOULD)
      .add(new PrefixQuery(new Term(NAME, analyzer.normalize(NAME, text))), Occur.SHOULD)
      .add(
        new BoostQuery(new TermQuery(new Term(CODE, analyzer.normalize(CODE, text))), codeBoost),
        Occur.SHOULD
      )
      .add(new PrefixQuery(new Term(CODE, analyzer.normalize(CODE, text))), Occur.SHOULD);
  }

  /**
   * Lucene's default of two edits is far too loose for short queries: at that distance "5" matches
   * "11", so every two-digit route would come back for a query of "5". The allowance therefore grows
   * with the length of the term, and a typo is never tolerated in the first character.
   */
  private static Query fuzzyNameQuery(BytesRef text) {
    var term = new Term(NAME, text);
    int length = text.utf8ToString().length();
    int maxEdits = length <= 3 ? 0 : (length <= 6 ? 1 : 2);
    if (maxEdits == 0) {
      return new TermQuery(term);
    }
    return new FuzzyQuery(term, maxEdits, 1);
  }

  private void addOriginalTextClause(BooleanQuery.Builder builder, String originalQuery) {
    try {
      var query = new QueryParser(NAME_NGRAM, analyzer).parse(QueryParser.escape(originalQuery));
      builder.add(new BoostQuery(query, ORIGINAL_TEXT_BOOST), Occur.SHOULD);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private Query typeFilter(SearchType type) {
    return new TermQuery(new Term(TYPE, analyzer.normalize(TYPE, type.documentType())));
  }

  private static Query modeFilter(Set<TransitMode> modes) {
    var builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
    for (var mode : modes) {
      // the mode field is not analyzed, so it is matched by its exact enum name
      builder.add(new TermQuery(new Term(MODE, mode.name())), Occur.SHOULD);
    }
    return builder.build();
  }

  private static Query distanceBoostQuery(WgsCoordinate focusPoint) {
    return LatLonPoint.newDistanceFeatureQuery(
      LOCATION,
      DISTANCE_BOOST,
      focusPoint.latitude(),
      focusPoint.longitude(),
      DISTANCE_PIVOT_METERS
    );
  }

  private static float codeBoost(SearchType type) {
    return switch (type) {
      case ROUTE -> ROUTE_CODE_BOOST;
      case STOP -> STOP_CODE_BOOST;
      case PATTERN -> PATTERN_CODE_BOOST;
    };
  }

  @Nullable
  private SearchHit toSearchHit(ScoreDoc scoreDoc) throws IOException {
    var document = searcher.storedFields().document(scoreDoc.doc);
    var type = SearchType.ofDocumentType(document.get(TYPE));
    if (type == null) {
      return null;
    }
    return new SearchHit(
      FeedScopedId.parse(document.get(ID)),
      type,
      scoreDoc.score,
      distanceOf(scoreDoc)
    );
  }

  /**
   * The distance the sort computed, so that callers do not have to recompute it. It is only present
   * when sorting by distance, and is infinite for entities that have no location at all.
   */
  @Nullable
  private static Double distanceOf(ScoreDoc scoreDoc) {
    if (
      scoreDoc instanceof FieldDoc fieldDoc &&
      fieldDoc.fields.length > 1 &&
      fieldDoc.fields[1] instanceof Double distance &&
      Double.isFinite(distance)
    ) {
      return distance;
    }
    return null;
  }

  private StopCluster toStopCluster(Document document) {
    var primaryId = FeedScopedId.parse(document.get(ID));
    var primary = stopClusterMapper.toLocation(primaryId);

    var secondaryIds = Arrays.stream(document.getValues(SECONDARY_IDS))
      .map(FeedScopedId::parse)
      .map(stopClusterMapper::toLocation)
      .toList();

    return new StopCluster(primary, secondaryIds);
  }

  static IndexWriterConfig iwcWithSuggestField(Analyzer analyzer, final Set<String> suggestFields) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec filterCodec = new Lucene103Codec() {
      final PostingsFormat postingsFormat = new Completion101PostingsFormat();

      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        if (suggestFields.contains(field)) {
          return postingsFormat;
        }
        return super.getPostingsFormatForField(field);
      }
    };
    iwc.setCodec(filterCodec);
    return iwc;
  }

  private static void addStopClusterToIndex(
    IndexWriter writer,
    String id,
    Collection<String> secondaryIds,
    Collection<I18NString> names,
    Collection<String> codes,
    double latitude,
    double longitude
  ) {
    String typeName = SearchType.STOP.documentType();

    Document document = new Document();
    document.add(new StoredField(ID, id));
    for (var secondaryId : secondaryIds) {
      document.add(new StoredField(SECONDARY_IDS, secondaryId));
    }
    document.add(new TextField(TYPE, typeName, Store.YES));
    for (var name : names) {
      var nameString = Objects.toString(name);
      document.add(new TextField(NAME, nameString, Store.YES));
      document.add(new TextField(NAME_NGRAM, nameString, Store.YES));
      document.add(new ContextSuggestField(SUGGEST, nameString, 1, typeName));

      // A stop is often known by a shorter form of its full name: the place before a "(...)"
      // qualifier, or one side of an "A/B" name that joins two adjacent places. Index each such form
      // as a name of its own so it matches as strongly as a stop actually named that, rather than as
      // a few tokens buried in a longer name.
      for (var alternative : alternativeNames(nameString)) {
        document.add(new TextField(NAME, alternative, Store.NO));
        document.add(new TextField(NAME_NGRAM, alternative, Store.NO));
      }
    }

    document.add(new LatLonPoint(LOCATION, latitude, longitude));
    document.add(new LatLonDocValuesField(LOCATION, latitude, longitude));
    document.add(new StoredField(LAT, latitude));
    document.add(new StoredField(LON, longitude));

    for (var code : codes) {
      document.add(new TextField(CODE, code, Store.YES));
      document.add(new ContextSuggestField(SUGGEST, code, 1, typeName));
    }

    addDocument(writer, document);
  }

  /**
   * Indexes a route or a pattern. Unlike a stop these have no single location: a route is indexed at
   * every stop it serves, because a line is "near" the user if any part of it is.
   */
  private static void addTransitDocumentToIndex(
    IndexWriter writer,
    SearchType type,
    LuceneTransitDocument doc
  ) {
    Document document = new Document();
    document.add(new StoredField(ID, doc.id()));
    document.add(new TextField(TYPE, type.documentType(), Store.YES));

    for (var name : doc.names()) {
      document.add(new TextField(NAME, name, Store.YES));
      document.add(new TextField(NAME_NGRAM, name, Store.YES));
    }
    for (var code : doc.codes()) {
      document.add(new TextField(CODE, code, Store.YES));
    }
    if (doc.mode() != null) {
      // not analyzed: the mode is matched as an exact enum name, never as free text
      document.add(new StringField(MODE, doc.mode().name(), Store.YES));
    }
    for (var coordinate : doc.coordinates()) {
      document.add(new LatLonPoint(LOCATION, coordinate.latitude(), coordinate.longitude()));
      document.add(
        new LatLonDocValuesField(LOCATION, coordinate.latitude(), coordinate.longitude())
      );
    }

    addDocument(writer, document);
  }

  /**
   * The additional names under which a stop should be findable, beyond its full name: each side of a
   * "A/B" name, and each name with its "(...)" qualifier removed. Blanks, and anything equal to the
   * full name, are dropped, so a plain name like "Krustpils iela" yields nothing.
   */
  static List<String> alternativeNames(String name) {
    var result = new LinkedHashSet<String>();
    for (var part : name.split("/")) {
      var trimmed = part.strip();
      if (!trimmed.isBlank() && !trimmed.equals(name)) {
        result.add(trimmed);
      }
      var withoutBrackets = stripBracketedText(trimmed);
      if (!withoutBrackets.isBlank() && !withoutBrackets.equals(name)) {
        result.add(withoutBrackets);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Removes any {@code (...)} sections from a name and collapses the resulting whitespace, so that
   * "Rīga (Prāgas iela)" becomes "Rīga". Returns an empty string if nothing is left outside the
   * brackets.
   */
  static String stripBracketedText(String name) {
    return name.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").strip();
  }

  private static void addDocument(IndexWriter writer, Document document) {
    try {
      writer.addDocument(document);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private Stream<Document> findDocuments(String searchTerms, @Nullable WgsCoordinate focusPoint) {
    searchTerms = searchTerms.strip();
    try {
      var nameParser = new QueryParser(NAME_NGRAM, analyzer);
      var nameQuery = nameParser.parse(searchTerms);

      var ngramNameQuery = new TermQuery(
        new Term(NAME_NGRAM, analyzer.normalize(NAME_NGRAM, searchTerms))
      );

      var fuzzyNameQuery = new FuzzyQuery(new Term(NAME, analyzer.normalize(NAME, searchTerms)));
      var prefixNameQuery = new PrefixQuery(new Term(NAME, analyzer.normalize(NAME, searchTerms)));
      var codeQuery = new TermQuery(new Term(CODE, analyzer.normalize(CODE, searchTerms)));

      var prefixCodeQuery = new PrefixQuery(new Term(CODE, analyzer.normalize(CODE, searchTerms)));

      var typeQuery = new TermQuery(
        new Term(TYPE, analyzer.normalize(TYPE, StopCluster.class.getSimpleName()))
      );

      var boostedCodeQuery = new BoostQuery(codeQuery, 100.0f);

      var builder = new BooleanQuery.Builder()
        .setMinimumNumberShouldMatch(1)
        .add(typeQuery, Occur.MUST)
        .add(boostedCodeQuery, Occur.SHOULD)
        .add(prefixCodeQuery, Occur.SHOULD)
        .add(nameQuery, Occur.SHOULD)
        .add(fuzzyNameQuery, Occur.SHOULD)
        .add(prefixNameQuery, Occur.SHOULD)
        .add(ngramNameQuery, Occur.SHOULD);
      if (focusPoint != null) {
        var distanceBoost = LatLonPoint.newDistanceFeatureQuery(
          LOCATION,
          // boost score
          10.0f,
          focusPoint.latitude(),
          focusPoint.longitude(),
          // pivot distance in meters
          100000.0
        );
        builder.add(distanceBoost, Occur.SHOULD);
      }
      var query = builder.build();

      TopDocs topDocs;
      if (focusPoint != null) {
        Sort sort = new Sort(
          new SortField("_score", SortField.Type.SCORE),
          LatLonDocValuesField.newDistanceSort(
            LOCATION,
            focusPoint.latitude(),
            focusPoint.longitude()
          )
        );
        topDocs = searcher.search(query, MAX_RESULTS, sort);
      } else {
        topDocs = searcher.search(query, MAX_RESULTS);
      }

      return Arrays.stream(topDocs.scoreDocs).map(scoreDoc -> {
        try {
          return searcher.storedFields().document(scoreDoc.doc);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (IOException | ParseException ex) {
      throw new RuntimeException(ex);
    }
  }
}
