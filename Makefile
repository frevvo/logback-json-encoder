MAVEN_ARGS=--settings settings.xml

.PHONY: package
package:
	mvn package ${MAVEN_ARGS}

.PHONY: deploy
deploy:
	mvn deploy ${MAVEN_ARGS}

.PHONY: release
release:
	mvn versions:set ${MAVEN_ARGS}
	mvn clean deploy ${MAVEN_ARGS}
	mvn scm:tag ${MAVEN_ARGS}

