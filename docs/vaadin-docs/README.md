# Documentation staged for vaadin/docs

The files under `articles/` in this directory are written for the
[vaadin/docs](https://github.com/vaadin/docs) repository, not for this one. They
live here because this repository is the only one the change could be committed
to; copy them over to open the actual documentation pull request.

## Contents

| File | Target path in `vaadin/docs` |
| --- | --- |
| `articles/tools/collaboration/migrating-to-signals.adoc` | `articles/tools/collaboration/migrating-to-signals.adoc` |

## Applying

```sh
git clone https://github.com/vaadin/docs.git
cd docs
git switch -c docs/collaboration-kit-to-signals-migration-guide
cp -r <this-directory>/articles/. articles/
```

The new page uses `order: 4`, which places it in the Collaboration Kit
navigation between _Quick Start Guide_ (`order: 2`) and _Binder & Components_
(`order: 5`). No other file has to change for the page to be published.

Optionally, add a pointer from the Collaboration Kit landing page so that
readers who start at the overview find the guide. In
`articles/tools/collaboration/index.adoc`, after the `Feature Limitations`
section:

```asciidoc
[[ce.overview.signals]]
== Migrating to Signals

Vaadin Flow has built-in <<{articles}/flow/ui-state/shared-signals#,shared
signals>> that cover most of what Collaboration Kit does, without an extra
dependency. See <<migrating-to-signals#,Migrating from Collaboration Kit to
Signals>> for a feature-by-feature mapping.
```

## Verification

The page was checked against the `vaadin/docs` tooling:

* `vale --config=.vale-pr.ini articles/tools/collaboration/migrating-to-signals.adoc`
  reports 0 errors, 0 warnings, and 0 suggestions.
* `asciidoctor -a skip-front-matter` renders it with no warnings, and every
  internal anchor and cross-file xref target resolves.

The signal APIs used in the examples were verified against
`com.vaadin.flow.signals` in [vaadin/flow](https://github.com/vaadin/flow) and
against `MessageList.bindItems()` / `AvatarGroup.bindItems()` in
[vaadin/flow-components](https://github.com/vaadin/flow-components).
