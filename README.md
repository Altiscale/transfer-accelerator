TcpProxy
========

TCP proxy that can connect to multiple replicas of the same server.

Proxy is written for data ingestion project but it is pretty generic.

Intended use is to open multiple ssh tunnels and then start the proxy.
For example:

ssh -f -N -L 14000:httpfs-server-name:14000 -p 22 workbench

ssh -f -N -L 14001:httpfs-server-name:14000 -p 22 workbench

ssh -f -N -L 14002:httpfs-server-name:14000 -p 22 workbench

java -jar TcpProxy-0.0.1-SNAPSHOT-jar-with-dependencies.jar -p 12345 -s localhost:14000 localhost:14001 localhost:14002

After this, you can use localhost:12345 as your HttpFS server. For example:
hdfs dfs -ls webhdfs://localhost:12345/

As long as your hadoop cluster can see this host, you can use distcp to
access files via this proxy. You can also use this proxy when using
copytohdfs.
