package net.tfminecraft.woodworking.station;

/** Result of a station action. Callers own player-facing messages. */
public enum StationFeedback {
    SUCCESS,
    WRONG_TYPE,
    LACKING_ITEMS,
    LACKING_HITS,
    RECIPE_MISMATCH,
    NO_PROJECT
}
