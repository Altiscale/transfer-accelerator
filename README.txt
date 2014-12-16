TransferAccelerator
===================

TransferAccelerator is a utility to connect clients to multiple replicas of the same server.
Users can also use TransferAccelerator to setup multiple ssh tunnels via jump-host to a single
server.

Building:
=========

To build TransferAccelerator you need to first install JDK and maven. You can then build a jar by running:

mvn package

After building, maven stores Jar file in target/ subdirectory.


Testing:
========

For testing, please run:

mvn test


Usage:
======

Once you build jar file, you can run it using:
java -jar target/TransferAccelerator-0.0.1-jar-with-dependencies.jar

It will print out supported command line arguments.


Use Cases:
==========


- Case 1: Connect to replica servers:

If you have replica servers running on server1:port1, server2:port2, and server3:port3 and you wish to load-balance clients using TransferAccelerator you can run:

java -jar target/TransferAccelerator-0.0.1-jar-with-dependencies.jar -p 14000 -s server1:port1 server2:port2 server3:port3


- Case 2: Connect to httpfs-server behind the firewall via jumphost and single ssh tunnel:

java -jar target/TransferAccelerator-0.0.1-jar-with-dependencies.jar -p 14000 -s localhost:15000 -j jumphost-sshd:22 -y httpfs-server:14000

After starting TransferAccelerator, you can use localhost:12345 as your httpfs-server:
hdfs dfs -ls webhdfs://localhost:14000/


- Case 3: Connect to httpfs-server behind the firewall via jumphost and multiple ssh tunnels:

java -jar target/TransferAccelerator-0.0.1-jar-with-dependencies.jar -p 14000 -s localhost:15000 localhost:15001 localhost:15002 -j sshd-host:22 -y httpfs-server:14000

After starting TransferAccelerator, you can use localhost:12345 as your httpfs-server:
hdfs dfs -ls webhdfs://localhost:14000/


Monitoring:
===========

TransferAccelerator publishes status via http interface running by default on port 1982 (can be overriden using -w,--webstatus_port <STATUS_PORT> command line flag).

You can access this interface in your browser at:
http://localhost:1982/stats

It also prints health status on:
http://localhost:1982/health
