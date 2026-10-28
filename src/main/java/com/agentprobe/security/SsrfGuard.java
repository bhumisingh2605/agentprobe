package com.agentprobe.security;

import com.agentprobe.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Blocks internal targets when users register agent endpoints (SSRF).
 * allow-private-targets=true permits loopback/private ranges for local dev (ShopBot on localhost).
 * Link-local / cloud metadata (169.254.x.x) is blocked in every mode.
 * Known limit: DNS rebinding between check and connect (mention as future work in README).
 */
@Component
public class SsrfGuard {

    private final boolean allowPrivate;

    public SsrfGuard(@Value("${agentprobe.security.allow-private-targets:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
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
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw bad("Endpoint host could not be resolved");
        }
        for (InetAddress a : addresses) {
            if (isBlocked(a)) {
                throw bad("Endpoint resolves to a blocked (internal) address");
            }
        }
        return uri;
    }

    boolean isBlocked(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isMulticastAddress() || a.isLinkLocalAddress()) {
            return true;
        }
        if (allowPrivate) {
            return false;
        }
        if (a.isLoopbackAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127);
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, msg);
    }
}