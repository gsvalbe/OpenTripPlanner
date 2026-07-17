package org.opentripplanner.apis.gtfs.model;

import javax.annotation.Nullable;

/**
 * A single entity matched by a search query, paired with the ranking information that put it where
 * it is in the result list.
 *
 * @param item the matched entity: a stop, a station, a route or a trip pattern
 * @param score relevance of the text match, comparable only within one result list
 * @param distance meters to the focus point, or null if there was no focus point or the entity has
 *                 no known location
 */
public record SearchResultModel(Object item, double score, @Nullable Double distance) {}
