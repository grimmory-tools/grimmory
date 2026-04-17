# Contributing to Grimmory

This document is for anyone considering opening a **discussion**, **issue**, or **pull request**,
whether you're a first-time user reporting something broken or an experienced developer submitting
code.

## Table of Contents

- [How We Work](#how-we-work)
- [First-Time Contributors](#first-time-contributors)
- [Quick Guide](#quick-guide)
  - [I'd like to contribute code](#id-like-to-contribute-code)
  - [Something isn't working](#something-isnt-working)
  - [I have an idea or feature request](#i-have-an-idea-or-feature-request)
  - [I've implemented something and want to submit it](#ive-implemented-something-and-want-to-submit-it)
  - [I need help getting set up or using Grimmory](#i-need-help-getting-set-up-or-using-grimmory)
- [Policies and Conventions](#policies-and-conventions)
  - [Issues Are Actionable](#issues-are-actionable)
  - [Pull Requests Implement an Issue](#pull-requests-implement-an-issue)
  - [Working in the Codebase](#working-in-the-codebase)
  - [Using AI When Contributing](#using-ai-when-contributing)

## How We Work

Grimmory uses a two-stage workflow designed to keep everyone's time well spent.

**Discussions first.** Ideas, bug reports, and support questions all start in
[GitHub Discussions](https://github.com/orgs/grimmory-tools/discussions). This is where problems get
understood, ideas get shaped, and the community weighs in. Nothing is off the table at this stage.
Discussions are where we figure out what's actually worth building or fixing.

**Issues mean it's ready.** When a discussion produces something clear and actionable, a maintainer
will promote it to an issue. That's the signal that the work is agreed, scoped, and ready to be
picked up. All open issues are automatically tracked in the
[Grimmory Roadmap](https://github.com/orgs/grimmory-tools/projects/1), which reflects what the
project is actually planning to do.

**Please don't open pull requests against work that isn't in the issue tracker.** This isn't
gatekeeping. It's how we protect your time. A PR without a backing issue has no guarantee of
direction, scope, or acceptance. Work that starts from an accepted issue gets a clear brief, prompt
review, and a much higher chance of being merged.

If you have something you want to work on that isn't in the tracker yet, open a discussion first.
We'll work through it together.

## First-Time Contributors

Projects like Grimmory run entirely on the energy of people willing to show up and help, and we're
genuinely glad you're here. Every bug report, reproduced issue, tested fix, or submitted feature
makes a real difference to the people who use this every day.

That said, the codebase is large, technically complex, and currently moving fast. There's meaningful
work happening across architecture, cleanup, and capability, and that pace creates a real risk of
well-intentioned contributions pulling in different directions or landing in areas mid-change. These
guardrails aren't here to make contributing feel difficult. They exist so that when you put in the
effort, it lands well, gets a proper review, and doesn't get lost or closed for reasons that had
nothing to do with the quality of your work.

The fastest path to a merged contribution is to find an issue, ask questions if you need to, and
build something that fits the shape of what's been agreed. We'll meet you there.

<!-- PLACEHOLDER: vouch system to be described here once finalised -->

## Quick Guide

### I'd like to contribute code

All issues in the Grimmory issue tracker are [actionable](#issues-are-actionable). Pick one and
start working on it. If you need help or guidance, comment on the issue. Issues that are especially
friendly to newcomers are tagged [`good first issue`][good-first-issue].

[good-first-issue]: https://github.com/grimmory-tools/grimmory/issues?q=is%3Aissue%20is%3Aopen%20label%3A%22good+first+issue%22

### Something isn't working

Search [existing discussions][discussions] and [closed issues][closed-issues] first. Your issue
may already have been reported or fixed.

> [!NOTE]
> If an open discussion already covers your problem, please don't add a "me too" comment. Use the
> upvote button on the discussion or an emoji reaction on the relevant post instead. This shows
> your support without notifying every participant by email.

If it hasn't been reported, open an [**Issue Triage**][bug] discussion and fill in the
template completely. The information it asks for is what contributors need to investigate. Skipping
fields slows things down for everyone.

> [!WARNING]
> A common mistake is to post a bug in the wrong category. If you're experiencing unexpected
> behaviour, use [**Issue Triage**][bug] and not [**Q&A**][qa] or [**Feature Requests & Ideas**][request]. 
> Posting in the wrong place means maintainers or contributors have to ask for information all
> over again, or ask you to repost entirely.

[discussions]: https://github.com/orgs/grimmory-tools/discussions
[closed-issues]: https://github.com/grimmory-tools/grimmory/issues?q=is%3Aissue%20state%3Aclosed
[bug]: https://github.com/orgs/grimmory-tools/discussions/new?category=issue-triage
[request]: https://github.com/orgs/grimmory-tools/discussions/new?category=feature-requests-ideas
[qa]: https://github.com/orgs/grimmory-tools/discussions/new?category=q-a

### I have an idea or feature request

Search first, as someone may have already suggested it. If not, open a discussion in
[**Feature Requests & Ideas**][request].

### I've implemented something and want to submit it

1. If there is an accepted issue for it, open a pull request.
2. If there is no issue yet, open a discussion and link to your branch.

### I need help getting set up or using Grimmory

Open a discussion in [**Q&A**][qa] discussion, or join the [Discord server][discord] and ask there.

[discord]: https://discord.gg/9YJ7HB4n8T

---

## Policies and Conventions

### Issues Are Actionable

The Grimmory [issue tracker](https://github.com/grimmory-tools/grimmory/issues) is for actionable
work items only. No discussion, no feature requests, no speculative ideas. Everything in the tracker
has already been through Discussions and been agreed by a maintainer.

**This means every open issue is ready to be worked on.** If you're not sure whether something
qualifies, open a discussion first.

### Pull Requests Implement an Issue

Pull requests must reference an accepted issue. If you open a pull request for something that isn't
in the tracker, it will be closed. Not because the idea is bad, but because the groundwork hasn't
been done yet.

Issues tagged [`feature`][feature-issues] represent accepted, well-scoped requests. Implement one
as described and your pull request will be accepted with a high degree of certainty.

> [!NOTE]
> **Pull requests are not the place to discuss feature design.** If you want to share a
> work-in-progress, open a discussion and link to your branch.

[feature-issues]: https://github.com/grimmory-tools/grimmory/issues?q=is%3Aissue%20is%3Aopen%20label%3Afeature

### Working in the Codebase

The technical side of development (environment setup, build commands, testing, branch naming, and
commit conventions) lives in [DEVELOPMENT.md](DEVELOPMENT.md). That's the right starting point once
you have an issue to work on.

Grimmory has two distinct codebases, each with their own conventions:

- **Backend** (Java / Spring Boot): [booklore-api/DEVELOPMENT.md](booklore-api/DEVELOPMENT.md)
- **Frontend** (TypeScript / Angular): [frontend/DEVELOPMENT.md](frontend/DEVELOPMENT.md)

If your change touches both, read both.

### Using AI When Contributing

You must understand what you're contributing. Whether that's a bug report, an idea, or a pull
request, it should reflect your own thinking and not unreviewed output from a tool.

The Grimmory project has clear rules for AI usage. Please read the [AI Usage Policy](AI_POLICY.md)
before contributing. **This matters.**
