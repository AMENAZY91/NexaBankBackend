package com.nexabank.api.logic.algorithm;

import com.nexabank.api.logic.structure.DynamicArray;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quick Sort — algoritmo de ordenamiento in-place, divide-and-conquer.
 *
 * <p>Complejidad:
 * <ul>
 *   <li>Tiempo — Mejor y Promedio: O(n log n)</li>
 *   <li>Tiempo — Peor caso: O(n²) (mitigado con pivote aleatorio)</li>
 *   <li>Espacio: O(log n) — recursión</li>
 *   <li>Estable: No</li>
 *   <li>In-place: Sí</li>
 * </ul>
 *
 * <p>Usa selección de pivote aleatorio para mitigar el peor caso.
 * Opera sobre {@link DynamicArray}, no depende de ningún framework externo.
 */
public class QuickSort {

    private QuickSort() {
        // Utility class — no instanciar
    }

    /**
     * Ordena un DynamicArray usando Quick Sort con pivote aleatorio.
     *
     * @param array      el array a ordenar (in-place)
     * @param comparator el criterio de comparación
     * @param <T>        el tipo de los elementos
     */
    public static <T> void sort(DynamicArray<T> array, Comparator<T> comparator) {
        if (array == null || array.size() <= 1) {
            return;
        }
        quickSort(array, 0, array.size() - 1, comparator);
    }

    /**
     * Implementación recursiva de Quick Sort.
     *
     * @param array el array a ordenar
     * @param low   índice inferior (inclusive)
     * @param high  índice superior (inclusive)
     * @param comparator el criterio de comparación
     */
    private static <T> void quickSort(DynamicArray<T> array, int low, int high, Comparator<T> comparator) {
        if (low >= high) {
            return;
        }

        int pivotIndex = partition(array, low, high, comparator);
        quickSort(array, low, pivotIndex - 1, comparator);
        quickSort(array, pivotIndex + 1, high, comparator);
    }

    /**
     * Partición con pivote aleatorio (Lomuto scheme).
     *
     * <p>Selecciona un pivote aleatorio para evitar el peor caso O(n²)
     * en datos ya ordenados o parcialmente ordenados.
     *
     * @param array el array a particionar
     * @param low   límite inferior
     * @param high  límite superior
     * @param comparator el criterio de comparación
     * @return la posición final del pivote
     */
    private static <T> int partition(DynamicArray<T> array, int low, int high, Comparator<T> comparator) {
        // Pivote aleatorio — mitiga peor caso
        int randomIndex = ThreadLocalRandom.current().nextInt(low, high + 1);
        array.swap(randomIndex, high);

        T pivot = array.get(high);
        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (comparator.compare(array.get(j), pivot) <= 0) {
                i++;
                array.swap(i, j);
            }
        }

        array.swap(i + 1, high);
        return i + 1;
    }
}
