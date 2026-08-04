# Connection Safety

Load this reference for any Screen-Remote ADB, scrcpy, socket, session-runtime, controller, codec,
decoder, media-stream, reconnect, health-monitor, or foreground-service change.

## Protocol order is a hard invariant

The scrcpy server assigns socket roles by `accept()` order and has no client role handshake.

Open channels strictly and sequentially:

1. `video`
2. `audio` when enabled
3. `control`

Never use `async`, parallel jobs, races, or completion order to create or assign these channels.
Preserve `openScrcpyChannelsSequentially` or an equivalently obvious sequential construct.

## Startup and retry boundaries

- Only the first direct-ADB `video` localabstract open may be retried while waiting for the server
  socket to appear.
- Do not apply that retry pattern to forwarded TCP mode: a local accept may succeed before the
  remote open fails and consume the server's role order.
- Once the server has assigned any role, reopening a partial trio against the same server cannot
  restart its accept sequence.
- Retrying a failed trio requires a fresh server/SCID lifecycle unless current protocol evidence
  proves otherwise.
- Wait for the video dummy byte before exposing the connected set. Do not read media metadata while
  channel establishment is incomplete.

## Metadata and streams

- Preserve exact, complete reads for fixed-size headers; never assume a single read fills a header.
- Keep video and audio negotiation independent. An audio codec ID of zero can disable audio at
  runtime.
- Treat negotiated codec and dimensions as runtime facts. Do not overwrite them with saved
  preferences.
- Preserve packet boundaries, presentation timestamps, codec configuration data, and decoder
  bootstrap ordering.

## Control path

- Keep control writes off the UI caller path.
- Preserve bounded backpressure and the distinction between coalescible motion events and
  non-droppable keys, clipboard, or device-control messages.
- Do not let video throughput block control traffic.

## Lifecycle and cleanup

- Serialize connect/disconnect ownership.
- Preserve coroutine cancellation semantics while cleaning partially created sockets, forwards,
  server processes, monitors, decoders, and session state.
- Clean old resources before creating a replacement forward when port reuse could delete the new
  mapping.
- Keep failure classification and diagnostics tied to the stage that failed.
- Do not add a global reconnect path that bypasses session ownership.

## Required review questions

1. Can any coroutine establish channels concurrently or reorder them?
2. Can a retry reuse a server whose accept sequence has already advanced?
3. Can metadata be read before all required channels exist?
4. Does cancellation leak a socket, forward, process, monitor, decoder, or job?
5. Are configuration, device capability, and negotiated runtime state still distinct?
6. Can a high-frequency control event create an unbounded queue or block the caller?

## Minimum regression anchors

Run the narrowest affected tests and always include socket-order coverage when channel establishment
changes:

```bash
cd Screen-Remote
./gradlew testDebugUnitTest --tests '*ConnectionSocketOrderTest'
```

Also select relevant tests for metadata, streams, controller transport, codec fallback, decoder
recovery, or service heartbeat. Finish with the broader unit suite and debug build when
implementation changes cross stages.
