# General patterns

## Contact Info

- Gustavs Švalbe

## Feature explanation

A route often has many trip patterns that riders see as one line: trains that use another
platform, express trains that skip stops, buses that end early or make a short detour through a
village. This sandbox feature builds one **general pattern** per route and direction that merges
those trip patterns into a single readable stop sequence.

For each stop the general pattern records how many trips reach it, stop there, pass without
stopping or do not take the detour to it, and where trips start and end. Clients can use this to
draw a line diagram with filled dots (all trips stop), outlined dots (some trips stop) and
termini, without any clustering or geometry code of their own.

General patterns describe the planned timetable. They are built once during graph build and are
not changed by real-time updates. Routing is not affected.

The general patterns are available in the [GTFS GraphQL API](#gtfs-graphql-api).

## How general patterns are built

1. Each stop is mapped to its parent station. Stops without a parent station are grouped when they
   have the same name and lie within `stationFallbackRadius` of each other.
2. Trip patterns with the same station sequence become one variant.
3. A variant joins a general pattern when they share at least `minSharedStations` stations and the
   combined stop order has no cycle. This keeps the two directions apart, so `direction_id` is not
   needed.
4. When the combined stop order is ambiguous, stops are ordered by their position along the
   shape. Such a merge is only kept if at least `corridorMinOverlap` of each variant's shape lies
   within `corridorBuffer` of the other. Branches and different roads stay separate.
5. A stop that a variant does not serve, but passes within `skipRadius` (`railSkipRadius` for rail)
   of, is marked as skipped. Otherwise it is a detour that the variant does not take.

## Configuration

Enable the feature in `otp-config.json`:

```json
// otp-config.json
{
  "otpFeatures": {
    "GeneralPatterns": true
  }
}
```

The `generalPatterns` block in `build-config.json` is optional. All parameters are documented in
[Build configuration](../BuildConfiguration.md#generalPatterns).

```json
// build-config.json
{
  "generalPatterns": {
    "excludeRegions": ["Rīga", "Vilnius", "Tallinna linn", "Kaunas"]
  }
}
```

`excludeRegions` uses the `region_name` column of `routes.txt`. Use `excludeFeeds`,
`excludeAgencies` or `excludeModes` for feeds without that column.

## GTFS GraphQL API

| Field | Returns |
| --- | --- |
| `Route.generalPatterns` | The route's general patterns, most trips first |
| `Pattern.generalPattern` | The general pattern a pattern is merged into |
| `generalPattern(id)` on the query root | One general pattern by id |

```graphql
{
  route(id: "pv:pv-ev-skul") {
    generalPatterns {
      gtfsId
      stops {
        stop { name }
        status          # ALWAYS, SOMETIMES_SKIPPED or DETOUR
        lineTerminus
        tripsStartHere
        tripsEndHere
      }
    }
  }
}
```

Each stop is at station level: `stop` is a stop the route uses there, `station` its parent station
and `mergedStops` all the stops merged into it, such as the platforms of a station.

## Changelog

- Initial version: build general patterns during graph build and store them in the graph.
- Expose general patterns in the GTFS GraphQL API.
