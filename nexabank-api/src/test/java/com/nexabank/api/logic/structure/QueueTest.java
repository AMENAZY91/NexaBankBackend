package com.nexabank.api.logic.structure;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas para Queue (estática circular y dinámica).
 *
 * <p>NOTA DE AUTORESÍA CRUZADA: Estas pruebas fueron escritas por un
 * desarrollador diferente al que implementó Queue.
 */
class QueueTest {

    private Queue<Integer> staticQueue;
    private Queue<Integer> dynamicQueue;

    @BeforeEach
    void setUp() {
        staticQueue = new Queue<>(3);
        dynamicQueue = new Queue<>();
    }

    @Test
    void testStaticEnqueueDequeue() {
        staticQueue.enqueue(10);
        staticQueue.enqueue(20);
        staticQueue.enqueue(30);
        assertEquals(10, staticQueue.dequeue());
        assertEquals(20, staticQueue.dequeue());
        assertEquals(30, staticQueue.dequeue());
        assertTrue(staticQueue.isEmpty());
    }

    @Test
    void testStaticOverflow() {
        staticQueue.enqueue(1);
        staticQueue.enqueue(2);
        staticQueue.enqueue(3);
        assertThrows(IllegalStateException.class, () -> staticQueue.enqueue(4));
    }

    @Test
    void testStaticWrapAround() {
        staticQueue.enqueue(1);
        staticQueue.enqueue(2);
        staticQueue.dequeue(); // libera posición 0
        staticQueue.enqueue(3);  // usa posición 0 (wrap around)
        assertEquals(2, staticQueue.dequeue());
        assertEquals(3, staticQueue.dequeue());
        assertTrue(staticQueue.isEmpty());
    }

    @Test
    void testDynamicEnqueueDequeue() {
        dynamicQueue.enqueue(1);
        dynamicQueue.enqueue(2);
        dynamicQueue.enqueue(3);
        assertEquals(1, dynamicQueue.dequeue());
        assertEquals(2, dynamicQueue.dequeue());
        assertEquals(3, dynamicQueue.dequeue());
        assertTrue(dynamicQueue.isEmpty());
    }

    @Test
    void testPeekDoesNotRemove() {
        dynamicQueue.enqueue(42);
        assertEquals(42, dynamicQueue.peek());
        assertEquals(42, dynamicQueue.peek());
        assertEquals(1, dynamicQueue.size());
    }

    @Test
    void testEmptyDequeue() {
        assertThrows(NoSuchElementException.class, dynamicQueue::dequeue);
        assertThrows(NoSuchElementException.class, dynamicQueue::peek);
    }

    @Test
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Queue<>(0));
    }
}
