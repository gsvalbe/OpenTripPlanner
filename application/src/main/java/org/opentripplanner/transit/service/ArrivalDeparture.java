package org.opentripplanner.transit.service;

public enum ArrivalDeparture {
  /**
   * Only calls where boarding is allowed.
   */
  DEPARTURES,
  /**
   * Only calls where alighting is allowed.
   */
  ARRIVALS,
  /**
   * Calls where either boarding or alighting is allowed. Calls which allow neither, such as
   * pass-through timing points, are excluded.
   */
  BOTH,
  /**
   * All calls, without filtering on whether boarding or alighting is allowed. Unlike
   * {@link #BOTH}, this includes calls which allow neither, such as pass-through timing points.
   */
  ALL,
}
