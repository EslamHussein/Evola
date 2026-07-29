package evola.learneridentity.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Plain Exposed [Table] objects — no FK() `.references()` links across module boundaries.
 * Referential integrity is enforced at the DB level by the Flyway migration's REFERENCES
 * clauses; modeling it again here would create a compile-time dependency between bounded
 * contexts' infrastructure modules, which we deliberately avoid.
 */
internal object LearnersTable : Table("learners") {
    val id = uuid("id")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ExternalIdentitiesTable : Table("external_identities") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val channel = varchar("channel", 50)
    val externalId = varchar("external_id", 255)
    val displayName = varchar("display_name", 255).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
