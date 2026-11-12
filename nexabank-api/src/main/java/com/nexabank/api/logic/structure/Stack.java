package com.nexabank.api.logic.structure;

import java.util.NoSuchElementException;

/**
 * Pila (LIFO) con dos implementaciones: estática (array de tamaño fijo) y
 * dinámica (lista doblemente ligada).
 *
 * <p>Operaciones comunes: push, pop, peek, isEmpty, size.
 *
 * <p>Justificación:
 * <ul>
 *   <li>Pila estática: predecible en memoria, sin asignaciones dinámicas. Útil
 *       cuando se conoce el límite máximo de elementos (ej. historial de deshacer
 *       con tope fijo).</li>
 *   <li>Pila dinámica: crece y se contrae según necesidad. Útil cuando el número
 *       de elementos es impredecible (ej. navegación de pantallas en la app).</li>
 * </ul>
 *
 * @param <T> tipo de los elementos
 */
public class Stack<T> {

    private enum Kind { STATIC, DYNAMIC }

    private final Kind kind;
    private final int capacity;
    private Object[] staticArray;
    private int top;
    private DoublyLinkedList<T> dynamicList;

    /**
     * Crea una pila estática con capacidad fija.
     *
     * @param capacity número máximo de elementos
     * @throws IllegalArgumentException si la capacidad es <= 0
     */
    public Stack(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.kind = Kind.STATIC;
        this.capacity = capacity;
        this.staticArray = new Object[capacity];
        this.top = -1;
        this.dynamicList = null;
    }

    /**
     * Crea una pila dinámica (sin límite fijo, salvo memoria disponible).
     */
    public Stack() {
        this.kind = Kind.DYNAMIC;
        this.capacity = 0;
        this.staticArray = null;
        this.top = -1;
        this.dynamicList = new DoublyLinkedList<>();
    }

    /**
     * Apila un elemento. O(1) amortizado (dinámica) / O(1) (estática).
     *
     * @throws IllegalStateException si la pila estática está llena
     */
    public void push(T value) {
        if (kind == Kind.STATIC) {
            if (top + 1 >= capacity) {
                throw new IllegalStateException("Stack overflow: capacidad estática alcanzada");
            }
            staticArray[++top] = value;
        } else {
            dynamicList.addLast(value);
        }
    }

    /**
     * Desapila y retorna el tope. O(1).
     *
     * @throws NoSuchElementException si la pila está vacía
     */
    @SuppressWarnings("unchecked")
    public T pop() {
        if (isEmpty()) {
            throw new NoSuchElementException("Stack vacío");
        }
        if (kind == Kind.STATIC) {
            T value = (T) staticArray[top];
            staticArray[top--] = null;
            return value;
        } else {
            return dynamicList.removeLast();
        }
    }

    /**
     * Retorna el tope sin eliminarlo. O(1).
     *
     * @throws NoSuchElementException si la pila está vacía
     */
    @SuppressWarnings("unchecked")
    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Stack vacío");
        }
        if (kind == Kind.STATIC) {
            return (T) staticArray[top];
        } else {
            return dynamicList.get(dynamicList.size() - 1);
        }
    }

    public boolean isEmpty() {
        if (kind == Kind.STATIC) return top == -1;
        return dynamicList.isEmpty();
    }

    public int size() {
        if (kind == Kind.STATIC) return top + 1;
        return dynamicList.size();
    }

    public void clear() {
        if (kind == Kind.STATIC) {
            for (int i = 0; i <= top; i++) staticArray[i] = null;
            top = -1;
        } else {
            dynamicList.clear();
        }
    }

    @Override
    public String toString() {
        if (kind == Kind.STATIC) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i <= top; i++) {
                sb.append(staticArray[i]);
                if (i < top) sb.append(" <- ");
            }
            sb.append("] (top)");
            return sb.toString();
        }
        return dynamicList.toString();
    }
}
