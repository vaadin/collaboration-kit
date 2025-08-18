#!/bin/bash
# Usage: bash ./scripts/prepareDeploy.sh <version> <token>
set -o pipefail

# return the major.minor numbers of a version
getBaseVersion() {
   echo $1 | tr - . | cut -d . -f1,2;
}
# get latest released version of an artifact by checking maven repos
getLatest() {
   base=`getBaseVersion $2`
   releases=`curl -s "https://repo.maven.apache.org/maven2/com/vaadin/$1/maven-metadata.xml"`
   prereleases=`curl -s "https://maven.vaadin.com/vaadin-prereleases/com/vaadin/$1/maven-metadata.xml"`

   stable=`echo "$releases" | grep '<version>' | cut -d '>' -f2 |cut -d '<' -f1 | grep "^$base" | tail -1`
   [ -n "$stable" ] && echo $stable && return
   pre=`echo "$prereleases" | grep '<version>' | cut -d '>' -f2 |cut -d '<' -f1 | grep "^$base" | grep -v "SNAPSHOT" | egrep 'alpha|beta|rc' | tail -1`
   [ -z "$pre" ] && pre=`echo "$prereleases" | grep '<version>' | cut -d '>' -f2 |cut -d '<' -f1 | egrep 'alpha|beta|rc' | tail -1`
   [ -z "$pre" ] && pre="$2"
   expr "$pre" : ".*SNAPSHOT" >/dev/null && echo "Releases cannot depend on SNAPSHOT: $1 - $pre" && exit 1 || echo $pre
}

getPlatformVersion() {
  name=$1
  [ "$1" = accordion ] && javaVersion="jsVersion" || javaVersion="javaVersion"

  echo "$versions" | jq -r ".core, .vaadin | .[\"$name\"]| .$javaVersion " | grep -v null
}

getNextVersion() {
  [ -z "$1" ] && return
  prefix=`echo $1 | perl -pe 's/[0-9]+$//'`
  number=`echo $1 | perl -pe 's/.*([0-9]+)$/$1/'`
  number=`expr $number + 1` || exit 1
  echo $prefix$number
}

setPomVersion() {
  [ -z "$1" ] && return
  key=`echo $1 | tr - .`".version"
  echo "Setting $key=$2 in pom.xml"
  mvn -B -q -N versions:set-property -Dproperty=$key -DnewVersion=$2 -DgenerateBackupPoms=false|| exit 1
}

### Check that version is given as a parameter and has a valid format
version=$1
! [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+([\.-](alpha|beta|rc)[0-9]+)?$ ]] && echo Invalid version format: $version && exit 1
[[ $version =~ (alpha|beta|rc) ]] && profile=prerelease || profile=maven-central
pomVersion=`cat pom.xml | grep '<version>' | head -1 | cut -d '>' -f2 | cut -d '<' -f1`

token=$2

### Extrat major.minor part from version
versionBase=`getBaseVersion $version`
pomBase=`getBaseVersion $pomVersion`

### Get the main branch version for CE
masterPom=`curl -s "https://$token@raw.githubusercontent.com/vaadin/collaboration-kit/main/pom.xml"`
masterMajorMinor=`echo "$masterPom" | grep '<version>' | cut -d '>' -f2 |cut -d '<' -f1 | grep "^$base" | head -1 | cut -d '-' -f1`

### Load versions file for this platform release
branch=$versionBase
if [ $branch = $masterMajorMinor ]
then
  branch=main
else
  customPom=`curl -s "https://$token@raw.githubusercontent.com/vaadin/collaboration-kit/$versionBase/pom.xml"`
  customMajorMinor=`echo "$customPom" | grep '<vaadin.component.version>' | cut -d '>' -f2 |cut -d '<' -f1 | grep "^$base" | head -1 | cut -d '-' -f1`
  branch=`getBaseVersion $customMajorMinor`
fi

echo $branch

versions=`curl -s "https://raw.githubusercontent.com/vaadin/platform/$branch/versions.json"`
[ $? != 0 ] && branch=main && versions=`curl -s "https://raw.githubusercontent.com/vaadin/platform/$branch/versions.json"`

### Check that current branch is valid for the version to release
[ $branch != main -a "$versionBase" != "$pomBase" ] && echo "Incorrect pomVersion=$pomVersion for version=$version" && exit 1

### Compute flow version
flow=`getPlatformVersion flow`
flow=`getLatest flow $flow`

### Compute cdi version
cdi=`getPlatformVersion flow-cdi`
cdi=`getLatest vaadin-cdi $cdi`

### Compute cdi version
component=`getPlatformVersion accordion`
component=`getLatest vaadin-flow-components-shared-parent $component`

## Modify poms with the versions to release
echo "Setting version=$version to collaboration-engine-internal"
mvn -B -q versions:set -DnewVersion=$version -DgenerateBackupPoms=false|| exit 1

setPomVersion flow $flow
setPomVersion flow.cdi $cdi
setPomVersion vaadin.component $component || exit 1


## Inform TC about computed parameters
echo "##teamcity[setParameter name='ce.branch' value='$branch']"
echo "##teamcity[setParameter name='maven.profile' value='$profile']"
echo "##teamcity[setParameter name='flow.version' value='$flow']"
echo "##teamcity[setParameter name='vaadin.cdi.version' value='$cdi']"
echo "##teamcity[setParameter name='component.version' value='$component']"
