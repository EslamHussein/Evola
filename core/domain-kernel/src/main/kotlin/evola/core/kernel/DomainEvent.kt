package evola.core.kernel

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
