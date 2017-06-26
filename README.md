# Miscellaneous Collection of Nexus 3 maintenance scripts


### Prerequisites

- For editing: Intellij Idea
- For running: Nexus3 repo (admin access to install tasks)


### Scripts

```
purge-maven-builds.groovy
```
- Purges redundant maven build assets and components (from 'maven-builds' repo - configurable'.
Script contains some parameters e.g. number of build artifacts to keep etc

```
purge-maven-releases.groovy
```
- Purges artifacts form the 'maven-releases' repo (configurable). Anything thats not a semantically versioned (configurable) and without a hard release nmuber is deleted.

```
cleanup-empty-components.groovy
```
- Script which looks through a repository (configurable)and removes components which have no assets


All scripts have a 'dryRun' option where you can run the script in non-destructive mode, just logging :)

--------------------------------------
Dragan Kocovski (dragan.k.oz@gmail.com
