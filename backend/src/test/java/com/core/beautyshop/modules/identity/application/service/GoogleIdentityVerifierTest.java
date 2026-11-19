package com.core.beautyshop.modules.identity.application.service;

import com.core.beautyshop.shared.exception.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdentityVerifierTest {
    private static final String CLIENT_ID = "local-test-client";
    private static RSAKey signingKey;
    private static GoogleIdentityVerifier verifier;

    @BeforeAll
    static void setUpVerifier() throws Exception {
        signingKey = new RSAKeyGenerator(2048).generate();
        verifier = new GoogleIdentityVerifier(CLIENT_ID, NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build());
    }

    @Test
    void acceptsBothGoogleIssuersAndNormalizesVerifiedEmail() throws Exception {
        for (String issuer : new String[]{"https://accounts.google.com", "accounts.google.com"}) {
            String token = sign(claims().issuer(issuer).build(), signingKey);
            var identity = verifier.verify(token);
            assertThat(identity.subject()).isEqualTo("google-user");
            assertThat(identity.email()).isEqualTo("customer@gmail.com");
            assertThat(identity.googleManagesEmail()).isTrue();
        }
    }

    @Test
    void rejectsWrongAudienceIssuerExpiryUnverifiedEmailAndSignature() throws Exception {
        rejects(claims().audience("different-client").build());
        rejects(claims().issuer("https://example.com").build());
        rejects(claims().expirationTime(Date.from(Instant.now().minusSeconds(1))).build());
        rejects(claims().claim("email_verified", false).build());

        String forged = sign(claims().build(), new RSAKeyGenerator(2048).generate());
        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(BusinessException.class);
    }

    private static void rejects(JWTClaimsSet claims) throws Exception {
        String token = sign(claims, signingKey);
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BusinessException.class);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().subject("google-user")
                .issuer("https://accounts.google.com").audience(CLIENT_ID)
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("email", "Customer@gmail.com").claim("email_verified", true);
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }
}
