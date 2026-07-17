package org.opentripplanner.ext.geocoder;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * The kinds of entity that {@link LuceneIndex#search} can return.
 */
public enum SearchType {
  /**
   * A stop or a station. Both are indexed as a single "stop cluster" document, and the GTFS API
   * models them as the same {@code Stop} type, so they are not told apart here either.
   */
  STOP("StopCluster"),
  ROUTE("Route"),
  PATTERN("Pattern");

  private static final Map<String, SearchType> BY_DOCUMENT_TYPE = Arrays.stream(values()).collect(
    Collectors.toMap(SearchType::documentType, Function.identity())
  );

  private final String documentType;

  SearchType(String documentType) {
    this.documentType = documentType;
  }

  /**
   * The value stored in the index's type field. It is kept separate from {@link #name()} because
   * the stop documents were indexed as "StopCluster" before this enum existed and the REST geocoder
   * still queries them under that name.
   */
  String documentType() {
    return documentType;
  }

  @Nullable
  static SearchType ofDocumentType(String documentType) {
    return BY_DOCUMENT_TYPE.get(documentType);
  }
}
