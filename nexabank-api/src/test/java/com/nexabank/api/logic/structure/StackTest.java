package com.nexabank.api.logic.structure;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas para Stack (estático y dinámico).
 *
 * <p>NOTA DE AUTORESÍA CRUZADA: Estas pruebas fueron escritas por un
 * desarrollador diferente al que implementó Stack.
 */
class StackTest {

    private Stack<Integer> staticStack;
    private Stack<Integer> dynamicStack;

    @BeforeEach
    void setUp() {
        staticStack = new Stack<>(3);
        dynamicStack = new Stack<>();
    }

    @Test
    void testStaticPushPop() {
        staticStack.push(10);
        staticStack.push(20);
        assertEquals(20, staticStack.peek());
        assertEquals(20, staticStack.pop());
        assertEquals(10, staticStack.pop());
        assertTrue(staticStack.isEmpty());
    }

    @Test
    void testStaticOverflow() {
        staticStack.push(1);
        staticStack.push(2);
        staticStack.push(3);
        assertThrows(IllegalStateException.class, () -> staticStack.push(4));
    }

    @Test
    void testStaticEmptyPop() {
        assertThrows(NoSuchElementException.class, staticStack::pop);
        assertThrows(NoSuchElementException.class, staticStack::peek);
    }

    @Test
    void testDynamicPushPop() {
        dynamicStack.push(100);
        dynamicStack.push(200);
        dynamicStack.push(300);
        assertEquals(300, dynamicStack.peek());
        assertEquals(300, dynamicStack.pop());
        assertEquals(200, dynamicStack.pop());
        assertEquals(100, dynamicStack.pop());
        assertTrue(dynamicStack.isEmpty());
    }

    @Test
    void testDynamicEmptyPop() {
        assertThrows(NoSuchElementException.class, dynamicStack::pop);
    }

    @Test
    void testSize() {
        assertEquals(0, staticStack.size());
        staticStack.push(5);
        assertEquals(1, staticStack.size());
        staticStack.pop();
        assertEquals(0, staticStack.size());
    }

    @Test
    void testClear() {
        dynamicStack.push(1);
        dynamicStack.push(2);
        dynamicStack.clear();
        assertTrue(dynamicStack.isEmpty());
    }

    @Test
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Stack<>(0));
        assertThrows(IllegalArgumentException.class, () -> new Stack<>(-1));
    }
}
