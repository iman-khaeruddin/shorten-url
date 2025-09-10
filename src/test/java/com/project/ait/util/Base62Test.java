package com.project.ait.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Base62 Utility Tests")
class Base62Test {

    private static final String VALID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Test
    @DisplayName("Should generate string with correct length")
    void encode_WithValidLength_ShouldReturnCorrectLength() {
        // Given
        int length = 5;

        // When
        String result = Base62.encode(length);

        // Then
        assertThat(result).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 8, 10, 15, 20})
    @DisplayName("Should generate strings with various lengths")
    void encode_WithVariousLengths_ShouldReturnCorrectLengths(int length) {
        // When
        String result = Base62.encode(length);

        // Then
        assertThat(result).hasSize(length);
    }

    @Test
    @DisplayName("Should generate string with only valid Base62 characters")
    void encode_ShouldContainOnlyValidCharacters() {
        // Given
        int length = 10;

        // When
        String result = Base62.encode(length);

        // Then
        for (char c : result.toCharArray()) {
            assertThat(VALID_CHARACTERS).contains(String.valueOf(c));
        }
    }

    @Test
    @DisplayName("Should generate different strings on multiple calls")
    void encode_MultipleCalls_ShouldGenerateDifferentStrings() {
        // Given
        int length = 8;
        Set<String> generatedStrings = new HashSet<>();
        int numberOfGenerations = 100;

        // When
        for (int i = 0; i < numberOfGenerations; i++) {
            generatedStrings.add(Base62.encode(length));
        }

        // Then
        // Due to randomness, we should have many different strings
        // It's extremely unlikely that all 100 generations would be identical
        assertThat(generatedStrings.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should handle length of 1")
    void encode_WithLengthOne_ShouldReturnSingleCharacter() {
        // When
        String result = Base62.encode(1);

        // Then
        assertThat(result).hasSize(1);
        assertThat(VALID_CHARACTERS).contains(result);
    }

    @Test
    @DisplayName("Should handle zero length")
    void encode_WithZeroLength_ShouldReturnEmptyString() {
        // When
        String result = Base62.encode(0);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should generate string without special characters")
    void encode_ShouldNotContainSpecialCharacters() {
        // Given
        int length = 20;
        String specialCharacters = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // When
        String result = Base62.encode(length);

        // Then
        for (char c : result.toCharArray()) {
            assertThat(specialCharacters).doesNotContain(String.valueOf(c));
        }
    }

    @Test
    @DisplayName("Should generate URL-safe characters only")
    void encode_ShouldGenerateUrlSafeCharacters() {
        // Given
        int length = 15;

        // When
        String result = Base62.encode(length);

        // Then
        // Base62 characters are URL-safe (no need for encoding)
        for (char c : result.toCharArray()) {
            assertThat(c).satisfiesAnyOf(
                    character -> assertThat(character).isBetween('0', '9'),
                    character -> assertThat(character).isBetween('a', 'z'),
                    character -> assertThat(character).isBetween('A', 'Z')
            );
        }
    }

    @Test
    @DisplayName("Should have good randomness distribution")
    void encode_ShouldHaveGoodRandomnessDistribution() {
        // Given
        int length = 1;
        int numberOfSamples = 1000;
        int[] characterCount = new int[VALID_CHARACTERS.length()];

        // When - Generate many single characters and count occurrences
        for (int i = 0; i < numberOfSamples; i++) {
            String generated = Base62.encode(length);
            char c = generated.charAt(0);
            int index = VALID_CHARACTERS.indexOf(c);
            if (index >= 0) {
                characterCount[index]++;
            }
        }

        // Then - Each character should appear at least once in 1000 samples
        // This is probabilistic, but with 1000 samples and 62 possible characters,
        // we should see a reasonably good distribution
        int charactersUsed = 0;
        for (int count : characterCount) {
            if (count > 0) {
                charactersUsed++;
            }
        }

        // With good randomness, we expect most characters to appear at least once
        assertThat(charactersUsed).isGreaterThan(40); // At least 40 out of 62 characters should appear
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent calls")
    void encode_ConcurrentCalls_ShouldBeThreadSafe() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int callsPerThread = 100;
        Set<String> allResults = new HashSet<>();
        Thread[] threads = new Thread[numberOfThreads];

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < callsPerThread; j++) {
                    String result = Base62.encode(8);
                    synchronized (allResults) {
                        allResults.add(result);
                    }
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        // All results should be valid and we should have many unique results
        assertThat(allResults).isNotEmpty();
        assertThat(allResults.size()).isGreaterThan(numberOfThreads * callsPerThread / 2);
        
        // Verify all results are valid
        for (String result : allResults) {
            assertThat(result).hasSize(8);
            for (char c : result.toCharArray()) {
                assertThat(VALID_CHARACTERS).contains(String.valueOf(c));
            }
        }
    }
}
