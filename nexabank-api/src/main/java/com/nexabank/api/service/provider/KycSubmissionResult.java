package com.nexabank.api.service.provider;

/**
 * Resultado de enviar una {@link KycSubmission} a un {@link KycProvider}.
 */
public record KycSubmissionResult(String verificationId, KycStatus status) {
}
