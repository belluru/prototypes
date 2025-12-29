import subprocess
import re
import time
import os

def run_benchmark(profile, consumers, work_duration):
    print(f"Running benchmark for {profile} with {consumers} consumers and {work_duration}ms work...")
    
    # Set environment variables for docker compose
    env = os.environ.copy()
    env["NUM_CONSUMERS"] = str(consumers)
    env["WORK_DURATION"] = str(work_duration)
    
    # Down first to ensure clean state
    subprocess.run(["docker", "compose", "--profile", profile, "down"], env=env, capture_output=True)
    
    # Up and wait for completion
    subprocess.run(["docker", "compose", "--profile", profile, "up", "--build", "-d"], env=env, capture_output=True)
    
    # Follow logs until "Application finished"
    service_name = f"app-{profile}"
    start_time = time.time()
    total_time_ms = 0
    
    while True:
        result = subprocess.run(["docker", "compose", "logs", service_name], env=env, capture_output=True, text=True)
        if "TOTAL_TIME_MS:" in result.stdout:
            match = re.search(r"TOTAL_TIME_MS: (\d+)", result.stdout)
            if match:
                total_time_ms = int(match.group(1))
                break
        
        if time.time() - start_time > 60: # 60s timeout
            print(f"Timeout waiting for {profile}")
            break
        time.sleep(1)
    
    subprocess.run(["docker", "compose", "--profile", profile, "down"], env=env, capture_output=True)
    return total_time_ms

def main():
    consumer_counts = [5, 10, 100, 200]
    work_duration = 50 # Short duration for faster benchmarks
    
    print(f"{'Consumers':<10} | {'Lua (ms)':<10} | {'Redlock (ms)':<15} | {'Diff (%)':<10}")
    print("-" * 55)
    
    for count in consumer_counts:
        lua_time = run_benchmark("lua", count, work_duration)
        red_time = run_benchmark("redlock", count, work_duration)
        
        diff = ((red_time - lua_time) / lua_time * 100) if lua_time > 0 else 0
        print(f"{count:<10} | {lua_time:<10} | {red_time:<15} | {diff:>8.2f}%")

if __name__ == "__main__":
    main()
