package com.nexabank.api.logic.algorithm;

import com.nexabank.api.logic.structure.DynamicArray;

import java.util.Comparator;

/**
 * Merge Sort — algoritmo de ordenamiento estable, divide-and-conquer.
 *
 * <p>Complejidad:
 * <ul>
 *   <li>Tiempo — Mejor, Promedio, Peor: O(n log n)</li>
 *   <li>Espacio: O(n) — requiere espacio auxiliar</li>
 *   <li>Estable: Sí</li>
 * </ul>
 *
 * <p>Opera sobre {@link DynamicArray}, no depende de ningún
 * framework externo (Spring, Firebase, HTTP).
 */
public class MergeSort {

    private MergeSort() {
        // Utility class — no instanciar
    }

    /**
     * Ordena un DynamicArray usando Merge Sort.
     *
     * @param array      el array a ordenar (se modifica in-place)
     * @param comparator el criterio de comparación
     * @param <T>        el tipo de los elementos
     */
    public static <T> void sort(DynamicArray<T> array, Comparator<T> comparator) {
        if (array == null || array.size() <= 1) {
            return;
        }
        mergeSort(array, 0, array.size() - 1, comparator);
    }

    /**
     * Implementación recursiva de Merge Sort.
     *
     * @param array el array a ordenar
     * @param left  índice izquierdo (inclusive)
     * @param right índice derecho (inclusive)
     * @param comparator el criterio de comparación
     */
    private static <T> void mergeSort(DynamicArray<T> array, int left, int right, Comparator<T> comparator) {
        if (left >= right) {
            return;
        }

        int mid = left + (right - left) / 2;

        mergeSort(array, left, mid, comparator);
        mergeSort(array, mid + 1, right, comparator);

        merge(array, left, mid, right, comparator);
    }

    /**
     * Mezcla dos subpartes ordenadas del array.
     *
     * @param array el array original
     * @param left  inicio de la primera mitad
     * @param mid   fin de la primera mitad
     * @param right fin de la segunda mitad
     * @param comparator el criterio de comparación
     */
    @SuppressWarnings("unchecked")
    private static <T> void merge(DynamicArray<T> array, int left, int mid, int right, Comparator<T> comparator) {
        int leftSize = mid - left + 1;
        int rightSize = right - mid;

        // Arrays temporales
        Object[] leftArr = new Object[leftSize];
        Object[] rightArr = new Object[rightSize];

        for (int i = 0; i < leftSize; i++) {
            leftArr[i] = array.get(left + i);
        }
        for (int j = 0; j < rightSize; j++) {
            rightArr[j] = array.get(mid + 1 + j);
        }

        int i = 0, j = 0, k = left;

        while (i < leftSize && j < rightSize) {
            // <= 0 para mantener estabilidad
            if (comparator.compare((T) leftArr[i], (T) rightArr[j]) <= 0) {
                array.set(k++, (T) leftArr[i++]);
            } else {
                array.set(k++, (T) rightArr[j++]);
            }
        }

        while (i < leftSize) {
            array.set(k++, (T) leftArr[i++]);
        }

        while (j < rightSize) {
            array.set(k++, (T) rightArr[j++]);
        }
    }
}
