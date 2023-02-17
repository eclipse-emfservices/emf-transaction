# Eclipse EMF Transaction: Release Notes

This page describes the noteworthy improvements provided by each release of Eclipse EMF Transaction.

## 1.14.0 (unreleased)

* Remove support for target platforms older than 2020-09
* Move to Java 11 as minimum version (the version needed by 2020-09)

## 1.13.0

* Releng improvements
  * Move to recent(ish) version of Tychos, CBI plug-ins, etc.
  * Generate Eclipse-SourceReference
  * Support building against multiple target platforms (including a "canary")
  * Remove unused dependencies
  * Use HTTPS only for all target platforms
* Remove EMF Query from the SDK feature
* Drop support for Oxygen and earlier. The oldest version of Eclipse supported is Photon.
* Move to EPL 2.0
* Move to Java 8 as minimum version
* Move to GitHub
  * As part of this, all remaining Bugzilla issues have been closed. Please reopen on GitHub Issues the ones you think are still relevant.
* Ensure support for recent versions of the Eclipse platform
* Fix some compilation warnings
