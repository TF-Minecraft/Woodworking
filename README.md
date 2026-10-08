# Woodworking

> Furniture crafting at the workbench for TF-Minecraft.

Woodworking turns furniture production into a practical workshop activity. Players choose a project, contribute its materials, work it with the required tools, and finish the piece with a branding tool. Each design has its own combination of ingredients and actions.

## Features

- **Furniture collections** — browse projects grouped into themes such as Cozy, Market, Royal, Marauder, and Witch.
- **Varied projects** — create furnishings such as chairs, sofas, carpets, sideboards, clocks, and decorative pieces from the supplied catalogue.
- **Material recipes** — projects combine named woods and other materials, including fabric where the design calls for it.
- **Multiple crafts at one bench** — woodworking, metalworking, and other actions bring whittling, engraving, hammering, and sewing into the same project system.
- **Visible progress** — track deposited materials and completed tool actions through the project interface and in-world feedback.
- **Persistent workshop projects** — station progress is stored so unfinished work can survive a server restart.

## From materials to furniture

Players need to know their chosen design: the bench accepts materials and tool actions even when they do not match it. A completed attempt produces furniture only when the recipe is correct; a wrong mix ruins the project. Cancelling unfinished work returns its materials. See the project guide for the full workbench rules.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Woodworking/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn -B --no-transfer-progress clean verify` with Java 21 after installing the pinned plugin dependencies used by CI. JUnit 5, Mockito and MockBukkit exercise project definitions and progress, YAML loaders, GUI pagination, permission checks, bench events, plugin lifecycle, and persisted station recovery.

JaCoCo enforces **100% line, branch and instruction coverage** across all production classes, without exclusions. HTML/XML reports appear in `target/site/jacoco/` and are uploaded by build and release CI. Surefire test results appear in `target/surefire-reports/` and are uploaded by build CI. External plugin APIs are mocked; a live Minecraft integration run remains useful for server-specific behavior.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
