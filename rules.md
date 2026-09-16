# Project Rules — SkillBook

## Git Commands (NEVER without permission)

The following are **strictly forbidden** unless the user explicitly grants permission first:

- `git commit`
- `git push`
- `git stash`
- `git rebase`
- `git reset` / `git checkout` (discarding changes)
- `git merge`
- `git branch -D` / force operations

You may run read-only Git commands (`git status`, `git diff`, `git log`) without asking.

## Database Commands (NEVER without permission)

The following are **strictly forbidden** unless the user explicitly grants permission first:

- Starting/stopping database containers or services (`docker-compose up`, `docker start/stop`, etc.)
- Creating/dropping databases or tables
- Running migrations (Flyway, SQL scripts, `mvn flyway:*`)
- Running destructive queries (`DELETE`, `DROP`, `TRUNCATE`, `UPDATE` on live data)

Whatever action is requested, explain what you want to run and why, and wait for approval.

## Permission Protocol

1. If an action falls into either forbidden category, **stop and ask** before proceeding.
2. State exactly which command(s) you intend to run.
3. Wait for explicit approval ("yes / go ahead / approved") before executing.