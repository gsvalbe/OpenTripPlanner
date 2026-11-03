package org.opentripplanner.ext.generalpattern.model;

/**
 * How the trips of a general pattern serve one of its stops. Only trips that reach the stop (the
 * stop lies between their first and last stop) are counted; a short-turn trip that ends before the
 * stop does not make it less than {@link #ALWAYS}.
 */
public enum StopStatus {
  /** Every trip that reaches the stop stops there. */
  ALWAYS,
  /** Some trips pass the stop without stopping. */
  SOMETIMES_SKIPPED,
  /** Some trips serve the stop through a detour that other trips do not take. */
  DETOUR,
}
