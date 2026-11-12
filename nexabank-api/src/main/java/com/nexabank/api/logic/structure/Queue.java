package com.nexabank.api.logic.structure;

import java.util.NoSuchElementException;

/**
 * Cola (FIFO) con dos implementaciones: estática (array circular de tamaño
 * fijo) y dinámica (lista doblemente ligada).
 *
 * <p>Operaciones comunes: enqueue, dequeue, peek, isEmpty, size.
 *
 * <p>Justificación:
 * <ul>
 *   <li>Cola estática circular: reusa el espacio del array sin desplazar
 *       elementos al hacer dequeue. Ideal para sistemas embebidos o cuando se
 *       conoce el tamaño máximo de la cola de mensajes.</li>
 *   <li>Cola dinámica: crece según la demanda, sin riesgo de overflow. Ideal
 *       para colas de transferencias pendientes o notificaciones en la app.</li>
 * </ul>
 *
 * @param <T> tipo de los elementos
 */
public class Queue<T> {

    private enum Kind { STATIC, DYNAMIC }

    private final Kind kind;
    private final int capacity;
    private Object[] staticArray;
    private int front;
    private int rear;
    private int count;
    private DoublyLinkedList<T> dynamicList;

    /**
     * Crea una cola estática circular con capacidad fija.
     *
     * @param capacity número máximo de elementos
     * @throws IllegalArgumentException si la capacidad es <= 0
     */
    public Queue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.kind = Kind.STATIC;
        this.capacity = capacity;
        this.staticArray = new Object[capacity];
        this.front = 0;
        this.rear = -1;
        this.count = 0;
        this.dynamicList = null;
    }

    /**
     * Crea una cola dinámica (sin límite fijo, salvo memoria disponible).
     */
    public Queue() {
        this.kind = Kind.DYNAMIC;
        this.capacity = 0;
        this.staticArray = null;
        this.front = 0;
        this.rear = -1;
        this.count = 0;
        this.dynamicList = new DoublyLinkedList<>();
    }

    /**
     * Encola un elemento al final. O(1).
     *
     * @throws IllegalStateException si la cola estática está llena
     */
    public void enqueue(T value) {
        if (kind == Kind.STATIC) {
            if (count >= capacity) {
                throw new IllegalStateException("Queue overflow: capacidad estática alcanzada");
            }
            rear = (rear + 1) % capacity;
            staticArray[rear] = value;
            count++;
        } else {
            dynamicList.addLast(value);
        }
    }

    /**
     * Desencola y retorna el frente. O(1).
     *
     * @throws NoSuchElementException si la cola está vacía
     */
    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (isEmpty()) {
            throw new NoSuchElementException("Queue vacía");
        }
        if (kind == Kind.STATIC) {
            T value = (T) staticArray[front];
            staticArray[front] = null;
            front = (front + 1) % capacity;
            count--;
            return value;
        } else {
            return dynamicList.removeFirst();
        }
    }

    /**
     * Retorna el frente sin eliminarlo. O(1).
     *
     * @throws NoSuchElementException si la cola está vacía
     */
    @SuppressWarnings("unchecked")
    public T peek() {
        if (isEmpty()) {
            throw new NoSuchElementException("Queue vacía");
        }
        if (kind == Kind.STATIC) {
            return (T) staticArray[front];
        } else {
            return dynamicList.get(0);
        }
    }

    public boolean isEmpty() {
        if (kind == Kind.STATIC) return count == 0;
        return dynamicList.isEmpty();
    }

    public int size() {
        if (kind == Kind.STATIC) return count;
        return dynamicList.size();
    }

    public void clear() {
        if (kind == Kind.STATIC) {
            for (int i = 0; i < capacity; i++) staticArray[i] = null;
            front = 0;
            rear = -1;
            count = 0;
        } else {
            dynamicList.clear();
        }
    }

    @Override
    public String toString() {
        if (kind == Kind.STATIC) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < count; i++) {
                int idx = (front + i) % capacity;
                sb.append(staticArray[idx]);
                if (i < count - 1) sb.append(" <- ");
            }
            sb.append("]");
            return sb.toString();
        }
        return dynamicList.toString();
    }
}
