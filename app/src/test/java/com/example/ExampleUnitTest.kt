package com.example

import com.example.util.ProxyFormatHelper
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testUserDataImpulseProxyParsing() {
        val raw = "gw.dataimpulse.com:824:defa891ce67dcbeaddcb__cr.cm:5166837e6d2d525e"
        val parsed = ProxyFormatHelper.parse(raw)
        assertNotNull("Proxy should parse successfully", parsed)
        assertEquals("gw.dataimpulse.com", parsed!!.host)
        assertEquals(824, parsed.port)
        assertEquals("defa891ce67dcbeaddcb__cr.cm", parsed.username)
        assertEquals("5166837e6d2d525e", parsed.password)
        assertEquals("SOCKS5", parsed.protocol)
        assertEquals("CM", parsed.countryCode)
    }

    @Test
    fun testUserPasswordAtHostPort() {
        val raw = "defa891ce67dcbeaddcb__cr.cm:5166837e6d2d525e@gw.dataimpulse.com:824"
        val parsed = ProxyFormatHelper.parse(raw)
        assertNotNull(parsed)
        assertEquals("gw.dataimpulse.com", parsed!!.host)
        assertEquals(824, parsed.port)
        assertEquals("defa891ce67dcbeaddcb__cr.cm", parsed.username)
        assertEquals("5166837e6d2d525e", parsed.password)
        assertEquals("CM", parsed.countryCode)
    }

    @Test
    fun testUserPasswordHostPort() {
        val raw = "defa891ce67dcbeaddcb__cr.cm:5166837e6d2d525e:gw.dataimpulse.com:824"
        val parsed = ProxyFormatHelper.parse(raw)
        assertNotNull(parsed)
        assertEquals("gw.dataimpulse.com", parsed!!.host)
        assertEquals(824, parsed.port)
        assertEquals("defa891ce67dcbeaddcb__cr.cm", parsed.username)
        assertEquals("5166837e6d2d525e", parsed.password)
        assertEquals("CM", parsed.countryCode)
    }

    @Test
    fun testUnauthenticatedHostPort() {
        val raw = "gw.dataimpulse.com:824"
        val parsed = ProxyFormatHelper.parse(raw)
        assertNotNull(parsed)
        assertEquals("gw.dataimpulse.com", parsed!!.host)
        assertEquals(824, parsed.port)
        assertEquals("", parsed.username)
        assertEquals("", parsed.password)
    }

    @Test
    fun testSocks5AndHttpPrefixes() {
        val s5 = "socks5://defa891ce67dcbeaddcb__cr.cm:5166837e6d2d525e@gw.dataimpulse.com:824"
        val parsedS5 = ProxyFormatHelper.parse(s5)
        assertNotNull(parsedS5)
        assertEquals("SOCKS5", parsedS5!!.protocol)
        assertEquals("gw.dataimpulse.com", parsedS5.host)

        val http = "http://defa891ce67dcbeaddcb__cr.cm:5166837e6d2d525e@gw.dataimpulse.com:824"
        val parsedHttp = ProxyFormatHelper.parse(http)
        assertNotNull(parsedHttp)
        assertEquals("HTTP", parsedHttp!!.protocol)
        assertEquals("gw.dataimpulse.com", parsedHttp.host)
    }

    @Test
    fun testHttpCommonPortAutoDetect() {
        val raw = "1.2.3.4:8080"
        val parsed = ProxyFormatHelper.parse(raw)
        assertNotNull(parsed)
        assertEquals("HTTP", parsed!!.protocol)
        assertEquals("1.2.3.4", parsed.host)
        assertEquals(8080, parsed.port)
    }
}
