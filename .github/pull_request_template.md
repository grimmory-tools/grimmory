## 📝 Description

<!-- Why is this change needed? Explain in your own words. -->

> **Required for `develop` and `main`.** Your PR title must use Conventional Commit format because maintainers squash-merge with the PR title and stable releases are computed from commit history. Example: `fix(reader): prevent blank pages on chapter jump`

**Linked Issue:** Fixes #<!-- issue number -->

> **Required.** Every PR must reference an approved issue. If no issue exists, [open one](https://github.com/grimmory-tools/grimmory/issues/new) and wait for maintainer approval before submitting a PR. Unsolicited PRs without a linked issue will be closed.

## 🏷️ Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Enhancement to existing feature
- [ ] Refactor (no behavior change)
- [ ] Breaking change (existing functionality affected)
- [ ] Documentation update

## 🔧 Changes

<!-- List the specific modifications made -->
-

## 🧪 Testing (MANDATORY)

> **PRs without this section filled out will be closed.** "Tests pass" or "Tested locally" is not sufficient. You must provide specifics.

**Manual testing steps you performed:**
<!-- Walk through the exact steps you took to verify your change works. Be specific. -->
1.
2.
3.

**Regression testing:**
<!-- How did you verify that existing related features still work after your change? -->
-

**Edge cases covered:**
<!-- What boundary conditions or unusual inputs did you test? -->
-

**Test output:**
<!-- Paste the actual terminal output from running tests. Not "all pass", the real output. -->

<details>
<summary>Backend test output (<code>./gradlew test</code>)</summary>

```
PASTE OUTPUT HERE
```

</details>

<details>
<summary>Frontend test output (<code>ng test</code>)</summary>

```
PASTE OUTPUT HERE
```

</details>

## 📸 Screen Recording / Screenshots (MANDATORY)

> Every PR must include a **screen recording or screenshots** showing the change working end-to-end in a running local instance (both backend and frontend). This means you must have actually built, run, and tested the code yourself. PRs without visual proof will be closed without review.

<!-- Attach screen recording or screenshots here -->

---

## ✅ Pre-Submission Checklist

> **All boxes must be checked before requesting review.** Incomplete PRs will be closed without review. No exceptions.

- [ ] This PR is linked to an approved issue
- [ ] Code follows project [backend and frontend conventions](../CONTRIBUTING.md#backend-conventions)
- [ ] Branch is up to date with `develop` (merge conflicts resolved)
- [ ] I ran the full stack locally (backend + frontend + database) and verified the change works
- [ ] Automated tests added or updated to cover changes (backend **and** frontend)
- [ ] All tests pass locally and output is pasted above
- [ ] Screen recording or screenshots are attached above proving the change works
- [ ] PR is a single focused change (one bug fix OR one feature, not multiple unrelated changes)
- [ ] PR is reasonably scoped (PRs over 1000+ changed lines will be closed, split into smaller PRs)
- [ ] No unsolicited refactors, cleanups, or "improvements" are bundled in
- [ ] Flyway migration versioning is correct _(if schema was modified)_
- [ ] Required documentation updates are included in this repo or the current Grimmory docs surface _(if user-facing changes)_

### 🤖 AI-Assisted Contributions

> **If any part of this PR was generated or assisted by AI tools (Copilot, Claude, ChatGPT, etc.), all items below are mandatory.** You are fully responsible for every line you submit. "The AI wrote it" is not an excuse, and AI-generated PRs that clearly haven't been reviewed are the #1 reason PRs get closed.

- [ ] I have read and understand every line of this PR and can explain any part of it during review
- [ ] I personally ran the code and verified it works (not just trusted the AI's output)
- [ ] PR is scoped to a single logical change, not a dump of everything the AI suggested
- [ ] Tests validate actual behavior, not just coverage (AI-generated tests often assert nothing meaningful)
- [ ] No dead code, placeholder comments, `TODO`s, or unused scaffolding left behind by AI
- [ ] I did not submit refactors, style changes, or "improvements" the AI suggested beyond the scope of the issue

---

## 💬 Additional Context _(optional)_

<!-- Any extra information or discussion points for reviewers -->
