package com.nexabank.api.logic.algorithm;

import com.nexabank.api.logic.structure.DynamicArray;

import java.util.Comparator;

/**
 * Binary Search — búsqueda binaria genérica sobre datos ordenados.
 *
 * <p>Complejidad:
 * <ul>
 *   <li>Tiempo: O(log n)</li>
 *   <li>Espacio: O(1)</li>
 * </ul>
 *
 * <p><strong>Pre-condición:</strong> el {@link DynamicArray} debe estar ordenado
 * respecto al criterio de búsqueda. Si los datos no están ordenados,
 * el resultado es indefinido.
 *
 * <p>Opera sobre {@link DynamicArray}, no depende de ningún framework externo.
 */
public class BinarySearch {

    private BinarySearch() {
        // Utility class — no instanciar
    }

    /**
     * Resultado de una búsqueda binaria.
     *
     * @param <T> el tipo del elemento buscado
     */
    public static class SearchResult<T> {
        private final boolean found;
        private final int position;
        private final T element;

        private SearchResult(boolean found, int position, T element) {
            this.found = found;
            this.position = position;
            this.element = element;
        }

        /** @return true si el elemento fue encontrado */
        public boolean isFound() {
            return found;
        }

        /** @return la posición del elemento, o -1 si no fue encontrado */
        public int getPosition() {
            return position;
        }

        /** @return el elemento encontrado, o null si no se encontró */
        public T getElement() {
            return element;
        }

        /**
         * Crea un resultado positivo.
         */
        public static <T> SearchResult<T> found(int position, T element) {
            return new SearchResult<>(true, position, element);
        }

        /**
         * Crea un resultado negativo.
         */
        public static <T> SearchResult<T> notFound() {
            return new SearchResult<>(false, -1, null);
        }
    }

    /**
     * Busca un elemento en un DynamicArray ordenado usando búsqueda binaria.
     *
     * <p>El array DEBE estar ordenado con el mismo {@code comparator}
     * antes de llamar a este método. Si no lo está, el resultado es indefinido.
     *
     * @param array      el array ordenado donde buscar
     * @param target     el elemento a buscar
     * @param comparator el criterio de comparación (debe coincidir con el orden del array)
     * @param <T>        el tipo de los elementos
     * @return un {@link SearchResult} indicando si fue encontrado y su posición
     */
    public static <T> SearchResult<T> search(DynamicArray<T> array, T target, Comparator<T> comparator) {
        if (array == null || array.isEmpty()) {
            return SearchResult.notFound();
        }

        int low = 0;
        int high = array.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            T midElement = array.get(mid);
            int cmp = comparator.compare(midElement, target);

            if (cmp == 0) {
                return SearchResult.found(mid, midElement);
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return SearchResult.notFound();
    }

    /**
     * Busca un elemento por una propiedad extraída de cada elemento del array.
     *
     * <p>Útil para buscar transacciones por ID, monto, etc.
     *
     * @param array     el array ordenado
     * @param targetKey el valor clave a buscar
     * @param extractor función que extrae la clave de cada elemento
     * @param keyComparator comparador para las claves
     * @param <T>       el tipo de los elementos del array
     * @param <K>       el tipo de la clave de búsqueda
     * @return un {@link SearchResult}
     */
    public static <T, K> SearchResult<T> searchByKey(
            DynamicArray<T> array,
            K targetKey,
            java.util.function.Function<T, K> extractor,
            Comparator<K> keyComparator) {

        if (array == null || array.isEmpty()) {
            return SearchResult.notFound();
        }

        int low = 0;
        int high = array.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            T midElement = array.get(mid);
            K midKey = extractor.apply(midElement);
            int cmp = keyComparator.compare(midKey, targetKey);

            if (cmp == 0) {
                return SearchResult.found(mid, midElement);
            } else if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return SearchResult.notFound();
    }
}
