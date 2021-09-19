init:
	cd ./experiments/charts/flink && \
	helm dep update
	cd ./experiments/charts/flink-job && \
	helm dep update
compile-flink-ar:
	cd ./flink-ar && \
	mvn clean install \
	 -Drat.ignoreErrors=true \
	 -Dmaven.test.skip \
	 -Dcheckstyle.skip \
	 -Dhadoop.version=2.8.0 \
	 -Pinclude-hadoop -f pom.xml

compile-flink:
	cd ./flink &&\
	mvn clean install \
	 -Drat.ignoreErrors=true \
	 -Dmaven.test.skip \
	 -Dcheckstyle.skip \
	 -Dhadoop.version=2.8.0 \
	 -Pinclude-hadoop -f pom.xml

compile-benchmark:
	cd ./betterCloudExample && \
	mvn clean install \
	 -Drat.ignoreErrors=true \
	 -Dmaven.test.skip \
	 -Dcheckstyle.skip \
	 -Dhadoop.version=2.8.0 \
	 -Pinclude-hadoop -f pom.xml

build-flink-image: #1.7.2
	./build-docker-baseline.sh 

build-flink-ar-image:
	# assuming cloned and built Flink repo is located in ./flink
	./build-docker-ar.sh 


