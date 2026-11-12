package com.nexabank.api.service.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador mock de {@link KycProvider} — única implementación hoy. Si se agrega un
 * proveedor real en el futuro, ambos beans deben quedar guardados con
 * {@code @ConditionalOnProperty} o {@code @Profile} para evitar ambigüedad de bean;
 * no se agrega esa guarda ahora porque no existe todavía ninguna propiedad de
 * configuración que elegir entre "mock" y "real".
 */
@Component
public class MockKycProvider implements KycProvider {

    @Override
    public KycSubmissionResult submit(KycSubmission submission) {
        return new KycSubmissionResult(UUID.randomUUID().toString(), KycStatus.UNDER_REVIEW);
    }

    @Override
    public KycStatus getStatus(String verificationId) {
        return KycStatus.UNDER_REVIEW;
    }
}
