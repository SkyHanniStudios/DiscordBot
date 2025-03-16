This is the source code for a Discord bot used by the [SkyHanni](https://github.com/hannibal002/SkyHanni) staff on
the [SkyHanni Support Server](https://discord.gg/skyhanni-997079228510117908).

## Technical Info

This Discord bot is written in [Kotlin](https://kotlinlang.org/).  
We use [JDA](https://github.com/discord-jda/JDA) to communicate with the Discord Bot API.  
We use [Gson](https://github.com/google/gson) to parse JSON responses from the APIs provided
by [Discord](https://discord.com/developers/docs/intro) and [GitHub](https://docs.github.com/en/rest).  
We use [SQLite](https://www.sqlite.org/index.html) to store tag information locally.

## Features

### Help

`!help`  
Lists all commands available to the user. The list varies for admins.

`!help <command>`  
Displays the usage, description, and parameters of a command.

`!<command> -help`  
Displays the usage, description, and parameters of a command.

### Tag

Used to display a longer text in response to a short command.

`!<tag>`  
Displays the response associated with the tag.  
Supports replying to another message, which deletes the original message and posts the response as a reply.  
Supports the `-d` parameter to delete the user’s message.

`!tagadd <tag> <response>`  
Creates a tag.

`!tagedit <tag> <response>`  
Edits an existing tag.

`!tagdelete <tag>`  
Deletes a tag.

`!taglist`  
Shows a list of all tags.

`!undo`  
Removes the last tag message sent by the user.

### Server

Displays information about a Skyblock-related Discord server.  
Excludes servers from streamers, YouTubers, and Hypixel/Skyblock guilds.  
The list is saved as JSON in this repository under `data/discord_servers.json`.  
When users send a server URL directly, they are reminded to use `!server <keyword>` instead, and unknown server links
are logged for future inclusion.  
Checks on startup of the bot via Discord API if the server id has changed, indicating a fake server that stole the
vanity url.

`!server <server>`  
Displays information about a server.  
Supports the `-d` parameter to show debug info for the server.

`!serverlist`  
Lists all servers.

`!updateservers`  
Admin command to reload the server list JSON file from GitHub.

### Pull Request

`!pr <number>`  
Displays information about the pull request with the given number, including a link to download the artifact.