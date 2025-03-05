This is the source code for a discord bot the [SkyHanni](https://github.com/hannibal002/SkyHanni) staff uses on the [SkyHanni Support Server](https://discord.gg/skyhanni-997079228510117908).

## Technical infos
This discord bot is written in [Kotlin](https://kotlinlang.org/).
We use [JDA](https://github.com/discord-jda/JDA) to communicate with the Discord Bot API.
We use [gson](https://github.com/google/gson) to parse the JSON responses from the APIs from [Discord](https://discord.com/developers/docs/intro) and [GitHub](https://docs.github.com/en/rest).
We use [SQLite](https://www.sqlite.org/index.html) to store the information locally.

## Features

### Help

`!help`
lists all commands the user can use
This list is different for admins

`!help <command>`
shows exact usage and descripton of command and parameters for a given command

`!<command> -help`
shows exact usage and descripton of command and parameters for a given command

### Tag
used to show a longer text as response when running a small command

`!<tag>`
returns a response under the tag
Allows to reply to antoher message so that this message gets deleted and the response is a reply to the other message.
Allows parameter `-d` to delete the user message

`!tagadd <tag> <response>`
creates a tag with 

`!tagedit <tag> <response>`
edits alrady eixsitng tags

`!tagdelete <tag>`
deletes a tag

`!taglist`
shows a list of all tags

`!undo`
removes the last tag message the user has sent

### Server
shows information about another, previously defined discord server

`!server <server`
shows infos about a server
Allows parameter `-d` to show debug info for this server

`!serveradd <server> <display name> <invite url> <description>`
adds a server

`serverdelete <server>`
delets a server

`!serverlist`
lists all servers

`!serveraliasadd <server> <alias>`
adds an alias for a server

`!serveraliasremove <server> <alias>`
removes an alias for a server

### Pull Request

`!pr <number>`
lists infos about this pr number.
Includes a link to downoad the artifact