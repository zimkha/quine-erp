# Agents

Each subagent is a single Markdown file here:

```
.claude/agents/
  my-agent-name.md
```

Format:

```markdown
---
name: my-agent-name
description: When Claude should delegate to this agent (used for auto-selection and proactive use).
tools: Read, Grep, Glob      # optional, omit to inherit all tools
model: sonnet                 # optional: sonnet | opus | haiku | inherit
---

System prompt / role definition for this agent: its responsibilities,
constraints, and how it should approach tasks in this project.
```

Reference an agent explicitly with the Agent tool (`subagent_type: my-agent-name`)
or let Claude pick one automatically based on `description`.
