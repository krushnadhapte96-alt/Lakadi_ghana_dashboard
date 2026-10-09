package com.ghana.dashboard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders the UPI payment QR that goes on bills.
 *
 * <p>ZXing is used for the matrix only; the PNG is rasterised here so the project
 * does not need the {@code javase} artifact and its extra imageio dependencies.
 *
 * <p>Bill printing must never fail because a QR could not be drawn, so every public
 * method returns {@code null} on failure and the caller simply omits the block.
 */
@Component
public class QrCodeService {

    private static final int DEFAULT_SIZE_PX = 220;
    private static final int QUIET_ZONE_MODULES = 2;

    /**
     * Builds the standard UPI intent string. {@code pa} is the payee VPA and
     * {@code pn} the payee name shown by most UPI apps before the customer confirms.
     */
    public String upiUri(String vpa, String payeeName) {
        if (vpa == null || vpa.isBlank()) {
            return null;
        }
        StringBuilder uri = new StringBuilder("upi://pay")
                .append("?pa=").append(urlEncode(vpa.trim()));
        String name = payeeName == null ? "" : payeeName.trim();
        if (!name.isEmpty()) {
            uri.append("&pn=").append(urlEncode(name));
        }
        return uri.toString();
    }

    /** PNG bytes for the given text, or {@code null} if it cannot be encoded. */
    public byte[] png(String text, int sizePx) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int size = sizePx > 0 ? sizePx : DEFAULT_SIZE_PX;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            // Bills get scanned off greasy paper and faded print, so the higher
            // correction level is worth the extra modules.
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = toImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "PNG", out)) {
                return null;
            }
            return out.toByteArray();
        } catch (WriterException | java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    public byte[] pngForUpi(String vpa, String payeeName) {
        return png(upiUri(vpa, payeeName), DEFAULT_SIZE_PX);
    }

    private static BufferedImage toImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? black : white);
            }
        }
        return image;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}