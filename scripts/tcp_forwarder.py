import asyncio
import os
import sys

LOCAL_PORT = int(os.getenv("LOCAL_PORT", "8888"))
REMOTE_HOST = os.getenv("REMOTE_HOST", "127.0.0.1")
REMOTE_PORT = int(os.getenv("REMOTE_PORT", "8888"))

async def pipe(reader, writer):
    try:
        while not reader.at_eof():
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except Exception:
        pass
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass

async def handle_client(local_reader, local_writer):
    try:
        remote_reader, remote_writer = await asyncio.open_connection(REMOTE_HOST, REMOTE_PORT)
    except Exception as e:
        local_writer.close()
        return

    await asyncio.gather(
        pipe(local_reader, remote_writer),
        pipe(remote_reader, local_writer),
        return_exceptions=True
    )

async def main():
    server = await asyncio.start_server(handle_client, "0.0.0.0", LOCAL_PORT)
    print(f"Forwarding 0.0.0.0:{LOCAL_PORT} -> {REMOTE_HOST}:{REMOTE_PORT}", flush=True)
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    asyncio.run(main())
