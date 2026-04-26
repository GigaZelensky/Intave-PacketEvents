# Intave Development Fork

This repository is an attributed development fork of
[Intave](https://github.com/intave/intave). It exists for testing, compatibility work,
local runtime fixes, and preparing focused changes that may be contributed upstream
where practical.

This fork is not affiliated with, endorsed by, or maintained by the Intave project,
the Intave contributors, or intave.ac. It is not a rebrand, commercial
redistribution, hosted service, separately marketed anticheat product, or substitute
product for official Intave. If you want the official project, use
[github.com/intave/intave](https://github.com/intave/intave).

All Intave names, branding, assets, source code, and copyrights remain with their
respective owners. This repository keeps the original license and attribution intact.

Required Notice: Copyright Intave (http://intave.ac)

## Purpose

This fork is maintained as a development and testing branch. It currently focuses on
PacketEvents-based runtime behavior, cross-version compatibility, and bug fixes found
through local testing.

Changes that are small, isolated, and useful to the upstream project can be split into
focused pull requests. Branch-specific experiments or larger architectural work may
remain in this fork until they are suitable for upstream review.

## About Intave

Intave is an anticheat plugin for Minecraft servers that has been in development
since 2016. After shutting down in mid-2025, the original project was made
source-available to the community.

Unlike traditional module-based anticheats, Intave simulates player movement,
client-side entity state, and block data to detect combat, movement, and interaction
manipulations. Intave also includes heuristic checks for aimbot, auto-clicker, timer,
placement, block breaking, inventory, and other behavior that cannot be detected by
simulation alone.

For official project information, documentation, and support, see:

- Upstream repository: https://github.com/intave/intave
- Documentation: https://docs.intave.ac/mechanics/checks-01-overview.html
- Discord: https://intave.ac/go/discord

## Implementation Notes

This branch uses PacketEvents for packet handling. PacketEvents must be installed as
a normal server plugin at runtime and is intentionally not shaded into the Intave jar.
ViaVersion is optional and is used only for cross-version protocol support when
present.

## Development

### Setup

1. Clone the repository.
2. Open the project as a Gradle project and let the IDE index it.
3. Install PacketEvents on any test server used to run this branch.

### Testing

Choose one of the `intave/run_X.X.X` Gradle tasks corresponding to the Minecraft
server version you want to test. Intave is then automatically installed on that
server. Make sure PacketEvents is also present in the server's `plugins` directory
before starting the server.

Running the plugin directly in the IDE enables breakpoints and hotswapping. The
upstream project recommends the
[Single Hotswap](https://plugins.jetbrains.com/plugin/14832-single-hotswap) IntelliJ
plugin for efficient hotswapping of method bodies.

## Contributing Upstream

This fork is not the official contribution channel. When a fix is appropriate for
the upstream project, it should be submitted as a focused pull request against
[intave/intave](https://github.com/intave/intave), following the upstream
contribution guidelines.

Useful upstream references:

- [Contributing guidelines](docs/CONTRIBUTING.md)
- [Project structure](docs/STRUCTURE.md)
- [Cheatsheet](docs/CHEATSHEET.md)
- [Block system overview](docs/BLOCK_SYSTEM.md)

## License

This repository remains under the same license as upstream Intave:
[PolyForm Perimeter License 1.0.0](LICENSE.md).

The official PolyForm Perimeter License text is available at
https://polyformproject.org/licenses/perimeter/1.0.0.

The license permits use, distribution, and changes for permitted purposes, while
excluding competitive use. In particular, the license defines competition around
using the software to market a product as a substitute for the functionality or
value of the software.

This fork is intended to be an attributed development fork, not a marketed substitute
product. Do not use this repository to rebrand Intave, remove attribution, sell or
commercially redistribute Intave, or present this fork as an official or independent
replacement product.

Third-party libraries remain under their respective licenses and may not be covered
by the PolyForm Perimeter License.
