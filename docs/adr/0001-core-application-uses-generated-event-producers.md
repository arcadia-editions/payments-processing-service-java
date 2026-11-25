# 0001. core.application calls the generated AsyncAPI event producers

Status: accepted (2026-11-25)

`zenwave-scripts.zw` generates the AsyncAPI SDK (producers in `asyncapi.provider`, Avro models in `events.avro`)
outside the hexagon, and the application services publish events through it directly. `ArchitectureTest` allows
`CoreApplication` to access an `EventProducerSDK` layer (`..asyncapi..`, `..events.avro..`) instead of adding an
outbound port and adapter per event: this is a demo, and the extra indirection would add code with no gain.
