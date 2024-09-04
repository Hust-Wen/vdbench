find -name "*.java" > sources.txt
javac @sources.txt -d ./classes -encoding utf-8 > output.log
jar -cvfM0 vdbench.jar ./ >> output.log