@echo off
java -server -Xms2G -Xmx2G -XX:+AlwaysPreTouch -XX:+ScavengeBeforeFullGC -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=dump.hprof -jar XacMinhServer-1.0.jar --serverPath=C:\Users\thinh\Desktop\Workspace\MCServer --server.port=2052
PAUSE