FROM tomcat:8.5-jre8
COPY ./target/*.war /tmp/runresources/
RUN set -x; mkdir -p ${CATALINA_HOME:?}/deploy && cd /tmp/runresources/ && for f in *.war; do unzip $f -d ${CATALINA_HOME:?}/deploy/$f/; done && cd /tmp && rm -rf /tmp/runresources
COPY ./build-aux/docker/tomcat/ROOT.xml $CATALINA_HOME/conf/Catalina/localhost/ROOT.xml
EXPOSE 8080
CMD ["catalina.sh", "run"]
