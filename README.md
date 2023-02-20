## Eclipse EMF Services - EMF Transaction

**NOTE:** As of march of 2021, the source code for EMF Transaction is now hosted on GitHub at https://github.com/eclipse/emf-transaction. If you have Git clones which refers to the old location at git.eclipse.org, update them or you will not get the latest changes.

_EMF Transaction_ provides transactional semantics for (local) EMF model changes, with support for
* multi-threading,
* model integrity,
* batched events,
* and automatic undo/redo support.

The framework also features improved integration between EMF and Eclipse, with traceability between EMF models and workspace resources, and integration with the Eclipse jobs API and the Eclipse operation history.

It is part of the [Eclipse EMF Services](https://projects.eclipse.org/projects/modeling.emfservices) project, which provides libraries that extend the core EMF framework with additional services or more powerful versions of services provided by EMF itself.

### Building

The build uses [Tycho](http://www.eclipse.org/tycho/). To launch a complete build, issue `mvn clean package`from the top-level directory.
The resulting update-site (p2 repository) can be found in `org.eclipse.emf.transaction.repository/target/repository`.

By default the build uses a Oxygen-based target platform to ensure compatibility.
You can specify a different platform from the ones available in platforms available in `org.eclipse.emf.transaction.target`.
For example `mvn clean package -Dplatform=2020-12` to build against a Target Platform which corresponds to Eclipse 2020-12.

### Continuous Integration

The official builds are run in the Eclipse Foundation's infrastructure, at https://ci.eclipse.org/emfservices/.

### Update Sites

Update Sites (p2 repositories) are available at:
* https://download.eclipse.org/modeling/emf/transaction/updates/interim: nightly builds
* https://download.eclipse.org/modeling/emf/transaction/updates/milestones: milestone builds
* https://download.eclipse.org/modeling/emf/transaction/updates/releases: official releases

| Version             | Repository URL                                                                       |
|:--------------------|:-------------------------------------------------------------------------------------|
| **1.13.0** (latest) | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R202208110935 |
| 1.12.0              | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201805140824 |
| 1.11.0              | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201706061339 |
| 1.10.0              | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201606071900 |
| 1.9.0               | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201506010221 |
| 1.8.0               | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201405281451 |
| 1.7.0               | https://download.eclipse.org/modeling/emf/transaction/updates/releases/R201306111400 |

### License

[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
