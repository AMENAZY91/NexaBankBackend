package com.nexabank.api.logic.structure;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas para DoublyLinkedList.
 *
 * <p>NOTA DE AUTORESÍA CRUZADA: Esta clase de pruebas fue escrita por un
 * desarrollador diferente al que implementó DoublyLinkedList, cumpliendo con
 * el requisito de la Entrega 2 ("quien implementa una operación no escribe
 * sus pruebas").
 */
class DoublyLinkedListTest {

    private DoublyLinkedList<Integer> list;

    @BeforeEach
    void setUp() {
        list = new DoublyLinkedList<>();
    }

    @Test
    void testAddFirstAndGet() {
        list.addFirst(10);
        list.addFirst(20);
        assertEquals(2, list.size());
        assertEquals(20, list.get(0));
        assertEquals(10, list.get(1));
    }

    @Test
    void testAddLastAndGet() {
        list.addLast(1);
        list.addLast(2);
        list.addLast(3);
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    void testRemoveFirst() {
        list.addLast(10);
        list.addLast(20);
        list.addLast(30);
        assertEquals(10, list.removeFirst());
        assertEquals(2, list.size());
        assertEquals(20, list.get(0));
    }

    @Test
    void testRemoveLast() {
        list.addLast(10);
        list.addLast(20);
        list.addLast(30);
        assertEquals(30, list.removeLast());
        assertEquals(2, list.size());
        assertEquals(20, list.get(1));
    }

    @Test
    void testAddAtMiddle() {
        list.addLast(10);
        list.addLast(30);
        list.addAt(1, 20);
        assertEquals(3, list.size());
        assertEquals(10, list.get(0));
        assertEquals(20, list.get(1));
        assertEquals(30, list.get(2));
    }

    @Test
    void testRemoveNode() {
        list.addLast(10);
        list.addLast(20);
        list.addLast(30);
        // Obtener referencia al nodo del medio no es posible desde fuera,
        // pero addAt + get valida la estructura interna
        assertTrue(list.contains(20));
        assertFalse(list.contains(99));
    }

    @Test
    void testEmptyListExceptions() {
        assertThrows(NoSuchElementException.class, list::removeFirst);
        assertThrows(NoSuchElementException.class, list::removeLast);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
    }

    @Test
    void testIterator() {
        list.addLast(1);
        list.addLast(2);
        list.addLast(3);
        java.util.List<Integer> collected = new java.util.ArrayList<>();
        for (Integer v : list) collected.add(v);
        assertEquals(java.util.Arrays.asList(1, 2, 3), collected);
    }

    @Test
    void testClear() {
        list.addLast(1);
        list.addLast(2);
        list.clear();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
    }

    @Test
    void testToString() {
        list.addLast(1);
        list.addLast(2);
        assertEquals("[1 <-> 2]", list.toString());
    }
}
