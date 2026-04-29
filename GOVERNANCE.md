# Grimmory Project Governance

> This document describes how the Grimmory project is governed: who participates, how decisions are made, and how the project evolves over time. It is a living document and may itself be amended through the processes described herein.

## Table of Contents

- [Principles](#principles)
- [Roles](#roles)
  - [Contributor](#contributor)
  - [Maintainer](#maintainer)
  - [Core Maintainer](#core-maintainer)
  - [Project Representative](#project-representative)
- [Decision Making](#decision-making)
  - [Lazy Consensus](#lazy-consensus)
  - [Official Voting](#official-voting)
  - [Examples](#examples)
  - [Recording Decisions](#recording-decisions)
- [Releases](#releases)
- [Code of Conduct](#code-of-conduct)
- [Amendments to This Document](#amendments-to-this-document)
- [License](#license)

---

## Principles

Grimmory is an open source project governed by **merit and participation**. Anyone who contributes in a sustained and constructive way can earn greater responsibility. There is no corporate backer or single controlling entity - the project belongs to its community.

We make decisions by **consensus where possible**, and by vote when we must. We prefer open, asynchronous discussion over closed decisions, and we value transparency, good faith, and respect for each other's time.

All Maintainers - regardless of whether they are Core Maintainers - have **equal voices** in project decisions. The distinction between Maintainer and Core Maintainer reflects responsibility and time commitment, not authority.

---

## Roles

### Contributor

Anyone who submits a bug report, opens a pull request, improves documentation, participates in discussions, or otherwise helps the project is a **Contributor**. No formal recognition is required - showing up is enough.

Contributors are the lifeblood of the project. All contributors are expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

### Maintainer

**Maintainers** are Contributors who have demonstrated sustained, high-quality participation and have been granted elevated permissions to the repository.

Maintainers may:

- Manage submitted pull requests according to the project's conventions and technical goals
- Triage issues and manage the repository
- Cast binding votes on project decisions and direction (see [Decision Making](#decision-making))

**How to become a Maintainer:** Maintainers are invited - they do not apply or self-nominate. Any Core Maintainer may propose inviting a Contributor by raising it in a private thread among all Maintainers. The proposal is discussed using the process described in [Decision Making](#decision-making). Only after consensus is reached is the Contributor approached with an invitation. The outcome is not announced publicly without the invitee's acceptance.

### Core Maintainer

**Core Maintainers** are Maintainers who have taken on a deeper level of responsibility and time commitment to the project. They have the same voting rights as Maintainers - the distinction is one of stewardship, not authority.

Core Maintainers take on responsibilities that require elevated repository or infrastructure access, such as managing releases, administering integrations, and making architectural decisions that affect the project's long-term direction. These privileges are a necessity of the work, and not a sign of elevated authority within the governance structure.

Core Maintainers are expected to:

- Participate actively and consistently in project discussions and decisions
- Steward the long-term health and architectural direction of the project
- Model the behaviour they expect from others

**How to become a Core Maintainer:** New Core Maintainers are invited by other Core Maintainers. Any Core Maintainer may propose inviting a Maintainer in a private thread among Core Maintainers, based on a history of sustained participation and a willingness to take on greater responsibility. The proposal is discussed using the process described in [Decision Making](#decision-making) and confirmed by the Core Maintainers only. Only after consensus is reached is the candidate invited.

<!-- The current Maintainers and Core Maintainers are listed in [`MAINTAINERS.md`](MAINTAINERS.md). -->

### Project Representative

The **Project Representative** is a Core Maintainer who serves as the named point of contact for administrative necessities - registries, account ownership, services that require a single responsible individual, and any external bureaucratic obligations. This is a practical role, not a leadership or governance one. The Project Representative has no additional voting power.

The Project Representative may delegate specific ownership responsibilities to another Maintainer at their discretion.

**Election:** The Project Representative is elected by a simple majority vote of Core Maintainers. Any Core Maintainer is eligible.

**Removal or replacement:** The Project Representative may step down at any time. They may be replaced at any time by a **2/3 supermajority vote of Core Maintainers**, following a 72-hour discussion period.

<!-- The current Project Representative is listed in [`MAINTAINERS.md`](MAINTAINERS.md). -->

---

## Decision Making

Grimmory makes decisions through **proposal, discussion, consensus, and recording the outcome**. Exceptions may be made only by following this same decision-making process.

All Maintainers and Core Maintainers have equal, binding votes on project decisions, except where a decision is explicitly scoped to Core Maintainers. No individual has unilateral decision-making power or veto authority.

### Lazy Consensus

Most decisions should proceed by **lazy consensus**. A proposal is made with a reasonable timeframe for feedback, discussion happens in public, and if nobody feels strongly enough to object, the proposal may proceed.

Lazy consensus may be used only when:

- A proposal has been made in the appropriate public venue
- There is a reasonable amount of time for discussion
- There is quorum, meaning at least **3 voting members** are present
- No `-1` vote is made during the proposal and discussion period

If those conditions are met, formal consensus gathering is not required and the proposal may proceed. The decision should then be recorded in the same venue or in the merged pull request so the outcome is visible to the community.

### Official Voting

If any `-1` vote is initially provided, an official vote must occur unless that `-1` is rescinded during discussion.

Official voting also requires:

- A proposal in the appropriate public venue
- A reasonable amount of time between the proposal and the vote
- Quorum, meaning at least **3 voting members** are present

Votes are cast as `+1` (in favour), `0` (abstain), or `-1` (against). A `-1` does **not** function as a veto. Simple majority rules. As long as there are more `+1` votes than `-1` votes, the decision passes.

Once a vote concludes, the decision should be recorded in the same venue or in the merged pull request so the outcome and vote tally remain visible.

### Examples

Examples of how this works in practice:

1. A release is proposed with the message: "I want to do a patch release in 48 hours". If at least two other voting members are present and there is no `-1`, then formal consensus gathering is not required and a release can be made.
2. A breaking change is desired, so a member drafts a short RFC document as a proposal and publishes it with the information that they want to merge initial work toward that goal in 3 days. Voting members are present but there is a `-1` vote. Discussion does not sway the member, and a formal vote is required. If the votes tally more `+1` votes than `-1` votes, the work to enable the breaking change moves forward.
3. A proposal to allow emergency fixes without a proposal is drafted and published for enacting within 3 days. Voting members are present, a `-1` vote is presented, but during discussion it is rescinded. No formal vote is required and the decision is enacted.
4. A proposal to require all members to wear funny hats during official meetings is published, but no voting members are present. No quorum can be made, so no funny hats vote is possible and funny hats are not enforced.

### Recording Decisions

Simple operational decisions may be proposed, discussed, and recorded in Discord when that is the most practical venue.

Larger or longer-lived decisions - including governance changes, significant technical direction, membership decisions, and other choices worth preserving for future reference - should be clarified and recorded in GitHub, whether in a Discussion, an Issue, a pull request, or another project document kept in the repository.

---

## Releases

Releases are proposed by any Maintainer and proceed by lazy consensus under the process described in [Decision Making](#decision-making). A release proposal should include a summary of changes, a proposed version number following the project's versioning convention, and a reasonable timeframe for feedback. If quorum is present and no `-1` vote is raised, the proposing Maintainer may cut the release without a formal vote.

---

## Code of Conduct

All participants in the Grimmory project are subject to the project's [Code of Conduct](CODE_OF_CONDUCT.md). Maintainers are collectively responsible for enforcing it. Reports of conduct violations are handled privately and confidentially by the Core Maintainers.

---

## Amendments to This Document

This governance document may be amended by a simple majority vote of all Maintainers, following a 7-day public discussion period in GitHub Discussions. All proposed changes must be made via a pull request so the diff is visible to the community.

---

## License

Grimmory is released under the terms described in [`LICENSE`](LICENSE).

---

*This document was adopted by the Grimmory Maintainers. It is inspired by the governance models of the [Apache Software Foundation](https://www.apache.org/foundation/how-it-works.html) and other open source communities, adapted for a small, independent project.*
