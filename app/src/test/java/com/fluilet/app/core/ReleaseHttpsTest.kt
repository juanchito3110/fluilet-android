package com.fluilet.app.core

import com.fluilet.app.core.network.TlsConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.UnknownServiceException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Cliente HTTPS de release contra un servidor TLS real: sin pines confía en cualquier certificado emitido por una CA
 * de confianza (aquí una CA de prueba hace el papel de las CAs del sistema Android, como Let's Encrypt en DigitalOcean);
 * con pines explícitos aplica certificate pinning.
 */
class ReleaseHttpsTest {

    private val ca = HeldCertificate.Builder().certificateAuthority(0).commonName("CA de prueba").build()
    private val certificate = HeldCertificate.Builder()
        .signedBy(ca)
        .commonName("localhost")
        .addSubjectAlternativeName("localhost")
        .build()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate, ca.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            start()
        }
    }

    @After fun tearDown() = server.shutdown()

    /** Igual que NetworkModule en release (TLS 1.3, pines de BuildConfig) con la CA de prueba como "CA del sistema". */
    private fun releaseClient(pins: String): OkHttpClient {
        val systemCas = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build()
        return TlsConfig.secure(OkHttpClient.Builder(), enforceTlsOnly = true, host = server.hostName, pins = pins)
            .sslSocketFactory(systemCas.sslSocketFactory(), systemCas.trustManager)
            .build()
    }

    private fun OkHttpClient.get(path: String) = newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test fun withoutPinsTheReleaseClientConnectsToAnyCertificateFromATrustedCa() {
        val client = releaseClient(pins = "")
        assertTrue("sin pinning", client.certificatePinner.pins.isEmpty())
        server.enqueue(MockResponse().setBody("""{"status":"ready"}"""))

        client.get("/health/ready").use { response ->
            assertEquals(200, response.code)
            assertEquals(TlsVersion.TLS_1_3, response.handshake!!.tlsVersion)
        }
    }

    @Test fun explicitPinsArePinned() {
        val rightPin = CertificatePinner.pin(certificate.certificate)
        val client = releaseClient(pins = "$rightPin,sha256/k3J9xQ2m5Vt7Lw0yZb1N4cPfRdGhUeSaTiOqWvXyKzM=")
        assertEquals(2, client.certificatePinner.pins.size)
        server.enqueue(MockResponse().setBody("ok"))

        client.get("/").use { assertEquals(200, it.code) }
    }

    @Test fun aCertificateThatDoesNotMatchThePinsIsRejected() {
        val client = releaseClient(pins = "sha256/k3J9xQ2m5Vt7Lw0yZb1N4cPfRdGhUeSaTiOqWvXyKzM=,sha256/n9Z7c1xYb2Q4w6E8r0T2y4U6i8O0p2A4s6D8f0G2h4J=")
        server.enqueue(MockResponse().setBody("ok"))
        try {
            client.get("/").close()
            fail("el pinning debía rechazar el certificado")
        } catch (expected: SSLPeerUnverifiedException) {
            assertTrue(expected.message!!.contains("Certificate pinning failure"))
        }
    }

    @Test fun releaseNeverFallsBackToCleartext() {
        val client = releaseClient(pins = "")
        val plain = MockWebServer().apply { start() }
        try {
            client.newCall(Request.Builder().url(plain.url("/")).build()).execute().close()
            fail("release no debe hablar HTTP en claro")
        } catch (expected: UnknownServiceException) {
            assertTrue(expected.message!!.contains("CLEARTEXT"))
        } finally {
            plain.shutdown()
        }
    }

    @Test fun placeholderPinsAreNeverTheDefault() {
        assertFalse(TlsConfig.hasPlaceholderPins(""))
        assertTrue(TlsConfig.secure(OkHttpClient.Builder(), true, "api.fluilet.com", "").build().certificatePinner.pins.isEmpty())
    }
}
