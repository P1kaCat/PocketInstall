# Contributing

Propose new distributions, UI improvements, driver support or fixes in this official repository. PocketInstall uses a restrictive proprietary license; access to its source is not permission to republish the app.

1. Open an issue explaining the feature and the problem it solves.
2. Prepare a private working copy and a dedicated branch. Modification, compilation and private testing are permitted solely to prepare this contribution.
3. Submit a pull request here. A working fork is permitted only as necessary for contribution and GitHub functionality. Do not distribute APKs, releases, mirrors or independent derivative applications.
4. Describe behavior, provenance and validation. For a distribution, include its official source, architecture, integrity checks, download size and choices left to the user. Distinguish delivery, installer startup, completed installation and first boot.

## Branches and commits

Use short branch names that describe the change, such as `feature/os-library`, `fix/winpe-network` or `docs/english-banner`. Do not include the name of an editor, assistant or development tool in branch names or commit subjects.

Keep each commit focused on one coherent change. Related implementation, resources and validation may share a commit; unrelated fixes, visual changes and documentation should be separate. Write English commit subjects that explain the resulting behavior, for example `fix(winpe): wait for DHCP before reporting startup`.

Preserve published commits and release tags. Clean up merged working branches once they are no longer needed, while retaining any unfinished work.

By voluntarily submitting a contribution, you confirm that you have the necessary rights and grant maintainers a worldwide, royalty-free, non-exclusive, irrevocable permission to use, modify, integrate and distribute it within PocketInstall under its license, subject to applicable third-party licenses. You retain ownership of your original code. This declaration cannot relicense someone else's dependency.

Maintainers decide whether to integrate proposals. Rejection does not grant permission to redistribute PocketInstall or a modified app. Mandatory legal rights, GitHub's applicable platform permissions and earlier license grants remain unaffected. See [LICENSE](LICENSE).
