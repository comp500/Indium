# Indium
Indium is an addon for the rendering optimization mod [Sodium](https://modrinth.com/mod/sodium), providing support for the Fabric Rendering API. The Fabric Rendering API is required for many mods that use advanced rendering effects, and is currently not supported directly by Sodium. Indium is based on the reference implementation Indigo, which is part of Fabric API with source available [here](https://github.com/FabricMC/fabric/tree/1.17/fabric-renderer-indigo) (licensed under Apache 2.0).

# Frequently Asked Questions
## What mods require Indium?
Any mod that uses the Fabric Rendering API will require Indium when used with Sodium. These include: Campanion, Bits and Chisels, LambdaBetterGrass, Continuity, Packages, and many more. Some of these mods may function without an implementation of the Fabric Rendering API, but have broken textures and models.

## Does Indium affect performance?
Indium's impact on performance should be negligible. However, mods that use the Fabric Rendering API may impact performance themselves. Indium will not provide a performance benefit over using only Sodium.

## Is Indium a replacement for Sodium?
No, Indium is an addon mod for Sodium. You must use both mods together to get Fabric Rendering API support with Sodium's rendering optimizations.

## Do I need Indium if I don't use Sodium?
No, Indigo (not In*dium*) is provided as part of Fabric API as the reference implementation of the Fabric Rendering API. Indigo disables itself when Sodium is installed.

## Will it be merged into Sodium?
Fabric Rendering API support is not currently a priority for upstream Sodium, and will not be explored until Sodium is in a more stable state.

## Which versions of Sodium are supported?
Sodium 0.2.0 or newer is required for all versions of Indium. I support the latest stable versions of Sodium for 1.18 and 1.19. Iris may not always be compatible with the latest release or build of Indium - please ask the Iris support channels if you have issues.

## Where do I download Indium?
Releases of Indium are available on [Modrinth](https://modrinth.com/mod/indium) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/indium), as well as [GitHub Releases](https://github.com/comp500/Indium/releases).
