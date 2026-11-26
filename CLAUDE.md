# CLAUDE.md

@AGENTS.md

## Claude Code specifics

- The SuperPowers plugin is enabled for the project ([`.claude/settings.json`](.claude/settings.json)). Invoke the matching `superpowers:*` skill before acting, as described in [Development workflow](AGENTS.md#development-workflow-superpowers): brainstorming before any new behaviour, writing-plans before code, test-driven-development while coding, verification-before-completion before saying it works.
- Implementation plans live in `documentation/architecture/plans/` and are written in French; subagent briefs and reports go to `.superpowers/sdd/<date>-<topic>/`, which git ignores.
- Subagents running Maven: one build at a time, focused tests during the tasks, the full `./mvnw -B verify` once at the end of the story.
- Personal settings (commit identity, remotes, language of the conversation) belong in `CLAUDE.local.md`, which is not versioned.
