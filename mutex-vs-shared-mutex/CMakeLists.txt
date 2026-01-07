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

# Fetch Abseil
FetchContent_Declare(
  absl
  GIT_REPOSITORY https://github.com/abseil/abseil-cpp.git
  GIT_TAG        20230802.1 # A recent stable version
)

# Disable Google Test and Benchmark's own tests to avoid dependency issues
set(BENCHMARK_ENABLE_GTEST OFF CACHE BOOL "Disable Google Test" FORCE)
set(BENCHMARK_ENABLE_TESTING OFF CACHE BOOL "Disable Benchmark Testing" FORCE)
set(BENCHMARK_ENABLE_INSTALL OFF CACHE BOOL "Disable Benchmark Install" FORCE)

# Abseil options
set(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "Use same C++ standard as project" FORCE)

FetchContent_MakeAvailable(google_benchmark absl)

# Executable targeting our main file
add_executable(mutex_benchmark main.cpp)

# Link to Google Benchmark and Abseil
target_link_libraries(mutex_benchmark PRIVATE 
    benchmark::benchmark
    absl::synchronization
)
