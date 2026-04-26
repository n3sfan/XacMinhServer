@echo off
java -Djava.library.path=libs -server -Xms2G -Xmx2G -XX:+AlwaysPreTouch -XX:+ScavengeBeforeFullGC -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dump.hprof -jar XacMinhServer-1.0.jar --serverPath=C:\Users\fynrae\Desktop\Workspace\MCServer-XacMinh --server.port=2052
PAUSE