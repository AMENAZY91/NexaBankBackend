package com.nexabank.api.logic.structure;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Array dinámico genérico — estructura de procesamiento del motor algorítmico.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Almacenamiento secuencial con redimensionamiento automático</li>
 *   <li>Operaciones: add, get, set, remove, size</li>
 *   <li>Sirve como entrada para MergeSort, QuickSort y BinarySearch</li>
 * </ul>
 *
 * <p>Complejidad:
 * <ul>
 *   <li>add (amortizado): O(1)</li>
 *   <li>get / set: O(1)</li>
 *   <li>remove: O(n)</li>
 *   <li>resize: O(n)</li>
 * </ul>
 *
 * <p><strong>Esta estructura NO se persiste en Firestore.</strong>
 * Es una estructura de procesamiento del backend.
 *
 * @param <T> el tipo de elementos almacenados
 */
public class DynamicArray<T> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final double GROWTH_FACTOR = 1.5;

    private Object[] elements;
    private int size;

    /**
     * Crea un DynamicArray con capacidad por defecto (16).
     */
    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Crea un DynamicArray con la capacidad inicial especificada.
     *
     * @param initialCapacity capacidad inicial
     * @throws IllegalArgumentException si la capacidad es negativa
     */
    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.elements = new Object[initialCapacity];
        this.size = 0;
    }

    /**
     * Agrega un elemento al final del array.
     * Amortizado O(1).
     *
     * @param element el elemento a agregar
     */
    public void add(T element) {
        ensureCapacity(size + 1);
        elements[size++] = element;
    }

    /**
     * Inserta un elemento en la posición especificada.
     * O(n) por el desplazamiento de elementos.
     *
     * @param index   la posición donde insertar
     * @param element el elemento a insertar
     * @throws IndexOutOfBoundsException si el índice está fuera de rango
     */
    public void addAt(int index, T element) {
        checkIndexForAdd(index);
        ensureCapacity(size + 1);
        System.arraycopy(elements, index, elements, index + 1, size - index);
        elements[index] = element;
        size++;
    }

    /**
     * Obtiene el elemento en la posición especificada.
     * O(1).
     *
     * @param index la posición del elemento
     * @return el elemento en la posición
     * @throws IndexOutOfBoundsException si el índice está fuera de rango
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) elements[index];
    }

    /**
     * Establece el elemento en la posición especificada.
     * O(1).
     *
     * @param index   la posición donde establecer
     * @param element el nuevo elemento
     * @return el elemento anterior en esa posición
     * @throws IndexOutOfBoundsException si el índice está fuera de rango
     */
    @SuppressWarnings("unchecked")
    public T set(int index, T element) {
        checkIndex(index);
        T old = (T) elements[index];
        elements[index] = element;
        return old;
    }

    /**
     * Elimina el elemento en la posición especificada.
     * O(n) por el desplazamiento de elementos.
     *
     * @param index la posición del elemento a eliminar
     * @return el elemento eliminado
     * @throws IndexOutOfBoundsException si el índice está fuera de rango
     */
    @SuppressWarnings("unchecked")
    public T remove(int index) {
        checkIndex(index);
        T removed = (T) elements[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        elements[--size] = null;
        return removed;
    }

    /**
     * Retorna el número de elementos en el array.
     *
     * @return el tamaño actual
     */
    public int size() {
        return size;
    }

    /**
     * Retorna true si el array no contiene elementos.
     *
     * @return true si está vacío
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Retorna la capacidad actual del array interno.
     *
     * @return la capacidad
     */
    public int capacity() {
        return elements.length;
    }

    /**
     * Elimina todos los elementos.
     */
    public void clear() {
        Arrays.fill(elements, 0, size, null);
        size = 0;
    }

    /**
     * Verifica si el array contiene el elemento especificado.
     * O(n).
     *
     * @param element el elemento a buscar
     * @return true si se encuentra
     */
    public boolean contains(T element) {
        return indexOf(element) >= 0;
    }

    /**
     * Retorna el índice de la primera ocurrencia del elemento, o -1 si no se encuentra.
     * O(n).
     *
     * @param element el elemento a buscar
     * @return el índice, o -1
     */
    public int indexOf(T element) {
        if (element == null) {
            for (int i = 0; i < size; i++) {
                if (elements[i] == null) return i;
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (element.equals(elements[i])) return i;
            }
        }
        return -1;
    }

    /**
     * Intercambia dos elementos en las posiciones dadas.
     * O(1). Usado internamente por QuickSort.
     *
     * @param i primera posición
     * @param j segunda posición
     * @throws IndexOutOfBoundsException si algún índice está fuera de rango
     */
    public void swap(int i, int j) {
        checkIndex(i);
        checkIndex(j);
        Object temp = elements[i];
        elements[i] = elements[j];
        elements[j] = temp;
    }

    /**
     * Crea una copia de una porción del array.
     *
     * @param fromIndex índice inicial (inclusive)
     * @param toIndex   índice final (exclusive)
     * @return un nuevo DynamicArray con la subparte
     */
    @SuppressWarnings("unchecked")
    public DynamicArray<T> subArray(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException(
                    "fromIndex: " + fromIndex + ", toIndex: " + toIndex + ", size: " + size);
        }
        DynamicArray<T> sub = new DynamicArray<>(toIndex - fromIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            sub.add((T) elements[i]);
        }
        return sub;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            @SuppressWarnings("unchecked")
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return (T) elements[cursor++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(elements[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > elements.length) {
            int newCapacity = Math.max((int) (elements.length * GROWTH_FACTOR), minCapacity);
            elements = Arrays.copyOf(elements, newCapacity);
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    private void checkIndexForAdd(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }
}
