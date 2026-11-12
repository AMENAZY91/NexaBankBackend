package com.nexabank.api.logic.algorithm;

/**
 * Algoritmos recursivos.
 *
 * <p>Se incluyen:
 * <ul>
 *   <li><b>Torre de Hanói:</b> mueve n discos de una torre a otra usando una torre
 *       auxiliar, respetando la regla de no colocar un disco mayor sobre uno menor.
 *       Recurrencia: T(n) = 2T(n-1) + 1 → O(2^n). Justificación de recursividad:
 *       el problema se descompone naturalmente en subproblemas idénticos de menor
 *       tamaño (mover n-1 discos, mover el disco base, mover n-1 discos de nuevo),
 *       lo que resulta elegante y fácil de verificar, aunque no sea la más
 *       eficiente en tiempo.</li>
 *   <li><b>Factorial recursivo:</b> n! = n * (n-1)!. Caso base: 0! = 1.</li>
 *   <li><b>Fibonacci recursivo:</b> F(n) = F(n-1) + F(n-2). Casos base: F(0)=0, F(1)=1.
 *       Nota: versión naive con O(2^n), incluida con fines didácticos.</li>
 * </ul>
 *
 * <p>¿Por qué recursividad? Porque la estructura del problema (dividir en subproblemas
 * más pequeños del mismo tipo) se ajusta naturalmente a llamadas recursivas, y el
 * costo adicional de la pila de llamadas es aceptable para los tamaños de entrada
 * esperados en este módulo (demo educativa).
 */
public class RecursiveAlgorithms {

    private RecursiveAlgorithms() {
        // Utility class — no instanciar
    }

    /**
     * Resuelve la Torre de Hanói para n discos.
     *
     * @param n      número de discos
     * @param from   torre origen
     * @param to     torre destino
     * @param aux    torre auxiliar
     * @param steps  lista donde se registran los movimientos realizados
     * @return número total de movimientos (2^n - 1)
     */
    public static int hanoi(int n, String from, String to, String aux, java.util.List<String> steps) {
        if (n <= 0) return 0;
        int moves = 0;
        moves += hanoi(n - 1, from, aux, to, steps);
        steps.add("Mover disco " + n + " de " + from + " a " + to);
        moves++;
        moves += hanoi(n - 1, aux, to, from, steps);
        return moves;
    }

    /**
     * Calcula el factorial de n de forma recursiva.
     *
     * @param n número no negativo
     * @return n!
     * @throws IllegalArgumentException si n < 0
     * @throws ArithmeticException si el resultado desborda long
     */
    public static long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n no puede ser negativo");
        }
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    /**
     * Calcula el n-ésimo número de Fibonacci de forma recursiva (versión naive).
     *
     * @param n índice no negativo
     * @return F(n)
     * @throws IllegalArgumentException si n < 0
     */
    public static long fibonacci(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n no puede ser negativo");
        }
        if (n == 0) return 0;
        if (n == 1) return 1;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    /**
     * Suma los elementos de un arreglo de forma recursiva (divide y vencerás).
     *
     * @param arr  arreglo de enteros
     * @param left índice inicial (inclusive)
     * @param right índice final (inclusive)
     * @return suma de arr[left..right]
     */
    public static long sumArray(int[] arr, int left, int right) {
        if (arr == null || arr.length == 0 || left > right) return 0;
        if (left == right) return arr[left];
        int mid = left + (right - left) / 2;
        return sumArray(arr, left, mid) + sumArray(arr, mid + 1, right);
    }
}
