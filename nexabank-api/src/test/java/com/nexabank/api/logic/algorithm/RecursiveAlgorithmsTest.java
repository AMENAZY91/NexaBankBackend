package com.nexabank.api.logic.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas para RecursiveAlgorithms.
 *
 * <p>NOTA DE AUTORESÍA CRUZADA: Estas pruebas fueron escritas por un
 * desarrollador diferente al que implementó RecursiveAlgorithms.
 */
class RecursiveAlgorithmsTest {

    @Test
    void testHanoi() {
        List<String> steps = new ArrayList<>();
        int moves = RecursiveAlgorithms.hanoi(3, "A", "C", "B", steps);
        assertEquals(7, moves); // 2^3 - 1
        assertEquals(7, steps.size());
        assertTrue(steps.contains("Mover disco 1 de A a C"));
        assertTrue(steps.contains("Mover disco 3 de A a C"));
    }

    @Test
    void testHanoiZero() {
        List<String> steps = new ArrayList<>();
        assertEquals(0, RecursiveAlgorithms.hanoi(0, "A", "C", "B", steps));
        assertTrue(steps.isEmpty());
    }

    @Test
    void testFactorial() {
        assertEquals(1, RecursiveAlgorithms.factorial(0));
        assertEquals(1, RecursiveAlgorithms.factorial(1));
        assertEquals(2, RecursiveAlgorithms.factorial(2));
        assertEquals(6, RecursiveAlgorithms.factorial(3));
        assertEquals(24, RecursiveAlgorithms.factorial(4));
        assertEquals(120, RecursiveAlgorithms.factorial(5));
    }

    @Test
    void testFactorialNegative() {
        assertThrows(IllegalArgumentException.class, () -> RecursiveAlgorithms.factorial(-1));
    }

    @Test
    void testFibonacci() {
        assertEquals(0, RecursiveAlgorithms.fibonacci(0));
        assertEquals(1, RecursiveAlgorithms.fibonacci(1));
        assertEquals(1, RecursiveAlgorithms.fibonacci(2));
        assertEquals(2, RecursiveAlgorithms.fibonacci(3));
        assertEquals(3, RecursiveAlgorithms.fibonacci(4));
        assertEquals(5, RecursiveAlgorithms.fibonacci(5));
        assertEquals(8, RecursiveAlgorithms.fibonacci(6));
    }

    @Test
    void testFibonacciNegative() {
        assertThrows(IllegalArgumentException.class, () -> RecursiveAlgorithms.fibonacci(-1));
    }

    @Test
    void testSumArray() {
        int[] arr = {1, 2, 3, 4, 5};
        assertEquals(15, RecursiveAlgorithms.sumArray(arr, 0, arr.length - 1));
        assertEquals(0, RecursiveAlgorithms.sumArray(arr, 2, 1)); // rango vacío
        assertEquals(3, RecursiveAlgorithms.sumArray(arr, 0, 1));
    }
}
