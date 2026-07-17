package org.opentripplanner.ext.geocoder;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * A single result of {@link LuceneIndex#search}.
 *
 * @param id the id of the matched entity, to be resolved against the transit model by the caller
 * @param type what kind of entity {@code id} refers to
 * @param score the relevance of the match. Only meaningful for comparing hits within one result
 *              list; the absolute value carries no meaning.
 * @param distanceMeters distance to the focus point, or null if no focus point was given or the
 *                       entity has no known location
 */
public record SearchHit(
  FeedScopedId id,
  SearchType type,
  float score,
  @Nullable Double distanceMeters
) {}
