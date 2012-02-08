#!/bin/bash

getArtifactDir() {
	local group=$1
	local artifact=$2
	local version=$3
	echo ~/.m2/repository/$(echo $1/$2 | sed 's@\.@/@g')/$3
}

getArtifactName() {
	local artifact=$1
	local version=$2
	echo $1-$2
}

getFakeVersion() {
	echo $1 | sed s/SNAPSHOT/FAKE/
}


snapshotDependencies=$(mvn -o dependency:list |grep SNAPSHOT | sed -e s/.*" "// -e s/.[^:]*$//)

echo The following SNAPSHOT dependencies have been found:
echo "$snapshotDependencies"
echo Preparing mock releases of these dependencies.
snapshotBambooVersion=$(grep bamboo.version.*SNAPSHOT pom.xml | sed -e 's@</.*@@' -e 's/.*>//')

if [[ ! $snapshotBambooVersion == *SNAPSHOT ]] ; then
	echo $snapshotBambooVersion is not a snapshot version - just release it.
	exit
fi

parentPoms="com.atlassian.bamboo:atlassian-bamboo-components:jar:$snapshotBambooVersion
com.atlassian.bamboo:atlassian-bamboo:jar:$snapshotBambooVersion
com.atlassian.bamboo:atlassian-bamboo-plugins:jar:$snapshotBambooVersion"

for dependency in $snapshotDependencies $parentPoms; do
	splitDep=( $(echo $dependency | sed s/:/" "/g) )
	group=${splitDep[0]}
	artifact=${splitDep[1]}
	version=${splitDep[3]}

	fakeVersion=$(getFakeVersion $version)

	echo Faking release of $group $artifact $version
	snapshotDir=$(getArtifactDir $group $artifact $version)
	fakeReleaseDir=$(getArtifactDir $group $artifact $fakeVersion)
	
	snapshotArtifact=$(getArtifactName $artifact $version)

	for snapshotArtifactFile in $snapshotDir/${snapshotArtifact}*; do
		fakeReleaseArtifactFile=$(echo $snapshotArtifactFile | sed s/$version/$fakeVersion/g)
		fakeReleaseArtifactDirectory=$(dirname $fakeReleaseArtifactFile)
		mkdir -p $fakeReleaseArtifactDirectory

		case $snapshotArtifactFile in
			*.pom)
				sed s/$version/$fakeVersion/g $snapshotArtifactFile >$fakeReleaseArtifactFile
				;;
			*)
				cp $snapshotArtifactFile $fakeReleaseArtifactFile
				;;
		esac
	done
done

mvn release:clean
mvn -o release:prepare -Dbamboo.version=$(getFakeVersion $snapshotBambooVersion)
