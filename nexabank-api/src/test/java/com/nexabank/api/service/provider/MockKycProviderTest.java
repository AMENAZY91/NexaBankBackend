package com.nexabank.api.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockKycProviderTest {

    private final MockKycProvider provider = new MockKycProvider();

    @Test
    void submit_shouldReturnUnderReview_withGeneratedVerificationId() {
        KycSubmissionResult result = provider.submit(
                new KycSubmission("USR-000001", "NATIONAL_ID", "12345678", "Juan Perez", "1990-01-01", "Calle 123"));

        assertThat(result.status()).isEqualTo(KycStatus.UNDER_REVIEW);
        assertThat(result.verificationId()).isNotBlank();
    }

    @Test
    void submit_shouldGenerateDistinctVerificationIds_acrossCalls() {
        KycSubmission submission = new KycSubmission("USR-000001", "NATIONAL_ID", "12345678", "Juan Perez", "1990-01-01", "Calle 123");

        KycSubmissionResult first = provider.submit(submission);
        KycSubmissionResult second = provider.submit(submission);

        assertThat(first.verificationId()).isNotEqualTo(second.verificationId());
    }

    @Test
    void getStatus_shouldReturnUnderReview() {
        assertThat(provider.getStatus("any-verification-id")).isEqualTo(KycStatus.UNDER_REVIEW);
    }
}
