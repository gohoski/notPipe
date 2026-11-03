package io.github.gohoski.notpipe.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class AuthPrompt {
    public final String instanceUrl;
    public final String userCode;            // e.g. "3642177" or "SKH-GPN-DCFJ"
    public final String verificationUrl;     // "http://.../gsign" or "https://yt.be/activate"
    public final int instructionStringRes;   // R.string.auth_desc_yt2009 or R.string.auth_desc_ytfixer
    public final byte[] qrImageData;         // Nullable raw PNG/JPEG bytes
    public final String rawPayload;          // Session helper data for polling (e.g. device ID)

    public AuthPrompt(String instanceUrl, String userCode, String verificationUrl,
                      int instructionStringRes, byte[] qrImageData, String rawPayload) {
        this.instanceUrl = instanceUrl;
        this.userCode = userCode;
        this.verificationUrl = verificationUrl;
        this.instructionStringRes = instructionStringRes;
        this.qrImageData = qrImageData;
        this.rawPayload = rawPayload;
    }

    public boolean hasQrCode() {
        return qrImageData != null && qrImageData.length > 0;
    }

    public Bitmap getQrBitmap() {
        if (!hasQrCode()) return null;
        try {
            return BitmapFactory.decodeByteArray(qrImageData, 0, qrImageData.length);
        } catch (Throwable t) {
            return null;
        }
    }
}