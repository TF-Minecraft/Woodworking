package net.tfminecraft.woodworking.station;

/** Result of a station action. Callers own player-facing messages. */
public enum StationFeedback {
    SUCCESS,
    CAPACITY,
    WRONG_TYPE,
    NONE,
    LACKING_ITEMS,
    LACKING_HITS,
    RECIPE_MISMATCH,
    NO_PROJECT
}
