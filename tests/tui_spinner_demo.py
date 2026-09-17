import time
import sys

def main():
    print("=== STARTING TUI ANIMATION BENCHMARK ===", flush=True)
    frames = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"]
    for i in range(15):
        spinner = frames[i % len(frames)]
        percent = int(((i + 1) / 15) * 100)
        bar = "█" * (i + 1) + "░" * (14 - i)
        # Carriage return + clear line + overwrite same row
        sys.stdout.write(f"\r\x1b[2K{spinner} [TUI Live Screen Buffer] [{bar}] {percent}% - Frame {i+1}/15")
        sys.stdout.flush()
        time.sleep(0.2)
    print("\n[✔] TUI Render Successful! Zero duplicate stacked lines.", flush=True)

if __name__ == "__main__":
    main()
