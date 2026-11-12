package com.nexabank.api.logic.structure;

import com.nexabank.api.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cola de transferencias pendientes usando la estructura {@link Queue} propia.
 *
 * <p>Demuestra la integración de una estructura de datos dinámica del núcleo
 * en el dominio bancario: las transferencias que aún no se han confirmado
 * (estado PENDING) se encolan aquí y se procesan en orden FIFO.
 *
 * <p>Esto desacopla la recepción de la solicitud de transferencia de su
 * ejecución efectiva en Firestore, permitiendo reintentos, ordenación y
 * control de concurrencia sin modificar el flujo existente de
 * {@link com.nexabank.api.service.TransferService}.
 */
public class PendingTransferQueue {

    private final Queue<TransferEntry> queue;

    public PendingTransferQueue() {
        this.queue = new Queue<>(); // versión dinámica: sin límite fijo
    }

    /**
     * Agrega una transferencia a la cola de pendientes. O(1).
     */
    public void enqueue(String transferId, String sourceAccountId, String destinationAccountId,
                        BigDecimal amount, String sourceCurrency, String destinationCurrency) {
        TransferEntry entry = new TransferEntry(transferId, sourceAccountId, destinationAccountId,
                amount, sourceCurrency, destinationCurrency, Instant.now());
        queue.enqueue(entry);
    }

    /**
     * Procesa la siguiente transferencia de la cola. O(1).
     * Retorna null si la cola está vacía.
     */
    public TransferEntry dequeue() {
        return queue.dequeue();
    }

    /**
     * Inspecciona la siguiente transferencia sin removerla. O(1).
     */
    public TransferEntry peek() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }

    @Override
    public String toString() {
        return "PendingTransferQueue(size=" + size() + ", " + queue + ")";
    }

    /** Entrada individual de una transferencia en cola. */
    public static class TransferEntry {
        private final String transferId;
        private final String sourceAccountId;
        private final String destinationAccountId;
        private final BigDecimal amount;
        private final String sourceCurrency;
        private final String destinationCurrency;
        private final Instant enqueuedAt;

        public TransferEntry(String transferId, String sourceAccountId,
                             String destinationAccountId, BigDecimal amount,
                             String sourceCurrency, String destinationCurrency,
                             Instant enqueuedAt) {
            this.transferId = transferId;
            this.sourceAccountId = sourceAccountId;
            this.destinationAccountId = destinationAccountId;
            this.amount = amount;
            this.sourceCurrency = sourceCurrency;
            this.destinationCurrency = destinationCurrency;
            this.enqueuedAt = enqueuedAt;
        }

        public String getTransferId() { return transferId; }
        public String getSourceAccountId() { return sourceAccountId; }
        public String getDestinationAccountId() { return destinationAccountId; }
        public BigDecimal getAmount() { return amount; }
        public String getSourceCurrency() { return sourceCurrency; }
        public String getDestinationCurrency() { return destinationCurrency; }
        public Instant getEnqueuedAt() { return enqueuedAt; }

        @Override
        public String toString() {
            return transferId + "(" + sourceAccountId + "→" + destinationAccountId +
                    ", " + amount + " " + sourceCurrency + ")";
        }
    }
}
