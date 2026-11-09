package org.opentripplanner.ext.generalpattern.internal.graphbuilder;

/**
 * A station visit in a stop sequence. Loop routes visit the same station more than once; the
 * occurrence number (0 for the first visit) keeps those visits apart.
 */
record Node(StationKey key, int occurrence) {
  @Override
  public String toString() {
    return occurrence == 0 ? key.toString() : key + "#" + occurrence;
  }
}
