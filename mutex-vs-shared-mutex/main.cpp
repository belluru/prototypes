#include <benchmark/benchmark.h>
#include <mutex>
#include <shared_mutex>

// Shared resource for benchmarking
static int shared_data = 0;
static std::mutex m;
static std::shared_mutex sm;

/**
 * Benchmark for std::mutex (Exclusive)
 * Simulates a small increment operation.
 */
static void BM_Mutex_SmallCriticalSection(benchmark::State &state) {
  for (auto _ : state) {
    std::lock_guard<std::mutex> lock(m);
    shared_data++;
    benchmark::DoNotOptimize(shared_data);
  }
}
BENCHMARK(BM_Mutex_SmallCriticalSection)
    ->Threads(2)
    ->Threads(6)
    ->Threads(12)
    ->Threads(32)
    ->Threads(64);

/**
 * Benchmark for std::shared_mutex (Exclusive Write)
 * Simulates a small increment operation.
 */
static void BM_SharedMutex_Write_Small(benchmark::State &state) {
  for (auto _ : state) {
    std::unique_lock<std::shared_mutex> lock(sm);
    shared_data++;
    benchmark::DoNotOptimize(shared_data);
  }
}
BENCHMARK(BM_SharedMutex_Write_Small)
    ->Threads(2)
    ->Threads(6)
    ->Threads(12)
    ->Threads(32)
    ->Threads(64);

/**
 * Benchmark for std::shared_mutex (Shared Read)
 * Simulates a small read operation.
 */
static void BM_SharedMutex_Read_Small(benchmark::State &state) {
  for (auto _ : state) {
    std::shared_lock<std::shared_mutex> lock(sm);
    int val = shared_data;
    benchmark::DoNotOptimize(val);
  }
}
BENCHMARK(BM_SharedMutex_Read_Small)
    ->Threads(2)
    ->Threads(6)
    ->Threads(12)
    ->Threads(32)
    ->Threads(64);

// Run the benchmark
BENCHMARK_MAIN();
