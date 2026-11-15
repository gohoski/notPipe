package io.github.gohoski.notpipe.api;

/**
 * Created by Gleb on 02.11.2026.
 * Conversion profiles supported by conversion-capable backends, ordered
 * by increasing resolution.
 */
public enum Codec {
    /** MPEG-4 Visual, 270p */
    MPEG4,
    /** MPEG-4 Visual, 360p */
    MPEG4_360,
    /** H.263, 288p */
    H263;

    /**
     * Resolves a persisted codec index to a Codec.
     * Falls back to MPEG-4 Visual for unknown indices.
     * @param index persisted index (settings spinner position, i.e. the ordinal)
     * @return the codec, never null
     */
    public static Codec fromIndex(int index) {
        Codec[] values = values();
        if (index < 0 || index >= values.length) {
            return MPEG4;
        }
        return values[index];
    }
}
