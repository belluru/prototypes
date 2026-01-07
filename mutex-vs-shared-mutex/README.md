# Is Abseil Mutex Faster for Small Critical Sections?

In high-performance C++ development, we often look beyond the standard library for optimized synchronization primitives. This repository compares the performance of `absl::Mutex` (from the [Abseil library](https://abseil.io/)) in both exclusive and shared modes.

On paper, the "Reader-Writer" pattern sounds like an easy win—why block multiple readers if they aren't changing data? However, in performance engineering, the "obvious" choice isn't always the fastest. If your critical section (the code inside the lock) is very small, the overhead of managing shared access might actually be slowing you down.

## The Contenders (via `absl::Mutex`)

### 1. `absl::MutexLock` (Exclusive)
Optimized for speed and minimal overhead under low to moderate contention.
- **Bookkeeping**: Minimal.
- **Best for**: Short logic, simple updates, or scenarios where writes are frequent.

### 2. `absl::ReaderMutexLock` (Shared)
Allows multiple readers to access data simultaneously but gives writers exclusive access.
- **Bookkeeping**: Higher than exclusive locks as it must track the number of active readers.
- **Best for**: Long read operations (like complex searches or I/O) where the cost of managing the lock is smaller than the time saved by parallel execution.

## The Bookkeeping Penalty

When you use a lock, you aren't just paying for the time the thread spends inside the lock; you are paying the "Lock Tax"—the overhead of acquiring and releasing it.

An `absl::Mutex` in shared mode has to perform complex atomic math to track how many readers are inside. For a "very small" operation (like reading a single integer or updating a small map), the time spent updating the internal "reader count" and checking for waiting writers can actually take longer than the actual work you're trying to protect.

## 🚀 Running the Benchmark

We use Docker to ensure a consistent build environment with all necessary dependencies (**CMake**, **Abseil-cpp**, **Google Benchmark**, and a C++17 compiler).

### Prerequisites
- [Docker](https://www.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/)

### Execution
Simply run:
```bash
docker compose up --build
```

### What to Look For
In the output, you will see a comparison of:
1. `BM_AbslMutex_Read_Exclusive`: **Exclusive** access using `absl::MutexLock` to perform a single integer read.
2. `BM_AbslMutex_Read_Shared`: **Shared** access using `absl::ReaderMutexLock` to perform the same integer read.

Compare the `ns/op` (nanoseconds per operation) as the number of threads increases (2, 6, 12, 32, 64). You will often find that for such small operations, the **"Lock Tax"** of the shared code (tracking reader counts) makes it slower than simple exclusive access, even when all threads are only reading.

Usually, a simple benchmark regarding the time and CPU cycles spent will help you make an informed decision.
