#include "absl/synchronization/mutex.h"
#include <benchmark/benchmark.h>

// Shared resource for benchmarking
static int shared_data = 0;
static absl::Mutex mu;

/**
 * Benchmark for absl::Mutex (Exclusive Lock for Read)
 * Simulates a small read using an exclusive lock.
 */
static void BM_AbslMutex_Read_Exclusive(benchmark::State &state) {
  for (auto _ : state) {
    absl::MutexLock lock(&mu);
    int val = shared_data;
    benchmark::DoNotOptimize(val);
  }
}
BENCHMARK(BM_AbslMutex_Read_Exclusive)
    ->Threads(2)
    ->Threads(6)
    ->Threads(12)
    ->Threads(32)
    ->Threads(64);

/**
 * Benchmark for absl::Mutex (Shared/Reader Lock for Read)
 * Simulates a small read using a reader lock.
 */
static void BM_AbslMutex_Read_Shared(benchmark::State &state) {
  for (auto _ : state) {
    absl::ReaderMutexLock lock(&mu);
    int val = shared_data;
    benchmark::DoNotOptimize(val);
  }
}
BENCHMARK(BM_AbslMutex_Read_Shared)
    ->Threads(2)
    ->Threads(6)
    ->Threads(12)
    ->Threads(32)
    ->Threads(64);

// Run the benchmark
BENCHMARK_MAIN();
