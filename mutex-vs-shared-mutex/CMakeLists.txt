cmake_minimum_required(VERSION 3.14)
project(mutex_benchmark LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Fetch Google Benchmark
FetchContent_Declare(
  google_benchmark
  GIT_REPOSITORY https://github.com/google/benchmark.git
  GIT_TAG        v1.8.3
)

FetchContent_MakeAvailable(google_benchmark)

# Executable targeting our main file
add_executable(mutex_benchmark main.cpp)

# Link to Google Benchmark
target_link_libraries(mutex_benchmark PRIVATE benchmark::benchmark)

# Disable Google Test if Benchmark tries to include it
set(BENCHMARK_ENABLE_GTEST OFF CACHE BOOL "Disable Google Test" FORCE)
