package net.tfminecraft.woodworking.station;

/** Result of a station action. Callers own player-facing messages. */
public enum StationFeedback {
    SUCCESS,
    WRONG_TYPE,
    RECIPE_MISMATCH,
    NO_PROJECT
}
