package com.csit314.platform.web.session;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

// ADM-13/14 - behind a proxy, getRemoteAddr() alone always returns the proxy's own
// address, which makes every sign-up look like it came from one IP
class ClientIpUnitTests {

    @Test
    void fallsBackToRemoteAddrWhenThereIsNoProxyHeader() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");

        assertThat(ClientIp.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void prefersTheForwardedForHeaderOverTheProxysOwnAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertThat(ClientIp.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void takesTheFirstAddressWhenTheHeaderCarriesAProxyChain() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2, 10.0.0.1");

        assertThat(ClientIp.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresABlankForwardedForHeader() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(ClientIp.resolve(request)).isEqualTo("203.0.113.5");
    }
}
