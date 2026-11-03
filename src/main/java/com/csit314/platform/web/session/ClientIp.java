package com.csit314.platform.web.session;

import jakarta.servlet.http.HttpServletRequest;

// ADM-13/14 - behind a reverse proxy (or in Docker), request.getRemoteAddr() returns the
// proxy's own address, so every sign-up/login looks like it came from one IP and the
// registration-burst alerts become meaningless. X-Forwarded-For carries the original
// client address when a proxy is involved, so prefer it when present.
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // the header can carry a chain of proxies - the first entry is the original client
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
