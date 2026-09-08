FROM eclipse-temurin:17-jdk AS java17

FROM jenkins/jenkins:2.568.3-lts-jdk21

USER root
COPY --from=java17 /opt/java/openjdk /opt/java17
RUN test -x /opt/java17/bin/java \
    && git --version \
    && curl --version

USER jenkins
COPY --chown=jenkins:jenkins infra/jenkins/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
