Alloy
=====

Loads unmodified Forge 1.8.9 mods on Lunar Client 1.8.9 (Windows), next to Weave mods.
Early version: not every mod works, see "Limits".

Install
-------
1. Extract the whole zip anywhere.
2. Double-click install.cmd. It copies the agent into your .alloy folder and shows a line
   starting with -javaagent.
3. In the Lunar Client launcher: gear icon of the launch box -> Profile Settings -> turn on
   advanced mode -> JVM Arguments -> turn on "Override Global Setting", paste that line and save.
4. Put your Forge 1.8.9 mod jars in the "mods" folder shown by install.cmd.
5. Launch 1.8.9.

The folder is C:\Users\<you>\.alloy. If your Windows user name has a space in it, install.cmd uses
C:\.alloy instead, because the launcher cannot take a path with a space.

On its first launch Alloy downloads the official Forge 1.8.9 jar (about 4 MB) from
maven.minecraftforge.net. Forge and Minecraft are not part of this download.

Weave
-----
Keep only Alloy's -javaagent line. Alloy starts Weave itself when it finds the Weave agent in
.weave\agents (in your user folder or in C:\.weave) and a Weave mod for 1.8.9.
Never put Forge mods in Weave's mods folder.

If something does not work
--------------------------
Open logs\latest.log in the .alloy folder. For each mod it says what the mod needs that Alloy does
not provide yet.

Limits
------
- Works: mods built on Forge events, key bindings, client commands, HUD drawing, and mods that
  use Mixin.
- A mixin aimed at code Lunar Client or OptiFine already rewrote may be skipped (it is logged).
- Not supported: coremods and tweakers, mods adding blocks or items, Forge network channels.
- Alloy changes nothing a server can see and does not hide itself. Check the rules of the servers
  you play on before using a mod there.

Uninstall
---------
Remove the -javaagent line from the launcher and delete the .alloy folder.

Source and license
------------------
Source code: https://github.com/EthnnMC/Alloy
Alloy is free software under the GNU General Public License v3.0 (LICENSE.txt), without warranty.
Third-party components are listed in THIRD-PARTY.txt.
