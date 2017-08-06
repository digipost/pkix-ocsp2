package net.klakegg.pkix.ocsp;

import net.klakegg.pkix.ocsp.builder.BuildHandler;
import net.klakegg.pkix.ocsp.builder.Builder;
import net.klakegg.pkix.ocsp.builder.Properties;
import net.klakegg.pkix.ocsp.builder.Property;

import java.net.URI;
import java.security.cert.X509Certificate;

/**
 * Implementation of OCSP client supporting verification of a single certificate.
 *
 * @author erlend
 */
public class OcspClient extends AbstractOcspClient {

    public static final Property<Boolean> EXCEPTION_ON_REVOKED = Property.create(true);

    public static final Property<Boolean> EXCEPTION_ON_UNKNOWN = Property.create(true);

    /**
     * Builder to create an instance of the client.
     *
     * @return Prepared client.
     */
    public static Builder<OcspClient> builder() {
        return new Builder<>(new BuildHandler<OcspClient>() {
            @Override
            public OcspClient build(Properties properties) {
                return new OcspClient(properties);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    private OcspClient(Properties properties) {
        super(properties);
    }

    public CertificateResult verify(X509Certificate certificate) throws OcspException {
        return verify(certificate, findIntermediate(certificate));
    }

    public CertificateResult verify(X509Certificate certificate, X509Certificate issuer) throws OcspException {
        URI uri = properties.get(OVERRIDE_URL);

        if (uri == null) {
            uri = detectOcspUri(certificate);

            // In case no URI was detected.
            if (uri == null)
                return new CertificateResult(CertificateStatus.UNKNOWN);
        }

        OcspRequest request = new OcspRequest();
        request.setIssuer(CertificateIssuer.generate(issuer));
        request.addCertificates(certificate);
        if (properties.get(NONCE))
            request.addNonce();

        OcspResponse response = fetch(request, uri);
        response.verifyResponse();

        CertificateResult certificateResult = response.getResult().get(certificate);

        switch (certificateResult.getStatus()) {
            case REVOKED:
                OcspException.trigger(properties.get(EXCEPTION_ON_REVOKED), "Certificate is revoked.");
                break;

            case UNKNOWN:
                OcspException.trigger(properties.get(EXCEPTION_ON_UNKNOWN), "Status of certificate is unknown.");
                break;
        }

        return certificateResult;
    }
}