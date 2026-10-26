package pt.tiago.nightcrush.leveltool

/**
 * NightCrush content tool: schema validation, generation, solving, calibration,
 * certification and asset export.
 *
 * Every command is `<command> [--key value | --flag]`; options are collected into
 * a map and the command decides which ones it needs. A `--flag` with no value
 * (or followed by another `--`) becomes the string "true".
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "help" || args[0] == "--help" || args[0] == "-h") {
        println(usage())
        return
    }
    val command = args[0]
    if (command.startsWith("-")) {
        println(usage())
        kotlin.system.exitProcess(2)
    }
    val parsed = Commands.Args(parseOptions(args.drop(1)))
    val result = try {
        dispatch(command, parsed)
    } catch (error: Exception) {
        println("$command: ${error.message}")
        1
    }
    kotlin.system.exitProcess(result)
}

private fun dispatch(command: String, args: Commands.Args): Int = when (command) {
    "validate" -> Commands.validate(args)
    "generate" -> Commands.generate(args)
    "solve" -> Commands.solve(args)
    "calibrate" -> Commands.calibrate(args)
    "certify" -> Commands.certify(args)
    "verify-certificates" -> Commands.verifyCertificates(args)
    "report" -> Commands.report(args)
    "export-assets" -> Commands.exportAssets(args)
    "daily-pool" -> Commands.dailyPool(args)
    "sync-revisions" -> Commands.syncRevisions(args)
    "stats" -> Commands.stats(args)
    "puzzles" -> Puzzles.generate(args)
    "retitle" -> Commands.retitle(args)
    "verify-puzzles" -> Puzzles.verify()
    else -> {
        println("Unknown command: $command\n")
        println(usage())
        2
    }
}

/** Turns [--key value | --flag] into the flat map the commands read. */
private fun parseOptions(tokens: List<String>): Map<String, String> {
    val values = LinkedHashMap<String, String>()
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        if (!token.startsWith("--")) {
            index++
            continue
        }
        val key = token.removePrefix("--")
        val next = tokens.getOrNull(index + 1)
        if (next != null && !next.startsWith("--")) {
            values[key] = next
            index += 2
        } else {
            values[key] = "true"
            index += 1
        }
    }
    return values
}

private fun usage(): String = """
    NightCrush level tool

    Usage: ./gradlew :leveltool:run --args='<command> [--key value | --flag]'

    Commands:
      validate --catalog <path> [--strict]
          Check the catalog and every level file. --strict adds the content rules
          and replays each certificate.
      generate --world <n> [--from <a> --to <b>] [--seed <s>] [--plan-seed <s>]
          Write candidate levels to content/candidates/world-NN/. --seed changes
          the boards, --plan-seed the goals and families of the world.
      solve --level <id> [--difficulty <difficulty>]
          Search a winning line and write its certificate.
      calibrate --level <id> [--games <n>] [--seed <s>]
          Measure the human win rate and adjust the move limit of each variant.
      certify --world <n> [--games <n>] [--seed <s>] [--no-promote]
          Generate, calibrate, solve and promote the levels of a world.
      verify-certificates --catalog <path>
          Replay every certificate of the catalog.
      report --output <path> [--catalog <path>]
          Write the content report as Markdown.
      export-assets --catalog <path> --output <dir>
          Copy the verified content plus a SHA-256 manifest to a directory.
      daily-pool [--output <path>]
          Write the list of levels eligible for the daily challenge.
      stats [--catalog <path>]
          Print the catalog totals.
      puzzles [--pack <1-4>] [--seed <s>]
          Generate and certify the 100 puzzles into content/puzzles/puzzles.json.
      verify-puzzles
          Replay the stored solution of every puzzle.
      retitle
          Rename every level from the title generator and rebuild the catalog.
      help
          Show this text.

    Options:
      --catalog <path>     Catalog file (default content/catalog.json where a default exists).
      --level <id>         Level id, for example level-0128.
      --difficulty <name>  easy, medium or hard.
      --world <n>          World number, 1..12.
      --from <a> --to <b>  Ordinal range for generation.
      --seed <n>           Deterministic seed for generation and calibration.
      --games <n>          Games per calibration step.
      --strict             Enable the strict validation rules.
      --output <path>      Output file or directory.
      --no-promote         certify: keep the candidates instead of publishing them.
""".trimIndent()
