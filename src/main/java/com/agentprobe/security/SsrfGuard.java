package com.agentprobe.security;

import com.agentprobe.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks requests to internal addresses when users register their own agent endpoints (SSRF).
 *
 * - allow-private-targets=true permits loopback and private ranges (local development only).
 * - allowed-hosts is a comma separated list of host names that may resolve to private addresses
 *   (for example "shopbot" inside Docker). Everything else stays blocked.
 * - Link-local / cloud metadata addresses (169.254.x.x), wildcard and multicast stay blocked in every mode.
 *
 * Known limit: DNS can change between this check and the connection (DNS rebinding).
 * Pinning the resolved IP is future work.
 */
@Component
public class SsrfGuard {

    private final boolean allowPrivate;
    private final Set<String> allowedHosts = new HashSet<String>();

    public SsrfGuard(boolean allowPrivate) {
        this(allowPrivate, "");
    }

    @Autowired
    public SsrfGuard(@Value("${agentprobe.security.allow-private-targets:false}") boolean allowPrivate,
                     @Value("${agentprobe.security.allowed-hosts:}") String allowedHostsCsv) {
        this.allowPrivate = allowPrivate;
        if (allowedHostsCsv != null) {
            for (String h : allowedHostsCsv.split(",")) {
                if (!h.isBlank()) {
                    allowedHosts.add(h.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public URI validate(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw bad("Endpoint URL is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw bad("Endpoint URL must start with http:// or https://");
        }
        if (uri.getHost() == null) {
            throw bad("Endpoint URL has no host");
        }
        if (uri.getUserInfo() != null) {
            throw bad("Credentials inside the URL are not allowed; use the auth header fields");
        }
        boolean hostAllowed = allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw bad("Endpoint host could not be resolved");
        }
        for (InetAddress a : addresses) {
            if (isBlocked(a, hostAllowed)) {
                throw bad("Endpoint resolves to a blocked (internal) address");
            }
        }
        return uri;
    }

    boolean isBlocked(InetAddress a) {
        return isBlocked(a, false);
    }

    boolean isBlocked(InetAddress a, boolean hostAllowed) {
        if (a.isAnyLocalAddress() || a.isMulticastAddress() || a.isLinkLocalAddress()) {
            return true; // always blocked, includes 169.254.169.254 metadata endpoint
        }
        if (allowPrivate || hostAllowed) {
            return false;
        }
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127); // 0.0.0.0/8 and CGNAT 100.64.0.0/10
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // unique local fc00::/7
        }
        return false;
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, msg);
    }
}