package com.nexabank.api.service;

import com.nexabank.api.dto.algorithm.SearchRequest;
import com.nexabank.api.dto.algorithm.SearchResponse;
import com.nexabank.api.dto.algorithm.SortRequest;
import com.nexabank.api.dto.algorithm.SortResponse;
import com.nexabank.api.dto.transaction.TransactionResponse;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.mapper.TransactionMapper;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.logic.algorithm.BinarySearch;
import com.nexabank.api.logic.algorithm.MergeSort;
import com.nexabank.api.logic.algorithm.QuickSort;
import com.nexabank.api.logic.structure.DynamicArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AlgorithmService {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public AlgorithmService(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    public SortResponse executeSort(SortRequest request, String accountId, String userId) {
        List<Transaction> sourceList = getTransactions(accountId, userId);
        if (sourceList.isEmpty()) {
            return SortResponse.builder()
                    .algorithm(request.getAlgorithm())
                    .field(request.getField())
                    .order(request.getOrder())
                    .inputSize(0)
                    .executionTimeNanos(0)
                    .data(List.of())
                    .build();
        }

        // 1. Cargar datos en la estructura de procesamiento de nexabank-core: DynamicArray
        DynamicArray<Transaction> dynamicArray = new DynamicArray<>(sourceList.size());
        for (Transaction tx : sourceList) {
            dynamicArray.add(tx);
        }

        // 2. Determinar comparador según campo y orden
        Comparator<Transaction> comparator = getComparator(request.getField(), "DESC".equalsIgnoreCase(request.getOrder()));

        // 3. Medir tiempo de ejecución del algoritmo
        long startTime = System.nanoTime();
        String algo = request.getAlgorithm().toUpperCase();

        if ("MERGE_SORT".equals(algo)) {
            MergeSort.sort(dynamicArray, comparator);
        } else if ("QUICK_SORT".equals(algo)) {
            QuickSort.sort(dynamicArray, comparator);
        } else {
            throw new InvalidTransactionException("Unsupported sort algorithm: " + request.getAlgorithm() + ". Use MERGE_SORT or QUICK_SORT.");
        }
        long durationNanos = System.nanoTime() - startTime;

        // 4. Transformar resultado
        List<TransactionResponse> resultData = new ArrayList<>(dynamicArray.size());
        for (Transaction tx : dynamicArray) {
            resultData.add(transactionMapper.toResponse(tx));
        }

        log.info("Executed {} on {} items in {} ns", algo, dynamicArray.size(), durationNanos);

        return SortResponse.builder()
                .algorithm(algo)
                .field(request.getField())
                .order(request.getOrder() != null ? request.getOrder().toUpperCase() : "ASC")
                .inputSize(dynamicArray.size())
                .executionTimeNanos(durationNanos)
                .data(resultData)
                .build();
    }

    public SearchResponse executeSearch(SearchRequest request, String accountId, String userId) {
        List<Transaction> sourceList = getTransactions(accountId, userId);
        if (sourceList.isEmpty()) {
            return SearchResponse.builder()
                    .algorithm(request.getAlgorithm())
                    .target(request.getValue())
                    .found(false)
                    .position(-1)
                    .executionTimeNanos(0)
                    .build();
        }

        // Cargar en DynamicArray y ordenar primero por el campo de búsqueda (pre-requisito de BinarySearch)
        DynamicArray<Transaction> dynamicArray = new DynamicArray<>(sourceList.size());
        for (Transaction tx : sourceList) {
            dynamicArray.add(tx);
        }

        Comparator<Transaction> comparator = getComparator(request.getField(), false);
        QuickSort.sort(dynamicArray, comparator);

        // Medir tiempo de búsqueda binaria pura
        long startTime = System.nanoTime();
        BinarySearch.SearchResult<Transaction> result;

        String field = request.getField().toLowerCase();
        if ("id".equals(field)) {
            result = BinarySearch.searchByKey(
                    dynamicArray,
                    request.getValue(),
                    Transaction::getId,
                    Comparator.naturalOrder()
            );
        } else if ("amount".equals(field)) {
            BigDecimal targetAmount = new BigDecimal(request.getValue());
            result = BinarySearch.searchByKey(
                    dynamicArray,
                    targetAmount,
                    Transaction::getAmount,
                    Comparator.naturalOrder()
            );
        } else {
            result = BinarySearch.searchByKey(
                    dynamicArray,
                    request.getValue(),
                    tx -> String.valueOf(tx.getId()),
                    Comparator.naturalOrder()
            );
        }
        long durationNanos = System.nanoTime() - startTime;

        return SearchResponse.builder()
                .algorithm("BINARY_SEARCH")
                .target(request.getValue())
                .found(result.isFound())
                .position(result.getPosition())
                .executionTimeNanos(durationNanos)
                .build();
    }

    private List<Transaction> getTransactions(String accountId, String userId) {
        if (accountId != null && !accountId.isBlank()) {
            return transactionRepository.findByAccountId(accountId);
        }
        if (userId != null && !userId.isBlank()) {
            return transactionRepository.findByUserId(userId);
        }
        return List.of();
    }

    private Comparator<Transaction> getComparator(String field, boolean descending) {
        Comparator<Transaction> comparator;
        String f = field != null ? field.toLowerCase() : "id";

        if ("amount".equals(f)) {
            comparator = Comparator.comparing(Transaction::getAmount, Comparator.nullsLast(BigDecimal::compareTo));
        } else if ("createdat".equals(f) || "timestamp".equals(f)) {
            comparator = Comparator.comparing(Transaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(Transaction::getId, Comparator.nullsLast(String::compareTo));
        }

        return descending ? comparator.reversed() : comparator;
    }
}
