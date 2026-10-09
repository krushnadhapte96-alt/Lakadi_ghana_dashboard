package com.ghana.dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Forces UTF-8 end to end.
 *
 * <p>Counters may run on devices whose default decode is not UTF-8. Hindi and Marathi names must
 * therefore not depend on the JVM or OS default. The filter requests UTF-8 on every request and
 * stamps it onto every character payload - JSON bodies above all - without every controller having
 * to set it itself.
 *
 * <p>The response charset is held back until the content type says the payload is text. Setting it
 * up front makes the container fold it into the {@code Content-Type} header, which would serve a
 * bill as {@code application/pdf;charset=UTF-8}; payloads that are not character data therefore
 * never receive one.
 */
@Component
public class Utf8EncodingFilter extends OncePerRequestFilter {

    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        request.setCharacterEncoding(UTF_8);
        DeferredCharsetResponse deferred = new DeferredCharsetResponse(response);
        deferred.setCharacterEncoding(UTF_8);
        chain.doFilter(request, deferred);
        deferred.finish();
    }

    /**
     * Keeps the requested charset in reserve until the content type identifies a character payload,
     * then lets it through. Responses whose content type is set behind its back are finished off by
     * {@link #finish()}.
     */
    private static final class DeferredCharsetResponse extends HttpServletResponseWrapper {

        private String charset;
        private boolean characterPayload;

        private DeferredCharsetResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setCharacterEncoding(String charset) {
            this.charset = charset;
            if (this.characterPayload) {
                apply();
            }
        }

        @Override
        public void setContentType(String type) {
            if (type != null && isCharacterPayload(type)) {
                this.characterPayload = true;
                apply();
            }
            super.setContentType(type);
        }

        @Override
        public void setHeader(String name, String value) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                setContentType(value);
            }
            else {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                setContentType(value);
            }
            else {
                super.addHeader(name, value);
            }
        }

        @Override
        public PrintWriter getWriter() throws IOException, UnsupportedEncodingException {
            this.characterPayload = true;
            apply();
            return super.getWriter();
        }

        @Override
        public void reset() {
            super.reset();
            this.characterPayload = false;
        }

        private void finish() {
            String contentType = super.getContentType();
            if (contentType == null || !isCharacterPayload(contentType) || containsCharset(contentType)) {
                return;
            }
            super.setContentType(contentType + ";charset=" + (this.charset != null ? this.charset : UTF_8));
        }

        private void apply() {
            if (this.charset != null) {
                super.setCharacterEncoding(this.charset);
            }
        }
    }

    /**
     * Whether the payload is decoded as characters rather than bytes: the families a charset
     * parameter means something for. Everything else - PDFs, images, archives - stays bare.
     */
    private static boolean isCharacterPayload(String contentType) {
        String mime = contentType;
        int parameter = mime.indexOf(';');
        if (parameter >= 0) {
            mime = mime.substring(0, parameter);
        }
        mime = mime.trim().toLowerCase(Locale.ROOT);
        return mime.startsWith("text/")
                || mime.equals("application/json")
                || mime.endsWith("+json")
                || mime.equals("application/xml")
                || mime.endsWith("+xml")
                || mime.equals("application/x-www-form-urlencoded")
                || mime.equals("application/graphql")
                || mime.equals("application/javascript");
    }

    private static boolean containsCharset(String contentType) {
        return contentType.toLowerCase(Locale.ROOT).contains("charset");
    }
}
