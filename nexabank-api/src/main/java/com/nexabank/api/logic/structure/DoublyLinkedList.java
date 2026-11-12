package com.nexabank.api.logic.structure;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Lista doblemente ligada genérica.
 *
 * <p>Cada nodo mantiene referencias al nodo anterior y siguiente, lo que
 * permite recorrer la lista en ambas direcciones y eliminar nodos en O(1)
 * una vez se tiene la referencia al nodo.
 *
 * <p>Operaciones principales:
 * <ul>
 *   <li>addFirst / addLast — O(1)</li>
 *   <li>removeFirst / removeLast — O(1)</li>
 *   <li>get(index) — O(n)</li>
 *   <li>remove(node) — O(1) si se tiene la referencia del nodo</li>
 *   <li>size — O(1)</li>
 * </ul>
 *
 * <p>Justificación de diseño: se elige lista doble sobre arreglo dinámico cuando
 * el sistema necesita inserciones y eliminaciones frecuentes en ambos extremos
 * (por ejemplo, historial de transacciones donde se agrega al final y se elimina
 * del frente para mantener un ventana deslizante), sin pagar el costo O(n) de
 * desplazamiento de elementos que impone un arreglo.
 *
 * @param <T> tipo de los elementos
 */
public class DoublyLinkedList<T> implements Iterable<T> {

    private Node<T> head;
    private Node<T> tail;
    private int size;

    private static class Node<T> {
        T value;
        Node<T> prev;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    public DoublyLinkedList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Agrega un elemento al inicio de la lista. O(1).
     */
    public void addFirst(T value) {
        Node<T> node = new Node<>(value);
        if (head == null) {
            head = tail = node;
        } else {
            node.next = head;
            head.prev = node;
            head = node;
        }
        size++;
    }

    /**
     * Agrega un elemento al final de la lista. O(1).
     */
    public void addLast(T value) {
        Node<T> node = new Node<>(value);
        if (tail == null) {
            head = tail = node;
        } else {
            node.prev = tail;
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Inserta un elemento en la posición dada. O(n).
     */
    public void addAt(int index, T value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        if (index == 0) {
            addFirst(value);
            return;
        }
        if (index == size) {
            addLast(value);
            return;
        }
        Node<T> curr = nodeAt(index);
        Node<T> node = new Node<>(value);
        node.prev = curr.prev;
        node.next = curr;
        curr.prev.next = node;
        curr.prev = node;
        size++;
    }

    /**
     * Elimina y retorna el primer elemento. O(1).
     */
    public T removeFirst() {
        if (head == null) {
            throw new NoSuchElementException("Lista vacía");
        }
        T value = head.value;
        head = head.next;
        if (head == null) {
            tail = null;
        } else {
            head.prev = null;
        }
        size--;
        return value;
    }

    /**
     * Elimina y retorna el último elemento. O(1).
     */
    public T removeLast() {
        if (tail == null) {
            throw new NoSuchElementException("Lista vacía");
        }
        T value = tail.value;
        tail = tail.prev;
        if (tail == null) {
            head = null;
        } else {
            tail.next = null;
        }
        size--;
        return value;
    }

    /**
     * Elimina el nodo dado (referencia directa). O(1).
     */
    public boolean remove(Node<T> node) {
        if (node == null) return false;
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }
        node.prev = null;
        node.next = null;
        size--;
        return true;
    }

    /**
     * Retorna el elemento en la posición dada. O(n).
     */
    public T get(int index) {
        return nodeAt(index).value;
    }

    /**
     * Retorna el nodo en la posición dada. O(n).
     */
    private Node<T> nodeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        Node<T> curr;
        if (index < size / 2) {
            curr = head;
            for (int i = 0; i < index; i++) curr = curr.next;
        } else {
            curr = tail;
            for (int i = size - 1; i > index; i--) curr = curr.prev;
        }
        return curr;
    }

    /**
     * Retorna el tamaño de la lista. O(1).
     */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    /**
     * Retorna true si la lista contiene el valor. O(n).
     */
    public boolean contains(T value) {
        for (T v : this) {
            if (v == null ? value == null : v.equals(value)) return true;
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> curr = head;

            @Override
            public boolean hasNext() {
                return curr != null;
            }

            @Override
            public T next() {
                if (curr == null) throw new NoSuchElementException();
                T value = curr.value;
                curr = curr.next;
                return value;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        Node<T> curr = head;
        while (curr != null) {
            sb.append(curr.value);
            if (curr.next != null) sb.append(" <-> ");
            curr = curr.next;
        }
        sb.append("]");
        return sb.toString();
    }
}
