package at.hannibal2.skyhanni.discord

import java.sql.Connection
import java.sql.DriverManager

data class Tag(val keyword: String, var response: String, var uses: Int)

object Database {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:bot.db")
    private val tags = mutableMapOf<String, Tag>()
    private val linkedForumPosts = mutableMapOf<String, Int>() // key = channel id, value = pr number

    init {
        val statement = connection.createStatement()
        ensureCountColumnExists()
        statement.execute(buildString {
            append("CREATE TABLE IF NOT EXISTS keywords (")
            append("id INTEGER PRIMARY KEY AUTOINCREMENT, ")
            append("keyword TEXT UNIQUE, ")
            append("response TEXT, ")
            append("count INTEGER DEFAULT 0)")
        })

        statement.execute(buildString {
            append("CREATE TABLE IF NOT EXISTS linkedposts (")
            append("id INTEGER PRIMARY KEY AUTOINCREMENT, ")
            append("channel_id STRING UNIQUE, ")
            append("pullrequest_id INTEGER UNIQUE)")
        })

        loadTagCache()
        loadLinkCache()
    }

    private fun loadTagCache() {
        val statement = connection.prepareStatement("SELECT keyword, response, count FROM keywords")
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val key = resultSet.getString("keyword").lowercase()
            val response = resultSet.getString("response")
            val count = resultSet.getInt("count")
            tags[key] = Tag(key, response, count)
        }
        resultSet.close()
    }

    private fun loadLinkCache() {
        val statement = connection.prepareStatement("SELECT channel_id, pullrequest_id FROM linkedposts")
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val channelId = resultSet.getString("channel_id")
            val pr = resultSet.getInt("pullrequest_id")
            linkedForumPosts[channelId] = pr
        }
        resultSet.close()
    }

    private fun ensureCountColumnExists() {
        val statement = connection.prepareStatement("PRAGMA table_info(keywords)")
        val resultSet = statement.executeQuery()
        var countExists = false
        while (resultSet.next()) {
            if (resultSet.getString("name") == "count") {
                countExists = true
                break
            }
        }
        resultSet.close()
        if (!countExists) {
            val statement1 = connection.createStatement()
            statement1.execute("ALTER TABLE keywords ADD COLUMN count INTEGER DEFAULT 0")
        }
    }

    fun addTag(keyword: String, response: String, count: Int = 0): Boolean {
        val key = keyword.lowercase()
        val statement = connection.prepareStatement(
            "INSERT OR REPLACE INTO keywords (keyword, response, count) VALUES (?, ?, ?)"
        )
        statement.setString(1, key)
        statement.setString(2, response)
        statement.setInt(3, count)
        val updated = statement.executeUpdate() > 0
        if (updated) {
            tags[key] = Tag(key, response, count)
        }
        return updated
    }

    fun addLink(channelId: String, pr: Int): Boolean {
        val statement = connection.prepareStatement(
            "INSERT OR REPLACE INTO linkedposts (channel_id, pullrequest_id) VALUES (?, ?)"
        )
        statement.setString(1, channelId)
        statement.setInt(2, pr)
        val updated = statement.executeUpdate() > 0
        if (updated) {
            linkedForumPosts[channelId] = pr
        }
        return updated
    }

    fun getResponse(keyword: String, increment: Boolean = false): String? {
        val key = keyword.lowercase()
        val kObj = tags[key] ?: return null
        if (increment) {
            kObj.uses++
            val statement = connection.prepareStatement(
                "UPDATE keywords SET count = ? WHERE keyword = ?"
            )
            statement.setInt(1, kObj.uses)
            statement.setString(2, key)
            statement.executeUpdate()
        }
        return kObj.response
    }

    fun getChannelId(prNumber: Int): String? = linkedForumPosts.entries.find { it.value == prNumber }?.key

    fun getPullrequest(channelId: String): Int? = linkedForumPosts[channelId]

    fun deleteTag(keyword: String): Boolean {
        val key = keyword.lowercase()
        val statement = connection.prepareStatement("DELETE FROM keywords WHERE keyword = ?")
        statement.setString(1, key)
        val updated = statement.executeUpdate() > 0
        if (updated) tags.remove(key)
        return updated
    }

    fun deleteLink(channelId: String): Boolean {
        val statement = connection.prepareStatement("DELETE FROM linkedposts WHERE channel_id = ?")
        statement.setString(1, channelId)
        val updated = statement.executeUpdate() > 0
        if (updated) linkedForumPosts.remove(channelId)
        return updated
    }

    fun listTags(): List<Tag> = tags.values.toList()

    fun listLinks(): Map<String, Int> = linkedForumPosts

    fun getTagCount(keyword: String): Int? {
        return tags[keyword.lowercase()]?.uses
    }

    fun containsKeyword(keyword: String): Boolean = tags.containsKey(keyword.lowercase())

    fun isLinked(channelId: String): Boolean = linkedForumPosts.containsKey(channelId)
}
