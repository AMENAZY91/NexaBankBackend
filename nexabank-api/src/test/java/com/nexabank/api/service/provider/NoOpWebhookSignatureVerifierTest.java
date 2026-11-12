package com.nexabank.api.service.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexabank.api.exception.InvalidWebhookSignatureException;
import org.junit.jupiter.api.Test;

class NoOpWebhookSignatureVerifierTest {

    private final NoOpWebhookSignatureVerifier verifier = new NoOpWebhookSignatureVerifier();

    @Test
    void verify_shouldAlwaysReject_evenWithWellFormedSignature() {
        assertThatThrownBy(() -> verifier.verify("sha256=anything", "{\"userId\":\"USR-000001\"}"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_shouldReject_whenSignatureMissing() {
        assertThatThrownBy(() -> verifier.verify(null, "{}"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
