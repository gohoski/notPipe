package io.github.gohoski.notpipe.api;

import java.io.IOException;

/**
 * Created by Gleb on 16.05.2026.
 */

public interface Conversion {
    /**
     * @param codec Conversion profile
     * @return Converted video URL
     */
    String getConvUrl(String id, Codec codec) throws IOException;
}
