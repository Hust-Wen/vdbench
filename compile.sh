find -name "*.java" > sources.txt
/usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/javac @sources.txt -d ./classes -encoding utf-8 > output.log
/usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/jar -cvf vdbench.jar ./ >> output.log