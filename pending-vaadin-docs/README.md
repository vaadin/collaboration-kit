# Pending patch for vaadin/docs#5888

This directory is a temporary holding place, not part of this repository's
content. It exists because a `GITHUB_TOKEN` expired mid-session, after the
commit below was made but before it could be pushed.

## What's here

`0001-docs-give-collaborative-binder-users-a-real-migratio.patch` applies to
the `docs/collaboration-kit-to-signals-migration-guide` branch of
[vaadin/docs](https://github.com/vaadin/docs), on top of commit `bf6f502`,
which is the current head of [PR #5888](https://github.com/vaadin/docs/pull/5888).

It rewrites the field-highlighting section of the migration guide. The earlier
text claimed the `@vaadin/field-highlighter` overlay couldn't be reused because
its Java wrapper isn't public API. That was wrong: the npm package has a
documented static JavaScript API, and Collaboration Kit drives it purely
through `Element::executeJs`, so application code can do the same and get an
identical result.

The recipe follows Collaboration Kit's own structure rather than reinventing it:
a subclass of `FieldHighlighterInitializer`, which both keeps the frontend
module in the production bundle and re-initializes on every attach; the
`vaadin-highlight-show` and `vaadin-highlight-hide` events for local focus, so
composite fields report the right sub-field; and removal by matching property
and user, so a second show event cannot orphan an entry.

## Applying it

```sh
git clone https://github.com/vaadin/docs.git
cd docs
git switch docs/collaboration-kit-to-signals-migration-guide
git am <this-directory>/0001-docs-give-collaborative-binder-users-a-real-migratio.patch
git push
```

Delete this directory once the patch is on the PR.

## Verification already done

Against a `vaadin/docs` checkout with the patch applied:

* `vale --config=.vale-pr.ini articles/tools/collaboration/migrating-to-signals.adoc`
  reports 0 errors, 0 warnings, and 0 suggestions.
* `asciidoctor -a skip-front-matter` renders the file with no warnings, and
  every internal anchor resolves.
