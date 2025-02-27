package at.hannibal2.skyhanni.discord

import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Database as ExposedDatabase

data class Server(
    val id: Int? = null,
    val keyword: String,
    val displayName: String,
    val inviteLink: String,
    val description: String
)

object Keywords : Table("keywords") {
    val id = integer("id").autoIncrement()
    val keyword = text("keyword").uniqueIndex()
    val response = text("response")
    override val primaryKey = PrimaryKey(id, name = "PK_Keywords_ID")
}

object Servers : Table("servers") {
    val id = integer("id").autoIncrement()
    val keyword = text("keyword").uniqueIndex()
    val displayName = text("display_name")
    val inviteLink = text("inviteLink").nullable()
    val description = text("description")
    override val primaryKey = PrimaryKey(id, name = "PK_Servers_ID")
}

object ServerAliases : Table("server_aliases") {
    val alias = text("alias").uniqueIndex()
    val serverKeyword = text("server_keyword")
    override val primaryKey = PrimaryKey(alias, name = "PK_ServerAliases_alias")
}

object Database {
    private val keywordCache = mutableMapOf<String, String>()
    private val serverCache = mutableMapOf<String, Server>()
    private val aliasCache = mutableMapOf<String, MutableList<String>>()

    init {
        ExposedDatabase.connect("jdbc:sqlite:bot.db", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(Keywords, Servers, ServerAliases)
            loadKeywordCache()
            loadServerCache()
            loadAliasCache()
        }
    }

    private fun loadKeywordCache() = transaction {
        Keywords.selectAll().forEach { row ->
            keywordCache[row[Keywords.keyword].lowercase()] = row[Keywords.response]
        }
    }

    private fun loadServerCache() = transaction {
        Servers.selectAll().forEach { row ->
            val server = Server(
                id = row[Servers.id],
                keyword = row[Servers.keyword],
                displayName = row[Servers.displayName],
                inviteLink = row[Servers.inviteLink] ?: "",
                description = row[Servers.description]
            )
            serverCache[server.keyword] = server
        }
    }

    private fun loadAliasCache() = transaction {
        ServerAliases.selectAll().forEach { row ->
            val alias = row[ServerAliases.alias]
            val canonical = row[ServerAliases.serverKeyword]
            serverCache[canonical]?.let { server ->
                serverCache[alias] = server
                aliasCache.getOrPut(canonical) { mutableListOf() }.add(alias)
            }
        }
    }

    fun addKeyword(keyword: String, response: String): Boolean = transaction {
        val insertStatement = Keywords.insert {
            it[Keywords.keyword] = keyword.lowercase()
            it[Keywords.response] = response
        }
        val updated = (insertStatement.resultedValues?.size ?: 0) > 0
        if (updated) keywordCache[keyword.lowercase()] = response
        updated
    }

    fun getResponse(keyword: String): String? = keywordCache[keyword.lowercase()]

    fun deleteKeyword(keyword: String): Boolean = transaction {
        val updated = Keywords.deleteWhere { Keywords.keyword eq keyword.lowercase() } > 0
        if (updated) keywordCache.remove(keyword.lowercase())
        updated
    }

    fun listKeywords(): List<String> = keywordCache.keys.toList()

    fun addServer(server: Server): Boolean = transaction {
        var updated = false
        insertOrUpdate(
            table = Servers,
            condition = { Servers.keyword eq server.keyword },
            update = {
                it[displayName] = server.displayName
                it[inviteLink] = server.inviteLink.ifEmpty { null }
                it[description] = server.description
                updated = true
            },
            insert = {
                it[keyword] = server.keyword
                it[displayName] = server.displayName
                it[inviteLink] = server.inviteLink.ifEmpty { null }
                it[description] = server.description
                updated = true
            }
        )

        // Update cache if the record was modified
        if (updated) {
            serverCache.entries
                .filter { it.value.keyword == server.keyword }
                .forEach { serverCache[it.key] = server }
        }

        updated
    }

    /**
     * Inserts a new row if the record does not exist, otherwise updates it.
     *
     * @param table The Exposed Table to operate on.
     * @param condition The condition to check if the record exists (e.g., Users.id eq 1).
     * @param update Block to apply when updating an existing record.
     * @param insert Block to apply when inserting a new record.
     */
    fun <T : Table> insertOrUpdate(
        table: T,
        condition: SqlExpressionBuilder.() -> Op<Boolean>,
        update: T.(UpdateStatement) -> Unit,
        insert: T.(InsertStatement<Number>) -> Unit
    ) {
        transaction {
            // Check if the row exists
            val exists = table.select { condition() }.limit(1).count() > 0

            if (exists) {
                // If it exists, update it
                table.update({ condition() }) {
                    table.update(it)
                }
            } else {
                // If it doesn't exist, insert a new row
                table.insert {
                    table.insert(it)
                }
            }
        }
    }


    fun getServer(keyword: String): Server? = serverCache[keyword]

    fun deleteServer(keyword: String): Boolean = transaction {
        val updated = Servers.deleteWhere { Servers.keyword eq keyword } > 0
        if (updated) {
            serverCache.entries.removeIf { it.value.keyword == keyword }
            aliasCache.remove(keyword)
            ServerAliases.deleteWhere { ServerAliases.serverKeyword eq keyword }
        }
        updated
    }

    fun listServers(): List<Server> = serverCache.values.toList()

    fun addServerAlias(serverKeyword: String, alias: String): Boolean = transaction {
        val server = getServer(serverKeyword) ?: return@transaction false
        val insertStatement = ServerAliases.insert {
            it[ServerAliases.alias] = alias
            it[ServerAliases.serverKeyword] = serverKeyword
        }
        val updated = (insertStatement.resultedValues?.size ?: 0) > 0
        if (updated) {
            serverCache[alias] = server
            aliasCache.getOrPut(serverKeyword) { mutableListOf() }.add(alias)
        }
        updated
    }

    fun deleteServerAlias(serverKeyword: String, alias: String): Boolean = transaction {
        val updated = ServerAliases.deleteWhere {
            (ServerAliases.serverKeyword eq serverKeyword) and (ServerAliases.alias eq alias)
        } > 0
        if (updated) {
            aliasCache[serverKeyword]?.remove(alias)
            serverCache.remove(alias)
        }
        updated
    }

    fun getServerAliases(canonical: String): List<String> = aliasCache[canonical]?.toList() ?: emptyList()
}
