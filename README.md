# HZL-Mongo

Small test bench to experiment with Hazelcast backed by a MongoDB store.

Points studied on Hazelcast:

- Spring integration/configuration
- data serialization
- persistent storage with MongoDB
- subscribing to change stream to prime in-memory map with values written directly to DB
- indexing value properties and search in the map using predicates and SQL

## Starting a local MongoDB instance with change stream support

Change stream is available only if your MongoDB server is part of a replica-set. The good news is that you can have a
replica-set of 1 node.

You only need to start your mongo process with the `--replSet` parameter and then execute `rs.initiate()` while
connected to your server.

Just do the following shell commands:

```bash
docker pull mongo
docker run -d --name local_mongo_rs -p 27017:27017 mongo mongod --replSet my-mongo-set
sleep 10
docker exec -it local_mongo_rs mongo --eval "rs.initiate()"
```

You should see a message with the following content along a bunch of other info:
> `"info2" : "no configuration specified. Using a default configuration for the set"`


It means that everything worked fine and that you now have a local instance of a MongoDB server that supports change
stream.
