# Skills

Each skill is a subfolder here containing a `SKILL.md` file:

```
.claude/skills/
  my-skill-name/
    SKILL.md
    (optional supporting files: scripts, templates, references)
```

`SKILL.md` format:

```markdown
---
name: my-skill-name
description: One-line summary of when to use this skill (shown in the skill listing).
---

Instructions the skill loads into the conversation when invoked.
```

Invoke with `/my-skill-name` or by asking Claude to use it.
