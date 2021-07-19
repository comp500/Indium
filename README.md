# Indium
Indium is an addon for the rendering optimisation mod [Sodium](https://modrinth.com/mod/sodium), providing support for the Fabric Rendering API. The Fabric Rendering API is required for many mods that use advanced rendering effects, and is currently not supported by Sodium directly. Indium is based upon the reference implementation Indigo, which is part of Fabric API with source available [here](https://github.com/FabricMC/fabric/tree/1.17/fabric-renderer-indigo). (licensed Apache 2.0)

# Frequently Asked Questions
## What mods require Indium?
Any mod that uses the Fabric Rendering API will require Indium when used with Sodium. These include: Campanion, Exotic Blocks, Bits and Chisels, LambdaBetterGrass,
ConnectedTexturesMod for Fabric, Packages, and many more. Some of these mods may function without an implementation of the Fabric Rendering API, but have broken textures and models.

## Does Indium affect performance?
Indium's impact on performance should be negligible, however mods that use the Fabric Rendering API could themselves impact performance. Indium will not provide a performance benefit over using only Sodium.

## Is Indium a replacement for Sodium?
No, Indium is an addon mod for Sodium - you must use both mods together to get Fabric Rendering API support with Sodium's rendering optimisations.

## Do I need Indium if I don't use Sodium?
No, Indigo is provided as part of Fabric API as the reference implementation of the Fabric Rendering API. Indigo disables itself when Sodium is installed.

## Will it be merged into Sodium?
Indium is currently unsuitable for merging into Sodium, as it duplicates significant portions of Sodium's rendering logic, which would be difficult to maintain as part of Sodium. However, work is being done to make the changes Indium does more suitable for integration into Sodium, so that it may be merged in the future. See [this page](https://github.com/comp500/Indium/issues?q=is%3Aopen+is%3Aissue+label%3Aenhancement) for some of the changes that are planned.

## Which versions of Sodium are supported?
Sodium 0.2.0 or newer is required for all versions of Indium. I support the latest stable versions of Sodium for 1.16 and 1.17, as well as the latest development builds for 1.17. For development builds, Indium must be obtained from Github Actions or compiled from source in the 1.17.x/main branch. Iris requires a custom fork of Sodium and may not always be compatible with the latest release or build of Indium - please ask the Iris support channels if you have issues.

## Where do I download Indium?
Releases of Indium are available from [Modrinth](https://modrinth.com/mod/indium), as well as [Github Releases](https://github.com/comp500/Indium/releases).

### CurseForge?
As Indium is not currently compatible with any released version of Sodium on CurseForge, it is not yet available for download from CurseForge. When Sodium 0.2.0 and newer release on CurseForge, Indium will also be released.
