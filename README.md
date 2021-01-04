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

## Points studied

### Hazelcast configuration

Most documentation for Hazelcast reference configuration done as a dedicated XML or YAML file. While this study started
this way (and still seeds the configuration from a file), most of the configuration has moved to a code based approach
supported by Spring Boot. In addition to ensure that the configuration of the data grid is synchronized with the code
using it, this has the benefit of delegating extension instantiation, configuration and injection to Spring. Otherwise,
bridging Hazelcast application context with Spring application context is cumbersome and complex.

### MongoDB write-through

The purpose of this Hazelcast IMDG is to have fast-access to long-lived data that must be persisted beyond process
lifecycle.

#### Generic storage concern

Hazelcast storage API is not asynchronous while since we are using Reactive Spring, the `MongoTemplate` is. This forces
to use `.block()` a lot. This is not necessarily a problem, just a remark.

I used the `MetadataAwareValue` version of the store so would track the TTL per entry and persist it instead of relying
on a global TTL defined at the map level. The global TTL will be used to seed the per-entry TTL but changing the TTL of
the map by config will impact only newly created entries. If a map is not subject to expiration semantics, of course we
wouldn't use such pattern (or hardcode all entries to have non expiring TTL).

#### Initial load

As long as at least one node is still available in a Hazelcast cluster, there's no invocation of the `loadAllKeys()`
method. This is invoked only in case where we are starting a new cluster. Notably, this is invoked only the 1st time the
map is interacted with, and it is a blocker for this 1st interaction.

As per Hazelcast documentation, actual loading of entries will be done in batched fashion. As such, the order in which
we are returning those entries is important.

I am not sure if reducing the number of entries is a good approach. It might depend on the map and its use-case. If an
entry is not listed in the initial `loadAllKeys()`, it will be loaded in a lazy fashion when referenced by its id.
However, any task performed on the values themselves will not work (ex: search on secondary index) because Hazelcast
will not be aware of them.

In such case, it might be necessary to force an interaction with the map early to trigger the initial loading early.

#### Change-stream

MongoDB exposes a change stream. This allows a subscriber to be notified of any new entry in the database. Since all
interaction with MongoDB are expected to be done via Hazelcast, in normal operation, the Hazelcast cluster is already
aware of all entries in the database.

If an entry is written in the database without having the Hazelcast cluster being aware, such entry would be loaded in a
lazy fashion if requested by its key. This could however be a problem for use-cases where requests are performed on the
values and not on the keys.

This situation will actually happen in a multi-datacenter deployment. Each datacenter will host its own Hazelcast
cluster but all clusters will share the same MongoDB storage

Hazelcast WAN replication has not been explored as part of this experimentation since it belongs to the Enterprise
distribution.

In this experimentation, the id of the current node is attached to any new entry written in the persistent store. When
receiving notification about a newly created entry in the store, the subscriber compares the id of the node that
published the entry with the ids of nodes part of the cluster the subscriber belongs to. If the id is not found, it
means the entry has been created by another cluster and might not be present in the current cluster. If this is the
case, the subscriber loads the entry in the current cluster.

In the current code, this is applied only for entry creation. This is ok for immutable entries. If the entries can be
updated, it needs to be applied on updates and deletion too to ensure both cluster contains up to date content.

Right now, each process will be a subscriber. It means that any event will be received multiple times and trigger the
cross-datacenter load logic multiple times. This is just an efficiency problem and could be addressed at a later time
(notably by using [Kafka as the actual subscriber][mongo-kafka] of the change stream and then use Kafka consumer groups)
.

[mongo-kafka]: https://docs.mongodb.com/kafka-connector/current/kafka-source

